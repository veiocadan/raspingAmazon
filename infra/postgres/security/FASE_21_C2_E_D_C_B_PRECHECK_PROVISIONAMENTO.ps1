# C2-E-D-C-B: somente leitura. Reaproveita gate C2-E-D-C-A antes de criar credenciais.
param(
    [Parameter(Mandatory = $true)][string]$CaminhoManifestoHba,
    [Parameter(Mandatory = $true)][string]$CaminhoBackupBanco,
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function ExigirSucesso([string]$Mensagem) { if ($LASTEXITCODE -ne 0) { throw $Mensagem } }

if (-not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'infra/postgres/security/FASE_21_C2_E_D_C_A_EXECUTAR_PRECHECK.ps1' -PathType Leaf)) {
    throw 'C2-E-D-C-B: execute na raiz do repositorio com C2-E-D-C-A versionada'
}

$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd('\') + '\'
$segredos = [System.IO.Path]::GetFullPath($DiretorioSegredos)
if ($segredos.StartsWith($raiz, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'C2-E-D-C-B: o diretorio de credenciais deve ficar FORA do Git'
}
if (Test-Path -LiteralPath $segredos) {
    throw 'C2-E-D-C-B: diretorio de credenciais ja existe. Nao sobrescrever ou gerar novas senhas.'
}
if (-not [System.Environment]::OSVersion.Platform.ToString().StartsWith('Win')) {
    throw 'C2-E-D-C-B: provisionamento DPAPI requer Windows'
}
if (-not (Get-Command 'powershell.exe' -ErrorAction SilentlyContinue)) {
    throw 'C2-E-D-C-B: Windows PowerShell indisponivel'
}
if (-not (Get-Command 'docker' -ErrorAction SilentlyContinue)) {
    throw 'C2-E-D-C-B: Docker indisponivel'
}

# Sem criar dados: garante disponibilidade de DPAPI, CSPRNG e identidade Windows.
$identidade = [System.Security.Principal.WindowsIdentity]::GetCurrent()
if ($null -eq $identidade.User) { throw 'C2-E-D-C-B: identidade Windows invalida' }
$gerador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $teste = New-Object byte[] 32
    $gerador.GetBytes($teste)
} finally { $gerador.Dispose() }

# Valida novamente backup do banco, hash do HBA, SCRAM real, porta, ownership,
# ACLs, NOLOGIN e nenhuma conexao da aplicacao. Nao imprime nenhuma senha.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_C_A_EXECUTAR_PRECHECK.ps1' -CaminhoManifestoHba $CaminhoManifestoHba -CaminhoBackupBanco $CaminhoBackupBanco
ExigirSucesso 'C2-E-D-C-B: gate anterior reprovado'

foreach ($nome in @(
    'FASE_21_C2_E_D_C_B_PROVISIONAR_ROLES.ps1',
    'FASE_21_C2_E_D_C_B_VERIFICAR_LOGINS.ps1',
    'FASE_21_C2_E_D_C_B_DESATIVAR_LOGINS.ps1',
    'FASE_21_C2_E_D_C_B_ALTERAR_ROLES.sql',
    'FASE_21_C2_E_D_C_B_TESTAR_LOGINS.sh'
)) {
    if (-not (Test-Path -LiteralPath (Join-Path 'infra/postgres/security' $nome) -PathType Leaf)) {
        throw "C2-E-D-C-B: arquivo incompleto: $nome"
    }
}
Write-Host '=== C2-E-D-C-B PRECHECK PASS - nenhuma role, senha ou arquivo secreto alterado ==='
