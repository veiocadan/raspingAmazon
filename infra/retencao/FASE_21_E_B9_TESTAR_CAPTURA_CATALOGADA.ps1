# FASE 21-E-B9. Gate somente sintetico: nenhum processo Java ou destino operacional.
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

function Assert-21EB9-Teste {
    param([bool]$Condicao,[string]$Descricao)
    if (-not $Condicao) { throw ('21-E-B9: teste reprovado: ' + $Descricao) }
}
function New-21EB9-Canais {
    $outDir = New-21EB1-Sandbox -Raiz $raiz
    $errDir = New-21EB1-Sandbox -Raiz $raiz
    return [pscustomobject]@{
        OutDir = $outDir
        ErrDir = $errDir
        Out = (New-21EB1-Sessao -Diretorio $outDir -MaximoSegmentoBytes 768 -MaximoTotalBytes 65536)
        Err = (New-21EB1-Sessao -Diretorio $errDir -MaximoSegmentoBytes 768 -MaximoTotalBytes 65536)
    }
}
function Get-21EB9-Evidencias {
    param([string]$Diretorio)
    $arquivos = @(Get-ChildItem -LiteralPath $Diretorio -File -Filter 'segmento-*.jsonl' -ErrorAction Stop | Sort-Object Name)
    $eventos = New-Object 'System.Collections.Generic.List[object]'
    foreach ($arquivo in $arquivos) {
        Assert-21EB9-Teste ($arquivo.Length -gt 0 -and $arquivo.Length -le 768) 'tamanho do segmento'
        foreach ($linha in [IO.File]::ReadAllLines($arquivo.FullName,[Text.Encoding]::UTF8)) {
            $item = ConvertFrom-Json -InputObject $linha -ErrorAction Stop
            Assert-21EB9-Teste ($item -is [pscustomobject]) 'registro persistido nao e objeto'
            $eventos.Add($item)
        }
    }
    return [pscustomobject]@{ Segmentos=$arquivos.Count; Eventos=$eventos.ToArray() }
}

Assert-21EB9-Teste (Assert-21EB1-Contexto -Raiz $raiz) 'startup e politica de retencao'
$catalogo = Get-Content -LiteralPath 'config/operacao/operational-log-catalog.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
Assert-21EB8-Catalogo -Catalogo $catalogo
Write-Host 'PASS - Contexto preparation_only/inventory_only e catalogo B8 estrito.'

$segredo = 'SEGREDO_FICTICIO_B9_Q7X92P1'
$codigo = @'
$ErrorActionPreference='Stop'
$defs = @(
    [pscustomobject]@{event='processing.job.claimed'; component='processing-worker'; operation='execute-job'; level='INFO'; outcome='CLAIMED'},
    [pscustomobject]@{event='processing.job.succeeded'; component='processing-worker'; operation='execute-job'; level='INFO'; outcome='SUCCEEDED'},
    [pscustomobject]@{event='processing.job.retry-scheduled'; component='processing-worker'; operation='execute-job'; level='WARN'; outcome='RETRY_WAIT'},
    [pscustomobject]@{event='processing.job.dead'; component='processing-worker'; operation='execute-job'; level='ERROR'; outcome='DEAD'},
    [pscustomobject]@{event='processing.job.failure-handled'; component='processing-worker'; operation='execute-job'; level='WARN'; outcome='PENDING'},
    [pscustomobject]@{event='integration.observation.persistence-failed'; component='integration-observation-recorder'; operation='persist-observation'; level='ERROR'; outcome='FAILURE'}
)
$segredo='SEGREDO_FICTICIO_B9_Q7X92P1'
for ($i=1; $i -le 24; $i++) {
    if (($i % 2) -eq 1) {
        $indice = [int][Math]::Floor(($i-1)/2) % 6
        $def = $defs[$indice]
        $obj = [ordered]@{
            timestamp='2026-10-27T12:00:00Z'; level=$def.level; event=$def.event
            component=$def.component; operation=$def.operation; outcome=$def.outcome
            runId=$i; jobId=$i; asin=$segredo; integration=$segredo
        }
        if ($indice -eq 5) {
            $obj['failureOrigin']='INTERNAL'
            $obj['failureType']='PERMANENT'
            $obj['errorCode']=$segredo
        }
        [Console]::Out.WriteLine((ConvertTo-Json -InputObject $obj -Compress -Depth 4))
    } else {
        [Console]::Out.WriteLine('TOKEN='+$segredo+'_'+$i)
    }
    [Console]::Error.WriteLine('at StackTrace('+ $segredo + '_' + $i + ')')
}
[Console]::Out.Flush()
[Console]::Error.Flush()
exit 0
'@

