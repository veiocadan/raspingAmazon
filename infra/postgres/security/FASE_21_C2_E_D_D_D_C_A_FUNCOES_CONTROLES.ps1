# FASE 21-C2-E-D-D-D-C-A - controles de supervisionamento: somente leitura.
# Nenhuma senha, migracao, processo Java, alteracao de estado ou kill.
Set-StrictMode -Version Latest

function Exigir-RaizProjeto-C {
    if (-not (Test-Path -LiteralPath '.git' -PathType Container) -or
        -not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf) -or
        -not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf)) {
        throw 'C2-E-D-D-D-C-A: executar a partir da raiz C:\dev\rasping-amazon'
    }
}

function Ler-ProcessosWindows-C {
    if (-not (Get-Command Get-CimInstance -ErrorAction SilentlyContinue)) {
        throw 'C2-E-D-D-D-C-A: CIM Windows indisponivel; nao presumir daemon parado'
    }
    try {
        $candidatos = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction Stop)
    } catch {
        throw 'C2-E-D-D-D-C-A: falha ao consultar processos; inspecao inconclusiva'
    }
    $processos = New-Object 'System.Collections.Generic.List[object]'
    foreach ($p in $candidatos) {
        # Falha fechada se nao conseguimos ler a linha de comando de um processo Java.
        if ([string]::IsNullOrWhiteSpace([string]$p.CommandLine)) {
            throw 'C2-E-D-D-D-C-A: existe processo Java com identidade nao verificavel; inspecionar manualmente'
        }
        if ([string]$p.CommandLine -match 'com[.]raspingamazon[.]infrastructure[.]bootstrap[.]ContinuousProcessingMain') {
            $processos.Add($p)
        }
    }
    return @($processos.ToArray())
}

function Ler-RegistroSupervisao-C {
    param([Parameter(Mandatory=$true)][string]$Caminho)

    if (-not [System.IO.Path]::IsPathRooted($Caminho)) {
        throw 'C2-E-D-D-D-C-A: registro deve ter caminho absoluto fora do Git'
    }
    $raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([char[]]@('\','/'))
    $absoluto = [System.IO.Path]::GetFullPath($Caminho)
    if ($absoluto.StartsWith($raiz + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'C2-E-D-D-D-C-A: registro de supervisao nao pode ficar dentro do Git'
    }
    if (-not (Test-Path -LiteralPath $absoluto -PathType Leaf)) {
        return $null
    }
    $item = Get-Item -LiteralPath $absoluto -ErrorAction Stop
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'C2-E-D-D-D-C-A: registro nao pode ser um link simbolico'
    }
    try {
        $registro = [System.IO.File]::ReadAllText($absoluto,[System.Text.Encoding]::UTF8) |
            ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw 'C2-E-D-D-D-C-A: registro nao e um objeto JSON valido; nao executar partida'
    }
    if ($null -eq $registro -or $registro -isnot [pscustomobject]) {
        throw 'C2-E-D-D-D-C-A: formato do registro invalido'
    }
    $chaves = @('schema_version','pid','creation_utc_ticks','java_main_class')
    $presentes = @($registro.PSObject.Properties | Where-Object { $_.MemberType -eq 'NoteProperty' })
    if ($presentes.Count -ne $chaves.Count) {
        throw 'C2-E-D-D-D-C-A: conjunto de chaves do registro invalido'
    }
    foreach ($chave in $chaves) {
        if ($null -eq $registro.PSObject.Properties[$chave]) {
            throw 'C2-E-D-D-D-C-A: chave obrigatoria do registro ausente'
        }
    }
    foreach ($propriedade in $presentes) {
        if ($chaves -cnotcontains $propriedade.Name) {
            throw 'C2-E-D-D-D-C-A: registro tem chave nao autorizada'
        }
    }
    if ([long]$registro.schema_version -ne 1 -or
        [long]$registro.pid -lt 1 -or
        [long]$registro.creation_utc_ticks -lt 1 -or
        $registro.java_main_class -cne 'com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMain') {
        throw 'C2-E-D-D-D-C-A: campos de identidade do registro invalidos'
    }
    return $registro
}

function Obter-StatusSupervisao-C {
    param([Parameter(Mandatory=$true)][string]$CaminhoRegistro)
    $registro = Ler-RegistroSupervisao-C -Caminho $CaminhoRegistro
    $processos = @(Ler-ProcessosWindows-C)
    if ($processos.Count -gt 1) {
        throw 'C2-E-D-D-D-C-A: multiplos daemons encontrados; nao iniciar outro'
    }
    if ($null -eq $registro -and $processos.Count -eq 0) {
        return 'PARADO_SEM_REGISTRO'
    }
    if ($null -eq $registro) {
        throw 'C2-E-D-D-D-C-A: daemon sem registro supervisionado; verificar manualmente'
    }
    if ($processos.Count -eq 0) {
        throw 'C2-E-D-D-D-C-A: registro existente, mas daemon nao localizado; possivel registro obsoleto'
    }
    $p = $processos[0]
    if ([long]$p.ProcessId -ne [long]$registro.pid) {
        throw 'C2-E-D-D-D-C-A: PID registrado diverge do daemon detectado'
    }
    try {
        $ticks = ([datetime]$p.CreationDate).ToUniversalTime().Ticks
    } catch {
        throw 'C2-E-D-D-D-C-A: nao foi possivel conferir data de criacao do processo'
    }
    if ([long]$ticks -ne [long]$registro.creation_utc_ticks) {
        throw 'C2-E-D-D-D-C-A: PID reutilizado ou data de inicio divergente; nao executar parada automatica'
    }
    return 'ATIVO_IDENTIFICADO'
}

function Exigir-PartidaBloqueada-C {
    param([Parameter(Mandatory=$true)][psobject]$Plano)
    if ($Plano.execution_mode -cne 'preparation_only') {
        throw 'C2-E-D-D-D-C-A: modo nao bloqueado; etapa nao autoriza partida'
    }
    foreach ($campo in @(
        'daemon_start_authorized',
        'flyway_migrate_authorized',
        'external_actions_authorized',
        'automatic_restart_authorized'
    )) {
        if ($Plano.PSObject.Properties[$campo].Value -isnot [bool] -or
            $Plano.PSObject.Properties[$campo].Value -ne $false) {
            throw ('C2-E-D-D-D-C-A: autorizacao nao permitida nesta fase: ' + $campo)
        }
    }
    Write-Host '=== C2-E-D-D-D-C-A GUARDA PASS - partida, migracao, efeitos externos e restart bloqueados ==='
}
