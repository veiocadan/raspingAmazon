# FASE 21-E-B8: catalogo estrito e projecao offline de eventos.
# Nao inicia processos, nao abre conexoes e nao escreve em disco.

function Get-21EB8-DefinicoesEsperadas {
    return @(
        [pscustomobject]@{ code='processing.job.claimed'; component='processing-worker'; operation='execute-job'; level='INFO'; outcomes=@('CLAIMED') },
        [pscustomobject]@{ code='processing.job.succeeded'; component='processing-worker'; operation='execute-job'; level='INFO'; outcomes=@('SUCCEEDED') },
        [pscustomobject]@{ code='processing.job.retry-scheduled'; component='processing-worker'; operation='execute-job'; level='WARN'; outcomes=@('RETRY_WAIT') },
        [pscustomobject]@{ code='processing.job.dead'; component='processing-worker'; operation='execute-job'; level='ERROR'; outcomes=@('DEAD') },
        [pscustomobject]@{ code='processing.job.failure-handled'; component='processing-worker'; operation='execute-job'; level='WARN'; outcomes=@('PENDING','RUNNING','RETRY_WAIT','SUCCEEDED','DEAD') },
        [pscustomobject]@{ code='integration.observation.persistence-failed'; component='integration-observation-recorder'; operation='persist-observation'; level='ERROR'; outcomes=@('FAILURE') }
    )
}

function Assert-21EB8-NomesExatos {
    param(
        [Parameter(Mandatory=$true)] [object] $Objeto,
        [Parameter(Mandatory=$true)] [string[]] $Nomes
    )
    if ($null -eq $Objeto -or $Objeto -isnot [pscustomobject]) {
        throw '21-E-B8: objeto do contrato invalido.'
    }
    $atuais = @($Objeto.PSObject.Properties.Name | Sort-Object -CaseSensitive)
    $esperados = @($Nomes | Sort-Object -CaseSensitive)
    if ($atuais.Count -ne $esperados.Count) {
        throw '21-E-B8: numero inesperado de propriedades.'
    }
    for ($i=0; $i -lt $esperados.Count; $i++) {
        if ($atuais[$i] -cne $esperados[$i]) {
            throw '21-E-B8: propriedade inesperada no contrato.'
        }
    }
}

function Assert-21EB8-Catalogo {
    param([Parameter(Mandatory=$true)] [object] $Catalogo)
    Assert-21EB8-NomesExatos -Objeto $Catalogo -Nomes @('schema_version','execution_mode','continuous_log_capture_authorized','destructive_actions_authorized','events')
    if (($Catalogo.schema_version -isnot [int] -and $Catalogo.schema_version -isnot [long]) -or $Catalogo.schema_version -ne 1 -or
        $Catalogo.execution_mode -cne 'offline_validation_only' -or
        $Catalogo.continuous_log_capture_authorized -isnot [bool] -or
        $Catalogo.continuous_log_capture_authorized -ne $false -or
        $Catalogo.destructive_actions_authorized -isnot [bool] -or
        $Catalogo.destructive_actions_authorized -ne $false) {
        throw '21-E-B8: catalogo nao autorizado.'
    }
    $fixos = @(Get-21EB8-DefinicoesEsperadas)
    $eventos = @($Catalogo.events)
    if ($eventos.Count -ne $fixos.Count) {
        throw '21-E-B8: quantidade de eventos diferente do contrato.'
    }
    for ($i=0; $i -lt $fixos.Count; $i++) {
        $real = $eventos[$i]
        $referencia = $fixos[$i]
        Assert-21EB8-NomesExatos -Objeto $real -Nomes @('code','component','operation','level','outcomes')
        foreach ($campo in @('code','component','operation','level')) {
            if ($real.$campo -isnot [string] -or $real.$campo -cne $referencia.$campo) {
                throw '21-E-B8: codigo ou metadado do catalogo adulterado.'
            }
        }
        $valores = @($real.outcomes)
        $esperados = @($referencia.outcomes)
        if ($valores.Count -ne $esperados.Count) {
            throw '21-E-B8: resultados do catalogo divergentes.'
        }
        for ($j=0; $j -lt $valores.Count; $j++) {
            if ($valores[$j] -isnot [string] -or $valores[$j] -cne $esperados[$j]) {
                throw '21-E-B8: resultado do catalogo divergente.'
            }
        }
    }
}

function New-21EB8-Marcador {
    param([Parameter(Mandatory=$true)] [string] $Canal)
    return [pscustomobject][ordered]@{
        kind='redacted_marker'
        channel=$Canal
        event=('runtime.' + $Canal + '.redacted')
        reason='UNTRUSTED_INPUT'
    }
}

