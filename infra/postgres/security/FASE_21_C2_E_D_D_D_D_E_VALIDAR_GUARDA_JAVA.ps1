# FASE 21-C2-E-D-D-D-D-E - valida a barreira Java SEM iniciar daemon.
# Executa somente testes JUnit com bootstrap simulado e verificacoes passivas.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'

Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
Exigir-PartidaBloqueada-C -Plano $plano
if ($plano.initial_schedule_interval -cne 'PT1H' -or [int]$plano.shutdown_grace_seconds -ne 30) {
    throw 'D-E: politica de intervalo ou parada divergente'
}
$registro = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-E: daemon ou registro detectado; nao prosseguir'
}
Write-Host '=== C2-E-D-D-D-D-E POLITICA PASS - partida produtiva bloqueada ==='

$arquivos = @(
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java',
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/DaemonStartupPolicyGate.java',
    'src/test/java/com/raspingamazon/infrastructure/bootstrap/DaemonStartupPolicyGateTest.java',
    'src/test/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMainTest.java',
    'src/test/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignalTest.java',
    'config/operacao/continuous-startup-policy.v1.json',
    'pom.xml'
)
foreach ($arquivo in $arquivos) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('D-E: arquivo necessario ausente: ' + $arquivo)
    }
}
$main = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($arquivos[0]))
$gate = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($arquivos[1]))
$posicaoGate = $main.IndexOf('DaemonStartupPolicyGate.requireAuthorized(policyPath)')
$posicaoBootstrap = $main.IndexOf('application = applicationOpener.get()')
if ($posicaoGate -lt 0 -or $posicaoBootstrap -lt 0 -or
    $posicaoGate -ge $posicaoBootstrap -or
    -not $main.Contains('ContinuousProcessingBootstrap::open') -or
    -not $main.Contains('SupervisedStopSignal.startIfConfigured') -or
    -not $main.Contains('SafeOperationalError.describe(exception)') -or
    -not $gate.Contains('STRICT_DUPLICATE_DETECTION')) {
    throw 'D-E: contrato da guarda Java nao encontrado ou ordem incorreta'
}
Write-Host '=== C2-E-D-D-D-D-E ORDEM PASS - autorizacao antes de JDBC e recovery ==='

$envExiste = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = if ($envExiste) { (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash } else { $null }

& mvn -q '-Dtest=DaemonStartupPolicyGateTest,ContinuousProcessingMainTest,SupervisedStopSignalTest' test
if ($LASTEXITCODE -ne 0) { throw 'D-E: testes Maven reprovados; nao prosseguir' }

$relatorios = @(
    @{ path = 'target/surefire-reports/TEST-com.raspingamazon.infrastructure.bootstrap.DaemonStartupPolicyGateTest.xml'; min = 12 },
    @{ path = 'target/surefire-reports/TEST-com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMainTest.xml'; min = 1 },
    @{ path = 'target/surefire-reports/TEST-com.raspingamazon.infrastructure.bootstrap.SupervisedStopSignalTest.xml'; min = 4 }
)
$total = 0
foreach ($item in $relatorios) {
    if (-not (Test-Path -LiteralPath $item.path -PathType Leaf)) {
        throw 'D-E: relatorio de testes ausente'
    }
    [xml]$xml = Get-Content -LiteralPath $item.path -Raw -Encoding UTF8
    $suite = $xml.testsuite
    if ([int]$suite.tests -lt [int]$item.min -or
        [int]$suite.failures -ne 0 -or
        [int]$suite.errors -ne 0 -or
        [int]$suite.skipped -ne 0) {
        throw 'D-E: testes de autorizacao, sigilo ou parada insuficientes'
    }
    $total += [int]$suite.tests
}
Write-Host ('=== C2-E-D-D-D-D-E JAVA PASS - ' + $total + ' testes sem falhas ===')

if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExiste -or
    ($envExiste -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash)) {
    throw 'D-E: .env foi alterado'
}
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-E: daemon detectado apos testes'
}
Write-Host '=== C2-E-D-D-D-D-E ENV PASS - .env intacto e nenhum daemon produtivo ==='
Write-Host '=== C2-E-D-D-D-D-E GATE PASS - Java rejeita bypass da politica antes do bootstrap ==='
Write-Host '=== C2-E-D-D-D-D-E BLOQUEIO ATIVO - nao autoriza partida, migracao ou efeitos externos ==='
