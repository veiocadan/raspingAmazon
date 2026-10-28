# FASE 21-F — backup PostgreSQL e restauracao em container isolado.
# Preflight nao acessa o banco. O modo real exige confirmacao explicita.
# Nenhum arquivo de backup ou container de prova e excluido automaticamente.
param(
    [ValidateSet('Verificar', 'BackupERestauracao')]
    [string]$Modo = 'Verificar',
    [switch]$ConfirmarBackupRestauracao,
    [string]$DiretorioBackup = 'C:\dev\rasping-amazon-backups\postgres',
    [string]$DiretorioCredenciais = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Falhar([string]$Mensagem) { throw ('FASE 21-F: ' + $Mensagem) }

function Exigir-SucessoDocker([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { Falhar $Mensagem }
}

function Exigir-Arquivo([string]$Caminho) {
    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) {
        Falhar ('arquivo necessario ausente: ' + $Caminho)
    }
}

function Ler-PoliticaJson([string]$Caminho) {
    Exigir-Arquivo $Caminho
    return Get-Content -LiteralPath $Caminho -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
}

function Validar-Protecoes {
    $branch = ((& git branch --show-current) | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
        Falhar 'branch inesperada'
    }
    $start = Ler-PoliticaJson 'config/operacao/continuous-startup-policy.v1.json'
    $ret = Ler-PoliticaJson 'config/operacao/retention-policy.v1.json'
    if ($start.execution_mode -cne 'preparation_only' -or
        $start.daemon_start_authorized -ne $false -or
        $start.flyway_migrate_authorized -ne $false -or
        $start.external_actions_authorized -ne $false -or
        $start.automatic_restart_authorized -ne $false) {
        Falhar 'startup nao esta bloqueado'
    }
    if ($ret.execution_mode -cne 'inventory_only' -or
        $ret.destructive_actions_authorized -ne $false -or
        $ret.continuous_log_capture_authorized -ne $false) {
        Falhar 'retencao nao esta bloqueada'
    }
    Exigir-Arquivo 'infra/compose.yml'
    Exigir-Arquivo 'infra/postgres/backup/FASE_21_F_CLIENTE_POSTGRES.sh'
    Exigir-Arquivo 'infra/postgres/backup/FASE_21_F_CONTAGENS_READONLY.sql'
    if (-not ((Get-Content 'infra/compose.yml' -Raw -Encoding UTF8) -match 'image:\s*postgres:18\.6')) {
        Falhar 'imagem de PostgreSQL divergente do contrato'
    }
    Write-Host 'PASS - Branch, startup preparation_only e retencao inventory_only confirmados.'
}

function Validar-Docker {
    $docker = Get-Command 'docker.exe' -ErrorAction SilentlyContinue
    if ($null -eq $docker) { Falhar 'docker.exe nao encontrado' }
    & docker image inspect 'postgres:18.6' 1>$null 2>$null
    Exigir-SucessoDocker 'imagem postgres:18.6 indisponivel localmente'
    $estado = ((& docker inspect --format '{{.State.Running}}' 'rasping-amazon-postgres' 2>$null) | Out-String).Trim()
    Exigir-SucessoDocker 'container PostgreSQL nao encontrado'
    if ($estado -cne 'true') { Falhar 'container PostgreSQL nao esta em execucao' }
    $imagem = ((& docker inspect --format '{{.Config.Image}}' 'rasping-amazon-postgres' 2>$null) | Out-String).Trim()
    Exigir-SucessoDocker 'nao foi possivel conferir imagem do container'
    if ($imagem -cne 'postgres:18.6') { Falhar 'imagem do container divergente' }
    $portas = ((& docker inspect --format '{{json .HostConfig.PortBindings}}' 'rasping-amazon-postgres' 2>$null) | Out-String).Trim() | ConvertFrom-Json
    Exigir-SucessoDocker 'nao foi possivel conferir portas'
    $bindings = @($portas.'5432/tcp')
    if ($bindings.Count -ne 1 -or $bindings[0].HostIp -cne '127.0.0.1' -or $bindings[0].HostPort -cne '5432') {
        Falhar 'PostgreSQL nao esta publicado exclusivamente em 127.0.0.1:5432'
    }
    Write-Host 'PASS - Docker PostgreSQL 18.6 presente, ativo e restrito ao loopback.'
}

function Ler-SegredoDpapi([string]$Caminho) {
    Exigir-Arquivo $Caminho
    $seguro = ConvertTo-SecureString ([System.IO.File]::ReadAllText($Caminho).Trim())
    $ponteiro = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try {
        $valor = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ponteiro)
        if ($valor -cnotmatch '^[0-9A-F]{64}$') { Falhar 'credencial DPAPI em formato inesperado' }
        return $valor
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ponteiro)
    }
}

