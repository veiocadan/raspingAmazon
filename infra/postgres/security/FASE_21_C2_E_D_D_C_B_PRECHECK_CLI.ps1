# FASE 21-C2-E-D-D-C-B - GATE anterior a execucao da CLI real.
# Somente leitura: NAO executa Java, nao usa SQL, nao modifica ambiente permanente.
param(
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$NomeContainer = 'rasping-amazon-postgres'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ExigirSucesso([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { throw $Mensagem }
}

if (-not (Test-Path -LiteralPath 'pom.xml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProvider.java' -PathType Leaf) -or
    -not (Test-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java' -PathType Leaf)) {
    throw 'C2-E-D-D-C-B: execute na raiz C:\dev\rasping-amazon; arquivo obrigatorio ausente'
}

$raizGit = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd('\') + '\'
$dirSeguro = [System.IO.Path]::GetFullPath($DiretorioSegredos)
if ($dirSeguro.StartsWith($raizGit, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'C2-E-D-D-C-B: credenciais nao podem estar dentro do repositorio'
}

foreach ($nome in @('rasping_runtime.dpapi','rasping_migrator.dpapi')) {
    $arquivo = Join-Path $dirSeguro $nome
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw 'C2-E-D-D-C-B: arquivo DPAPI ausente; nao reprovisionar'
    }
    # Leitura DPAPI para verificar disponibilidade ao mesmo usuario Windows.
    # Nao recupera em texto, nao exibe nem armazena as senhas.
    $codificado = [System.IO.File]::ReadAllText($arquivo).Trim()
    $seguro = ConvertTo-SecureString -String $codificado
    if ($seguro.Length -ne 64) {
        throw 'C2-E-D-D-C-B: credencial DPAPI com tamanho inesperado'
    }
    Remove-Variable codificado, seguro -ErrorAction SilentlyContinue
}
Write-Host '=== C2-E-D-D-C-B DPAPI PASS - credenciais legiveis pelo usuario Windows, fora do Git ==='

$portasJson = & docker inspect --format '{{json .HostConfig.PortBindings}}' $NomeContainer
ExigirSucesso 'C2-E-D-D-C-B: nao foi possivel inspecionar o PostgreSQL'
$portas = $portasJson | ConvertFrom-Json
$publicadas = @($portas.'5432/tcp')
if ($publicadas.Count -ne 1 -or
    $publicadas[0].HostIp -ne '127.0.0.1' -or
    $publicadas[0].HostPort -ne '5432') {
    throw 'C2-E-D-D-C-B: a porta PostgreSQL deve permanecer em 127.0.0.1:5432'
}
Write-Host '=== C2-E-D-D-C-B PORTA PASS - 127.0.0.1:5432 ==='

$provider = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProvider.java').Path)
$migration = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java').Path)
if (-not $provider.Contains('DB_MIGRATION_USER') -or
    -not $provider.Contains('DB_MIGRATION_PASSWORD') -or
    -not $migration.Contains('databaseMigrationUser()') -or
    -not $migration.Contains('databaseMigrationPassword()')) {
    throw 'C2-E-D-D-C-B: separacao de identidades Java nao localizada'
}
Write-Host '=== C2-E-D-D-C-B JAVA PASS - contrato runtime e migracao separado ==='

if ($null -eq (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw 'C2-E-D-D-C-B: Maven nao localizado no PATH'
}
Write-Host '=== C2-E-D-D-C-B PRECHECK PASS - nenhum login executado e nenhum ambiente modificado ==='
