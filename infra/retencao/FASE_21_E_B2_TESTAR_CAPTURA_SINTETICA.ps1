# FASE 21-E-B2. Teste autonomo: nao executa daemon, nao apaga arquivos.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
. (Join-Path $PSScriptRoot 'FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B2_FUNCOES_CAPTURA_SINTETICA.ps1')

if (-not (Assert-21EB1-Contexto -Raiz $raiz)) {
    throw '21-E-B2: contrato de seguranca invalido'
}
Write-Host 'PASS - Contrato E-A, startup preparation_only e sandbox B1 confirmados.'

function New-21EB2-Fixture {
    param([int]$Eventos, [int]$CodigoSaida = 0)
    $codigo = @'
$ErrorActionPreference = 'Stop'
for ($i = 1; $i -le __EVENTOS__; $i++) {
    $num = '{0:D4}' -f $i
    $out = '{"timestamp":"2026-10-15T00:00:00Z","level":"INFO","event":"fase21eb2.stdout.' + $num + '","component":"synthetic-child","operation":"io","outcome":"SUCCESS"}'
    $err = '{"timestamp":"2026-10-15T00:00:00Z","level":"ERROR","event":"fase21eb2.stderr.' + $num + '","component":"synthetic-child","operation":"io","outcome":"FAILURE"}'
    [Console]::Out.WriteLine($out)
    [Console]::Error.WriteLine($err)
}
[Console]::Out.Flush()
[Console]::Error.Flush()
exit __SAIDA__
'@
    return $codigo.Replace('__EVENTOS__', [string]$Eventos).Replace('__SAIDA__', [string]$CodigoSaida)
}

function New-21EB2-Canais {
    param([string]$Raiz, [long]$MaximoTotalPorCanal = 65536)
    $diretorioOut = New-21EB1-Sandbox -Raiz $Raiz
    $diretorioErr = New-21EB1-Sandbox -Raiz $Raiz
    return [pscustomobject]@{
        Saida = New-21EB1-Sessao -Diretorio $diretorioOut -MaximoSegmentoBytes 512 -MaximoTotalBytes $MaximoTotalPorCanal
        Erro  = New-21EB1-Sessao -Diretorio $diretorioErr -MaximoSegmentoBytes 512 -MaximoTotalBytes $MaximoTotalPorCanal
        DiretorioSaida = $diretorioOut
        DiretorioErro = $diretorioErr
    }
}

function Assert-21EB2-EvidenciaCanal {
    param(
        [string]$Diretorio,
        [ValidateSet('stdout','stderr')][string]$Canal,
        [int]$Quantidade,
        [long]$MaximoSegmento = 512
    )
    $segmentos = @(Get-ChildItem -LiteralPath $Diretorio -Filter 'segmento-*.jsonl' -File | Sort-Object Name)
    if ($segmentos.Count -le 1) { throw '21-E-B2: segmentacao por canal nao ocorreu' }
    $observados = New-Object 'System.Collections.Generic.List[string]'
    foreach ($segmento in $segmentos) {
        if ($segmento.Length -lt 1 -or $segmento.Length -gt $MaximoSegmento) {
            throw '21-E-B2: segmento fora do limite'
        }
        foreach ($linha in [System.IO.File]::ReadAllLines($segmento.FullName, [Text.Encoding]::UTF8)) {
            Assert-21EB2-LinhaEsperada -Linha $linha -Canal $Canal
            $observados.Add($linha)
        }
    }
    if ($observados.Count -ne $Quantidade) {
        throw '21-E-B2: perda ou duplicacao de eventos de canal'
    }
    for ($i = 1; $i -le $Quantidade; $i++) {
        $indice = '{0:D4}' -f $i
        $objeto = $observados[$i - 1] | ConvertFrom-Json
        $esperado = 'fase21eb2.' + $Canal + '.' + $indice
        if ($objeto.event -cne $esperado) {
            throw '21-E-B2: ordem do canal incorreta'
        }
    }
    return $segmentos.Count
}

