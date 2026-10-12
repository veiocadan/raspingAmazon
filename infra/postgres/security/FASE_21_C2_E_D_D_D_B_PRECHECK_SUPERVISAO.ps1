# FASE 21-C2-E-D-D-D-B - precheck de PLANEJAMENTO da inicializacao supervisionada.
# Somente leitura: NAO executa daemon, Java, Maven, Flyway migrate nem altera .env.
param(
    [string]$CaminhoPlano = 'config/operacao/continuous-startup-policy.v1.json',
    [string]$CaminhoPoliticaAlertas = 'config/operacao/alert-policy.v1.json',
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$CaminhoBackupBanco = 'C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf)) {
    throw 'C2-E-D-D-D-B: executar na raiz C:\dev\rasping-amazon'
}
$arquivoFuncoes = 'infra/postgres/security/FASE_21_C2_E_D_D_D_B_FUNCOES_PLANO.ps1'
$gateAnterior = 'infra/postgres/security/FASE_21_C2_E_D_D_D_A_PRECHECK_BOOTSTRAP.ps1'
foreach ($arquivo in @($arquivoFuncoes,$gateAnterior)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw 'C2-E-D-D-D-B: falta arquivo da fase anterior ou deste pacote'
    }
}
. $arquivoFuncoes
Verificar-Git-B

# Preservar fingerprint do .env para detectar qualquer alteracao durante o precheck.
$envAntesExiste = Test-Path -LiteralPath '.env' -PathType Leaf
$hashAntes = $null
if ($envAntesExiste) {
    $hashAntes = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
}

# O gate anterior confirma backup, usuario DPAPI, rede, fontes e daemon local parado.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $gateAnterior `
    -DiretorioSegredos $DiretorioSegredos `
    -CaminhoPolitica $CaminhoPoliticaAlertas `
    -CaminhoBackupBanco $CaminhoBackupBanco
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-D-B: gate A reprovado; nenhuma partida permitida'
}

$plano = Ler-PlanoStartup-B -Caminho $CaminhoPlano
Verificar-ContratoRuntime-B
Write-Host '=== C2-E-D-D-D-B PLANO PASS - politica valida, em modo preparation_only ==='
Write-Host ('=== C2-E-D-D-D-B CADENCIA PASS - ' + $plano.initial_schedule_interval + ' e proposta publica, NAO aplicada ao Java ===')
Write-Host '=== C2-E-D-D-D-B BLOQUEIOS PASS - daemon, Flyway, acoes externas e reinicio automatico proibidos ==='
Write-Host ('=== C2-E-D-D-D-B AMBIENTE CADENCIA - ' + (Obter-PresencaChave-B -Nome 'PROCESSING_SCHEDULE_INTERVAL') + ' ===')

$envDepoisExiste = Test-Path -LiteralPath '.env' -PathType Leaf
if ($envAntesExiste -ne $envDepoisExiste) {
    throw 'C2-E-D-D-D-B: a existencia do .env mudou durante o precheck'
}
if ($envDepoisExiste) {
    $hashDepois = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
    if ($hashDepois -cne $hashAntes) {
        throw 'C2-E-D-D-D-B: conteudo do .env foi alterado; investigar'
    }
}
Write-Host '=== C2-E-D-D-D-B ENV PASS - .env intocado ==='
Write-Host '=== C2-E-D-D-D-B PRECHECK PASS - somente planejamento; nenhum processo produtivo iniciado ==='
Write-Host '=== C2-E-D-D-D-B BLOQUEIO ATIVO - NAO iniciar daemon, worker, scheduler ou Flyway migrate ==='