$normal = New-21EB9-Canais
$resumo = Invoke-21EB9-CapturaCatalogadaSintetica -CodigoFilho $codigo `
    -SessaoStdout $normal.Out -SessaoStderr $normal.Err -Catalogo $catalogo `
    -MaximoCombinadoBytes 65536
$out = Get-21EB9-Evidencias -Diretorio $normal.OutDir
$err = Get-21EB9-Evidencias -Diretorio $normal.ErrDir
Assert-21EB9-Teste ($resumo.StdoutEventos -eq 24 -and $resumo.StderrEventos -eq 24 -and
    $resumo.StdoutCatalogados -eq 12 -and $resumo.StdoutRedigidos -eq 12 -and
    $resumo.StderrRedigidos -eq 24 -and $resumo.ExitCode -eq 0) 'contadores'
Assert-21EB9-Teste ($out.Eventos.Count -eq 24 -and $err.Eventos.Count -eq 24 -and
    $out.Segmentos -ge 2 -and $err.Segmentos -ge 2 -and
    $normal.Out.Encerrada -and $normal.Err.Encerrada) 'segmentos ou fechamento'
for ($i=1; $i -le 24; $i++) {
    $entrada = $out.Eventos[$i-1]
    if (($i % 2) -eq 1) {
        $indice = [int][Math]::Floor(($i-1)/2) % 6
        $def = $catalogo.events[$indice]
        Assert-21EB9-Teste ($entrada.event -ceq $def.code -and
            $entrada.level -ceq $def.level -and $entrada.outcome -ceq $def.outcomes[0] -and
            $entrada.runId -eq $i -and $entrada.jobId -eq $i) 'evento catalogado fora de ordem'
        if ($indice -eq 5) {
            Assert-21EB9-Teste ($entrada.failureOrigin -ceq 'INTERNAL' -and
                $entrada.failureType -ceq 'PERMANENT' -and
                $entrada.errorCode -ceq 'REDACTED') 'falha nao minimizada com tripla valida'
        }
    } else {
        Assert-21EB9-Teste ($entrada.event -ceq 'runtime.stdout.redacted') 'marcador stdout fora de ordem'
    }
    Assert-21EB9-Teste ($err.Eventos[$i-1].event -ceq 'runtime.stderr.redacted') 'marcador stderr fora de ordem'
}
$arquivos = @(
    Get-ChildItem -LiteralPath $normal.OutDir -File -Filter 'segmento-*.jsonl' -ErrorAction Stop
    Get-ChildItem -LiteralPath $normal.ErrDir -File -Filter 'segmento-*.jsonl' -ErrorAction Stop
)
$texto = ($arquivos | ForEach-Object { [IO.File]::ReadAllText($_.FullName,[Text.Encoding]::UTF8) }) -join "`n"
Assert-21EB9-Teste (-not $texto.Contains($segredo) -and -not $texto.Contains('StackTrace(') -and
    -not $texto.Contains('TOKEN=') -and -not $texto.Contains('"integration"') -and
    -not $texto.Contains('"asin"') -and -not $texto.Contains('errorCodeRedacted')) 'vazamento no segmento'
Write-Host 'PASS - Captura B9: stdout=24, stderr=24; 12 catalogados + 36 marcadores.'
Write-Host 'PASS - Seis codigos preservados com ordem/correlacao; falha usa errorCode=REDACTED.'
Write-Host 'PASS - Segredos ficticios, textos livres e stack traces ausentes dos segmentos.'

$naoZero = New-21EB9-Canais
$codigoErro = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED","jobId":9}')
[Console]::Error.WriteLine('StackTrace segredo')
[Console]::Out.Flush()
[Console]::Error.Flush()
exit 17
'@
$rejeitado = $false
try {
    Invoke-21EB9-CapturaCatalogadaSintetica -CodigoFilho $codigoErro `
        -SessaoStdout $naoZero.Out -SessaoStderr $naoZero.Err -Catalogo $catalogo `
        -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*codigo de saida sintetico nao zero*') { throw }
    $rejeitado = $true
}
Assert-21EB9-Teste ($rejeitado -and $naoZero.Out.TotalBytes -gt 0 -and
    $naoZero.Err.TotalBytes -gt 0 -and $naoZero.Out.Encerrada -and
    $naoZero.Err.Encerrada) 'saida nao zero sem preservacao'
Write-Host 'PASS - Codigo nao zero rejeitado; evidencias anteriores de ambos canais preservadas.'

