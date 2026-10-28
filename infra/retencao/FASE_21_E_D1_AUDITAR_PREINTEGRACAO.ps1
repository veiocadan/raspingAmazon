# FASE 21-E-D1: auditoria estaticamente bloqueada; SOMENTE LEITURA.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
. (Join-Path $raiz 'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1')
. (Join-Path $raiz 'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1')
. (Join-Path $raiz 'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1')

$contrato = Get-Content -LiteralPath (Join-Path $raiz 'config/operacao/supervisor-log-integration-review.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
$startup = Get-Content -LiteralPath (Join-Path $raiz 'config/operacao/continuous-startup-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
$retencao = Get-Content -LiteralPath (Join-Path $raiz 'config/operacao/retention-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
$catalogo = Get-Content -LiteralPath (Join-Path $raiz 'config/operacao/operational-log-catalog.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
Assert-21ED1-Contrato -Contrato $contrato
Assert-21ED1-BloqueioPoliticas -Startup $startup -Retencao $retencao -Catalogo $catalogo
Assert-21EA-Politica -Politica $retencao
Assert-21EB8-Catalogo -Catalogo $catalogo
Write-Host 'PASS - Quatro contratos e bloqueios confirmados.'

$branch = (& git branch --show-current | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw '21-E-D1: branch inesperada'
}
Assert-21ED1-FontesIntactas -Raiz $raiz -Contrato $contrato
Write-Host 'PASS - Cinco fontes intactas conforme ZIP recebido e verificado.'
$supervisor = Get-Content -LiteralPath (Join-Path $raiz $contrato.expected_sources[0].path) -Raw -Encoding UTF8
$b9 = Get-Content -LiteralPath (Join-Path $raiz $contrato.expected_sources[2].path) -Raw -Encoding UTF8
Assert-21ED1-FonteSupervisor -Conteudo $supervisor
Assert-21ED1-FonteB9 -Conteudo $b9
Write-Host 'PASS - Gate de partida, reserva, PID e parada supervisionada preservados.'
Write-Host 'PASS - B9 confirmada como captura exclusivamente sintetica em target.'
Write-Host 'CAPTURA_SUPERVISOR=IMPLEMENTADA_MAS_NAO_AUTORIZADA'
Write-Host 'ESCRITOR_PRODUTIVO=IMPLEMENTADO_MAS_NAO_AUTORIZADO'
Write-Host 'PARADA_COOPERATIVA_POR_FALHA_DE_CAPTURA=VALIDADA_APENAS_EM_TESTE'
Write-Host 'POLITICA_LOGS_EXTERNA=PROPOSTA_NAO_ATIVADA'
Write-Host 'PROTEGIDO - Sem processo filho, JVM, credenciais, PostgreSQL ou backups.'
Write-Host 'PASS - Revisao D1 somente-leitura aprovada; nenhuma ativacao autorizada.'
