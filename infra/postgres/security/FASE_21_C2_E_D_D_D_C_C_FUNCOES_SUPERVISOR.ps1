# FASE 21-C2-E-D-D-D-C-C - funcoes de supervisao Windows.
# Nao iniciar ou parar processos ao importar este arquivo.
Set-StrictMode -Version Latest

function Exigir-Arquivos-C-C {
    foreach ($arquivo in @(
        'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1',
        'infra/postgres/security/FASE_21_C2_E_D_D_C_C_POLITICA_FUNCOES.ps1',
        'config/operacao/continuous-startup-policy.v1.json',
        'config/operacao/alert-policy.v1.json',
        'src/main/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignal.java',
        'pom.xml'
    )) {
        if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
            throw ('C2-E-D-D-D-C-C: dependencia ausente: ' + $arquivo)
        }
    }
}

function Ler-PlanoSupervisionado-C-C {
    param([string]$Caminho='config/operacao/continuous-startup-policy.v1.json')
    $plano = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($Caminho),[System.Text.Encoding]::UTF8) | ConvertFrom-Json -ErrorAction Stop
    $chaves = @('schema_version','execution_mode','initial_schedule_interval',
        'daemon_start_authorized','flyway_migrate_authorized','external_actions_authorized',
        'automatic_restart_authorized','shutdown_grace_seconds')
    $props = @($plano.PSObject.Properties | Where-Object { $_.MemberType -eq 'NoteProperty' })
    if ($props.Count -ne $chaves.Count) { throw 'C2-E-D-D-D-C-C: esquema do plano invalido' }
    foreach ($p in $props) { if ($chaves -cnotcontains $p.Name) { throw 'C2-E-D-D-D-C-C: chave inesperada no plano' } }
    foreach ($nome in $chaves) { if ($null -eq $plano.PSObject.Properties[$nome]) { throw 'C2-E-D-D-D-C-C: chave do plano ausente' } }
    if ($plano.schema_version -ne 1 -or $plano.schema_version -is [bool]) { throw 'C2-E-D-D-D-C-C: schema_version invalido' }
    if ($plano.execution_mode -cnotin @('preparation_only','supervised')) { throw 'C2-E-D-D-D-C-C: execution_mode desconhecido' }
    foreach ($nome in @('daemon_start_authorized','flyway_migrate_authorized','external_actions_authorized','automatic_restart_authorized')) {
        if ($plano.PSObject.Properties[$nome].Value -isnot [bool]) { throw 'C2-E-D-D-D-C-C: autorizacao precisa ser booleana' }
    }
    if ($plano.flyway_migrate_authorized -or $plano.automatic_restart_authorized) { throw 'C2-E-D-D-D-C-C: Flyway e reinicio automatico permanecem proibidos' }
    if ($plano.execution_mode -ceq 'preparation_only') {
        if ($plano.daemon_start_authorized -or $plano.external_actions_authorized) {
            throw 'C2-E-D-D-D-C-C: modo preparacao nao permite efeitos externos'
        }
    } else {
        if (-not $plano.daemon_start_authorized -or -not $plano.external_actions_authorized) {
            throw 'C2-E-D-D-D-C-C: modo supervisionado exige autorizacao explicita de efeitos externos'
        }
    }
    if ($plano.initial_schedule_interval -isnot [string]) { throw 'C2-E-D-D-D-C-C: cadencia invalida' }
    try { $intervalo = [System.Xml.XmlConvert]::ToTimeSpan($plano.initial_schedule_interval) }
    catch { throw 'C2-E-D-D-D-C-C: cadencia nao e duracao ISO-8601' }
    if ($intervalo -lt [TimeSpan]::FromMinutes(15) -or $intervalo -gt [TimeSpan]::FromHours(24)) {
        throw 'C2-E-D-D-D-C-C: cadencia fora dos limites do plano'
    }
    if ($plano.shutdown_grace_seconds -isnot [int] -and $plano.shutdown_grace_seconds -isnot [long]) {
        throw 'C2-E-D-D-D-C-C: prazo de parada invalido'
    }
    if ($plano.shutdown_grace_seconds -lt 15 -or $plano.shutdown_grace_seconds -gt 600) {
        throw 'C2-E-D-D-D-C-C: prazo de parada fora do limite'
    }
    return $plano
}

function Exigir-PartidaAutorizada-C-C {
    param([psobject]$Plano)
    if ($Plano.execution_mode -cne 'supervised' -or
        -not $Plano.daemon_start_authorized -or
        -not $Plano.external_actions_authorized) {
        throw 'C2-E-D-D-D-C-C: PARTIDA BLOQUEADA pelo plano preparation_only; nao iniciar daemon'
    }
}

function Exigir-GitPronto-C-C {
    $branch = ((& git branch --show-current 2>$null) | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
        throw 'C2-E-D-D-D-C-C: branch de seguranca inesperada'
    }
    $pendencias = @(& git status --porcelain --untracked-files=all)
    if ($LASTEXITCODE -ne 0 -or $pendencias.Count -ne 0) {
        throw 'C2-E-D-D-D-C-C: Git deve estar limpo antes de uma partida produtiva'
    }
}

