# FASE 21-C2-E-D-D-D-D-B — inventario sem executar daemon, Flyway ou escrita SQL.
param(
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$NomeContainer = 'rasping-amazon-postgres',
    [string]$RegistroProdutivo = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'

Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
Exigir-PartidaBloqueada-C -Plano $plano
if ((Obter-StatusSupervisao-C -CaminhoRegistro $RegistroProdutivo) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-B: o daemon produtivo precisa estar parado e sem registro'
}
Write-Host '=== C2-E-D-D-D-D-B CONTROLES PASS - bloqueio produtivo e ausencia de daemon confirmados ==='

$branch = ((& git branch --show-current 2>$null) | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw 'D-B: branch Git inesperada'
}
$mudancasRastreadas = @(& git diff --name-only HEAD)
if ($LASTEXITCODE -ne 0 -or $mudancasRastreadas.Count -gt 0) {
    throw 'D-B: existe modificacao em arquivo ja versionado; investigar'
}

$scriptSQL = 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_B_INVENTARIO_READONLY.sql'
$scriptShell = 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_B_CONSULTAR_INVENTARIO.sh'
foreach ($arquivo in @($scriptSQL,$scriptShell)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) { throw ('D-B: arquivo ausente: ' + $arquivo) }
}
if (-not (Test-Path -LiteralPath $DiretorioSegredos -PathType Container)) {
    throw 'D-B: pasta DPAPI ausente; nao reprovisionar segredos'
}
$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([char[]]@('\','/'))
$caminhoSegredos = [System.IO.Path]::GetFullPath($DiretorioSegredos)
if ($caminhoSegredos.StartsWith($raiz + [System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'D-B: credenciais nao podem ficar dentro do repositorio'
}

$arquivoEnvExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$hashEnvAntes = $null
if ($arquivoEnvExistia) { $hashEnvAntes = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash }

$porta = & docker inspect --format '{{json .HostConfig.PortBindings}}' $NomeContainer
if ($LASTEXITCODE -ne 0) { throw 'D-B: Docker inspect falhou' }
$bindings = $porta | ConvertFrom-Json
$entradas = @($bindings.'5432/tcp')
if ($entradas.Count -ne 1 -or $entradas[0].HostIp -cne '127.0.0.1' -or $entradas[0].HostPort -cne '5432') {
    throw 'D-B: PostgreSQL nao esta restrito ao loopback'
}
Write-Host '=== C2-E-D-D-D-D-B PORTA PASS - PostgreSQL em 127.0.0.1:5432 ==='

foreach ($arquivo in @($scriptSQL,$scriptShell)) {
    $nome = [System.IO.Path]::GetFileName($arquivo)
    & docker cp $arquivo ($NomeContainer + ':/tmp/' + $nome) | Out-Null
    if ($LASTEXITCODE -ne 0) { throw ('D-B: falha na copia do arquivo publico ' + $nome) }
}

$segredo = $null
$linhas = @()
try {
    $segredo = Ler-SenhaDPAPI-C-C -Caminho (Join-Path $caminhoSegredos 'rasping_runtime.dpapi')
    $linhas = @($segredo | & docker exec -i $NomeContainer sh '/tmp/FASE_21_C2_E_D_D_D_D_B_CONSULTAR_INVENTARIO.sh' 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'D-B: inventario SQL reprovado; nao executar daemon'
    }
} finally {
    Remove-Variable segredo -ErrorAction SilentlyContinue
}

$esperadas = @(
    'jobs_dead','jobs_pending','jobs_retry_wait','jobs_running','jobs_total',
    'outbox_delivery_unknown','outbox_processing','outbox_total',
    'runs_running','runs_total',
    'schedules_enabled','schedules_enabled_due','schedules_total'
)
$observadas = @{}
foreach ($linha in $linhas) {
    $linha = [string]$linha
    if ($linha -cnotmatch '^([a-z_]+)\|([0-9]+)$') {
        throw 'D-B: resposta SQL inesperada, recusar sem exibir dados'
    }
    $nome = $Matches[1]
    if ($esperadas -cnotcontains $nome -or $observadas.ContainsKey($nome)) {
        throw 'D-B: resposta SQL duplicada ou nao autorizada'
    }
    $observadas[$nome] = [long]::Parse($Matches[2],[System.Globalization.CultureInfo]::InvariantCulture)
}
if ($observadas.Count -ne $esperadas.Count) {
    throw 'D-B: inventario incompleto'
}
foreach ($nome in $esperadas) {
    Write-Host ('=== C2-E-D-D-D-D-B CONTAGEM ' + $nome + '=' + $observadas[$nome] + ' ===')
}
Write-Host '=== C2-E-D-D-D-D-B SQL READ ONLY PASS - contagens agregadas, sem registros de negocio ==='

if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $arquivoEnvExistia) {
    throw 'D-B: existencia do .env foi alterada'
}
if ($arquivoEnvExistia -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $hashEnvAntes) {
    throw 'D-B: hash .env foi alterado'
}
if ((Obter-StatusSupervisao-C -CaminhoRegistro $RegistroProdutivo) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-B: daemon produtivo ou registro inesperado apos a consulta'
}
Write-Host '=== C2-E-D-D-D-D-B AMBIENTE PASS - .env e daemon preservados ==='
Write-Host '=== C2-E-D-D-D-D-B INVENTARIO PASS - somente leitura comprovada ==='
Write-Host '=== C2-E-D-D-D-D-B BLOQUEIO ATIVO - nao iniciar daemon, nao executar Flyway migrate ==='
