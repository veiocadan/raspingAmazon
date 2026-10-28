# Testes B8: somente leitura, objetos sinteticos em memoria.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).ProviderPath
Set-Location -LiteralPath $raiz
. (Join-Path $PSScriptRoot 'FASE_21_E_B8_FUNCOES_CATALOGO.ps1')

function Assert-21EB8-Teste {
    param([bool] $Condicao, [string] $Mensagem)
    if (-not $Condicao) { throw ('21-E-B8: ' + $Mensagem) }
}

$startup = Get-Content -LiteralPath 'config/operacao/continuous-startup-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$retencao = Get-Content -LiteralPath 'config/operacao/retention-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-21EB8-Teste ($startup.execution_mode -ceq 'preparation_only' -and
    $startup.daemon_start_authorized -eq $false -and
    $startup.flyway_migrate_authorized -eq $false -and
    $startup.external_actions_authorized -eq $false -and
    $startup.automatic_restart_authorized -eq $false) 'startup nao bloqueado.'
Assert-21EB8-Teste ($retencao.execution_mode -ceq 'inventory_only' -and
    $retencao.destructive_actions_authorized -eq $false -and
    $retencao.continuous_log_capture_authorized -eq $false) 'retencao nao bloqueada.'
Write-Host 'PASS - Startup preparation_only e captura/exclusoes proibidas.'

$catalogo = Get-Content -LiteralPath 'config/operacao/operational-log-catalog.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-21EB8-Catalogo -Catalogo $catalogo
Write-Host 'PASS - Catalogo de seis eventos validado com campos estritos.'

$worker = Get-Content -LiteralPath 'src/main/java/com/raspingamazon/application/orchestration/worker/ProcessingWorker.java' -Raw -Encoding UTF8
$recorder = Get-Content -LiteralPath 'src/main/java/com/raspingamazon/application/observability/BestEffortIntegrationObservationRecorder.java' -Raw -Encoding UTF8
$codigos = @([regex]::Matches(($worker + "`n" + $recorder), '(?:processing\.job|integration\.observation)\.[a-z-]+') | ForEach-Object { $_.Value } | Sort-Object -Unique)
$esperados = @($catalogo.events | ForEach-Object { $_.code } | Sort-Object)
Assert-21EB8-Teste ($codigos.Count -eq 6) 'inventario de produtores mudou.'
for ($i=0; $i -lt 6; $i++) {
    Assert-21EB8-Teste ($codigos[$i] -ceq $esperados[$i]) 'codigo Java nao catalogado.'
}
Write-Host 'PASS - Inventario Java corresponde exatamente aos seis codigos.'

$protegido = 'SENHA_FICTICIA_B8_7n6q'
$casos = @(
    [ordered]@{timestamp='2026-10-27T12:00:00Z';level='INFO';event='processing.job.claimed';component='processing-worker';operation='execute-job';outcome='CLAIMED';runId=14;jobId=15;jobType='COLLECT_DEALS';asin=$protegido;integration=$protegido},
    [ordered]@{timestamp='2026-10-27T12:00:01Z';level='INFO';event='processing.job.succeeded';component='processing-worker';operation='execute-job';outcome='SUCCEEDED';jobId=15},
    [ordered]@{timestamp='2026-10-27T12:00:02Z';level='WARN';event='processing.job.retry-scheduled';component='processing-worker';operation='execute-job';outcome='RETRY_WAIT';jobId=15},
    [ordered]@{timestamp='2026-10-27T12:00:03Z';level='ERROR';event='processing.job.dead';component='processing-worker';operation='execute-job';outcome='DEAD';jobId=15},
    [ordered]@{timestamp='2026-10-27T12:00:04Z';level='WARN';event='processing.job.failure-handled';component='processing-worker';operation='execute-job';outcome='PENDING';jobId=15},
    [ordered]@{timestamp='2026-10-27T12:00:05Z';level='ERROR';event='integration.observation.persistence-failed';component='integration-observation-recorder';operation='persist-observation';outcome='FAILURE';failureOrigin='INTERNAL';failureType='PERMANENT';errorCode=$protegido;integration=$protegido}
)
foreach ($caso in $casos) {
    $json = ConvertTo-Json -InputObject $caso -Compress -Depth 4
    $seguro = ConvertTo-21EB8-EventoSeguro -Linha $json -Canal stdout -Catalogo $catalogo
    Assert-21EB8-Teste ($seguro.kind -ceq 'structured_allowlisted') 'evento autorizado rejeitado.'
    Assert-21EB8-Teste ($seguro.event -ceq $caso.event) 'identidade do evento perdida.'
    $persistido = ConvertTo-Json -InputObject $seguro -Compress -Depth 5
    Assert-21EB8-Teste (-not $persistido.Contains($protegido)) 'texto sensivel persistido.'
}
Write-Host 'PASS - Seis eventos aprovados; sem texto livre ou segredo ficticio na projecao.'

$invalido = @(
    '{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.desconhecido","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED"}',
    '{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED","token":"SECRETO"}',
    '{"timestamp":"2026-10-27T12:00:00Z","level":"ERROR","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED"}',
    '{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED","jobId":-1}',
    '{"timestamp":"2026-10-27T12:00:00Z","level":"INFO","event":"processing.job.claimed","component":"processing-worker","operation":"execute-job","outcome":"CLAIMED","jobType":"DESCONHECIDO"}',
    '{"nao":"json completo"}'
)
foreach ($linha in $invalido) {
    $seguro = ConvertTo-21EB8-EventoSeguro -Linha $linha -Canal stdout -Catalogo $catalogo
    Assert-21EB8-Teste ($seguro.kind -ceq 'redacted_marker') 'entrada invalida aceita.'
}
$err = ConvertTo-21EB8-EventoSeguro -Linha ((ConvertTo-Json -InputObject $casos[0] -Compress)) -Canal stderr -Catalogo $catalogo
Assert-21EB8-Teste ($err.kind -ceq 'redacted_marker' -and $err.channel -ceq 'stderr') 'stderr nao redigido.'
Write-Host 'PASS - Seis entradas invalidas e stderr redigidos sem texto bruto.'

$mutacoes = @(
    @{chave='continuous_log_capture_authorized'; valor=$true},
    @{chave='destructive_actions_authorized'; valor=$true},
    @{chave='execution_mode'; valor='supervised'}
)
foreach ($mutacao in $mutacoes) {
    $copia = ConvertFrom-Json -InputObject (ConvertTo-Json -InputObject $catalogo -Depth 6 -Compress)
    $copia.($mutacao.chave) = $mutacao.valor
    $rejeitado = $false
    try { Assert-21EB8-Catalogo -Catalogo $copia } catch { $rejeitado = $true }
    Assert-21EB8-Teste $rejeitado ('mutacao de politica aceita: ' + $mutacao.chave)
}
$copia = ConvertFrom-Json -InputObject (ConvertTo-Json -InputObject $catalogo -Depth 6 -Compress)
$copia.events[0].code = 'processing.job.desconhecido'
$rejeitado = $false
try { Assert-21EB8-Catalogo -Catalogo $copia } catch { $rejeitado = $true }
Assert-21EB8-Teste $rejeitado 'codigo nao autorizado no catalogo.'
Write-Host 'PASS - Quatro adulteracoes de catalogo rejeitadas.'
Write-Host 'PROTEGIDO - Validacao offline: sem processos filhos, daemon, PostgreSQL, logs reais, arquivos temporarios ou exclusoes.'
Write-Host 'PASS - Gate 21-E-B8 offline aprovado; nenhuma integracao produtiva autorizada.'
