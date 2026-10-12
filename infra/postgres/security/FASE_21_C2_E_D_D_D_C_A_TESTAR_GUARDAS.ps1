# FASE 21-C2-E-D-D-D-C-A - prova de bloqueios sem daemon ou segredos.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$funcoes = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
$funcoesPlano = 'infra/postgres/security/FASE_21_C2_E_D_D_D_B_FUNCOES_PLANO.ps1'
foreach ($arquivo in @($funcoes,$funcoesPlano)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw 'C2-E-D-D-D-C-A: funcoes ausentes'
    }
}
. $funcoesPlano
. $funcoes
Exigir-RaizProjeto-C
$plano = Ler-PlanoStartup-B -Caminho 'config/operacao/continuous-startup-policy.v1.json'
Exigir-PartidaBloqueada-C -Plano $plano

# Provar comportamento fail-closed diante de alteracoes em memoria.
# Nenhum arquivo de politica e alterado.
$campos = @(
    'daemon_start_authorized',
    'flyway_migrate_authorized',
    'external_actions_authorized',
    'automatic_restart_authorized'
)
foreach ($campo in $campos) {
    $mutacao = $plano | ConvertTo-Json -Depth 4 | ConvertFrom-Json
    $mutacao.PSObject.Properties[$campo].Value = $true
    $foiBloqueado = $false
    try { Exigir-PartidaBloqueada-C -Plano $mutacao | Out-Null }
    catch { $foiBloqueado = $true }
    if (-not $foiBloqueado) {
        throw ('C2-E-D-D-D-C-A: guarda falhou ao rejeitar ' + $campo)
    }
}
$modoAlterado = $plano | ConvertTo-Json -Depth 4 | ConvertFrom-Json
$modoAlterado.execution_mode = 'supervised_foreground'
$modoBloqueado = $false
try { Exigir-PartidaBloqueada-C -Plano $modoAlterado | Out-Null }
catch { $modoBloqueado = $true }
if (-not $modoBloqueado) {
    throw 'C2-E-D-D-D-C-A: guarda falhou ao rejeitar alteracao de modo'
}
Write-Host '=== C2-E-D-D-D-C-A NEGATIVOS PASS - cinco mutacoes em memoria rejeitadas ==='
Write-Host '=== C2-E-D-D-D-C-A TESTE PASS - nao iniciou daemon, Java, Flyway ou worker ==='