$quota = New-21EB9-Canais
$rejeitado = $false
try {
    Invoke-21EB9-CapturaCatalogadaSintetica -CodigoFilho $codigo `
        -SessaoStdout $quota.Out -SessaoStderr $quota.Err -Catalogo $catalogo `
        -MaximoCombinadoBytes 700 | Out-Null
} catch {
    if ($_.Exception.Message -cne '21-E-B9: falha na captura; dois canais lacrados') { throw }
    $rejeitado = $true
}
$usados = [long]$quota.Out.TotalBytes + [long]$quota.Err.TotalBytes
Assert-21EB9-Teste ($rejeitado -and $usados -gt 0 -and $usados -le 700 -and
    $quota.Out.Encerrada -and $quota.Err.Encerrada) 'limite combinado'
Write-Host 'PASS - Limite combinado lacrou os dois canais sem eliminar segmentos.'

$grande = New-21EB9-Canais
$codigoGrande = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED","jobId":8}')
[Console]::Out.WriteLine(('X' * 40000))
[Console]::Out.Flush()
exit 0
'@
$rejeitado = $false
try {
    Invoke-21EB9-CapturaCatalogadaSintetica -CodigoFilho $codigoGrande `
        -SessaoStdout $grande.Out -SessaoStderr $grande.Err -Catalogo $catalogo `
        -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*linha excede limite*') { throw }
    $rejeitado = $true
}
$antes = Get-21EB9-Evidencias -Diretorio $grande.OutDir
Assert-21EB9-Teste ($rejeitado -and $antes.Eventos.Count -eq 1 -and
    $antes.Eventos[0].event -ceq 'processing.job.claimed' -and
    $grande.Out.Encerrada -and $grande.Err.Encerrada) 'linha excessiva'
Write-Host 'PASS - Linha excessiva bloqueada; evento catalogado anterior preservado.'

$disco = New-21EB9-Canais
$canalManual = [pscustomobject]@{
    Nome='stdout'; Sessao=$disco.Out; Eventos=0; Estruturados=0; Redigidos=0
}
$linhaManual = '{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED","jobId":7}'
Write-21EB9-LinhaCatalogada -Canal $canalManual -Linha $linhaManual `
    -OutraSessao $disco.Err -Catalogo $catalogo -MaximoCombinadoBytes 65536
$segmento = Join-Path $disco.OutDir 'segmento-000001.jsonl'
Assert-21EB9-Teste (Test-Path -LiteralPath $segmento -PathType Leaf) 'segmento de controle'
$disco.Out.Fluxo.Dispose()
$hashAntes = (Get-FileHash -LiteralPath $segmento -Algorithm SHA256).Hash
$rejeitado = $false
try {
    Write-21EB9-LinhaCatalogada -Canal $canalManual -Linha $linhaManual `
        -OutraSessao $disco.Err -Catalogo $catalogo -MaximoCombinadoBytes 65536
} catch {
    if ($_.Exception.Message -cne '21-E-B9: falha na captura; dois canais lacrados') { throw }
    $rejeitado = $true
}
$hashDepois = (Get-FileHash -LiteralPath $segmento -Algorithm SHA256).Hash
Assert-21EB9-Teste ($rejeitado -and $hashAntes -ceq $hashDepois -and
    $disco.Out.Encerrada -and $disco.Err.Encerrada) 'falha simulada do fluxo'
$rejeitadoNovo = $false
try {
    Write-21EB9-LinhaCatalogada -Canal $canalManual -Linha $linhaManual `
        -OutraSessao $disco.Err -Catalogo $catalogo -MaximoCombinadoBytes 65536
} catch {
    if ($_.Exception.Message -cne '21-E-B9: sessao lacrada; gravacao proibida') { throw }
    $rejeitadoNovo = $true
}
Assert-21EB9-Teste $rejeitadoNovo 'canal lacrado aceitou novo evento'
Write-Host 'PASS - Falha de escrita lacra canais; evidencia anterior conservada byte a byte.'

$timeout = New-21EB9-Canais
$rejeitado = $false
try {
    Invoke-21EB9-CapturaCatalogadaSintetica -CodigoFilho '[Threading.Thread]::Sleep(30000)' `
        -SessaoStdout $timeout.Out -SessaoStderr $timeout.Err -Catalogo $catalogo `
        -MaximoCombinadoBytes 65536 -TimeoutSegundos 5 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*timeout sintetico*') { throw }
    $rejeitado = $true
}
Assert-21EB9-Teste ($rejeitado -and $timeout.Out.TotalBytes -eq 0 -and
    $timeout.Err.TotalBytes -eq 0 -and $timeout.Out.Encerrada -and
    $timeout.Err.Encerrada) 'timeout sintetico'
Write-Host 'PASS - Timeout encerrou apenas o filho sintetico; sem registros inventados.'

Write-Host 'PROTEGIDO - Sem Java, supervisor, banco, backups, logs reais ou exclusao.'
Write-Host 'PASS - Gate 21-E-B9 sintetico aprovado; captura produtiva continua desautorizada.'