function ConvertTo-21EB8-EventoSeguro {
    param(
        [AllowEmptyString()] [Parameter(Mandatory=$true)] [string] $Linha,
        [Parameter(Mandatory=$true)] [ValidateSet('stdout','stderr')] [string] $Canal,
        [Parameter(Mandatory=$true)] [object] $Catalogo
    )
    Assert-21EB8-Catalogo -Catalogo $Catalogo
    if ($Canal -cne 'stdout' -or $Linha.Length -gt 32768 -or $Linha.Contains("`n") -or $Linha.Contains("`r")) {
        return New-21EB8-Marcador -Canal $Canal
    }
    try {
        $original = ConvertFrom-Json -InputObject $Linha -ErrorAction Stop
        if ($null -eq $original -or $original -isnot [pscustomobject]) {
            return New-21EB8-Marcador -Canal $Canal
        }
        $permitidos = @('timestamp','level','event','component','operation','outcome','runId','jobId','jobType','candidateId','snapshotId','evaluationId','publicationId','durationMs','failureOrigin','failureType','errorCode','asin','integration')
        $nomes = @($original.PSObject.Properties.Name)
        foreach ($nome in $nomes) {
            if ($permitidos -cnotcontains $nome) {
                return New-21EB8-Marcador -Canal $Canal
            }
        }
        foreach ($obrigatorio in @('timestamp','level','event','component','operation','outcome')) {
            if ($nomes -cnotcontains $obrigatorio -or $original.$obrigatorio -isnot [string]) {
                return New-21EB8-Marcador -Canal $Canal
            }
        }
        $def = $null
        foreach ($item in @($Catalogo.events)) {
            if ($original.event -ceq $item.code) {
                $def = $item
                break
            }
        }
        if ($null -eq $def -or $original.level -cne $def.level -or
            $original.component -cne $def.component -or
            $original.operation -cne $def.operation -or
            @($def.outcomes) -cnotcontains $original.outcome) {
            return New-21EB8-Marcador -Canal $Canal
        }
        $timestamp = [DateTimeOffset]::MinValue
        $invariante = [System.Globalization.CultureInfo]::InvariantCulture
        $estilo = [System.Globalization.DateTimeStyles]::None
        if (-not [DateTimeOffset]::TryParse($original.timestamp,$invariante,$estilo,[ref]$timestamp)) {
            return New-21EB8-Marcador -Canal $Canal
        }
        if ($original.timestamp -notmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$') {
            return New-21EB8-Marcador -Canal $Canal
        }
        $saida = [ordered]@{
            kind='structured_allowlisted'
            channel='stdout'
            timestamp=$timestamp.ToString('o',$invariante)
            level=$def.level
            event=$def.code
            component=$def.component
            operation=$def.operation
            outcome=$original.outcome
        }
        foreach ($nome in @('runId','jobId','candidateId','snapshotId','evaluationId','publicationId')) {
            if ($nomes -ccontains $nome) {
                $valor = $original.$nome
                if (($valor -isnot [long] -and $valor -isnot [int]) -or $valor -le 0) {
                    return New-21EB8-Marcador -Canal $Canal
                }
                $saida[$nome] = [long]$valor
            }
        }
        if ($nomes -ccontains 'durationMs') {
            $valor = $original.durationMs
            if (($valor -isnot [long] -and $valor -isnot [int]) -or $valor -lt 0) {
                return New-21EB8-Marcador -Canal $Canal
            }
            $saida['durationMs'] = [long]$valor
        }
        if ($nomes -ccontains 'jobType') {
            if ($original.jobType -isnot [string] -or
                @('COLLECT_DEALS','ENRICH_DEAL','EVALUATE_DEAL','PUBLICATION_DISPATCH') -cnotcontains $original.jobType) {
                return New-21EB8-Marcador -Canal $Canal
            }
            $saida['jobType'] = $original.jobType
        }
        $falhaPresenca = @()
        foreach ($campoFalha in @('failureOrigin','failureType','errorCode')) {
            if ($nomes -ccontains $campoFalha) {
                $falhaPresenca += $campoFalha
            }
        }
        if ($falhaPresenca.Count -ne 0 -and $falhaPresenca.Count -ne 3) {
            return New-21EB8-Marcador -Canal $Canal
        }
        if ($falhaPresenca.Count -eq 3) {
            if ($original.failureOrigin -isnot [string] -or
                @('INTERNAL','EXTERNAL') -cnotcontains $original.failureOrigin -or
                $original.failureType -isnot [string] -or
                @('TRANSIENT','PERMANENT') -cnotcontains $original.failureType -or
                $original.errorCode -isnot [string] -or
                [string]::IsNullOrWhiteSpace($original.errorCode)) {
                return New-21EB8-Marcador -Canal $Canal
            }
            $saida['failureOrigin'] = $original.failureOrigin
            $saida['failureType'] = $original.failureType
            $saida['errorCodeRedacted'] = $true
        }
        # ASIN, integration e errorCode nunca sao copiados como texto.
        return [pscustomobject]$saida
    } catch {
        # Nunca incluir a excecao, o JSON original ou a linha em logs.
        return New-21EB8-Marcador -Canal $Canal
    }
}
