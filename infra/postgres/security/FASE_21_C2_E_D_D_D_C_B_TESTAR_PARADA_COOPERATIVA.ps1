# FASE 21-C2-E-D-D-D-C-B - teste isolado da parada cooperativa.
# Nao inicia daemon, worker, scheduler, CLI, Flyway, Docker ou SQL.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath '.git' -PathType Container) -or
    -not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf)) {
    throw 'C2-E-D-D-D-C-B: executar na raiz de C:\dev\rasping-amazon'
}

$arquivos = @(
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java',
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignal.java',
    'src/test/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMainTest.java',
    'src/test/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignalTest.java',
    'config/operacao/continuous-startup-policy.v1.json'
)
foreach ($arquivo in $arquivos) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('C2-E-D-D-D-C-B: arquivo ausente: ' + $arquivo)
    }
}

$plano = Get-Content -LiteralPath 'config/operacao/continuous-startup-policy.v1.json' -Raw -Encoding UTF8 | ConvertFrom-Json
if ($plano.execution_mode -cne 'preparation_only' -or
    $plano.daemon_start_authorized -ne $false -or
    $plano.flyway_migrate_authorized -ne $false -or
    $plano.external_actions_authorized -ne $false -or
    $plano.automatic_restart_authorized -ne $false) {
    throw 'C2-E-D-D-D-C-B: politica operacional nao esta bloqueada; nao testar'
}
Write-Host '=== C2-E-D-D-D-C-B BLOQUEIOS PASS - daemon e efeitos externos continuam proibidos ==='

$status = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_STATUS_SUPERVISAO.ps1'
if (-not (Test-Path -LiteralPath $status -PathType Leaf)) {
    throw 'C2-E-D-D-D-C-B: status de supervisao da etapa anterior ausente'
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $status
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-D-C-B: status do daemon inconclusivo'
}

$main = Get-Content -LiteralPath $arquivos[0] -Raw -Encoding UTF8
if (-not $main.Contains('SupervisedStopSignal.startIfConfigured') -or
    -not $main.Contains('application.awaitTermination()') -or
    -not $main.Contains('SafeOperationalError.describe(exception)') -or
    -not $main.Contains('static String failureMessage(RuntimeException exception)')) {
    throw 'C2-E-D-D-D-C-B: integracao Java com parada cooperativa nao localizada'
}
Write-Host '=== C2-E-D-D-D-C-B JAVA PASS - parada cooperativa integrada ao lifecycle ==='

$envAntesExiste = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = $null
if ($envAntesExiste) {
    $envHash = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
}

# Compila toda a arvore de testes e executa tambem o teste legado
# que impede a exposicao de credenciais em erros operacionais.
& mvn -q '-Dtest=SupervisedStopSignalTest,ContinuousProcessingMainTest' test
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-D-C-B: Maven falhou; nao prosseguir'
}

$relatorio = 'target/surefire-reports/TEST-com.raspingamazon.infrastructure.bootstrap.SupervisedStopSignalTest.xml'
if (-not (Test-Path -LiteralPath $relatorio -PathType Leaf)) {
    throw 'C2-E-D-D-D-C-B: relatorio Surefire nao localizado'
}
[xml]$resultado = Get-Content -LiteralPath $relatorio -Raw -Encoding UTF8
if ([int]$resultado.testsuite.tests -ne 4 -or
    [int]$resultado.testsuite.failures -ne 0 -or
    [int]$resultado.testsuite.errors -ne 0 -or
    [int]$resultado.testsuite.skipped -ne 0) {
    throw 'C2-E-D-D-D-C-B: os 4 testes nao foram aprovados'
}
$relatorioLegado = 'target/surefire-reports/TEST-com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMainTest.xml'
if (-not (Test-Path -LiteralPath $relatorioLegado -PathType Leaf)) {
    throw 'C2-E-D-D-D-C-B: relatorio do teste legado de sigilo nao encontrado'
}
[xml]$resultadoLegado = Get-Content -LiteralPath $relatorioLegado -Raw -Encoding UTF8
if ([int]$resultadoLegado.testsuite.tests -ne 1 -or
    [int]$resultadoLegado.testsuite.failures -ne 0 -or
    [int]$resultadoLegado.testsuite.errors -ne 0 -or
    [int]$resultadoLegado.testsuite.skipped -ne 0) {
    throw 'C2-E-D-D-D-C-B: teste legado de sanitizacao de erros reprovado'
}
Write-Host '=== C2-E-D-D-D-C-B TESTES PASS - 5 testes, zero falhas e erros ==='
Write-Host '=== C2-E-D-D-D-C-B SIGILO PASS - SafeOperationalError restaurado e testado ==='

$envDepoisExiste = Test-Path -LiteralPath '.env' -PathType Leaf
if ($envDepoisExiste -ne $envAntesExiste) {
    throw 'C2-E-D-D-D-C-B: presenca do .env foi alterada'
}
if ($envDepoisExiste -and
    (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash) {
    throw 'C2-E-D-D-D-C-B: .env foi alterado'
}
Write-Host '=== C2-E-D-D-D-C-B ENV PASS - .env intacto ==='
Write-Host '=== C2-E-D-D-D-C-B GATE PASS - mecanismo de parada validado sem iniciar daemon ==='
Write-Host '=== C2-E-D-D-D-C-B PENDENTE - ainda faltam launcher e parada supervisionada Windows ==='
