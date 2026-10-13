# FASE 21-C2-E-D-D-D-D-H: caminhos de falha e codigo de saida do daemon.
# Nao inicia daemon, nao acessa credenciais, banco, migracao ou integracoes.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'
Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
Exigir-PartidaBloqueada-C -Plano $plano
if ($plano.initial_schedule_interval -cne 'PT1H' -or $plano.shutdown_grace_seconds -ne 30) {
    throw 'D-H: politica de duracao divergente'
}
$registro = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-H: daemon ou registro produtivo encontrado'
}
Write-Host '=== C2-E-D-D-D-D-H POLITICA PASS - daemon bloqueado e ausente ==='

$envExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = if ($envExistia) { (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash } else { $null }
$main = 'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java'
$code = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($main))
$policyPosition = $code.IndexOf('DaemonStartupPolicyGate.requireAuthorized(policyPath)', [System.StringComparison]::Ordinal)
$bootstrapPosition = $code.IndexOf('application = applicationOpener.get()', [System.StringComparison]::Ordinal)
$closePosition = $code.IndexOf('application.close();', $bootstrapPosition, [System.StringComparison]::Ordinal)
$failurePosition = $code.IndexOf('exitCode = EXIT_OPERATIONAL_FAILURE;', $closePosition, [System.StringComparison]::Ordinal)
if ($policyPosition -lt 0 -or $bootstrapPosition -lt 0 -or $policyPosition -ge $bootstrapPosition -or
    $closePosition -lt 0 -or $failurePosition -lt 0 -or
    -not $code.Contains('SafeOperationalError.describe(exception)')) {
    throw 'D-H: ordem ou resultado de fechamento nao esta implementado'
}
Write-Host '=== C2-E-D-D-D-D-H CONTRATO PASS - falha do fechamento altera exit code ==='

$testes = @(
    'ContinuousProcessingFailurePathsTest',
    'ContinuousProcessingMainTest',
    'DaemonStartupPolicyGateTest',
    'SupervisedStopSignalTest'
)
& mvn -q ('-Dtest=' + ($testes -join ',')) test
if ($LASTEXITCODE -ne 0) {
    throw 'D-H: testes Maven reprovados; nao prosseguir'
}
$contagem = 0
foreach ($nome in $testes) {
    $arquivo = Join-Path 'target/surefire-reports' ('TEST-com.raspingamazon.infrastructure.bootstrap.' + $nome + '.xml')
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw 'D-H: relatorio esperado ausente'
    }
    [xml]$xml = Get-Content -LiteralPath $arquivo -Raw
    $suite = $xml.testsuite
    if ([int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0) {
        throw 'D-H: falhas, erros ou skips no gate Maven'
    }
    $contagem += [int]$suite.tests
}
if ($contagem -lt 25) {
    throw 'D-H: menos testes executados do que o esperado'
}
Write-Host ('=== C2-E-D-D-D-D-H JAVA PASS - ' + $contagem + ' testes, nenhuma falha ===')
Write-Host '=== C2-E-D-D-D-D-H RECURSOS PASS - falha simulada em runtime, JDBC e close ==='
Write-Host '=== C2-E-D-D-D-D-H SIGILO PASS - falhas verificadas por testes sem imprimir segredos ==='

if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExistia -or
    ($envExistia -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash) -or
    (Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-H: ambiente ou processo alterado'
}
Write-Host '=== C2-E-D-D-D-D-H ENV PASS - .env e daemon preservados ==='
Write-Host '=== C2-E-D-D-D-D-H GATE PASS - falhas do lifecycle nao produzem sucesso falso ==='
Write-Host '=== C2-E-D-D-D-D-H BLOQUEIO ATIVO - nao iniciar daemon nem efeitos externos ==='
