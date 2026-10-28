# FASE 21-E-B3 - contrato OFFLINE entre JSON Lines Java e diagnosticos de console.
# Importar nao inicia processos, nao grava arquivos e nao modifica o supervisor.
Set-StrictMode -Version Latest

function Test-21EB3-EventoJavaSeguro {
    param([Parameter(Mandatory=$true)][string]$Linha)
    if ($Linha.Length -gt 32768 -or $Linha.Contains("`r") -or $Linha.Contains("`n")) {
        return $false
    }
    try { $obj = $Linha | ConvertFrom-Json -ErrorAction Stop }
    catch { return $false }
    if ($null -eq $obj -or $obj -isnot [pscustomobject]) { return $false }

    $campos = @('timestamp','level','event','component','operation','outcome',
        'runId','jobId','jobType','candidateId','snapshotId','evaluationId',
        'publicationId','asin','integration','durationMs','failureOrigin',
        'failureType','errorCode')
    foreach ($propriedade in $obj.PSObject.Properties) {
        if ($campos -cnotcontains $propriedade.Name -or $null -eq $propriedade.Value) { return $false }
        if ($propriedade.Value -is [array] -or $propriedade.Value -is [pscustomobject] -or
            $propriedade.Value -is [bool]) { return $false }
    }
    foreach ($chave in @('timestamp','level','event','component','operation','outcome')) {
        if ($obj.PSObject.Properties.Name -cnotcontains $chave -or
            $obj.$chave -isnot [string] -or [string]::IsNullOrWhiteSpace($obj.$chave)) {
            return $false
        }
    }
    if ($obj.timestamp.Length -gt 64 -or
        $obj.timestamp -cnotmatch '^\d{4}-\d{2}-\d{2}T' -or
        $obj.timestamp -cnotmatch '(Z|[+-]\d{2}:\d{2})$') { return $false }
    try { [void][DateTimeOffset]::Parse($obj.timestamp, [Globalization.CultureInfo]::InvariantCulture) }
    catch { return $false }
    if (@('DEBUG','INFO','WARN','ERROR') -cnotcontains $obj.level) { return $false }

    foreach ($chave in @('event','component','operation','outcome')) {
        if ($obj.$chave -cnotmatch '^[A-Za-z][A-Za-z0-9_.:-]{0,119}$') { return $false }
    }
    foreach ($chave in @('jobType','integration','failureOrigin','failureType','errorCode')) {
        if ($obj.PSObject.Properties.Name -ccontains $chave) {
            $valor = $obj.$chave
            if ($valor -isnot [string] -or $valor -cnotmatch '^[A-Za-z][A-Za-z0-9_.:-]{0,119}$') {
                return $false
            }
        }
    }
    if ($obj.PSObject.Properties.Name -ccontains 'asin' -and
        ($obj.asin -isnot [string] -or $obj.asin -cnotmatch '^[A-Z0-9]{10}$')) {
        return $false
    }
    foreach ($chave in @('runId','jobId','candidateId','snapshotId','evaluationId',
        'publicationId','durationMs')) {
        if ($obj.PSObject.Properties.Name -ccontains $chave) {
            $valor = $obj.$chave
            if (($valor -isnot [int] -and $valor -isnot [long]) -or
                $valor -lt $(if ($chave -ceq 'durationMs') { 0 } else { 1 })) {
                return $false
            }
        }
    }
    $propriedadesFalha = @('failureOrigin','failureType','errorCode')
    $quantidade = @($propriedadesFalha | Where-Object { $obj.PSObject.Properties.Name -ccontains $_ }).Count
    if ($quantidade -ne 0 -and $quantidade -ne 3) { return $false }
    if ($quantidade -eq 3) {
        if (@('INTERNAL','EXTERNAL') -cnotcontains $obj.failureOrigin) { return $false }
    }
    return $true
}

function New-21EB3-MarcadorRedigido {
    param(
        [Parameter(Mandatory=$true)][ValidateSet('stdout','stderr')][string]$Canal,
        [Parameter(Mandatory=$true)][string]$Evento,
        [Parameter(Mandatory=$true)][string]$Codigo
    )
    # Jamais incluir o texto original, nem hash reversivel por dicionario.
    $marcador = [ordered]@{
        timestamp = [DateTimeOffset]::UtcNow.ToString('o')
        level = 'ERROR'
        event = $Evento
        component = 'runtime-capture'
        operation = 'safe-classification'
        outcome = 'FAILURE'
        errorCode = $Codigo
    }
    return (ConvertTo-Json -InputObject $marcador -Compress -Depth 3)
}

function Convert-21EB3-LinhaConsole {
    param(
        [AllowEmptyString()][Parameter(Mandatory=$true)][string]$Linha,
        [Parameter(Mandatory=$true)][ValidateSet('stdout','stderr')][string]$Canal
    )
    if ($Canal -ceq 'stdout') {
        if (-not [string]::IsNullOrWhiteSpace($Linha) -and
            (Test-21EB3-EventoJavaSeguro -Linha $Linha)) {
            # O texto vem de um objeto JSON validado pela lista permitida.
            # Nao aceitar campos arbitrarios nem strings livres extensas.
            $objeto = $Linha | ConvertFrom-Json -ErrorAction Stop
            $json = ConvertTo-Json -InputObject $objeto -Compress -Depth 3
            return [pscustomobject]@{ Classificacao='STRUCTURED'; LinhaJson=$json }
        }
        return [pscustomobject]@{
            Classificacao='REDACTED';
            LinhaJson=(New-21EB3-MarcadorRedigido -Canal stdout `
                -Evento 'runtime.stdout.unclassified' -Codigo 'STDOUT_UNCLASSIFIED_REDACTED')
        }
    }

    if ($Linha -ceq 'Continuous processing interrupted') {
        $evento = 'runtime.stderr.interrupted'
        $codigo = 'DAEMON_INTERRUPTED'
    } elseif ($Linha -cmatch '^Continuous processing (failed|cleanup failed|shutdown failed): [A-Za-z_$][A-Za-z0-9_$]{0,99} \(details suppressed\)$') {
        switch -CaseSensitive ($Matches[1]) {
            'failed' { $evento = 'runtime.stderr.failed'; $codigo = 'DAEMON_OPERATION_FAILED' }
            'cleanup failed' { $evento = 'runtime.stderr.cleanup_failed'; $codigo = 'DAEMON_CLEANUP_FAILED' }
            'shutdown failed' { $evento = 'runtime.stderr.shutdown_failed'; $codigo = 'DAEMON_SHUTDOWN_FAILED' }
            default { throw '21-E-B3: estado de classificacao invalido' }
        }
    } else {
        $evento = 'runtime.stderr.unclassified'
        $codigo = 'STDERR_UNCLASSIFIED_REDACTED'
    }
    return [pscustomobject]@{
        Classificacao='REDACTED'
        LinhaJson=(New-21EB3-MarcadorRedigido -Canal stderr -Evento $evento -Codigo $codigo)
    }
}
