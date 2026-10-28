# FASE 21-E-C1 - Simulacao de elegibilidade temporal. SOMENTE LEITURA.
# Importar este arquivo nao acessa o disco nem executa operacoes externas.
Set-StrictMode -Version Latest

function Assert-21EC1-StartupBloqueado {
    param([Parameter(Mandatory=$true)][object]$Plano)
    $chaves = @('schema_version','execution_mode','initial_schedule_interval',
        'daemon_start_authorized','flyway_migrate_authorized',
        'external_actions_authorized','automatic_restart_authorized',
        'shutdown_grace_seconds')
    Assert-21EA-NomesExatos -Objeto $Plano -Nomes $chaves -Contexto 'startup C1'
    if (($Plano.schema_version -isnot [int] -and $Plano.schema_version -isnot [long]) -or
        $Plano.schema_version -ne 1 -or
        $Plano.execution_mode -isnot [string] -or
        $Plano.execution_mode -cne 'preparation_only' -or
        $Plano.initial_schedule_interval -isnot [string] -or
        $Plano.initial_schedule_interval -cne 'PT1H' -or
        ($Plano.shutdown_grace_seconds -isnot [int] -and $Plano.shutdown_grace_seconds -isnot [long]) -or
        $Plano.shutdown_grace_seconds -ne 30) {
        throw '21-E-C1: contrato de startup inesperado'
    }
    foreach ($campo in @('daemon_start_authorized','flyway_migrate_authorized',
        'external_actions_authorized','automatic_restart_authorized')) {
        if ($Plano.PSObject.Properties[$campo].Value -isnot [bool] -or
            $Plano.PSObject.Properties[$campo].Value -ne $false) {
            throw '21-E-C1: startup ou efeitos externos autorizados'
        }
    }
}

function Test-21EC1-AnteriorAoPrazo {
    param(
        [Parameter(Mandatory=$true)][datetime]$UltimaAlteracaoUtc,
        [Parameter(Mandatory=$true)][datetime]$AgoraUtc,
        [Parameter(Mandatory=$true)][int]$PrazoDias
    )
    if ($PrazoDias -ne 7) { throw '21-E-C1: prazo local nao autorizado' }
    if ($UltimaAlteracaoUtc.Kind -ne [System.DateTimeKind]::Utc -or
        $AgoraUtc.Kind -ne [System.DateTimeKind]::Utc) {
        throw '21-E-C1: horario deve ser UTC'
    }
    return ($UltimaAlteracaoUtc -lt $AgoraUtc.AddDays(-$PrazoDias))
}

function New-21EC1-Contadores {
    return [pscustomobject]@{
        arquivos = [long]0
        bytes = [long]0
        candidatos = [long]0
        bytes_candidatos = [long]0
        redirecionamentos_ignorados = [long]0
    }
}

