# FASE 21-C2-E-D-D-D-B - contrato de politica de startup supervisionado.
# Apenas funcoes puras de validacao. Nenhuma credencial ou processo produtivo.
Set-StrictMode -Version Latest

function Exigir-Arquivo-B {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) {
        throw ('C2-E-D-D-D-B: arquivo ausente: ' + $Caminho)
    }
}

function Ler-PlanoStartup-B {
    param([Parameter(Mandatory=$true)][string]$Caminho)
    Exigir-Arquivo-B -Caminho $Caminho
    try {
        $objeto = [System.IO.File]::ReadAllText(
            [System.IO.Path]::GetFullPath($Caminho),
            [System.Text.Encoding]::UTF8
        ) | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw 'C2-E-D-D-D-B: JSON de partida invalido; nao ativar daemon'
    }
    if ($null -eq $objeto -or $objeto -isnot [pscustomobject]) {
        throw 'C2-E-D-D-D-B: politica precisa ser um objeto JSON'
    }

    $esperadas = @(
        'schema_version',
        'execution_mode',
        'initial_schedule_interval',
        'daemon_start_authorized',
        'flyway_migrate_authorized',
        'external_actions_authorized',
        'automatic_restart_authorized',
        'shutdown_grace_seconds'
    )
    $propriedades = @($objeto.PSObject.Properties | Where-Object { $_.MemberType -eq 'NoteProperty' })
    if ($propriedades.Count -ne $esperadas.Count) {
        throw 'C2-E-D-D-D-B: politica possui numero incorreto de chaves'
    }
    foreach ($propriedade in $propriedades) {
        if ($esperadas -cnotcontains $propriedade.Name) {
            throw 'C2-E-D-D-D-B: politica possui chave nao autorizada'
        }
    }
    foreach ($nome in $esperadas) {
        if (@($propriedades | Where-Object { $_.Name -ceq $nome }).Count -ne 1) {
            throw 'C2-E-D-D-D-B: politica possui chave obrigatoria ausente'
        }
    }

    if (($objeto.schema_version -isnot [int] -and $objeto.schema_version -isnot [long]) -or
        $objeto.schema_version -ne 1) {
        throw 'C2-E-D-D-D-B: schema_version deve ser o inteiro 1'
    }
    if ($objeto.execution_mode -isnot [string] -or
        $objeto.execution_mode -cne 'preparation_only') {
        throw 'C2-E-D-D-D-B: execution_mode deve permanecer preparation_only'
    }
    foreach ($nome in @(
        'daemon_start_authorized',
        'flyway_migrate_authorized',
        'external_actions_authorized',
        'automatic_restart_authorized'
    )) {
        $valor = $objeto.PSObject.Properties[$nome].Value
        if ($valor -isnot [bool] -or $valor -ne $false) {
            throw ('C2-E-D-D-D-B: bloqueio precisa permanecer false: ' + $nome)
        }
    }

    if ($objeto.initial_schedule_interval -isnot [string]) {
        throw 'C2-E-D-D-D-B: intervalo precisa ser texto ISO-8601'
    }
    try {
        $intervalo = [System.Xml.XmlConvert]::ToTimeSpan($objeto.initial_schedule_interval)
    } catch {
        throw 'C2-E-D-D-D-B: intervalo nao esta em formato ISO-8601'
    }
    if ($intervalo -lt [TimeSpan]::FromMinutes(15) -or
        $intervalo -gt [TimeSpan]::FromHours(24)) {
        throw 'C2-E-D-D-D-B: proposta inicial precisa estar entre 15 minutos e 24 horas'
    }
    if (($objeto.shutdown_grace_seconds -isnot [int] -and
         $objeto.shutdown_grace_seconds -isnot [long]) -or
        $objeto.shutdown_grace_seconds -lt 15 -or
        $objeto.shutdown_grace_seconds -gt 600) {
        throw 'C2-E-D-D-D-B: janela de shutdown deve ser inteiro de 15 a 600 segundos'
    }
    return $objeto
}

function Verificar-ContratoRuntime-B {
    $caminhoProvider = 'src/main/java/com/raspingamazon/infrastructure/config/ContinuousProcessingEnvironmentConfigProvider.java'
    $caminhoDaemon = 'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java'
    $caminhoBootstrap = 'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingBootstrap.java'
    foreach ($arquivo in @($caminhoProvider,$caminhoDaemon,$caminhoBootstrap)) {
        Exigir-Arquivo-B -Caminho $arquivo
    }
    $provider = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($caminhoProvider))
    $principal = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($caminhoDaemon))
    $bootstrap = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($caminhoBootstrap))
    if (-not $provider.Contains('PROCESSING_SCHEDULE_INTERVAL') -or
        $provider -notmatch 'readRequired\s*\(') {
        throw 'C2-E-D-D-D-B: Java nao exige explicitamente a cadencia do schedule'
    }
    if ($principal -notmatch 'application\.start\s*\(' -or
        $bootstrap -notmatch 'startupRecoveryService\s*\(' -or
        $bootstrap -notmatch 'EnsureProcessingScheduleUseCase') {
        throw 'C2-E-D-D-D-B: contrato do daemon mudou; rever antes de partir'
    }
    Write-Host '=== C2-E-D-D-D-B JAVA PASS - cadencia obrigatoria e efeitos de startup reconhecidos ==='
}

function Verificar-Git-B {
    if (-not (Test-Path -LiteralPath '.git' -PathType Container)) {
        throw 'C2-E-D-D-D-B: executar na raiz do projeto Git'
    }
    $branch = ((& git branch --show-current 2>$null) | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
        throw 'C2-E-D-D-D-B: branch de seguranca esperada nao encontrada'
    }
    & git diff --quiet HEAD --
    if ($LASTEXITCODE -ne 0) {
        throw 'C2-E-D-D-D-B: arquivos rastreados modificados; verificar Git antes de avancar'
    }
    # Arquivos novos deste ZIP podem aparecer como untracked, intencionalmente.
    Write-Host '=== C2-E-D-D-D-B GIT PASS - branch correta, nenhum arquivo rastreado modificado ==='
}

function Obter-PresencaChave-B {
    param([Parameter(Mandatory=$true)][string]$Nome)
    $processo = [System.Environment]::GetEnvironmentVariable($Nome,'Process')
    if ([string]::IsNullOrWhiteSpace($processo)) { $presencaProcesso = 'AUSENTE' }
    else { $presencaProcesso = 'PRESENTE' }
    $presencaEnv = 'ARQUIVO_AUSENTE'
    if (Test-Path -LiteralPath '.env' -PathType Leaf) {
        $presencaEnv = 'CHAVE_AUSENTE'
        foreach ($linha in [System.IO.File]::ReadAllLines([System.IO.Path]::GetFullPath('.env'))) {
            if ($linha -match ('^\s*' + [regex]::Escape($Nome) + '\s*=')) {
                $presencaEnv = 'CHAVE_PRESENTE'
                break
            }
        }
    }
    return ('PROCESSO=' + $presencaProcesso + ' | .ENV=' + $presencaEnv)
}
