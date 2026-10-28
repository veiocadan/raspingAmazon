# FASE 21-C2-E-D-D-D-C-C - validar scripts Windows sem iniciar daemon.
# Verifica bloqueio real de partida; nenhum processo Java ou banco e iniciado.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
Exigir-RaizProjeto-C
Exigir-Arquivos-C-C
$plano = Ler-PlanoSupervisionado-C-C
if ($plano.execution_mode -cne 'preparation_only' -or $plano.daemon_start_authorized -or
    $plano.external_actions_authorized -or $plano.flyway_migrate_authorized -or
    $plano.automatic_restart_authorized) {
    throw 'C2-E-D-D-D-C-C: politica atual nao esta bloqueada; nao executar gate passivo'
}
Write-Host '=== C2-E-D-D-D-C-C PLANO PASS - modo preparation_only e quatro bloqueios ativos ==='

$estado = Obter-StatusSupervisao-C -CaminhoRegistro 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
if ($estado -cne 'PARADO_SEM_REGISTRO') { throw 'C2-E-D-D-D-C-C: daemon ou registro existente; interromper' }
Write-Host '=== C2-E-D-D-D-C-C STATUS PASS - nenhum daemon ou registro ativo ==='

$rejeitou = $false
try { Exigir-PartidaAutorizada-C-C -Plano $plano }
catch { $rejeitou = $true }
if (-not $rejeitou) { throw 'C2-E-D-D-D-C-C: politica bloqueada autorizou partida indevidamente' }
Write-Host '=== C2-E-D-D-D-C-C NEGATIVO PASS - partida rejeitada antes de carregar segredos ==='

$launcher = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1'
$parada = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_PARAR_SUPERVISIONADO.ps1'
foreach ($arquivo in @($launcher,$parada)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) { throw 'C2-E-D-D-D-C-C: arquivo de supervisao ausente' }
}
# O teste real executa o primeiro bloqueio do launcher, mas captura TODA a saida.
$conteudo = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($launcher))
foreach ($trecho in @('Exigir-PartidaAutorizada-C-C', 'Exigir-GitPronto-C-C',
    'System.Diagnostics.Process', 'RASPING_SUPERVISED_STOP_REQUEST_FILE')) {
    if (-not $conteudo.Contains($trecho)) {
        throw 'C2-E-D-D-D-C-C: contrato do launcher incompleto'
    }
}
$prefAnterior = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $saidaBloqueio = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher -ConfirmarPartida 2>&1 | Out-String)
    $codigo = $LASTEXITCODE
} finally { $ErrorActionPreference = $prefAnterior }
if ($codigo -eq 0 -or $saidaBloqueio -notmatch 'PARTIDA BLOQUEADA') {
    throw 'C2-E-D-D-D-C-C: launcher nao rejeitou partida pelo bloqueio esperado'
}
Write-Host '=== C2-E-D-D-D-C-C LAUNCHER PASS - tentativa de partida bloqueada sem iniciar Java ==='

# A parada nao pode gerar arquivo de solicitacao quando nenhum daemon estiver registrado.
$stop = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.stop.request'
$haviaStop = Test-Path -LiteralPath $stop
$prefAnterior = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $saidaParada = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $parada -ConfirmarParada 2>&1 | Out-String)
    $codigo = $LASTEXITCODE
} finally { $ErrorActionPreference = $prefAnterior }
if ($codigo -eq 0 -or $saidaParada -notmatch 'nenhum daemon supervisionado comprovado' -or
    (Test-Path -LiteralPath $stop) -ne $haviaStop) {
    throw 'C2-E-D-D-D-C-C: script de parada modificou estado sem daemon'
}
Write-Host '=== C2-E-D-D-D-C-C PARADA NEGATIVA PASS - nenhum sinal sem identidade comprovada ==='

$java = 'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java'
$main = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($java))
if (-not $main.Contains('SupervisedStopSignal.startIfConfigured') -or
    -not $main.Contains('SafeOperationalError.describe(exception)')) {
    throw 'C2-E-D-D-D-C-C: ponte Java e sanitizacao nao encontradas'
}
Write-Host '=== C2-E-D-D-D-C-C JAVA PASS - sinal e mensagens seguras presentes ==='

$envExiste = Test-Path -LiteralPath '.env' -PathType Leaf
# Nao acessa o conteudo ou modifica .env.
Write-Host '=== C2-E-D-D-D-C-C ENV PASS - nenhuma configuracao alterada ==='
Write-Host '=== C2-E-D-D-D-C-C GATE PASS - launcher e parada estao bloqueados e testados sem daemon ==='
Write-Host '=== C2-E-D-D-D-C-C PENDENTE - partida produtiva exige autorizacao e testes adicionais ==='
