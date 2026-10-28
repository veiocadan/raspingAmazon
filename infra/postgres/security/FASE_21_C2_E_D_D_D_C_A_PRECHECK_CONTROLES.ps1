# FASE 21-C2-E-D-D-D-C-A - gate de controles passivos de supervisao.
# Somente leitura; nao cria registros, senhas ou processos produtivos.
param(
    [string]$CaminhoRegistro = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json',
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$CaminhoBackupBanco = 'C:\dev\rasping-amazon-backups\rasping_amazon_c2d_20261012_174306.dump'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$anteriores = @(
    'infra/postgres/security/FASE_21_C2_E_D_D_D_B_PRECHECK_SUPERVISAO.ps1',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_STATUS_SUPERVISAO.ps1',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_TESTAR_GUARDAS.ps1',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
)
foreach ($arquivo in $anteriores) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('C2-E-D-D-D-C-A: dependencia ausente: ' + $arquivo)
    }
}
$envExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = $null
if ($envExistia) { $envHash = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash }

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $anteriores[0] `
    -DiretorioSegredos $DiretorioSegredos -CaminhoBackupBanco $CaminhoBackupBanco
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-D-C-A: gate B reprovado; parar'
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $anteriores[1] `
    -CaminhoRegistro $CaminhoRegistro
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-D-C-A: status do daemon inconclusivo; parar'
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $anteriores[2]
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-D-C-A: testes negativos dos bloqueios reprovaram'
}

$envDepois = Test-Path -LiteralPath '.env' -PathType Leaf
if ($envDepois -ne $envExistia) {
    throw 'C2-E-D-D-D-C-A: existencia do .env mudou'
}
if ($envDepois -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash) {
    throw 'C2-E-D-D-D-C-A: .env foi alterado'
}
Write-Host '=== C2-E-D-D-D-C-A ENV PASS - .env preservado ==='
Write-Host '=== C2-E-D-D-D-C-A PRECHECK PASS - controles passivos e bloqueios conferidos ==='
Write-Host '=== C2-E-D-D-D-C-A BLOQUEIO ATIVO - NAO iniciar daemon, worker, scheduler nem Flyway migrate ==='
