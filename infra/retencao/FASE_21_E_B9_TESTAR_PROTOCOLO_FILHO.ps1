# FASE 21-E-B9: regressao de CLIXML do processo filho sintetico; sem daemon.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).ProviderPath
Set-Location -LiteralPath $raiz

. (Join-Path $PSScriptRoot 'FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B4_FUNCOES_CAPTURA_MISTA.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B6_FUNCOES_PROTECAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B8_FUNCOES_CATALOGO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1')

function Assert-21EB9-Protocolo {
    param([bool]$Condicao, [string]$Descricao)
    if (-not $Condicao) { throw ('21-E-B9: protocolo reprovado: ' + $Descricao) }
}

Assert-21EB9-Protocolo (Assert-21EB1-Contexto -Raiz $raiz) 'politica de seguranca'
$catalogo = Get-Content -LiteralPath 'config/operacao/operational-log-catalog.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
Assert-21EB8-Catalogo -Catalogo $catalogo
Write-Host 'PASS - preparation_only, inventory_only e catalogo B8 confirmados.'

$outDir = New-21EB1-Sandbox -Raiz $raiz
$errDir = New-21EB1-Sandbox -Raiz $raiz
$out = New-21EB1-Sessao -Diretorio $outDir -MaximoSegmentoBytes 768 -MaximoTotalBytes 8192
$err = New-21EB1-Sessao -Diretorio $errDir -MaximoSegmentoBytes 768 -MaximoTotalBytes 8192
$codigo = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED","jobId":1}')
[Console]::Out.WriteLine('TOKEN_FICTICIO_PROTOCOLO_51')
[Console]::Error.WriteLine('at StackTrace(TOKEN_FICTICIO_PROTOCOLO_51)')
[Console]::Out.Flush()
[Console]::Error.Flush()
exit 0
'@

$resumo = Invoke-21EB9-CapturaCatalogadaSintetica -CodigoFilho $codigo `
    -SessaoStdout $out -SessaoStderr $err -Catalogo $catalogo `
    -MaximoCombinadoBytes 8192
Assert-21EB9-Protocolo ($resumo.StdoutEventos -eq 2 -and
    $resumo.StderrEventos -eq 1 -and
    $resumo.StdoutCatalogados -eq 1 -and
    $resumo.StdoutRedigidos -eq 1 -and
    $resumo.StderrRedigidos -eq 1 -and
    $resumo.ExitCode -eq 0) 'contadores incluem mensagens extras do PowerShell'
Write-Host 'PASS - stdout=2 e stderr=1; nenhum marcador CLIXML adicional.'

$arquivosOut = @(Get-ChildItem -LiteralPath $outDir -File -Filter 'segmento-*.jsonl' -ErrorAction Stop)
$arquivosErr = @(Get-ChildItem -LiteralPath $errDir -File -Filter 'segmento-*.jsonl' -ErrorAction Stop)
Assert-21EB9-Protocolo ($arquivosOut.Count -gt 0 -and $arquivosErr.Count -gt 0) 'segmentos ausentes'
$eventosOut = @($arquivosOut | Sort-Object Name | ForEach-Object {
    foreach ($linha in [IO.File]::ReadAllLines($_.FullName,[Text.Encoding]::UTF8)) {
        ConvertFrom-Json -InputObject $linha -ErrorAction Stop
    }
})
$eventosErr = @($arquivosErr | Sort-Object Name | ForEach-Object {
    foreach ($linha in [IO.File]::ReadAllLines($_.FullName,[Text.Encoding]::UTF8)) {
        ConvertFrom-Json -InputObject $linha -ErrorAction Stop
    }
})
Assert-21EB9-Protocolo ($eventosOut.Count -eq 2 -and $eventosErr.Count -eq 1) 'registro adicional persistido'
Assert-21EB9-Protocolo ($eventosOut[0].event -ceq 'processing.job.claimed' -and
    $eventosOut[1].event -ceq 'runtime.stdout.redacted' -and
    $eventosErr[0].event -ceq 'runtime.stderr.redacted') 'eventos fora do catalogo seguro'
$conteudo = (@($arquivosOut)+@($arquivosErr) | ForEach-Object {
    [IO.File]::ReadAllText($_.FullName,[Text.Encoding]::UTF8)
}) -join "`n"
Assert-21EB9-Protocolo (-not $conteudo.Contains('TOKEN_FICTICIO_PROTOCOLO_51') -and
    -not $conteudo.Contains('StackTrace(') -and
    -not $conteudo.Contains('CLIXML') -and
    -not $conteudo.Contains('TOKEN=')) 'conteudo bruto persistido'
Write-Host 'PASS - Codigo catalogado preservado; texto de stdout/stderr ausente dos segmentos.'

$filhos = @(Get-ChildItem -LiteralPath $outDir -File -Filter 'filho-sintetico-*.ps1' -ErrorAction Stop)
Assert-21EB9-Protocolo ($filhos.Count -eq 1 -and $filhos[0].Length -gt 3) 'arquivo filho nao preservado'
$prefixo = [IO.File]::ReadAllBytes($filhos[0].FullName)
Assert-21EB9-Protocolo ($prefixo[0] -eq 239 -and $prefixo[1] -eq 187 -and $prefixo[2] -eq 191) 'BOM UTF-8 ausente'
Assert-21EB9-Protocolo ($out.Encerrada -and $err.Encerrada) 'sessoes ainda abertas'
Assert-21EB1-ACLPrivada -Diretorio $outDir
Assert-21EB1-ACLPrivada -Diretorio $errDir
Write-Host 'PASS - Filho sintetico privado executado via -File; evidencias preservadas sob target.'
Write-Host 'PROTEGIDO - Nenhum daemon, supervisor, banco, backup ou log real acessado.'
Write-Host 'PASS - Regressao de protocolo B9 aprovada; captura produtiva continua proibida.'
