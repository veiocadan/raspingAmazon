# C2-E-D-D-C-A: teste operacional real da CLI Java com credenciais DPAPI.
# Executa apenas consultas CLI de runs/jobs; nao inicia daemon, nao aplica Flyway
# e nao altera .env, credenciais, HBA ou roles.
param(
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$NomeContainer = 'rasping-amazon-postgres'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ExigirSucesso([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { throw $Mensagem }
}

function LerSegredo([string]$Caminho) {
    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) {
        throw 'C2-E-D-D-C-A: credencial DPAPI ausente; nao executar provisionamento novamente'
    }
    $texto = [System.IO.File]::ReadAllText($Caminho).Trim()
    $seguro = ConvertTo-SecureString -String $texto
    $ponteiro = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try {
        $senha = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ponteiro)
        if ($senha -cnotmatch '^[0-9A-F]{64}$') {
            throw 'C2-E-D-D-C-A: credencial DPAPI em formato inesperado'
        }
        return $senha
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ponteiro)
    }
}

if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'src/test/java/com/raspingamazon/infrastructure/security/OperationalCredentialCliProbe.java' -PathType Leaf)) {
    throw 'C2-E-D-D-C-A: execute na raiz do projeto e instale todos os arquivos'
}

$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd('\') + '\'
$segredos = [System.IO.Path]::GetFullPath($DiretorioSegredos)
if ($segredos.StartsWith($raiz, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'C2-E-D-D-C-A: segredos nao podem estar dentro do Git'
}

$portasJson = & docker inspect --format '{{json .HostConfig.PortBindings}}' $NomeContainer
ExigirSucesso 'C2-E-D-D-C-A: falha na inspecao do contêiner'
$portas = $portasJson | ConvertFrom-Json
$ligacoes = @($portas.'5432/tcp')
if ($ligacoes.Count -ne 1 -or
    $ligacoes[0].HostIp -ne '127.0.0.1' -or
    $ligacoes[0].HostPort -ne '5432') {
    throw 'C2-E-D-D-C-A: porta PostgreSQL nao esta restrita ao loopback'
}
Write-Host '=== C2-E-D-D-C-A PORTA PASS - 127.0.0.1:5432 ==='

# Confere a presenca do contrato de separacao no codigo antes de abrir conexoes.
$provedor = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProvider.java').Path)
$migracao = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java').Path)
if (-not $provedor.Contains('DB_MIGRATION_USER') -or
    -not $provedor.Contains('DB_MIGRATION_PASSWORD') -or
    -not $migracao.Contains('databaseMigrationUser()') -or
    -not $migracao.Contains('databaseMigrationPassword()')) {
    throw 'C2-E-D-D-C-A: separacao Java nao localizada'
}
Write-Host '=== C2-E-D-D-C-A CONTRATO JAVA PASS ==='

# O estado anterior das variaveis do PowerShell e restaurado mesmo em falha.
$nomes = @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD',
           'DB_MIGRATION_USER', 'DB_MIGRATION_PASSWORD')
$originais = @{}
foreach ($nome in $nomes) {
    $originais[$nome] = [System.Environment]::GetEnvironmentVariable($nome, 'Process')
}

$runtime = $null
$migrator = $null
try {
    $runtime = LerSegredo (Join-Path $segredos 'rasping_runtime.dpapi')
    $migrator = LerSegredo (Join-Path $segredos 'rasping_migrator.dpapi')
    if ($runtime -ceq $migrator) {
        throw 'C2-E-D-D-C-A: senhas tecnicas nao sao independentes'
    }

    [System.Environment]::SetEnvironmentVariable('DB_HOST', '127.0.0.1', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_PORT', '5432', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_NAME', 'rasping_amazon', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_USER', 'rasping_runtime', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_PASSWORD', $runtime, 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_MIGRATION_USER', 'rasping_migrator', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_MIGRATION_PASSWORD', $migrator, 'Process')

    Write-Host '=== C2-E-D-D-C-A CLI PROBE - listagens reais sem daemon nem migrations ==='
    & mvn '-Dtest=OperationalCredentialCliProbe' test
    ExigirSucesso 'C2-E-D-D-C-A: teste da CLI reprovado; nao ativar processo operacional'
    Write-Host '=== C2-E-D-D-C-A GATE PASS - CLI Java validada com runtime, variaveis originais restauradas ==='
} finally {
    foreach ($nome in $nomes) {
        [System.Environment]::SetEnvironmentVariable($nome, $originais[$nome], 'Process')
    }
    Remove-Variable runtime, migrator -ErrorAction SilentlyContinue
}