function Add-21EC1-ArquivoAoResumo {
    param(
        [Parameter(Mandatory=$true)][object]$Contadores,
        [Parameter(Mandatory=$true)][object]$Arquivo,
        [Parameter(Mandatory=$true)][datetime]$AgoraUtc,
        [Parameter(Mandatory=$true)][int]$PrazoDias
    )
    foreach ($nome in @('Attributes','PSIsContainer','Length','LastWriteTimeUtc')) {
        if ($null -eq $Arquivo.PSObject.Properties[$nome]) {
            throw '21-E-C1: metadado incompleto'
        }
    }
    if (($Arquivo.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        $Contadores.redirecionamentos_ignorados++
        return
    }
    if ($Arquivo.PSIsContainer -ne $false) { throw '21-E-C1: entrada nao e arquivo' }
    if ($Arquivo.Length -isnot [int] -and $Arquivo.Length -isnot [long]) {
        throw '21-E-C1: tamanho invalido'
    }
    if ([long]$Arquivo.Length -lt 0) { throw '21-E-C1: tamanho negativo' }
    if ($Arquivo.LastWriteTimeUtc -isnot [datetime]) {
        throw '21-E-C1: data invalida'
    }
    # Checar a classificacao ANTES de alterar os contadores (falha fechada).
    $elegivel = Test-21EC1-AnteriorAoPrazo -UltimaAlteracaoUtc $Arquivo.LastWriteTimeUtc `
        -AgoraUtc $AgoraUtc -PrazoDias $PrazoDias
    $Contadores.arquivos++
    $Contadores.bytes += [long]$Arquivo.Length
    if ($elegivel) {
        $Contadores.candidatos++
        $Contadores.bytes_candidatos += [long]$Arquivo.Length
    }
}

function Get-21EC1-ResumoEscopo {
    param(
        [Parameter(Mandatory=$true)][string]$RaizRepositorio,
        [Parameter(Mandatory=$true)][object]$Regra,
        [Parameter(Mandatory=$true)][datetime]$AgoraUtc
    )
    # A allowlist e imutavel e a politica deve ter sido validada por Assert-21EA-Politica.
    $escopos = @{
        'amazon-diagnostics' = 'target/diagnostics'
        'maven-test-reports' = 'target/surefire-reports'
    }
    if ($Regra.id -isnot [string] -or -not $escopos.ContainsKey($Regra.id) -or
        $Regra.relative_path -isnot [string] -or
        $Regra.relative_path -cne $escopos[$Regra.id] -or
        $Regra.proposed_max_age_days -ne 7) {
        throw '21-E-C1: escopo ou prazo fora da allowlist'
    }
    $raiz = [System.IO.Path]::GetFullPath($RaizRepositorio).TrimEnd([char[]]@('\','/'))
    Assert-21EA-SemRedirecionamento -Diretorio $raiz
    $relativo = $Regra.relative_path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $diretorio = [System.IO.Path]::GetFullPath((Join-Path $raiz $relativo))
    if (-not $diretorio.StartsWith(($raiz + [System.IO.Path]::DirectorySeparatorChar),
        [System.StringComparison]::OrdinalIgnoreCase)) {
        throw '21-E-C1: caminho fora do repositorio'
    }
    Assert-21EA-SemRedirecionamento -Diretorio $diretorio
    $contadores = New-21EC1-Contadores
    if (-not (Test-Path -LiteralPath $diretorio)) {
        return [pscustomobject]@{ escopo=$Regra.id; estado='AUSENTE';
            arquivos=$contadores.arquivos; bytes=$contadores.bytes;
            candidatos=$contadores.candidatos; bytes_candidatos=$contadores.bytes_candidatos;
            redirecionamentos_ignorados=$contadores.redirecionamentos_ignorados }
    }
    if (-not (Test-Path -LiteralPath $diretorio -PathType Container)) {
        throw '21-E-C1: escopo nao e diretorio'
    }
    $fila = New-Object 'System.Collections.Generic.Queue[string]'
    $fila.Enqueue($diretorio)
    while ($fila.Count -gt 0) {
        $pasta = $fila.Dequeue()
        Assert-21EA-SemRedirecionamento -Diretorio $pasta
        foreach ($item in @(Get-ChildItem -LiteralPath $pasta -Force -ErrorAction Stop)) {
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
                $contadores.redirecionamentos_ignorados++
                continue
            }
            if ($item.PSIsContainer) {
                $fila.Enqueue($item.FullName)
                continue
            }
            Add-21EC1-ArquivoAoResumo -Contadores $contadores -Arquivo $item `
                -AgoraUtc $AgoraUtc -PrazoDias ([int]$Regra.proposed_max_age_days)
        }
    }
    return [pscustomobject]@{
        escopo=$Regra.id; estado='PRESENTE';
        arquivos=$contadores.arquivos; bytes=$contadores.bytes;
        candidatos=$contadores.candidatos; bytes_candidatos=$contadores.bytes_candidatos;
        redirecionamentos_ignorados=$contadores.redirecionamentos_ignorados
    }
}