function Preparar-DiretorioPrivado([string]$Caminho) {
    if (-not [System.IO.Path]::IsPathRooted($Caminho)) { Falhar 'caminho de backup nao absoluto' }
    $absoluto = [System.IO.Path]::GetFullPath($Caminho)
    $raizRepo = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([char[]]@('\','/'))
    if ($absoluto.Equals($raizRepo,[System.StringComparison]::OrdinalIgnoreCase) -or
        $absoluto.StartsWith($raizRepo + [System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)) {
        Falhar 'backups devem ficar fora do repositorio'
    }
    $pai = Split-Path -Parent $absoluto
    if (-not (Test-Path -LiteralPath $pai -PathType Container)) { Falhar 'pasta-pai de backups inexistente' }
    $sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $permitidos = @($sid,'S-1-5-18','S-1-5-32-544')
    if (-not (Test-Path -LiteralPath $absoluto)) {
        $aclNova = New-Object System.Security.AccessControl.DirectorySecurity
        $aclNova.SetAccessRuleProtection($true,$false)
        foreach ($textoSid in $permitidos) {
            $s = [System.Security.Principal.SecurityIdentifier]::new($textoSid)
            $r = [System.Security.AccessControl.FileSystemAccessRule]::new(
                $s, [System.Security.AccessControl.FileSystemRights]::FullControl,
                ([System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [System.Security.AccessControl.InheritanceFlags]::ObjectInherit),
                [System.Security.AccessControl.PropagationFlags]::None,
                [System.Security.AccessControl.AccessControlType]::Allow)
            $aclNova.AddAccessRule($r)
        }
        [void][System.IO.Directory]::CreateDirectory($absoluto,$aclNova)
    }
    $item = Get-Item -LiteralPath $absoluto -ErrorAction Stop
    if (-not $item.PSIsContainer -or ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        Falhar 'diretorio de backup invalido ou redirecionado'
    }
    $acl = Get-Acl -LiteralPath $absoluto
    if (-not $acl.AreAccessRulesProtected) { Falhar 'ACL dos backups herda permissoes' }
    foreach ($regra in $acl.Access) {
        if ($regra.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) { continue }
        $id = $regra.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
        if ($permitidos -cnotcontains $id) { Falhar 'ACL dos backups permite outra identidade' }
    }
    return $absoluto
}

function EscreverNovo([string]$Caminho,[string]$Conteudo) {
    $f = [System.IO.File]::Open($Caminho,[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,[System.IO.FileShare]::None)
    try {
        $b = [System.Text.UTF8Encoding]::new($false).GetBytes($Conteudo)
        $f.Write($b,0,$b.Length)
        $f.Flush($true)
    } finally { $f.Dispose() }
}

# Cliente PostgreSQL 18.6 executado em container efemero da MESMA imagem.
# Para evitar quoting de "sh -c" pelo Windows PowerShell 5.1, o codigo
# esta em arquivo .sh versionado, montado somente para leitura.
# A senha DPAPI entra SOMENTE pelo STDIN; nao vai no argv ou no Docker env.
function Obter-MontagemCliente {
    $diretorio = (Resolve-Path -LiteralPath 'infra/postgres/backup' -ErrorAction Stop).ProviderPath
    return ('type=bind,source=' + $diretorio + ',target=/scripts,readonly')
}

function Backup-Real([string]$Pasta,[string]$Nome,[string]$Senha) {
    $montagemBackup = 'type=bind,source=' + $Pasta + ',target=/backup'
    $montagemCliente = Obter-MontagemCliente
    $Senha | & docker run --rm -i --network 'container:rasping-amazon-postgres' `
        --mount $montagemBackup --mount $montagemCliente `
        'postgres:18.6' sh '/scripts/FASE_21_F_CLIENTE_POSTGRES.sh' 'backup' $Nome 1>$null 2>$null
    Exigir-SucessoDocker 'pg_dump falhou; preserve qualquer arquivo parcial e interrompa'
}

function Ler-ContagensOrigem([string]$Senha) {
    $montagemCliente = Obter-MontagemCliente
    $saidas = @($Senha | & docker run --rm -i --network 'container:rasping-amazon-postgres' `
        --mount $montagemCliente `
        'postgres:18.6' sh '/scripts/FASE_21_F_CLIENTE_POSTGRES.sh' 'counts' 2>$null)
    Exigir-SucessoDocker 'consulta de contagens da origem falhou'
    $texto = ($saidas | Out-String).Trim()
    if ($texto -cnotmatch '^[0-9]+(\|[0-9]+){5}$') { Falhar 'contagens da origem em formato inesperado' }
    return $texto
}

function Ler-ContagensRestauracao([string]$Container) {
    # O SQL fica em arquivo, nao em argumento -c com aspas internas (PS 5.1).
    $arquivoSql = (Resolve-Path -LiteralPath 'infra/postgres/backup/FASE_21_F_CONTAGENS_READONLY.sql' -ErrorAction Stop).ProviderPath
    $sqlIsolado = '/tmp/FASE_21_F_CONTAGENS_READONLY.sql'
    & docker cp $arquivoSql ($Container + ':' + $sqlIsolado) 1>$null 2>$null
    Exigir-SucessoDocker 'falha ao disponibilizar SQL readonly no container isolado'
    $linhas = @(& docker exec -u postgres $Container psql -X -w -q -A -t -v ON_ERROR_STOP=1 -U postgres -d rasping_amazon -f $sqlIsolado 2>$null)
    Exigir-SucessoDocker 'consulta de contagens restauradas falhou'
    $texto = ($linhas | Out-String).Trim()
    if ($texto -cnotmatch '^[0-9]+(\|[0-9]+){5}$') { Falhar 'contagens restauradas em formato inesperado' }
    return $texto
}

Validar-Protecoes
Validar-Docker
if ($Modo -ceq 'Verificar') {
    Write-Host 'PASS - Preflight 21-F aprovado; nenhum banco consultado ou modificado.'
    Write-Host 'BLOQUEADO - Backup/restauracao real requer Modo BackupERestauracao e confirmacao explicita.'
    exit 0
}
if (-not $ConfirmarBackupRestauracao) { Falhar 'confirmacao explicita obrigatoria para backup e restauracao' }

$senha = $null
$isolado = $null
$iniciado = $false
try {
    $pasta = Preparar-DiretorioPrivado $DiretorioBackup
    $identidade = [guid]::NewGuid().ToString('N').Substring(0,12)
    $nome = 'rasping_21f_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + $identidade + '.dump'
    $backup = Join-Path $pasta $nome
    $manifesto = $backup + '.manifest.json'
    $prova = $backup + '.restauracao.json'
    foreach ($arquivo in @($backup,$manifesto,$prova)) {
        if (Test-Path -LiteralPath $arquivo) { Falhar 'arquivo ja existe; nada sera sobrescrito' }
    }
    $senha = Ler-SegredoDpapi (Join-Path $DiretorioCredenciais 'rasping_migrator.dpapi')
    Backup-Real -Pasta $pasta -Nome $nome -Senha $senha
    $item = Get-Item -LiteralPath $backup -ErrorAction Stop
    if ($item.Length -lt 100) { Falhar 'arquivo de backup pequeno ou incompleto' }
    $f = [System.IO.File]::OpenRead($backup)
    try {
        $magic = New-Object byte[] 5
        if ($f.Read($magic,0,5) -ne 5 -or [System.Text.Encoding]::ASCII.GetString($magic) -cne 'PGDMP') {
            Falhar 'assinatura do arquivo pg_dump invalida'
        }
    } finally { $f.Dispose() }
    $hash = (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash
    $contagens = Ler-ContagensOrigem -Senha $senha
    $senha = $null
    $meta = [ordered]@{ schema_version = 1; origem = 'rasping_amazon'; tipo = 'pg_dump_custom_postgresql_18_6';
        criado_utc = [DateTime]::UtcNow.ToString('o'); arquivo = $nome;
        bytes = [long]$item.Length; sha256 = $hash; restauracao_validada = $false }
    EscreverNovo -Caminho $manifesto -Conteudo ($meta | ConvertTo-Json -Depth 4)
    Write-Host 'PASS - Backup binario PostgreSQL criado com SHA-256 e manifesto fora do Git.'
    if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -cne $hash) { Falhar 'backup foi alterado' }
    $isolado = 'rasping-21f-restore-' + $identidade
    $saida = & docker run -d --name $isolado --network none -e 'POSTGRES_HOST_AUTH_METHOD=trust' 'postgres:18.6' 2>$null
    Exigir-SucessoDocker 'nao foi possivel criar container de restauracao isolado'
    $iniciado = $true
    $pronto = $false
    for ($tentativa=0; $tentativa -lt 45; $tentativa++) {
        & docker exec -u postgres $isolado pg_isready -q -U postgres 1>$null 2>$null
        if ($LASTEXITCODE -eq 0) { $pronto = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $pronto) { Falhar 'PostgreSQL de restauracao nao ficou pronto' }
    & docker cp $backup "${isolado}:/tmp/rasping_21f.dump" 1>$null 2>$null
    Exigir-SucessoDocker 'falha ao copiar arquivo para container isolado'
    & docker exec -u postgres $isolado pg_restore --list '/tmp/rasping_21f.dump' 1>$null 2>$null
    Exigir-SucessoDocker 'arquivo pg_dump nao pode ser lido por pg_restore'
    & docker exec -u postgres $isolado createdb -U postgres rasping_amazon 1>$null 2>$null
    Exigir-SucessoDocker 'falha ao criar banco somente no container isolado'
    & docker exec -u postgres $isolado pg_restore --exit-on-error --no-owner --no-acl -U postgres -d rasping_amazon '/tmp/rasping_21f.dump' 1>$null 2>$null
    Exigir-SucessoDocker 'restauracao falhou no container isolado'
    $restauradas = Ler-ContagensRestauracao -Container $isolado
    if ($restauradas -cne $contagens) { Falhar 'contagens de tabelas e registros divergentes; preservar evidencias' }
    if ($contagens.Split('|')[4] -eq '0' -or $contagens.Split('|')[5] -eq '0') {
        Falhar 'schema ou historico Flyway nao encontrado na restauracao'
    }
    if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -cne $hash) {
        Falhar 'backup alterado durante a restauracao'
    }
    EscreverNovo -Caminho $prova -Conteudo (([ordered]@{
        schema_version = 1; comprovado_utc = [DateTime]::UtcNow.ToString('o');
        arquivo = $nome; sha256 = $hash; container_isolado = $isolado;
        comparacao_contagens = 'coincidentes'; pg_restore = 'aprovado';
        ambiente = 'docker_network_none'; prod_modificado = $false
    }) | ConvertTo-Json -Depth 4)
    Write-Host 'PASS - Restauracao completa no banco isolado; contagens, schema e Flyway conferidos.'
    Write-Host ('BACKUP=' + $backup)
    Write-Host ('MANIFESTO=' + $manifesto)
    Write-Host ('PROVA=' + $prova)
    Write-Host ('SHA256=' + $hash)
    Write-Host ('CONTAINER_ISOLADO=' + $isolado)
    Write-Host 'PASS - Gate 21-F: backup e restauracao aprovados; banco de origem nao alterado.'
} finally {
    $senha = $null
    if ($iniciado -and $null -ne $isolado) {
        & docker stop -t 15 $isolado 1>$null 2>$null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning 'Container isolado nao parou; inspecionar manualmente; nunca parar o PostgreSQL original.'
        } else {
            Write-Host 'PROTEGIDO - Container restaurado parado e preservado para auditoria; nenhum backup excluido.'
        }
    }
}
