# C2-E-D-C-A - gate seguro ANTES de gerar credenciais tecnicas.
# Execucao somente leitura em PostgreSQL. Copia apenas dois arquivos SEM segredos
# para /tmp no container. Nao faz ALTER ROLE, reload ou mudanca em HBA.
param(
    [Parameter(Mandatory = $true)]
    [string]$CaminhoManifestoHba,
    [Parameter(Mandatory = $true)]
    [string]$CaminhoBackupBanco,
    [string]$NomeContainer = 'rasping-amazon-postgres'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ExigirSucesso([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { throw $Mensagem }
}

if (-not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf)) {
    throw 'C2-E-D-C-A: execute a partir da raiz do repositorio'
}

# Nao executar 'docker compose config' sem --quiet: pode imprimir segredos.
& docker compose -f infra/compose.yml config --quiet
ExigirSucesso 'C2-E-D-C-A: docker compose invalido'

# Exige os backups ja testados. O hash do HBA pode ser diferente do ativo:
# o backup e propositadamente da politica ANTERIOR a ativacao SCRAM.
if (-not (Test-Path -LiteralPath $CaminhoManifestoHba -PathType Leaf)) {
    throw 'C2-E-D-C-A: manifesto original de backup HBA ausente'
}
$manifesto = Get-Content -LiteralPath $CaminhoManifestoHba -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifesto.etapa -cne 'FASE_21_C2_E_C_C_A' -or
    $manifesto.container -cne $NomeContainer -or
    $manifesto.sha256 -notmatch '^[A-Fa-f0-9]{64}$' -or
    -not (Test-Path -LiteralPath $manifesto.arquivoBackup -PathType Leaf)) {
    throw 'C2-E-D-C-A: manifesto HBA invalido ou arquivo ausente'
}
$arquivoHba = Get-Item -LiteralPath $manifesto.arquivoBackup
if ([long]$arquivoHba.Length -ne [long]$manifesto.comprimentoBytes) {
    throw 'C2-E-D-C-A: tamanho do backup HBA divergiu do manifesto'
}
$hashHba = (Get-FileHash -LiteralPath $arquivoHba.FullName -Algorithm SHA256).Hash
if ($hashHba -cne $manifesto.sha256.ToUpperInvariant()) {
    throw 'C2-E-D-C-A: hash do backup HBA divergente'
}
if (-not (Test-Path -LiteralPath $CaminhoBackupBanco -PathType Leaf)) {
    throw 'C2-E-D-C-A: backup PostgreSQL previamente restaurado nao encontrado'
}
if ((Get-Item -LiteralPath $CaminhoBackupBanco).Length -lt 1000) {
    throw 'C2-E-D-C-A: backup PostgreSQL com tamanho incompativel'
}
Write-Host '=== C2-E-D-C-A BACKUPS PASS - backup PostgreSQL e manifesto HBA encontrados e integros ==='

# A etapa Java anterior deve existir antes da ativacao real das roles.
$contratos = @(
    @{ Path='src/main/java/com/raspingamazon/infrastructure/config/EnvironmentConfigProvider.java'; Tokens=@('DB_MIGRATION_USER','DB_MIGRATION_PASSWORD') },
    @{ Path='src/main/java/com/raspingamazon/infrastructure/config/ApplicationConfig.java'; Tokens=@('databaseMigrationUser','databaseMigrationPassword') },
    @{ Path='src/main/java/com/raspingamazon/infrastructure/migration/DatabaseMigration.java'; Tokens=@('databaseMigrationUser\s*\(','databaseMigrationPassword\s*\(') },
    @{ Path='.env.example'; Tokens=@('DB_MIGRATION_USER','DB_MIGRATION_PASSWORD') }
)
foreach ($item in $contratos) {
    if (-not (Test-Path -LiteralPath $item.Path -PathType Leaf)) {
        throw "C2-E-D-C-A: contrato ausente: $($item.Path)"
    }
    $fonte = Get-Content -LiteralPath $item.Path -Raw -Encoding UTF8
    foreach ($token in $item.Tokens) {
        if ($fonte -notmatch $token) {
            throw "C2-E-D-C-A: revisar contrato: $($item.Path)"
        }
    }
}
Write-Host '=== C2-E-D-C-A JAVA PASS - contratos runtime e Flyway separados ==='

$bindingsText = & docker inspect --format '{{json .HostConfig.PortBindings}}' $NomeContainer
ExigirSucesso 'C2-E-D-C-A: docker inspect falhou'
$bindings = ($bindingsText | Out-String).Trim() | ConvertFrom-Json
$porta = @($bindings.'5432/tcp')
if ($porta.Count -ne 1 -or $porta[0].HostIp -cne '127.0.0.1' -or $porta[0].HostPort -cne '5432') {
    throw 'C2-E-D-C-A: porta 5432 nao esta restrita a 127.0.0.1'
}
Write-Host '=== C2-E-D-C-A PORTA PASS - exposicao de rede restrita ==='

$pasta = 'infra/postgres/security'
$helper = Join-Path $pasta 'FASE_21_C2_E_D_C_A_EXECUTAR_SQL.sh'
$sql = Join-Path $pasta 'FASE_21_C2_E_D_C_A_PRECHECK_PROVISIONAMENTO.sql'
foreach ($file in @($helper,$sql)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "C2-E-D-C-A: arquivo necessario ausente: $file"
    }
}
$helperContainer = '/tmp/FASE_21_C2_E_D_C_A_EXECUTAR_SQL.sh'
$sqlContainer = '/tmp/FASE_21_C2_E_D_C_A_PRECHECK_PROVISIONAMENTO.sql'
& docker cp $helper "${NomeContainer}:${helperContainer}"
ExigirSucesso 'C2-E-D-C-A: falha ao copiar executor Linux'
& docker cp $sql "${NomeContainer}:${sqlContainer}"
ExigirSucesso 'C2-E-D-C-A: falha ao copiar auditoria SQL'

& docker compose -f infra/compose.yml exec -T postgres sh $helperContainer $sqlContainer
ExigirSucesso 'C2-E-D-C-A: autenticacao ou auditoria do banco reprovada'

Write-Host '=== C2-E-D-C-A GATE PASS - pronta para preparar provisionamento; nenhuma role ou senha alterada ==='
