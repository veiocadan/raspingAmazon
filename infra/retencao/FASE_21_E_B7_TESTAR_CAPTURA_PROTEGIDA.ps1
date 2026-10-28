# FASE 21-E-B7 - contrato isolado integrado; Windows PowerShell, nunca daemon.
# Somente cria cenarios ineditos sob target/retencao-21eb1-testes.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
. (Join-Path $PSScriptRoot 'FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B4_FUNCOES_CAPTURA_MISTA.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B6_FUNCOES_PROTECAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B7_FUNCOES_CAPTURA_PROTEGIDA.ps1')

if (-not (Assert-21EB1-Contexto -Raiz $raiz)) {
    throw '21-E-B7: contexto seguro reprovado'
}
Write-Host 'PASS - Contratos A, B1, B3, B4/B5 e B6 carregados; preparation_only.'

function New-21EB7-Canais {
    $outDir = New-21EB1-Sandbox -Raiz $raiz
    $errDir = New-21EB1-Sandbox -Raiz $raiz
    return [pscustomobject]@{
        OutDir = $outDir
        ErrDir = $errDir
        Out = New-21EB1-Sessao -Diretorio $outDir -MaximoSegmentoBytes 512 -MaximoTotalBytes 65536
        Err = New-21EB1-Sessao -Diretorio $errDir -MaximoSegmentoBytes 512 -MaximoTotalBytes 65536
    }
}

function Get-21EB7-Eventos {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    $arquivos = @(Get-ChildItem -LiteralPath $Diretorio -File -Filter 'segmento-*.jsonl' | Sort-Object Name)
    $eventos = New-Object 'System.Collections.Generic.List[object]'
    foreach ($arquivo in $arquivos) {
        if ($arquivo.Length -le 0 -or $arquivo.Length -gt 512) {
            throw '21-E-B7: segmento fora de limite'
        }
        foreach ($linha in [IO.File]::ReadAllLines($arquivo.FullName,[Text.Encoding]::UTF8)) {
            $item = $linha | ConvertFrom-Json -ErrorAction Stop
            if ($null -eq $item -or $item -isnot [pscustomobject]) {
                throw '21-E-B7: registro nao e objeto JSON'
            }
            $eventos.Add($item)
        }
    }
    return [pscustomobject]@{ Segmentos=$arquivos.Count; Eventos=$eventos.ToArray() }
}

$segredo = 'SEGREDOFICTICIO21EB7ABCDEFGH'
$codigo = @'
$ErrorActionPreference = 'Stop'
for ($i = 1; $i -le 20; $i++) {
    if ($i % 2 -eq 1) {
        $linha = '{"timestamp":"2026-10-15T18:00:00Z","level":"WARN","event":"SEGREDOFICTICIO21EB7ABCDEFGH","component":"SEGREDOFICTICIO21EB7ABCDEFGH","operation":"SEGREDOFICTICIO21EB7ABCDEFGH","outcome":"SEGREDOFICTICIO21EB7ABCDEFGH","integration":"SEGREDOFICTICIO21EB7ABCDEFGH","jobId":' + $i + '}'
        [Console]::Out.WriteLine($linha)
    } else {
        [Console]::Out.WriteLine('TOKEN=SEGREDOFICTICIO21EB7ABCDEFGH_' + $i)
    }
    if ($i % 4 -eq 0) {
        [Console]::Error.WriteLine('Continuous processing failed: RuntimeException (details suppressed)')
    } else {
        [Console]::Error.WriteLine('at StackTrace(SEGREDOFICTICIO21EB7ABCDEFGH_' + $i + ')')
    }
}
[Console]::Out.Flush()
[Console]::Error.Flush()
exit 0
'@

