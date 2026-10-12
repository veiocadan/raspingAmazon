# C2-E-D-C-B: altera APENAS LOGIN e senha de rasping_runtime e rasping_migrator.
# Armazena as duas credenciais via DPAPI no Windows, fora do repositorio.
param(
    [Parameter(Mandatory = $true)][string]$CaminhoManifestoHba,
    [Parameter(Mandatory = $true)][string]$CaminhoBackupBanco,
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [switch]$ConfirmarProvisionamento
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmarProvisionamento) {
    throw 'C2-E-D-C-B: use -ConfirmarProvisionamento somente apos autorizacao'
}
function ExigirSucesso([string]$Mensagem) { if ($LASTEXITCODE -ne 0) { throw $Mensagem } }
function NovaSenha {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $b = New-Object byte[] 32
        $rng.GetBytes($b)
        return [System.BitConverter]::ToString($b).Replace('-', '')
    } finally { $rng.Dispose() }
}
function GravarProtegido([string]$Caminho, [string]$Senha) {
    $seguro = ConvertTo-SecureString -String $Senha -AsPlainText -Force
    $protegido = ConvertFrom-SecureString -SecureString $seguro
    [System.IO.File]::WriteAllText($Caminho, $protegido + [Environment]::NewLine, [System.Text.Encoding]::UTF8)
}

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_C_B_PRECHECK_PROVISIONAMENTO.ps1' -CaminhoManifestoHba $CaminhoManifestoHba -CaminhoBackupBanco $CaminhoBackupBanco -DiretorioSegredos $DiretorioSegredos
ExigirSucesso 'C2-E-D-C-B: gate de provisionamento reprovado'

# Pastas e arquivos protegidos ANTES de gravar qualquer segredo.
$destino = [System.IO.Path]::GetFullPath($DiretorioSegredos)
[System.IO.Directory]::CreateDirectory($destino) | Out-Null
$identidade = [System.Security.Principal.WindowsIdentity]::GetCurrent()
$acl = New-Object System.Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true, $false)
$heranca = [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
foreach ($sid in @($identidade.User, ([System.Security.Principal.SecurityIdentifier]::new('S-1-5-18')))) {
    $regra = New-Object System.Security.AccessControl.FileSystemAccessRule -ArgumentList @(
        $sid, [System.Security.AccessControl.FileSystemRights]::FullControl,
        $heranca, [System.Security.AccessControl.PropagationFlags]::None,
        [System.Security.AccessControl.AccessControlType]::Allow
    )
    $acl.AddAccessRule($regra)
}
(New-Object System.IO.DirectoryInfo($destino)).SetAccessControl($acl)
$runtimeFile = Join-Path $destino 'rasping_runtime.dpapi'
$migratorFile = Join-Path $destino 'rasping_migrator.dpapi'
if (Test-Path -LiteralPath $runtimeFile -PathType Leaf) { throw 'C2-E-D-C-B: arquivo runtime ja existe' }
if (Test-Path -LiteralPath $migratorFile -PathType Leaf) { throw 'C2-E-D-C-B: arquivo migrator ja existe' }

$runtime = NovaSenha
$migrator = NovaSenha
if ($runtime -ceq $migrator) { throw 'C2-E-D-C-B: falha na unicidade das senhas' }
GravarProtegido $runtimeFile $runtime
GravarProtegido $migratorFile $migrator

# A SQL preenchida existe somente em memoria e via STDIN; NUNCA e gravada em disco.
$arquivoSql = 'infra/postgres/security/FASE_21_C2_E_D_C_B_ALTERAR_ROLES.sql'
$template = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $arquivoSql).Path)
if (([regex]::Matches($template, [regex]::Escape('__RUNTIME_SECRET__')).Count -ne 1) -or
    ([regex]::Matches($template, [regex]::Escape('__MIGRATOR_SECRET__')).Count -ne 1)) {
    throw 'C2-E-D-C-B: template SQL invalido; roles nao alteradas'
}
$sql = $template.Replace('__RUNTIME_SECRET__', $runtime).Replace('__MIGRATOR_SECRET__', $migrator)
# Oculta stdout e stderr do psql: uma falha SQL pode incluir o comando com senha.
$sql | & docker compose -f infra/compose.yml exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5 exec psql -X -w -h postgres -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -q -f -' 2>$null | Out-Null
ExigirSucesso 'C2-E-D-C-B: provisionamento SQL falhou. Credenciais DPAPI preservadas; nao repetir nem compartilhar logs.'
Write-Host '=== C2-E-D-C-B SQL COMMIT - LOGIN e SCRAM configurados; valores secretos nao exibidos ==='

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_C_B_VERIFICAR_LOGINS.ps1' -DiretorioSegredos $destino
ExigirSucesso 'C2-E-D-C-B: logins nao validados. Nao ativar runtime. Consultar procedimento de reversao.'
Write-Host '=== C2-E-D-C-B PROVISIONAMENTO PASS - roles ativas, segredos DPAPI fora do Git ==='
Remove-Variable sql, runtime, migrator -ErrorAction SilentlyContinue
