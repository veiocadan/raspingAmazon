# FASE 21-E-B4 - gate Windows de integracao ISOLADA B1 + B3 + processo sintetico.
# Somente grava cenarios privados em target/retencao-21eb1-testes.
# Nao inicia JVM, nao modifica supervisor, PostgreSQL, backups ou logs operacionais.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
. (Join-Path $PSScriptRoot 'FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B4_FUNCOES_CAPTURA_MISTA.ps1')

if (-not (Assert-21EB1-Contexto -Raiz $raiz)) {
    throw '21-E-B4: contexto de seguranca invalido'
}
Write-Host 'PASS - Politica 21-E-A e startup preparation_only validados.'

function New-21EB4-Canais {
    param([long]$MaximoCanal = 65536)
    $outDir = New-21EB1-Sandbox -Raiz $raiz
    $errDir = New-21EB1-Sandbox -Raiz $raiz
    return [pscustomobject]@{
        OutDir = $outDir
        ErrDir = $errDir
        Out = New-21EB1-Sessao -Diretorio $outDir -MaximoSegmentoBytes 512 -MaximoTotalBytes $MaximoCanal
        Err = New-21EB1-Sessao -Diretorio $errDir -MaximoSegmentoBytes 512 -MaximoTotalBytes $MaximoCanal
    }
}

function Get-21EB4-Registros {
    param([string]$Diretorio, [long]$MaximoSegmento = 512)
    $segmentos = @(Get-ChildItem -LiteralPath $Diretorio -File -Filter 'segmento-*.jsonl' | Sort-Object Name)
    $registros = New-Object 'System.Collections.Generic.List[object]'
    foreach ($s in $segmentos) {
        if ($s.Length -le 0 -or $s.Length -gt $MaximoSegmento) {
            throw '21-E-B4: segmento fora do limite'
        }
        foreach ($linha in [IO.File]::ReadAllLines($s.FullName, [Text.Encoding]::UTF8)) {
            $obj = $linha | ConvertFrom-Json -ErrorAction Stop
            if ($null -eq $obj -or $obj -isnot [pscustomobject]) {
                throw '21-E-B4: evento gravado nao e objeto JSON'
            }
            $registros.Add($obj)
        }
    }
    return [pscustomobject]@{ Segmentos=$segmentos.Count; Eventos=$registros.ToArray() }
}

$codigoMisto = @'
$ErrorActionPreference = 'Stop'
for ($i = 1; $i -le 32; $i++) {
    $num = '{0:D4}' -f $i
    if ($i % 2 -eq 1) {
        $linha = '{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"fase21eb4.stdout.' + $num + '","component":"synthetic-child","operation":"emit","outcome":"SUCCESS","jobId":7}'
        [Console]::Out.WriteLine($linha)
    } else {
        [Console]::Out.WriteLine('payload desconhecido token=SEGREDO_FICTICIO_21EB4_' + $num)
    }
    if ($i % 4 -eq 0) {
        [Console]::Error.WriteLine('Continuous processing failed: RuntimeException (details suppressed)')
    } elseif ($i -eq 2) {
        [Console]::Error.WriteLine('{"authorization":"SEGREDO_FICTICIO_21EB4"}')
    } else {
        [Console]::Error.WriteLine('    at Synthetic.StackFrame(token=SEGREDO_FICTICIO_21EB4_' + $num + ')')
    }
}
[Console]::Out.Flush()
[Console]::Error.Flush()
exit 0
'@

