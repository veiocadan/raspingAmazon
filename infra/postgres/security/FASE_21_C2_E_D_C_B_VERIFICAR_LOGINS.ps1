# C2-E-D-C-B: testes reais de ambas as credenciais sem imprime-las.
param(
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$NomeContainer = 'rasping-amazon-postgres'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function ExigirSucesso([string]$Mensagem) { if ($LASTEXITCODE -ne 0) { throw $Mensagem } }
function LerSegredo([string]$Caminho) {
    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) { throw 'C2-E-D-C-B: credencial local ausente' }
    $cifra = [System.IO.File]::ReadAllText($Caminho).Trim()
    $seguro = ConvertTo-SecureString $cifra
    $ptr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try {
        $valor = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
        if ($valor -cnotmatch '^[0-9A-F]{64}$') { throw 'C2-E-D-C-B: formato DPAPI inesperado' }
        return $valor
    } finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
if (-not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf)) {
    throw 'C2-E-D-C-B: execute na raiz do repositorio'
}
$runtime = LerSegredo (Join-Path $DiretorioSegredos 'rasping_runtime.dpapi')
$migrator = LerSegredo (Join-Path $DiretorioSegredos 'rasping_migrator.dpapi')
if ($runtime -ceq $migrator) { throw 'C2-E-D-C-B: segredos iguais' }
$script = 'infra/postgres/security/FASE_21_C2_E_D_C_B_TESTAR_LOGINS.sh'
if (-not (Test-Path -LiteralPath $script -PathType Leaf)) { throw 'C2-E-D-C-B: script de teste nao encontrado' }
$destino = '/tmp/FASE_21_C2_E_D_C_B_TESTAR_LOGINS.sh'
& docker cp $script "${NomeContainer}:${destino}"
ExigirSucesso 'C2-E-D-C-B: falha ao copiar script sem segredos'
# Unica transmissao dos valores: STDIN criptografado? Docker local STDIN, nunca argv.
# Linux shell recebe e usa a senha apenas como variavel em memoria durante o teste.
@($runtime, $migrator) | & docker compose -f infra/compose.yml exec -T postgres sh $destino 2>$null
ExigirSucesso 'C2-E-D-C-B: autenticacao das roles falhou'
Write-Host '=== C2-E-D-C-B LOGINS PASS - duas credenciais distintas e politica SCRAM confirmadas ==='
Remove-Variable runtime, migrator -ErrorAction SilentlyContinue