$normal = New-21EB7-Canais
$resumo = Invoke-21EB7-CapturaProtegidaSintetica -CodigoFilho $codigo `
    -SessaoStdout $normal.Out -SessaoStderr $normal.Err -MaximoCombinadoBytes 65536
$out = Get-21EB7-Eventos -Diretorio $normal.OutDir
$err = Get-21EB7-Eventos -Diretorio $normal.ErrDir
if ($resumo.StdoutEventos -ne 20 -or $resumo.StderrEventos -ne 20 -or
    $resumo.StdoutMinimizados -ne 10 -or $resumo.StdoutRedigidos -ne 10 -or
    $resumo.StderrRedigidos -ne 20 -or $resumo.ExitCode -ne 0 -or
    $out.Eventos.Count -ne 20 -or $err.Eventos.Count -ne 20 -or
    $out.Segmentos -lt 2 -or $err.Segmentos -lt 2 -or
    -not $normal.Out.Encerrada -or -not $normal.Err.Encerrada) {
    throw '21-E-B7: contadores, fechamento ou segmentacao invalidos'
}
for ($i=1;$i -le 20;$i++) {
    $item=$out.Eventos[$i-1]
    if ($i % 2 -eq 1) {
        if ($item.event -cne 'runtime.stdout.structured_minimized' -or
            $item.jobId -ne $i -or $item.level -cne 'WARN' -or
            $item.PSObject.Properties.Name -ccontains 'integration') {
            throw '21-E-B7: projecao stdout alterada ou fora de ordem'
        }
    } elseif ($item.event -cne 'runtime.stdout.unclassified') {
        throw '21-E-B7: marcador stdout fora de ordem'
    }
    $esperadoErr = if ($i % 4 -eq 0) { 'runtime.stderr.failed' } else { 'runtime.stderr.unclassified' }
    if ($err.Eventos[$i-1].event -cne $esperadoErr) {
        throw '21-E-B7: marcador stderr fora de ordem'
    }
}
$texto = (@(Get-ChildItem -LiteralPath $normal.OutDir -Filter 'segmento-*.jsonl' -File | Get-Content) +
    @(Get-ChildItem -LiteralPath $normal.ErrDir -Filter 'segmento-*.jsonl' -File | Get-Content)) -join "`n"
if ($texto.Contains($segredo) -or $texto.Contains('StackTrace(') -or
    $texto.Contains('TOKEN=') -or $texto.Contains('"integration"')) {
    throw '21-E-B7: vazamento em segmentos'
}
Write-Host 'PASS - Captura integrada: stdout=20, stderr=20; 10 minimizados + 30 marcadores.'
Write-Host 'PASS - Projecao de campos livres, correlacao numerica e ordem por canal confirmadas.'
Write-Host 'PASS - Nenhum token ficticio, stack trace ou texto livre sensivel persistido.'

$falha = New-21EB7-Canais
$codigoFalha = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"synthetic.failure","component":"synthetic","operation":"emit","outcome":"FAILURE"}')
[Console]::Error.WriteLine('Continuous processing interrupted')
[Console]::Out.Flush()
[Console]::Error.Flush()
exit 17
'@
$bloqueou = $false
try {
    Invoke-21EB7-CapturaProtegidaSintetica -CodigoFilho $codigoFalha `
        -SessaoStdout $falha.Out -SessaoStderr $falha.Err -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*codigo nao zero*') { throw }
    $bloqueou = $true
}
if (-not $bloqueou -or $falha.Out.TotalBytes -le 0 -or $falha.Err.TotalBytes -le 0 -or
    -not $falha.Out.Encerrada -or -not $falha.Err.Encerrada) {
    throw '21-E-B7: codigo nao zero nao preservou evidencias'
}
Write-Host 'PASS - Saida nao zero rejeitada; evidencias parciais de ambos canais preservadas.'

$quota = New-21EB7-Canais
$recusou = $false
try {
    Invoke-21EB7-CapturaProtegidaSintetica -CodigoFilho $codigo `
        -SessaoStdout $quota.Out -SessaoStderr $quota.Err -MaximoCombinadoBytes 550 | Out-Null
} catch {
    if ($_.Exception.Message -cne '21-E-B7: captura bloqueada; ambos canais lacrados sem exclusao') { throw }
    $recusou = $true
}
$uso = [long]$quota.Out.TotalBytes + [long]$quota.Err.TotalBytes
if (-not $recusou -or $uso -le 0 -or $uso -gt 550 -or
    -not $quota.Out.Encerrada -or -not $quota.Err.Encerrada) {
    throw '21-E-B7: quota global nao foi fail-closed'
}
Write-Host 'PASS - Limite combinado lacra ambos os canais sem apagar historico.'

