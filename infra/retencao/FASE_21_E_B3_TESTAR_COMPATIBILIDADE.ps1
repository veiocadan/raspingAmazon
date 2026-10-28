# FASE 21-E-B3 - testes OFFLINE; nenhum processo filho, conexao, daemon ou exclusao.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
. (Join-Path $PSScriptRoot 'FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1')
. (Join-Path $PSScriptRoot 'FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1')

if (-not (Assert-21EB1-Contexto -Raiz $raiz)) {
    throw '21-E-B3: estado inicial de seguranca invalido'
}
Write-Host 'PASS - Politicas E-A e startup preparation_only confirmadas.'

$adaptador = Join-Path $raiz 'src/main/java/com/raspingamazon/infrastructure/observability/JsonStructuredOperationalLogAdapter.java'
$principal = Join-Path $raiz 'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java'
$bootstrap = Join-Path $raiz 'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingBootstrap.java'
$supervisor = Join-Path $raiz 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1'
foreach ($p in @($adaptador,$principal,$bootstrap,$supervisor)) {
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { throw '21-E-B3: fonte de contrato ausente' }
}
$java = Get-Content -LiteralPath $adaptador -Raw -Encoding UTF8
$entry = Get-Content -LiteralPath $principal -Raw -Encoding UTF8
$boot = Get-Content -LiteralPath $bootstrap -Raw -Encoding UTF8
$sup = Get-Content -LiteralPath $supervisor -Raw -Encoding UTF8
if ($java -notmatch 'json\.put\(\s*"timestamp"' -or
    $java -notmatch 'json\.put\(\s*"level"' -or
    $java -notmatch 'writer\.println\(\s*json' -or
    $entry -notmatch 'SafeOperationalError\.describe\(exception\)' -or
    $boot -notmatch 'new\s+PrintWriter\s*\(\s*System\.out\s*,\s*true\s*\)' -or
    $sup -match '(?m)^\s*\$psi\.RedirectStandard(Output|Error)\s*=') {
    throw '21-E-B3: contrato Java/supervisor mudou; rever antes da integracao'
}
Write-Host 'PASS - Formato Java e ausencia de redirecionamento no supervisor confirmados.'

$cenarioOut = New-21EB1-Sandbox -Raiz $raiz
$cenarioErr = New-21EB1-Sandbox -Raiz $raiz
$out = New-21EB1-Sessao -Diretorio $cenarioOut -MaximoSegmentoBytes 512 -MaximoTotalBytes 65536
$err = New-21EB1-Sessao -Diretorio $cenarioErr -MaximoSegmentoBytes 512 -MaximoTotalBytes 65536

$validas = @(
    '{"timestamp":"2026-10-15T18:00:00Z","level":"INFO","event":"processing.job.succeeded","component":"processing-worker","operation":"execute","outcome":"SUCCESS","jobId":7,"durationMs":15}',
    '{"timestamp":"2026-10-15T18:00:01Z","level":"ERROR","event":"amazon.product.request.failed","component":"amazon-http","operation":"request","outcome":"FAILURE","asin":"B012345678","failureOrigin":"EXTERNAL","failureType":"TRANSIENT","errorCode":"HTTP_429"}'
)
$casosOut = @($validas) + @(
    'JAVA STDOUT token=SEGREDO_FICTICIO_21EB3',
    '{"timestamp":"2026-10-15T18:00:01Z","level":"INFO","event":"processing.job.failed","component":"worker","operation":"execute","outcome":"FAILURE","authorization":"SEGREDO_FICTICIO_21EB3"}',
    '{"timestamp":"2026-10-15T18:00:01Z","level":"INFO","event":"processing.job.failed","component":"password=SEGREDO_FICTICIO_21EB3","operation":"execute","outcome":"FAILURE"}',
    '');