function Exigir-CaminhoSupervisao-C-C {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    $absoluto = [System.IO.Path]::GetFullPath($Diretorio)
    $raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([char[]]@('\','/'))
    if (-not [System.IO.Path]::IsPathRooted($Diretorio) -or
        $absoluto.StartsWith($raiz + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -or
        $absoluto.TrimEnd([char[]]@('\','/')) -ceq $raiz) {
        throw 'C2-E-D-D-D-C-C: supervisao deve ficar fora do Git em caminho absoluto'
    }
    if (Test-Path -LiteralPath $absoluto) {
        $item = Get-Item -LiteralPath $absoluto -ErrorAction Stop
        if (-not $item.PSIsContainer -or ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'C2-E-D-D-D-C-C: diretorio de supervisao inseguro ou simbolico'
        }
    }
    return $absoluto
}

function Exigir-ACLPrivada-C-C {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    $acl = Get-Acl -LiteralPath $Diretorio -ErrorAction Stop
    if (-not $acl.AreAccessRulesProtected) { throw 'C2-E-D-D-D-C-C: diretorio de supervisao herda ACLs; recusar' }
    $permitidos = @(
        [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value,
        'S-1-5-18', # SYSTEM
        'S-1-5-32-544' # Administradores
    )
    foreach ($regra in $acl.Access) {
        if ($regra.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) { continue }
        $sid = $regra.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
        $direitos = $regra.FileSystemRights
        $escrita = [System.Security.AccessControl.FileSystemRights]::Write -bor
                   [System.Security.AccessControl.FileSystemRights]::Modify -bor
                   [System.Security.AccessControl.FileSystemRights]::FullControl -bor
                   [System.Security.AccessControl.FileSystemRights]::Delete
        if ($permitidos -notcontains $sid -and ($direitos -band $escrita) -ne 0) {
            throw 'C2-E-D-D-D-C-C: ACL permite escrita de identidade nao autorizada'
        }
    }
}

function Criar-DiretorioPrivado-C-C {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    if (Test-Path -LiteralPath $Diretorio) {
        Exigir-ACLPrivada-C-C -Diretorio $Diretorio
        return
    }
    $pai = Split-Path -Parent $Diretorio
    if (-not (Test-Path -LiteralPath $pai -PathType Container)) { throw 'C2-E-D-D-D-C-C: pasta pai do backup nao existe' }
    $conta = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $seguranca = New-Object System.Security.AccessControl.DirectorySecurity
    $seguranca.SetAccessRuleProtection($true,$false)
    foreach ($sidTexto in @($conta.Value,'S-1-5-18','S-1-5-32-544')) {
        $sid = [System.Security.Principal.SecurityIdentifier]::new($sidTexto)
        $regra = [System.Security.AccessControl.FileSystemAccessRule]::new(
            $sid,
            [System.Security.AccessControl.FileSystemRights]::FullControl,
            [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [System.Security.AccessControl.InheritanceFlags]::ObjectInherit,
            [System.Security.AccessControl.PropagationFlags]::None,
            [System.Security.AccessControl.AccessControlType]::Allow
        )
        $seguranca.AddAccessRule($regra)
    }
    [void][System.IO.Directory]::CreateDirectory($Diretorio,$seguranca)
    Exigir-ACLPrivada-C-C -Diretorio $Diretorio
}

function Ler-SenhaDPAPI-C-C {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) { throw 'C2-E-D-D-D-C-C: credencial DPAPI ausente' }
    $seguro = ConvertTo-SecureString -String ([System.IO.File]::ReadAllText($Caminho).Trim())
    $ponteiro = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try {
        $valor = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ponteiro)
        if ($valor -cnotmatch '^[0-9A-F]{64}$') { throw 'C2-E-D-D-D-C-C: formato DPAPI inesperado' }
        return $valor
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ponteiro)
    }
}

function Exigir-SemSolicitacaoAntiga-C-C {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    if (Test-Path -LiteralPath $Caminho) {
        throw 'C2-E-D-D-D-C-C: pedido antigo de parada presente; investigar antes de partida'
    }
}


# FASE 21-C2-E-D-D-D-D-G - remocao de credenciais privilegiadas herdadas.
# Recebe o dicionario do PROCESSO FILHO, sem alterar o ambiente do PowerShell.
# Nao armazena, devolve ou registra o valor de qualquer variavel removida.
function Remover-VariaveisPrivilegiadas-C-C {
    param(
        [Parameter(Mandatory=$true)]
        [System.Collections.Specialized.StringDictionary]$Variaveis
    )

    # ProcessStartInfo.EnvironmentVariables inclui valores herdados da sessao.
    # Nao basta evitar atribuir DB_MIGRATION_PASSWORD explicitamente.
    foreach ($nome in @($Variaveis.Keys)) {
        if ($nome -match '^(DB_MIGRATION_|FLYWAY_|PG(HOST|PORT|DATABASE|USER|PASSWORD|PASSFILE|SERVICE|SERVICEFILE)$)') {
            [void]$Variaveis.Remove($nome)
        }
    }
}