$normal = New-21EB2-Canais -Raiz $raiz
$resumo = Invoke-21EB2-CapturaSintetica -CodigoFilho (New-21EB2-Fixture -Eventos 64) `
    -SessaoStdout $normal.Saida -SessaoStderr $normal.Erro -MaximoCombinadoBytes 65536
if ($resumo.StdoutEventos -ne 64 -or $resumo.StderrEventos -ne 64 -or
    $resumo.ExitCode -ne 0 -or $resumo.BytesTotais -le 0) {
    throw '21-E-B2: contadores de captura invalidos'
}
$segmentosOut = Assert-21EB2-EvidenciaCanal -Diretorio $normal.DiretorioSaida -Canal stdout -Quantidade 64
$segmentosErr = Assert-21EB2-EvidenciaCanal -Diretorio $normal.DiretorioErro -Canal stderr -Quantidade 64
Write-Host ('PASS - Captura concorrente: stdout=64, stderr=64, segmentos={0}+{1}.' -f $segmentosOut,$segmentosErr)
Write-Host 'PASS - Ordem individual de cada fluxo preservada, sem perda ou duplicacao.'

$falhaFilho = New-21EB2-Canais -Raiz $raiz
$falhou = $false
try {
    Invoke-21EB2-CapturaSintetica -CodigoFilho (New-21EB2-Fixture -Eventos 2 -CodigoSaida 17) `
        -SessaoStdout $falhaFilho.Saida -SessaoStderr $falhaFilho.Erro -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*codigo de saida nao zero*') { throw }
    $falhou = $true
}
if (-not $falhou -or $falhaFilho.Saida.TotalBytes -le 0 -or $falhaFilho.Erro.TotalBytes -le 0) {
    throw '21-E-B2: falha do filho nao preservou evidencia ou nao falhou fechado'
}
Write-Host 'PASS - Saida nao zero rejeitada e evidencias preservadas.'

$linhaInvalida = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-15T00:00:00Z","token":"nao-gravar"}')
[Console]::Out.Flush()
'@
$invalido = New-21EB2-Canais -Raiz $raiz
$rejeitou = $false
try {
    Invoke-21EB2-CapturaSintetica -CodigoFilho $linhaInvalida `
        -SessaoStdout $invalido.Saida -SessaoStderr $invalido.Erro -MaximoCombinadoBytes 65536 | Out-Null
} catch { $rejeitou = $true }
if (-not $rejeitou -or $invalido.Saida.TotalBytes -ne 0 -or $invalido.Erro.TotalBytes -ne 0) {
    throw '21-E-B2: entrada invalida persistida ou nao rejeitada'
}
Write-Host 'PASS - Campo proibido na saida do filho rejeitado antes de gravar.'

$canalTrocado = New-21EB2-Canais -Raiz $raiz
$saidaErrada = @'
[Console]::Error.WriteLine('{"timestamp":"2026-10-15T00:00:00Z","level":"INFO","event":"fase21eb2.stdout.0001","component":"synthetic-child","operation":"io","outcome":"SUCCESS"}')
[Console]::Error.Flush()
'@
$rejeitouCanal = $false
try {
    Invoke-21EB2-CapturaSintetica -CodigoFilho $saidaErrada `
        -SessaoStdout $canalTrocado.Saida -SessaoStderr $canalTrocado.Erro -MaximoCombinadoBytes 65536 | Out-Null
} catch { $rejeitouCanal = $true }
if (-not $rejeitouCanal -or $canalTrocado.Saida.TotalBytes -ne 0 -or
    $canalTrocado.Erro.TotalBytes -ne 0) {
    throw '21-E-B2: troca de canal nao rejeitada'
}
Write-Host 'PASS - Evento stdout forjado em stderr rejeitado antes de gravar.'

$quota = New-21EB2-Canais -Raiz $raiz
$bloqueou = $false
try {
    Invoke-21EB2-CapturaSintetica -CodigoFilho (New-21EB2-Fixture -Eventos 10) `
        -SessaoStdout $quota.Saida -SessaoStderr $quota.Erro -MaximoCombinadoBytes 512 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*limite combinado atingido*') { throw }
    $bloqueou = $true
}
if (-not $bloqueou -or
    ($quota.Saida.TotalBytes + $quota.Erro.TotalBytes) -gt 512) {
    throw '21-E-B2: limite combinado nao rejeitado corretamente'
}
Write-Host 'PASS - Limite total combinado aplicado a stdout e stderr; sem apagar historico.'

$travado = New-21EB2-Canais -Raiz $raiz
$tempoRejeitado = $false
try {
    Invoke-21EB2-CapturaSintetica -CodigoFilho '[System.Threading.Thread]::Sleep(30000)' `
        -SessaoStdout $travado.Saida -SessaoStderr $travado.Erro `
        -MaximoCombinadoBytes 65536 -TimeoutSegundos 5 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*tempo limite*') { throw }
    $tempoRejeitado = $true
}
if (-not $tempoRejeitado -or $travado.Saida.TotalBytes -ne 0 -or
    $travado.Erro.TotalBytes -ne 0) {
    throw '21-E-B2: timeout nao falhou fechado'
}
Write-Host 'PASS - Timeout do filho sintetico detectado; somente processo proprio encerrado.'
Write-Host 'PROTEGIDO - Somente filho PowerShell sintetico; sem daemon, PostgreSQL, backup ou logs reais.'
Write-Host 'PASS - 21-E-B2 local isolado aprovado. Captura produtiva continua proibida.'
