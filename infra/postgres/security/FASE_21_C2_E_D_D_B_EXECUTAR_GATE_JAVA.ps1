# C2-E-D-D-B: prova real de JDBC e Flyway, SEM alterar banco, HBA ou .env.
# Executar com o MESMO usuario Windows do provisionamento DPAPI C2-E-D-C-B.
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
        throw 'C2-E-D-D-B: credencial DPAPI ausente; nao reprovisionar'
    }
    $texto = [System.IO.File]::ReadAllText($Caminho).Trim()
    $seguro = ConvertTo-SecureString -String $texto
    $ponteiro = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try {
        $senha = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ponteiro)
        if ($senha -cnotmatch '^[0-9A-F]{64}$') {
            throw 'C2-E-D-D-B: formato DPAPI invalido'
        }
        return $senha
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ponteiro)
    }
}

if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'src/test/java/com/raspingamazon/infrastructure/security/CredentialSeparationJdbcFlywayProbe.java' -PathType Leaf)) {
    throw 'C2-E-D-D-B: execute na raiz do projeto e instale o pacote completo'
}
$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd('\') + '\'
$segredos = [System.IO.Path]::GetFullPath($DiretorioSegredos)
if ($segredos.StartsWith($raiz, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'C2-E-D-D-B: diretorio de segredos nao pode estar no Git'
}

# Nao prosseguir em caso de porta Docker aberta a outras interfaces.
$portasJson = & docker inspect --format '{{json .HostConfig.PortBindings}}' $NomeContainer
ExigirSucesso 'C2-E-D-D-B: falha ao inspecionar a porta Docker'
$portas = $portasJson | ConvertFrom-Json
$ligacoes = @($portas.'5432/tcp')
if ($ligacoes.Count -ne 1 -or
    $ligacoes[0].HostIp -ne '127.0.0.1' -or
    $ligacoes[0].HostPort -ne '5432') {
    throw 'C2-E-D-D-B: porta 5432 nao esta limitada a 127.0.0.1'
}
Write-Host '=== C2-E-D-D-B PORTA PASS - PostgreSQL restrito ao loopback ==='

# Verificar o contrato Java sem ler qualquer variavel secreta.
$provedor = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProvider.java').Path)
$migracao = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java').Path)
if (-not $provedor.Contains('DB_MIGRATION_USER') -or
    -not $provedor.Contains('DB_MIGRATION_PASSWORD') -or
    -not $migracao.Contains('databaseMigrationUser()') -or
    -not $migracao.Contains('databaseMigrationPassword()')) {
    throw 'C2-E-D-D-B: contrato Java de credenciais divergente'
}
Write-Host '=== C2-E-D-D-B CONTRATO JAVA PASS ==='

# As variaveis do processo filho sao temporarias. Nunca se escreve .env ou arquivo de senha.
$nomes = @('DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD', 'DB_MIGRATION_USER', 'DB_MIGRATION_PASSWORD')
$originais = @{}
foreach ($nome in $nomes) {
    $originais[$nome] = [System.Environment]::GetEnvironmentVariable($nome, 'Process')
}
$runtime = $null
$migrator = $null
try {
    $runtime = LerSegredo (Join-Path $segredos 'rasping_runtime.dpapi')
    $migrator = LerSegredo (Join-Path $segredos 'rasping_migrator.dpapi')
    if ($runtime -ceq $migrator) { throw 'C2-E-D-D-B: credenciais nao sao distintas' }

    [System.Environment]::SetEnvironmentVariable('DB_HOST', '127.0.0.1', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_PORT', '5432', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_NAME', 'rasping_amazon', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_USER', 'rasping_runtime', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_PASSWORD', $runtime, 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_MIGRATION_USER', 'rasping_migrator', 'Process')
    [System.Environment]::SetEnvironmentVariable('DB_MIGRATION_PASSWORD', $migrator, 'Process')

    Write-Host '=== C2-E-D-D-B JAVA PROBE - autenticacao JDBC e Flyway validate, sem DDL ==='
    & mvn '-Dtest=CredentialSeparationJdbcFlywayProbe' test
    ExigirSucesso 'C2-E-D-D-B: teste Java reprovado; nao migrar inicializacao operacional'
    Write-Host '=== C2-E-D-D-B GATE PASS - JDBC e Flyway validados, ambiente original preservado ==='
} finally {
    foreach ($nome in $nomes) {
        [System.Environment]::SetEnvironmentVariable($nome, $originais[$nome], 'Process')
    }
    Remove-Variable runtime, migrator -ErrorAction SilentlyContinue
}
