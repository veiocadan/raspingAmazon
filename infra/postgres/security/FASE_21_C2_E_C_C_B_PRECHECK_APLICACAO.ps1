# FASE 21-C2-E-C-C-B - PRECHECK SEM ALTERAR A POLITICA HBA.
# Exige o manifesto REAL do backup C2-E-C-C-A, externo ao Git.
# Executar na raiz C:\dev\rasping-amazon pelo PowerShell do Windows.
param(
    [Parameter(Mandatory = $true)]
    [string]$CaminhoManifesto,
    [string]$CaminhoCompose = 'infra/compose.yml',
    [string]$NomeContainer = 'rasping-amazon-postgres'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ExigirCodigoZero([string]$Mensagem) {
    if ($LASTEXITCODE -ne 0) { throw $Mensagem }
}

function ExtrairSha256([object]$Saida) {
    $valor = ($Saida | Out-String).Trim()
    if ($valor -match '^([0-9A-Fa-f]{64})\s+') {
        return $Matches[1].ToUpperInvariant()
    }
    throw 'Hash SHA-256 retornado pelo container em formato inesperado.'
}

if (-not (Test-Path -LiteralPath $CaminhoManifesto -PathType Leaf)) {
    throw 'Manifesto de backup HBA nao encontrado.'
}
if (-not (Test-Path -LiteralPath $CaminhoCompose -PathType Leaf)) {
    throw 'Compose ausente. Execute na raiz do repositorio.'
}
$m = Get-Content -LiteralPath $CaminhoManifesto -Raw -Encoding UTF8 | ConvertFrom-Json
if ($m.etapa -cne 'FASE_21_C2_E_C_C_A' -or $m.container -cne $NomeContainer) {
    throw 'Manifesto nao corresponde ao backup validado da C2-E-C-C-A.'
}
if ($m.caminhoHbaOriginal -notmatch '^/(?:[A-Za-z0-9_.-]+/)+pg_hba\.conf$' -or $m.caminhoHbaOriginal.Contains('/../')) {
    throw 'Manifesto com caminho HBA fora do contrato.'
}
if ($m.sha256 -notmatch '^[0-9a-fA-F]{64}$') { throw 'Hash invalido no manifesto.' }
if ($m.uidOriginal -notmatch '^\d+$' -or $m.gidOriginal -notmatch '^\d+$' -or $m.modoOriginal -notmatch '^[0-7]{3,4}$') {
    throw 'Metadados do HBA invalidos no manifesto.'
}
if (-not (Test-Path -LiteralPath $m.arquivoBackup -PathType Leaf)) {
    throw 'Arquivo de backup do HBA nao encontrado.'
}
if ([long](Get-Item -LiteralPath $m.arquivoBackup).Length -ne [long]$m.comprimentoBytes) {
    throw 'Tamanho do backup diverge do manifesto.'
}
$hashBackup = (Get-FileHash -LiteralPath $m.arquivoBackup -Algorithm SHA256).Hash.ToUpperInvariant()
if ($hashBackup -cne $m.sha256.ToUpperInvariant()) { throw 'Backup nao corresponde ao manifesto.' }

& docker compose -f $CaminhoCompose config --quiet
ExigirCodigoZero 'Compose invalido.'

# Nao expor 'docker compose config' integralmente (pode conter senhas).
$bindingsJson = & docker inspect --format '{{json .HostConfig.PortBindings}}' $NomeContainer
ExigirCodigoZero 'Falha ao obter binding da porta PostgreSQL.'
$bindings = ($bindingsJson | Out-String).Trim() | ConvertFrom-Json
$porta = @($bindings.'5432/tcp')
if ($porta.Count -ne 1 -or $porta[0].HostIp -cne '127.0.0.1' -or $porta[0].HostPort -cne '5432') {
    throw 'Porta 5432 nao esta exclusivamente publicada em 127.0.0.1.'
}

$caminhoAtivo = & docker compose -f $CaminhoCompose exec -T postgres psql -X -A -t -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -c 'SHOW hba_file;'
ExigirCodigoZero 'Falha ao localizar HBA ativo.'
if (($caminhoAtivo | Out-String).Trim() -cne $m.caminhoHbaOriginal) {
    throw 'O HBA ativo diverge do backup aprovado.'
}

$metadados = & docker compose -f $CaminhoCompose exec -T postgres sh -c 'test -f "$1" && test ! -L "$1" && stat -c "%u:%g:%a" "$1"' verificar $m.caminhoHbaOriginal
ExigirCodigoZero 'HBA ativo ausente, invalido ou symlink.'
$metadadosEsperados = "$($m.uidOriginal):$($m.gidOriginal):$($m.modoOriginal)"
if (($metadados | Out-String).Trim() -cne $metadadosEsperados) {
    throw 'Metadados do HBA ativo divergentes do backup.'
}

$hashHbaAtual = ExtrairSha256 (& docker compose -f $CaminhoCompose exec -T postgres sha256sum $m.caminhoHbaOriginal)
ExigirCodigoZero 'Nao foi possivel calcular SHA-256 do HBA ativo.'
if ($hashHbaAtual -cne $hashBackup) {
    throw 'HBA ativo diferente do backup. Nao sobrescrever mudancas desconhecidas.'
}

# Reexecuta a auditoria da configuracao ORIGINAL.
$precheckSql = 'infra/postgres/security/FASE_21_C2_E_C_C_A_PRECHECK_HBA_ORIGINAL.sql'
if (-not (Test-Path -LiteralPath $precheckSql -PathType Leaf)) { throw 'SQL de precheck original ausente.' }
Get-Content -LiteralPath $precheckSql -Raw -Encoding UTF8 |
    docker compose -f $CaminhoCompose exec -T postgres psql -X -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -f -
ExigirCodigoZero 'Falha no precheck da politica HBA original.'

# Valida EXATAMENTE as sete linhas SCRAM propostas, ainda sem aplica-las.
$validar = 'infra/postgres/security/FASE_21_C2_E_C_C_A_VALIDAR_POLITICA_PROPOSTA.ps1'
if (-not (Test-Path -LiteralPath $validar -PathType Leaf)) { throw 'Validador da politica proposta ausente.' }
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $validar
ExigirCodigoZero 'Proposta SCRAM nao corresponde ao contrato.'

# Confirma, de novo, que a senha administrativa autentica no caminho TCP interno.
$testeTcp = 'infra/postgres/security/FASE_21_C2_E_C_B_TESTAR_SCRAM_TCP.sh'
if (-not (Test-Path -LiteralPath $testeTcp -PathType Leaf)) { throw 'Teste TCP SCRAM ausente.' }
$destinoTcp = '/tmp/FASE_21_C2_E_C_B_TESTAR_SCRAM_TCP.sh'
& docker cp $testeTcp "${NomeContainer}:${destinoTcp}"
ExigirCodigoZero 'Falha ao copiar o teste TCP ao container.'
& docker compose -f $CaminhoCompose exec -T postgres sh $destinoTcp
ExigirCodigoZero 'Credencial administrativa SCRAM nao passou no teste TCP.'

# Evita executar a troca durante atividade de processamento.
$conexoes = & docker compose -f $CaminhoCompose exec -T postgres psql -X -A -t -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND backend_type = 'client backend';"
ExigirCodigoZero 'Falha ao verificar conexoes de aplicacao.'
if (($conexoes | Out-String).Trim() -cne '0') {
    throw 'Existem outras conexoes ao banco. Parar runtime e repetir o precheck.'
}

Write-Host '=== C2-E-C-C-B PRECHECK PASS - backup, politica, SCRAM, porta e inatividade confirmados ==='
Write-Host '=== HBA ATIVO NAO FOI MODIFICADO E NAO HOUVE RELOAD ==='
