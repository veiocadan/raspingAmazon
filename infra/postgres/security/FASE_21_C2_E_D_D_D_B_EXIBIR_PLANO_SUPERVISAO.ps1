# FASE 21-C2-E-D-D-D-B - exibe plano de supervisao sem executar o daemon.
# Nao recebe senhas. Nao altera configuracao nem inicia processo produtivo.
param(
    [string]$CaminhoPlano = 'config/operacao/continuous-startup-policy.v1.json'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$arquivoFuncoes = 'infra/postgres/security/FASE_21_C2_E_D_D_D_B_FUNCOES_PLANO.ps1'
if (-not (Test-Path -LiteralPath $arquivoFuncoes -PathType Leaf)) {
    throw 'C2-E-D-D-D-B: arquivo de funcoes nao encontrado'
}
. $arquivoFuncoes
$plano = Ler-PlanoStartup-B -Caminho $CaminhoPlano
Write-Host '=== C2-E-D-D-D-B PLANO DE INICIALIZACAO ==='
Write-Host ('Modo: ' + $plano.execution_mode)
Write-Host ('Intervalo inicial proposto: ' + $plano.initial_schedule_interval)
Write-Host ('Prazo previsto de parada graciosa: ' + $plano.shutdown_grace_seconds + ' segundos')
Write-Host 'Partida do daemon: BLOQUEADA'
Write-Host 'Migracao Flyway: BLOQUEADA'
Write-Host 'Execucao de publicacoes ou chamadas externas: NAO AUTORIZADA NESTA FASE'
Write-Host 'Reinicio automatico: BLOQUEADO'
Write-Host 'Precondicoes futuras: revisar configuracoes operacionais, identidade runtime, recursos externos, recovery e estrategia de shutdown.'
Write-Host '=== C2-E-D-D-D-B PLANO PASS - exibicao somente leitura; nenhum Java iniciado ==='