$normal = New-21EB4-Canais
$resumo = Invoke-21EB4-CapturaMistaSintetica -CodigoFilho $codigoMisto `
    -SessaoStdout $normal.Out -SessaoStderr $normal.Err -MaximoCombinadoBytes 65536
if ($resumo.StdoutEventos -ne 32 -or $resumo.StderrEventos -ne 32 -or
    $resumo.StdoutEstruturados -ne 16 -or $resumo.StdoutRedigidos -ne 16 -or
    $resumo.ExitCode -ne 0) {
    throw '21-E-B4: contadores invalidos'
}
$out = Get-21EB4-Registros -Diretorio $normal.OutDir
$err = Get-21EB4-Registros -Diretorio $normal.ErrDir
if ($out.Eventos.Count -ne 32 -or $err.Eventos.Count -ne 32 -or
    $out.Segmentos -lt 2 -or $err.Segmentos -lt 2) {
    throw '21-E-B4: perda, duplicacao ou segmentacao ausente'
}
for ($i = 1; $i -le 32; $i++) {
    $num = '{0:D4}' -f $i
    if ($i % 2 -eq 1) {
        if ($out.Eventos[$i-1].event -cne ('fase21eb4.stdout.' + $num)) {
            throw '21-E-B4: evento estruturado fora de ordem'
        }
    } elseif ($out.Eventos[$i-1].event -cne 'runtime.stdout.unclassified') {
        throw '21-E-B4: mensagem stdout desconhecida nao foi redigida'
    }
    $esperadoErr = if ($i % 4 -eq 0) { 'runtime.stderr.failed' } else { 'runtime.stderr.unclassified' }
    if ($err.Eventos[$i-1].event -cne $esperadoErr) {
        throw '21-E-B4: stderr fora de ordem ou mal classificado'
    }
}
$persistido = @(Get-ChildItem -LiteralPath $normal.OutDir -File -Filter 'segmento-*.jsonl' | Get-Content) +
    @(Get-ChildItem -LiteralPath $normal.ErrDir -File -Filter 'segmento-*.jsonl' | Get-Content)
$texto = $persistido -join "`n"
if ($texto.Contains('SEGREDO_FICTICIO_21EB4') -or
    $texto.Contains('authorization') -or $texto.Contains('StackFrame(') -or
    $texto.Contains('payload desconhecido')) {
    throw '21-E-B4: conteudo de entrada nao confiavel persistido'
}
Write-Host 'PASS - Captura mista: stdout=32, stderr=32; 16 estruturados + 48 marcadores.'
Write-Host ('PASS - Segmentos stdout={0}; stderr={1}; ordem e integridade confirmadas.' -f $out.Segmentos,$err.Segmentos)
Write-Host 'PASS - Nenhum token ficticio, payload desconhecido ou stack trace persistido.'

$falha = New-21EB4-Canais
$codigoFalha = @'
[Console]::Out.WriteLine('{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"fase21eb4.partial","component":"synthetic-child","operation":"emit","outcome":"SUCCESS"}')
[Console]::Error.WriteLine('Continuous processing failed: RuntimeException (details suppressed)')
[Console]::Out.Flush()
[Console]::Error.Flush()
exit 17
'@
$bloqueou = $false
try {
    Invoke-21EB4-CapturaMistaSintetica -CodigoFilho $codigoFalha `
        -SessaoStdout $falha.Out -SessaoStderr $falha.Err -MaximoCombinadoBytes 65536 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*codigo nao zero*') { throw }
    $bloqueou = $true
}
if (-not $bloqueou -or $falha.Out.TotalBytes -le 0 -or $falha.Err.TotalBytes -le 0) {
    throw '21-E-B4: falha do filho nao foi identificada ou perdeu evidencias'
}
Write-Host 'PASS - Codigo nao zero detectado; evidencias parciais preservadas.'

$quota = New-21EB4-Canais
$rejeitou = $false
try {
    Invoke-21EB4-CapturaMistaSintetica -CodigoFilho $codigoMisto `
        -SessaoStdout $quota.Out -SessaoStderr $quota.Err -MaximoCombinadoBytes 512 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*limite combinado*') { throw }
    $rejeitou = $true
}
if (-not $rejeitou -or ($quota.Out.TotalBytes + $quota.Err.TotalBytes) -gt 512 -or
    ($quota.Out.TotalBytes + $quota.Err.TotalBytes) -le 0) {
    throw '21-E-B4: limite nao respeitado ou evidencias descartadas'
}
Write-Host 'PASS - Limite combinado protege ambos os canais sem exclusao.'

$timeout = New-21EB4-Canais
$expirou = $false
try {
    Invoke-21EB4-CapturaMistaSintetica -CodigoFilho '[Threading.Thread]::Sleep(30000)' `
        -SessaoStdout $timeout.Out -SessaoStderr $timeout.Err `
        -MaximoCombinadoBytes 65536 -TimeoutSegundos 5 | Out-Null
} catch {
    if ($_.Exception.Message -notlike '*timeout sintetico*') { throw }
    $expirou = $true
}
if (-not $expirou -or $timeout.Out.TotalBytes -ne 0 -or $timeout.Err.TotalBytes -ne 0) {
    throw '21-E-B4: timeout nao protegido'
}
Write-Host 'PASS - Timeout limitado ao filho sintetico, sem eventos inventados.'
Write-Host 'PROTEGIDO - Sem Java, daemon, supervisor ativo, PostgreSQL, backup ou logs reais.'
Write-Host 'PASS - 21-E-B4 local isolado aprovado; captura produtiva segue desautorizada.'
