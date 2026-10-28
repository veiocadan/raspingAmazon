# FASE 21-C2-E-D-A: orquestracao apenas de leitura.
# Executar na raiz C:\dev\rasping-amazon usando Windows PowerShell.
# A senha administrativa existente e utilizada SOMENTE dentro do container.
# Nao exibe, copia ou salva nenhum segredo na maquina host.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf)) {
    throw 'C2-E-D-A: execute na raiz do repositorio rasping-amazon'
}

$sourceChecks = @(
    @{
        Path = 'src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java'
        Tokens = @('databaseUser\s*\(', 'databasePassword\s*\(')
    },
    @{
        Path = 'src/main/java/com/raspingamazon/infrastructure/persistence/DatabaseConnection.java'
        Tokens = @('databaseUser\s*\(', 'databasePassword\s*\(')
    },
    @{
        Path = 'src/main/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProvider.java'
        Tokens = @('DB_USER', 'DB_PASSWORD')
    }
)

foreach ($check in $sourceChecks) {
    if (-not (Test-Path -LiteralPath $check.Path -PathType Leaf)) {
        throw "C2-E-D-A: arquivo Java esperado ausente: $($check.Path)"
    }
    $content = Get-Content -LiteralPath $check.Path -Raw -Encoding UTF8
    foreach ($token in $check.Tokens) {
        if ($content -notmatch $token) {
            throw "C2-E-D-A: contrato Java mudou; revisar manualmente: $($check.Path)"
        }
    }
}
Write-Host '=== C2-E-D-A FONTE PASS - runtime e Flyway ainda dependem do contrato ApplicationConfig ==='
Write-Host '=== ATENCAO: a separacao efetiva de credenciais Java ainda NAO foi implementada ==='

& docker compose -f infra/compose.yml config --quiet
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-A: Docker Compose invalido'
}

$bindings = & docker inspect --format '{{json .HostConfig.PortBindings}}' rasping-amazon-postgres
if ($LASTEXITCODE -ne 0) {
    throw 'C2-E-D-A: docker inspect falhou'
}
$portMap = $bindings | ConvertFrom-Json
$entry = $portMap.PSObject.Properties['5432/tcp']
if ($null -eq $entry -or $entry.Value.Count -ne 1 -or
    $entry.Value[0].HostIp -cne '127.0.0.1' -or
    $entry.Value[0].HostPort -cne '5432') {
    throw 'C2-E-D-A: porta 5432 deixou de estar restrita ao host loopback'
}
Write-Host '=== C2-E-D-A PORTA PASS - somente 127.0.0.1:5432 ==='

$helper = 'infra/postgres/security/FASE_21_C2_E_D_A_EXECUTAR_SQL.sh'
if (-not (Test-Path -LiteralPath $helper -PathType Leaf)) {
    throw 'C2-E-D-A: executor Linux ausente'
}
$helperDestino = '/tmp/FASE_21_C2_E_D_A_EXECUTAR_SQL.sh'
& docker cp $helper "rasping-amazon-postgres:$helperDestino"
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-A: falha ao copiar executor Linux' }

$checks = @(
    'FASE_21_C2_E_D_A_PRECHECK_IDENTIDADES.sql',
    'FASE_21_C2_E_D_A_VERIFICAR_RUNTIME_SIMULADO.sql'
)

foreach ($file in $checks) {
    $localFile = Join-Path 'infra/postgres/security' $file
    if (-not (Test-Path -LiteralPath $localFile -PathType Leaf)) {
        throw "C2-E-D-A: arquivo SQL ausente: $localFile"
    }
    $target = "/tmp/$file"
    & docker cp $localFile "rasping-amazon-postgres:$target"
    if ($LASTEXITCODE -ne 0) {
        throw "C2-E-D-A: falha ao copiar $file"
    }

    # Executor Linux usa POSTGRES_PASSWORD apenas no processo dentro do container.
    & docker compose -f infra/compose.yml exec -T postgres sh $helperDestino $target
    if ($LASTEXITCODE -ne 0) {
        throw "C2-E-D-A: auditoria SQL reprovada: $file"
    }
}

Write-Host '=== C2-E-D-A GATE PASS - nenhuma credencial criada, nenhum LOGIN habilitado ==='
