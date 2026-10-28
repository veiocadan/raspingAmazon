# FASE 21-E-D1: testes hermeticos em memoria; nenhum processo filho.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).ProviderPath
. (Join-Path $raiz 'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1')
$arquivo = Join-Path $raiz 'config/operacao/supervisor-log-integration-review.v1.json'
$original = Get-Content -LiteralPath $arquivo -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
Assert-21ED1-Contrato -Contrato $original
Write-Host 'PASS - Contrato D1 original validado.'

function Testar-MutacaoD1 {
    param([Parameter(Mandatory=$true)][string]$Nome,
          [Parameter(Mandatory=$true)][scriptblock]$Mutacao)
    $obj = $original | ConvertTo-Json -Depth 10 | ConvertFrom-Json
    & $Mutacao $obj
    $rejeitado = $false
    try { Assert-21ED1-Contrato -Contrato $obj }
    catch { $rejeitado = $true }
    if (-not $rejeitado) { throw ('21-E-D1: adulteracao aceita: ' + $Nome) }
    Write-Host ('PASS - Adulteracao rejeitada: ' + $Nome)
}
Testar-MutacaoD1 'autorizar-daemon' { param($v) $v.daemon_start_authorized = $true }
Testar-MutacaoD1 'autorizar-captura' { param($v) $v.continuous_log_capture_authorized = $true }
Testar-MutacaoD1 'autorizar-integracao' { param($v) $v.supervisor_integration_authorized = $true }
Testar-MutacaoD1 'autorizar-exclusao' { param($v) $v.destructive_actions_authorized = $true }
Testar-MutacaoD1 'trocar-modo' { param($v) $v.execution_mode = 'supervised' }
Testar-MutacaoD1 'trocar-estado-adaptador' { param($v) $v.production_adapter_status = 'ready' }
Testar-MutacaoD1 'alterar-fingerprint' { param($v) $v.expected_sources[0].sha256 = ('0' * 64) }
Testar-MutacaoD1 'caminho-externo' { param($v) $v.expected_sources[0].path = '../outro.ps1' }
Testar-MutacaoD1 'campo-surpresa' { param($v) $v | Add-Member -NotePropertyName 'enable_runtime' -NotePropertyValue $true }
Testar-MutacaoD1 'remover-fonte' { param($v) $v.expected_sources = @($v.expected_sources | Select-Object -Skip 1) }

$supervisor = Get-Content -LiteralPath (Join-Path $raiz 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1') -Raw -Encoding UTF8
$b9 = Get-Content -LiteralPath (Join-Path $raiz 'infra/retencao/FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1') -Raw -Encoding UTF8
Assert-21ED1-FonteSupervisor -Conteudo $supervisor
Assert-21ED1-FonteB9 -Conteudo $b9
Write-Host 'PASS - Garantias do supervisor e limite sintetico B9 encontrados.'

$alterado = $supervisor.Replace('Exigir-PartidaAutorizada-C-C','FUNCAO_REMOVIDA')
$rejeitado = $false
try { Assert-21ED1-FonteSupervisor -Conteudo $alterado } catch { $rejeitado = $true }
if (-not $rejeitado) { throw '21-E-D1: remoção do gate foi aceita' }
Write-Host 'PASS - Supervisor sem gate de partida rejeitado.'

$alterado = $b9.Replace('target/retencao-21eb1-testes','logs-operacionais')
$rejeitado = $false
try { Assert-21ED1-FonteB9 -Conteudo $alterado } catch { $rejeitado = $true }
if (-not $rejeitado) { throw '21-E-D1: B9 fora do sandbox foi aceito' }
Write-Host 'PASS - Reutilizacao produtiva direta da B9 rejeitada.'

$startup = Get-Content -LiteralPath (Join-Path $raiz 'config/operacao/continuous-startup-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$retencao = Get-Content -LiteralPath (Join-Path $raiz 'config/operacao/retention-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$catalogo = Get-Content -LiteralPath (Join-Path $raiz 'config/operacao/operational-log-catalog.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-21ED1-BloqueioPoliticas -Startup $startup -Retencao $retencao -Catalogo $catalogo
$startup.daemon_start_authorized = $true
$rejeitado = $false
try { Assert-21ED1-BloqueioPoliticas -Startup $startup -Retencao $retencao -Catalogo $catalogo }
catch { $rejeitado = $true }
if (-not $rejeitado) { throw '21-E-D1: partida autorizada indevidamente' }
Write-Host 'PASS - Mutacao de autorizacao do startup rejeitada.'
Write-Host 'PROTEGIDO - Testes apenas de memoria; nenhum arquivo foi criado, alterado ou removido.'
Write-Host 'PASS - Gate 21-E-D1 hermetico aprovado; captura produtiva continua bloqueada.'
