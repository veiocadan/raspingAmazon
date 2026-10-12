# FASE 21-C2-E-D-D-C-C - verifica a politica inicial sem iniciar a CLI.
param(
    [string]$CaminhoPolitica = 'config/operacao/alert-policy.v1.json',
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/config/EnvironmentOperationalAlertPolicyProvider.java' -PathType Leaf)) {
    throw 'C2-E-D-D-C-C: execute na raiz C:\dev\rasping-amazon'
}
$funcoes = 'infra/postgres/security/FASE_21_C2_E_D_D_C_C_POLITICA_FUNCOES.ps1'
$executor = 'infra/postgres/security/FASE_21_C2_E_D_D_C_B_EXECUTAR_CLI_DPAPI.ps1'
$precheckAnterior = 'infra/postgres/security/FASE_21_C2_E_D_D_C_B_PRECHECK_CLI.ps1'
foreach ($arquivo in @($funcoes,$executor,$precheckAnterior)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw 'C2-E-D-D-C-C: script de dependencia nao encontrado; manter etapas anteriores instaladas'
    }
}
. $funcoes
$politica = Ler-PoliticaAlertasVersionada -Caminho $CaminhoPolitica
if ($politica.Count -ne 5) {
    throw 'C2-E-D-D-C-C: politica incompleta'
}
Write-Host '=== C2-E-D-D-C-C POLITICA PASS - 5 parametros publicos validos e versionados ==='

$codigoJava = [System.IO.File]::ReadAllText(
    [System.IO.Path]::GetFullPath('src/main/java/com/raspingamazon/infrastructure/config/EnvironmentOperationalAlertPolicyProvider.java')
)
foreach ($nome in $politica.Keys) {
    if (-not $codigoJava.Contains($nome)) {
        throw 'C2-E-D-D-C-C: contrato Java nao reconhece campo da politica'
    }
}
Write-Host '=== C2-E-D-D-C-C CONTRATO PASS - mesmas 5 chaves requeridas no Java ==='

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $precheckAnterior -DiretorioSegredos $DiretorioSegredos
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-D-C-C: precheck de credenciais e porta reprovado'
}
Write-Host '=== C2-E-D-D-C-C PRECHECK PASS - nao executou CLI nem alterou .env ou segredos ==='
