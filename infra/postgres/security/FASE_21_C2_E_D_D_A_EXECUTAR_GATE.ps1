# C2-E-D-D-A — Somente leitura: validacao real de permissoes do runtime e migrator.
# Segredos carregados do DPAPI do usuario atual, enviados apenas via STDIN ao contêiner.
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
        throw 'C2-E-D-D-A: arquivo DPAPI nao encontrado; nao recriar credenciais'
    }
    $cifra = [System.IO.File]::ReadAllText($Caminho).Trim()
    $seguro = ConvertTo-SecureString $cifra
    $ptr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
    try {
        $valor = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
        if ($valor -cnotmatch '^[0-9A-F]{64}$') {
            throw 'C2-E-D-D-A: credencial protegida invalida'
        }
        return $valor
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

if (-not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf)) {
    throw 'C2-E-D-D-A: execute na raiz do repositorio'
}
if (-not (Test-Path -LiteralPath 'infra/postgres/security/FASE_21_C2_E_D_C_B_VERIFICAR_LOGINS.ps1' -PathType Leaf)) {
    throw 'C2-E-D-D-A: etapa de provisionamento anterior ausente'
}
if (-not (Test-Path -LiteralPath $DiretorioSegredos -PathType Container)) {
    throw 'C2-E-D-D-A: diretorio DPAPI ausente'
}
$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd('\') + '\'
$segredos = [System.IO.Path]::GetFullPath($DiretorioSegredos)
if ($segredos.StartsWith($raiz, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'C2-E-D-D-A: segredos dentro do repositorio nao sao permitidos'
}

# Assegura que a exposicao TCP do host continua local, antes de consultar credenciais.
$portasJson = & docker inspect --format '{{json .HostConfig.PortBindings}}' $NomeContainer
ExigirSucesso 'C2-E-D-D-A: nao foi possivel inspecionar Docker'
$portas = $portasJson | ConvertFrom-Json
$ligacoes = @($portas.'5432/tcp')
if ($ligacoes.Count -ne 1 -or $ligacoes[0].HostIp -ne '127.0.0.1' -or $ligacoes[0].HostPort -ne '5432') {
    throw 'C2-E-D-D-A: porta PostgreSQL nao esta restrita ao loopback'
}
Write-Host '=== C2-E-D-D-A PORTA PASS - 127.0.0.1:5432 ==='

# Confere contrato Java sem ler variaveis de ambiente ou escrever credenciais no .env.
$javaConfig = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProvider.java').Path)
$javaMigration = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath 'src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java').Path)
if (-not $javaConfig.Contains('DB_MIGRATION_USER') -or
    -not $javaConfig.Contains('DB_MIGRATION_PASSWORD') -or
    -not $javaMigration.Contains('databaseMigrationUser()') -or
    -not $javaMigration.Contains('databaseMigrationPassword()')) {
    throw 'C2-E-D-D-A: contrato de credenciais Java divergente'
}
Write-Host '=== C2-E-D-D-A JAVA PASS - separacao de credenciais preservada ==='

$nomes = @(
    'FASE_21_C2_E_D_D_A_RUNTIME_READONLY.sql',
    'FASE_21_C2_E_D_D_A_MIGRATOR_READONLY.sql',
    'FASE_21_C2_E_D_D_A_TESTAR_ACESSO_REAL.sh'
)
foreach ($nome in $nomes) {
    $origem = Join-Path 'infra/postgres/security' $nome
    if (-not (Test-Path -LiteralPath $origem -PathType Leaf)) {
        throw "C2-E-D-D-A: arquivo ausente: $nome"
    }
    & docker cp $origem "${NomeContainer}:/tmp/$nome"
    ExigirSucesso 'C2-E-D-D-A: falha ao instalar script sem credenciais'
}

$runtime = $null
$migrator = $null
try {
    $runtime = LerSegredo (Join-Path $segredos 'rasping_runtime.dpapi')
    $migrator = LerSegredo (Join-Path $segredos 'rasping_migrator.dpapi')
    if ($runtime -ceq $migrator) { throw 'C2-E-D-D-A: duas credenciais identicas' }

    # STDERR suprimido apenas para a transmissao de credenciais. Falhas recebem mensagem generica.
    @($runtime, $migrator) | & docker compose -f infra/compose.yml exec -T postgres sh '/tmp/FASE_21_C2_E_D_D_A_TESTAR_ACESSO_REAL.sh' 2>$null
    ExigirSucesso 'C2-E-D-D-A: gate de autenticacao/permissoes reprovado; nao migrar aplicacao'
} finally {
    Remove-Variable runtime, migrator -ErrorAction SilentlyContinue
}
Write-Host '=== C2-E-D-D-A GATE PASS - identidades reais prontas para transicao; nenhuma credencial Java alterada ==='