$comprido = New-21EB7-Canais
$codigoComprido = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"synthetic.before","component":"synthetic","operation":"emit","outcome":"SUCCESS"}')
[Console]::Out.WriteLine(('X' * 40000))
[Console]::Out.Flush()
exit 0
'@
$recusou = $false
try {
    Invoke-21EB7-CapturaProtegidaSintetica -CodigoFilho $codigoComprido `
        -SessaoStdout $comprido.Out -SessaoStderr $comprido.Err -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*linha excede limite*') { throw }
    $recusou = $true
}
$eventosAnteriores = @( (Get-21EB7-Eventos -Diretorio $comprido.OutDir).Eventos )
if (-not $recusou -or $eventosAnteriores.Count -ne 1 -or
    $eventosAnteriores[0].event -cne 'runtime.stdout.structured_minimized' -or
    -not $comprido.Out.Encerrada -or -not $comprido.Err.Encerrada) {
    throw '21-E-B7: linha grande nao preservou evento anterior'
}
Write-Host 'PASS - Linha excessiva rejeitada; evidencia minimizada anterior intacta.'

# Injecao deterministica de falha de escrita. Sem tocar ACL ou arquivo operacional.
$disco = New-21EB7-Canais
$canalManual = [pscustomobject]@{
    Nome='stdout'; Sessao=$disco.Out; Eventos=0; Estruturados=0; Redigidos=0
}
$linhaManual = '{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"synthetic.safe","component":"synthetic","operation":"emit","outcome":"SUCCESS","runId":21}'
Write-21EB7-LinhaProtegida -Canal $canalManual -Linha $linhaManual `
    -OutraSessao $disco.Err -MaximoCombinadoBytes 65536
$arquivo = Join-Path $disco.OutDir 'segmento-000001.jsonl'
if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
    throw '21-E-B7: evidencia de controle nao criada'
}
$disco.Out.Fluxo.Dispose()
$antes = (Get-FileHash -LiteralPath $arquivo -Algorithm SHA256).Hash
$recusou = $false
try {
    Write-21EB7-LinhaProtegida -Canal $canalManual -Linha $linhaManual `
        -OutraSessao $disco.Err -MaximoCombinadoBytes 65536
} catch {
    if ($_.Exception.Message -cne '21-E-B7: captura bloqueada; ambos canais lacrados sem exclusao') { throw }
    $recusou = $true
}
$depois = (Get-FileHash -LiteralPath $arquivo -Algorithm SHA256).Hash
if (-not $recusou -or $antes -cne $depois -or
    -not $disco.Out.Encerrada -or -not $disco.Err.Encerrada -or
    $disco.Out.TotalBytes -le 0) {
    throw '21-E-B7: falha de armazenamento nao lacrou ambos os canais'
}
$recusouNovo = $false
try {
    Write-21EB7-LinhaProtegida -Canal $canalManual -Linha $linhaManual `
        -OutraSessao $disco.Err -MaximoCombinadoBytes 65536
} catch {
    if ($_.Exception.Message -cne '21-E-B7: sessao encerrada') { throw }
    $recusouNovo = $true
}
if (-not $recusouNovo) { throw '21-E-B7: sessao lacrada aceitou evento' }
Write-Host 'PASS - Falha de escrita lacra os dois canais; evidencia anterior preservada byte a byte.'

$timeout = New-21EB7-Canais
$expirou = $false
try {
    Invoke-21EB7-CapturaProtegidaSintetica -CodigoFilho '[Threading.Thread]::Sleep(30000)' `
        -SessaoStdout $timeout.Out -SessaoStderr $timeout.Err `
        -MaximoCombinadoBytes 65536 -TimeoutSegundos 5 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*timeout sintetico*') { throw }
    $expirou = $true
}
if (-not $expirou -or $timeout.Out.TotalBytes -ne 0 -or $timeout.Err.TotalBytes -ne 0 -or
    -not $timeout.Out.Encerrada -or -not $timeout.Err.Encerrada) {
    throw '21-E-B7: timeout nao interrompeu apenas filho sintetico'
}
Write-Host 'PASS - Timeout encerra somente o filho sintetico, sem eventos inventados.'
Write-Host 'PROTEGIDO - Sem JVM, supervisor, PostgreSQL, backups, destino de logs ou exclusao.'
Write-Host 'PASS - Gate 21-E-B7 isolado aprovado; captura produtiva continua desautorizada.'
