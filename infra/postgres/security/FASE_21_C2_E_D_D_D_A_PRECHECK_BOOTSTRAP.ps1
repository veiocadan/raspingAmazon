# FASE 21-C2-E-D-D-D-A - Gate ANTES de preparar inicializacao permanente.
# Somente leitura. Nao executa Maven, migracao, SQL ou Java; nao inicia o daemon.
param(
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$CaminhoPolitica = 'config/operacao/alert-policy.v1.json',
    [string]$CaminhoBackupBanco = 'C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath '.git' -PathType Container)) {
    throw 'C2-E-D-D-D-A: execute na raiz C:\dev\rasping-amazon'
}

$funcoes = 'infra/postgres/security/FASE_21_C2_E_D_D_D_A_FUNCOES_BOOTSTRAP.ps1'
$precheckAnterior = 'infra/postgres/security/FASE_21_C2_E_D_D_C_C_PRECHECK_POLITICA.ps1'
if (-not (Test-Path -LiteralPath $funcoes -PathType Leaf) -or
    -not (Test-Path -LiteralPath $precheckAnterior -PathType Leaf)) {
    throw 'C2-E-D-D-D-A: faltam dependencias de precheck das fases anteriores'
}
. $funcoes

# Bloquear se o bootstrap produtivo ja estiver ativo. Nao e garantia para
# processos em outras maquinas: e somente uma das verificacoes do gate local.
Verificar-SemDaemonWindows
Verificar-BackupPreexistente -CaminhoBackupBanco $CaminhoBackupBanco
Verificar-ContratoBootstrap

foreach ($comando in @('mvn','java','git','docker')) {
    if ($null -eq (Get-Command $comando -ErrorAction SilentlyContinue)) {
        throw ('C2-E-D-D-D-A: comando necessario nao localizado: ' + $comando)
    }
}
Write-Host '=== C2-E-D-D-D-A FERRAMENTAS PASS - Maven, Java, Git e Docker localizados ==='

# Gate C-C existente valida politica JSON, disponibilidade DPAPI e bind da porta.
# Nao inicia CLI, nao utiliza SQL e nao altera senhas.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $precheckAnterior -DiretorioSegredos $DiretorioSegredos -CaminhoPolitica $CaminhoPolitica
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-D-A: politica, DPAPI ou porta reprovadas; nao iniciar daemon'
}

Write-Host '=== C2-E-D-D-D-A PRECHECK PASS - analise de bootstrap concluida, sem execucao produtiva ==='
Write-Host '=== C2-E-D-D-D-A BLOQUEIO ATIVO - NAO iniciar daemon, worker, scheduler ou Flyway migrate ==='
