# FASE 21-C2-E-D-D-C-C - CLI operacional com politica JSON versionada.
# Reutiliza o executor DPAPI anterior; nao muda .env ou configuracao permanente.
param(
    [string]$CaminhoPolitica = 'config/operacao/alert-policy.v1.json',
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [switch]$ConfirmarExecucao
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmarExecucao) {
    throw 'C2-E-D-D-C-C: execute somente apos PRECHECK PASS, com -ConfirmarExecucao'
}

$precheck = 'infra/postgres/security/FASE_21_C2_E_D_D_C_C_PRECHECK_POLITICA.ps1'
$funcoes = 'infra/postgres/security/FASE_21_C2_E_D_D_C_C_POLITICA_FUNCOES.ps1'
$executor = 'infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_DPAPI.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $precheck -CaminhoPolitica $CaminhoPolitica -DiretorioSegredos $DiretorioSegredos
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-C-C: precheck reprovado; nao iniciar a CLI'
}
. $funcoes
$politica = Ler-PoliticaAlertasVersionada -Caminho $CaminhoPolitica
$nomes = @(
    'ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD',
    'ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS',
    'ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS',
    'ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION',
    'ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES'
)
$anteriores = @{}
foreach ($nome in $nomes) {
    $anteriores[$nome] = [System.Environment]::GetEnvironmentVariable($nome, 'Process')
}
$envAntes = Test-Path -LiteralPath '.env' -PathType Leaf
$hashAntes = $null
if ($envAntes) {
    $hashAntes = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
}
try {
    foreach ($nome in $nomes) {
        [System.Environment]::SetEnvironmentVariable($nome,$politica[$nome],'Process')
    }
    Write-Host '=== C2-E-D-D-C-C POLITICA APLICADA PASS - parametros iniciais somente na arvore de processos ==='
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $executor -DiretorioSegredos $DiretorioSegredos -ConfirmarExecucao
    if ($LASTEXITCODE -ne 0) {
        throw 'C2-E-D-D-C-C: alguma consulta operacional falhou; nao repetir automaticamente'
    }
    Write-Host '=== C2-E-D-D-C-C CLI PASS - cinco consultas operacionais aprovadas com politica versionada ==='
} finally {
    foreach ($nome in $nomes) {
        [System.Environment]::SetEnvironmentVariable($nome,$anteriores[$nome],'Process')
    }
    Write-Host '=== C2-E-D-D-C-C AMBIENTE PASS - variaveis ALERT restauradas ==='
    $envDepois = Test-Path -LiteralPath '.env' -PathType Leaf
    if ($envAntes -ne $envDepois) {
        throw 'C2-E-D-D-C-C: presenca de .env foi alterada'
    }
    if ($envDepois) {
        $hashDepois = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash
        if ($hashAntes -cne $hashDepois) {
            throw 'C2-E-D-D-C-C: .env foi alterado durante a execucao'
        }
    }
    Write-Host '=== C2-E-D-D-C-C ENV INTACTO PASS - arquivo .env preservado ==='
}
