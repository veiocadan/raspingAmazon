# FASE 21-E-B6 - prototipo offline de minimizacao e lacre de falha.
# Nao altera B1/B3/B4; importar nao inicia processo nem realiza I/O.
Set-StrictMode -Version Latest

function Convert-21EB6-LinhaMinimizada {
    param(
        [AllowEmptyString()][Parameter(Mandatory=$true)][string]$Linha,
        [Parameter(Mandatory=$true)][ValidateSet('stdout','stderr')][string]$Canal
    )
    # O contrato B3 classifica o formato; a B6 impede a copia de textos
    # livres mesmo quando passaram pelo validador sintatico.
    $classificada = Convert-21EB3-LinhaConsole -Linha $Linha -Canal $Canal
    if ($classificada.Classificacao -cne 'STRUCTURED') {
        if ($classificada.Classificacao -cne 'REDACTED') {
            throw '21-E-B6: classificacao inesperada'
        }
        return $classificada
    }
    if ($Canal -cne 'stdout') {
        throw '21-E-B6: evento estruturado fora de stdout'
    }

    $obj = $classificada.LinhaJson | ConvertFrom-Json -ErrorAction Stop
    if (-not (Test-21EB3-EventoJavaSeguro -Linha $classificada.LinhaJson)) {
        throw '21-E-B6: contrato de entrada invalido'
    }
    $seguro = [ordered]@{
        timestamp = $obj.timestamp
        level = $obj.level
        event = 'runtime.stdout.structured_minimized'
        component = 'runtime-capture'
        operation = 'safe-projection'
        outcome = 'RECORDED'
    }
    # Apenas inteiros positivos/nao negativos ja validados pela B3.
    # Nenhum campo textual da origem (event, integration, errorCode, etc.)
    # e levado ao segmento: evita vazamento de segredos sintaticamente validos.
    foreach ($campo in @('runId','jobId','candidateId','snapshotId',
        'evaluationId','publicationId','durationMs')) {
        if ($obj.PSObject.Properties.Name -ccontains $campo) {
            $seguro[$campo] = $obj.$campo
        }
    }
    $jsonSeguro = ConvertTo-Json -InputObject $seguro -Compress -Depth 3
    if (-not (Test-21EB3-EventoJavaSeguro -Linha $jsonSeguro)) {
        throw '21-E-B6: projecao invalida'
    }
    return [pscustomobject]@{
        Classificacao = 'STRUCTURED_MINIMIZED'
        LinhaJson = $jsonSeguro
    }
}

function Write-21EB6-EventoLacrado {
    param(
        [Parameter(Mandatory=$true)][object]$Sessao,
        [Parameter(Mandatory=$true)][string]$LinhaJson
    )
    if ($Sessao.Encerrada) {
        throw '21-E-B6: sessao lacrada, gravacao proibida'
    }
    try {
        Write-21EB1-EventoSintetico -Sessao $Sessao -LinhaJson $LinhaJson
    } catch {
        # Falha fechada: apos qualquer erro, proibir novas escritas nesta
        # sessao. Nao excluir ou truncar segmentos; manter evidencias parciais.
        $Sessao.Encerrada = $true
        if ($null -ne $Sessao.Fluxo) {
            try { $Sessao.Fluxo.Dispose() } catch { }
            $Sessao.Fluxo = $null
        }
        # Nao incluir mensagem da excecao (pode conter caminho ou dado bruto).
        throw '21-E-B6: falha de persistencia; sessao lacrada sem exclusao'
    }
}