$casosErr = @(
    'Continuous processing interrupted',
    'Continuous processing failed: IllegalStateException (details suppressed)',
    'Continuous processing cleanup failed: RuntimeException (details suppressed)',
    'Continuous processing shutdown failed: RuntimeException (details suppressed)',
    'Exception: token=SEGREDO_FICTICIO_21EB3',
    '    at exemplo.Classe.metodo(Classe.java:123)',
    '{"token":"SEGREDO_FICTICIO_21EB3"}'
)
try {
    $estruturados = 0
    $redigidosOut = 0
    $redigidosErr = 0
    foreach ($linha in $casosOut) {
        $resultado = Convert-21EB3-LinhaConsole -Linha $linha -Canal stdout
        if ($resultado.Classificacao -ceq 'STRUCTURED') { $estruturados++ }
        elseif ($resultado.Classificacao -ceq 'REDACTED') { $redigidosOut++ }
        else { throw '21-E-B3: classificacao de stdout invalida' }
        Write-21EB1-EventoSintetico -Sessao $out -LinhaJson $resultado.LinhaJson
    }
    foreach ($linha in $casosErr) {
        $resultado = Convert-21EB3-LinhaConsole -Linha $linha -Canal stderr
        if ($resultado.Classificacao -cne 'REDACTED') { throw '21-E-B3: stderr bruto aceito' }
        $redigidosErr++
        Write-21EB1-EventoSintetico -Sessao $err -LinhaJson $resultado.LinhaJson
    }
} finally {
    Close-21EB1-Sessao -Sessao $out
    Close-21EB1-Sessao -Sessao $err
}
if ($estruturados -ne 2 -or $redigidosOut -ne 4 -or $redigidosErr -ne 7) {
    throw '21-E-B3: contagem de classificacao incorreta'
}

$eventosOut = @(Get-ChildItem -LiteralPath $cenarioOut -Filter 'segmento-*.jsonl' -File | Sort-Object Name |
    ForEach-Object { [System.IO.File]::ReadAllLines($_.FullName, [Text.Encoding]::UTF8) })
$eventosErr = @(Get-ChildItem -LiteralPath $cenarioErr -Filter 'segmento-*.jsonl' -File | Sort-Object Name |
    ForEach-Object { [System.IO.File]::ReadAllLines($_.FullName, [Text.Encoding]::UTF8) })
if ($eventosOut.Count -ne $casosOut.Count -or $eventosErr.Count -ne $casosErr.Count) {
    throw '21-E-B3: perda ou duplicacao de registro normalizado'
}
$persistidos = ($eventosOut + $eventosErr) -join "`n"
if ($persistidos.Contains('SEGREDO_FICTICIO_21EB3') -or
    $persistidos.Contains('authorization') -or
    $persistidos.Contains('Classe.java:123') -or
    $persistidos.Contains('token=')) {
    throw '21-E-B3: texto diagnostico nao confiavel foi persistido'
}
foreach ($linha in $eventosErr) {
    $obj = $linha | ConvertFrom-Json -ErrorAction Stop
    if ($obj.component -cne 'runtime-capture' -or $obj.errorCode -notmatch '^[A-Z_]+$') {
        throw '21-E-B3: classificacao de stderr nao higienizada'
    }
}
Write-Host ('PASS - Stdout: estruturados={0}, redigidos={1}; stderr: marcadores={2}.' -f $estruturados,$redigidosOut,$redigidosErr)
Write-Host 'PASS - Mensagens de falha Java reconhecidas sem persistir texto bruto.'
Write-Host 'PASS - Saidas desconhecidas, campo secreto e stack trace preservam somente marcadores.'
Write-Host 'PASS - Ordem, quantidade e segmentacao mantidas em sandbox privado.'
Write-Host 'PROTEGIDO - Sem processo filho, daemon, supervisor ativo, PostgreSQL, backup ou exclusao.'
Write-Host 'PASS - 21-E-B3 OFFLINE aprovado; integracao produtiva continua bloqueada.'
