# FASE 21-E-B9: regressao de codificacao, somente filhos PowerShell sinteticos.
# O teste nao toca logs reais, supervisor, banco, backups nem autoriza exclusoes.
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

function Assert-21EB9-Codificacao {
    param([bool]$Condicao,[string]$Motivo)
    if (-not $Condicao) { throw ('21-E-B9: codificacao reprovada: ' + $Motivo) }
}

function New-21EB9-CodificacaoCanais {
    $diretorioOut = New-21EB1-Sandbox -Raiz $raiz
    $diretorioErr = New-21EB1-Sandbox -Raiz $raiz
    return [pscustomobject]@{
        DiretorioOut = $diretorioOut
        DiretorioErr = $diretorioErr
        Out = (New-21EB1-Sessao -Diretorio $diretorioOut -MaximoSegmentoBytes 768 -MaximoTotalBytes 16384)
        Err = (New-21EB1-Sessao -Diretorio $diretorioErr -MaximoSegmentoBytes 768 -MaximoTotalBytes 16384)
    }
}

Assert-21EB9-Codificacao (Assert-21EB1-Contexto -Raiz $raiz) 'politicas de startup e retencao'
$catalogo = Get-Content -LiteralPath 'config/operacao/operational-log-catalog.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
Assert-21EB8-Catalogo -Catalogo $catalogo
Write-Host 'PASS - Politicas e catalogo B8 confirmados.'

# stderr deliberadamente contem 0xA2: byte isolado nao e UTF-8 valido.
# O capturador deve criar um marcador SEM os caracteres de entrada.
$codigoStderr = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED","jobId":31}')
[Console]::Out.Flush()
$bruto = [Console]::OpenStandardError()
[byte[]]$amostra = @(65,162,10)
$bruto.Write($amostra,0,$amostra.Length)
$bruto.Flush()
exit 0
'@
$canais = New-21EB9-CodificacaoCanais
$resultado = Invoke-21EB9-CapturaCatalogadaSintetica -CodigoFilho $codigoStderr `
    -SessaoStdout $canais.Out -SessaoStderr $canais.Err -Catalogo $catalogo `
    -MaximoCombinadoBytes 16384
Assert-21EB9-Codificacao ($resultado.StdoutEventos -eq 1 -and $resultado.StdoutCatalogados -eq 1 -and
    $resultado.StderrEventos -eq 1 -and $resultado.StderrRedigidos -eq 1 -and
    $resultado.ExitCode -eq 0) 'contagem com stderr nao UTF-8'
Assert-21EB9-Codificacao ($canais.Out.Encerrada -and $canais.Err.Encerrada) 'fechamento das sessoes'
$arquivosOut = @(Get-ChildItem -LiteralPath $canais.DiretorioOut -File -Filter 'segmento-*.jsonl' -ErrorAction Stop)
$arquivosErr = @(Get-ChildItem -LiteralPath $canais.DiretorioErr -File -Filter 'segmento-*.jsonl' -ErrorAction Stop)
Assert-21EB9-Codificacao ($arquivosOut.Count -eq 1 -and $arquivosErr.Count -eq 1) 'quantidade de segmentos'
$linhaOut = [IO.File]::ReadAllText($arquivosOut[0].FullName,[Text.Encoding]::UTF8)
$linhaErr = [IO.File]::ReadAllText($arquivosErr[0].FullName,[Text.Encoding]::UTF8)
Assert-21EB9-Codificacao ($linhaOut.Contains('processing.job.claimed') -and
    $linhaErr.Contains('runtime.stderr.redacted') -and
    -not $linhaErr.Contains([char]0x00A2)) 'redacao do stderr'
$entradaOut = ConvertFrom-Json -InputObject $linhaOut -ErrorAction Stop
$entradaErr = ConvertFrom-Json -InputObject $linhaErr -ErrorAction Stop
Assert-21EB9-Codificacao ($entradaOut.event -ceq 'processing.job.claimed' -and
    $entradaErr.event -ceq 'runtime.stderr.redacted') 'eventos persistidos'
Write-Host 'PASS - Byte isolado A2 em stderr aceito somente como marcador; texto original ausente.'

# stdout, ao contrario de stderr, continua proibindo bytes invalidos: falha fechada.
$codigoStdout = @'
$bruto = [Console]::OpenStandardOutput()
[byte[]]$amostra = @(195,40,10)
$bruto.Write($amostra,0,$amostra.Length)
$bruto.Flush()
exit 0
'@
$canaisInvalidos = New-21EB9-CodificacaoCanais
$rejeitou = $false
try {
    Invoke-21EB9-CapturaCatalogadaSintetica -CodigoFilho $codigoStdout `
        -SessaoStdout $canaisInvalidos.Out -SessaoStderr $canaisInvalidos.Err `
        -Catalogo $catalogo -MaximoCombinadoBytes 16384 | Out-Null
} catch {
    $rejeitou = $true
}
Assert-21EB9-Codificacao ($rejeitou -and $canaisInvalidos.Out.Encerrada -and
    $canaisInvalidos.Err.Encerrada -and $canaisInvalidos.Out.TotalBytes -eq 0 -and
    $canaisInvalidos.Err.TotalBytes -eq 0) 'stdout invalido deveria falhar sem persistir'
Write-Host 'PASS - Byte UTF-8 invalido em stdout rejeitado, sem registros artificiais.'
Write-Host 'PROTEGIDO - Apenas filhos PowerShell sinteticos e segmentos privados sob target; sem exclusoes.'
Write-Host 'PASS - Regressao de codificacao B9 aprovada; captura produtiva segue desautorizada.'
