# FASE 21-C2-E-C-C-A — Backup verificavel do pg_hba.conf efetivamente ativo.
# Copia somente do contêiner PARA o Windows. Nao altera o cluster.
# Rodar na raiz do repositorio, sem imprimir credenciais ou docker compose config.
param(
    [string]$CaminhoCompose = 'infra/compose.yml',
    [string]$DiretorioBackup = 'C:\dev\rasping-amazon-backups\hba',
    [string]$NomeContainer = 'rasping-amazon-postgres'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $CaminhoCompose -PathType Leaf)) {
    throw 'Arquivo Compose nao encontrado; execute na raiz do repositorio.'
}

& docker compose -f $CaminhoCompose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose invalido; nenhum backup executado.' }

# A identificacao do arquivo acontece via PostgreSQL, nunca por caminho presumido.
$saidaCaminho = & docker compose -f $CaminhoCompose exec -T postgres psql -X -A -t -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -c 'SHOW hba_file;'
if ($LASTEXITCODE -ne 0) { throw 'Impossivel identificar o HBA ativo por SQL.' }
$caminhoHba = ($saidaCaminho | Out-String).Trim()

# O cluster auditado usa arquivo fisico pg_hba.conf em caminho Linux absoluto.
# Caminhos inesperados exigem avaliacao humana, nao heuristica automatica.
if ($caminhoHba -notmatch '^/(?:[A-Za-z0-9_.-]+/)+pg_hba\.conf$' -or $caminhoHba.Contains('/../')) {
    throw 'Caminho HBA inesperado: interrompido sem expor ou copiar o arquivo.'
}

$saidaMetadados = & docker compose -f $CaminhoCompose exec -T postgres sh -c 'test -f "$1" && test ! -L "$1" && stat -c "%u:%g:%a" "$1"' verificar $caminhoHba
if ($LASTEXITCODE -ne 0) { throw 'Arquivo HBA nao e arquivo regular legivel.' }
$metadados = ($saidaMetadados | Out-String).Trim()
if ($metadados -match '^(\d+):(\d+):([0-7]{3,4})$') {
    $uidOriginal = $Matches[1]
    $gidOriginal = $Matches[2]
    $modoOriginal = $Matches[3]
} else {
    throw 'Metadados do arquivo HBA inesperados.'
}

$saidaHashAntes = & docker compose -f $CaminhoCompose exec -T postgres sha256sum $caminhoHba
if ($LASTEXITCODE -ne 0) { throw 'Falha ao calcular hash do HBA na origem.' }
$textoHashAntes = ($saidaHashAntes | Out-String).Trim()
if ($textoHashAntes -match '^([0-9A-Fa-f]{64})\s+') {
    $hashAntes = $Matches[1].ToUpperInvariant()
} else { throw 'Formato do SHA-256 de origem invalido.' }

if (-not (Test-Path -LiteralPath $DiretorioBackup)) {
    New-Item -ItemType Directory -Path $DiretorioBackup -Force | Out-Null
}
if (-not (Test-Path -LiteralPath $DiretorioBackup -PathType Container)) {
    throw 'Pasta de backups indisponivel.'
}

$carimbo = Get-Date -Format 'yyyyMMdd_HHmmss_fff'
$arquivoBackup = Join-Path $DiretorioBackup "pg_hba_c2ecc_original_$carimbo.conf"
$arquivoManifesto = Join-Path $DiretorioBackup "pg_hba_c2ecc_original_$carimbo.json"
if ((Test-Path -LiteralPath $arquivoBackup) -or (Test-Path -LiteralPath $arquivoManifesto)) {
    throw 'Nome de backup ja existente; nao sobrescrever.'
}

& docker cp "${NomeContainer}:${caminhoHba}" $arquivoBackup
if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar HBA para o Windows.' }

if (-not (Test-Path -LiteralPath $arquivoBackup -PathType Leaf)) {
    throw 'Arquivo de backup nao foi criado.'
}
$tamanho = (Get-Item -LiteralPath $arquivoBackup).Length
if ($tamanho -lt 100) { throw 'Backup HBA com tamanho inesperado.' }

$hashWindows = (Get-FileHash -LiteralPath $arquivoBackup -Algorithm SHA256).Hash.ToUpperInvariant()
$saidaHashDepois = & docker compose -f $CaminhoCompose exec -T postgres sha256sum $caminhoHba
if ($LASTEXITCODE -ne 0) { throw 'Falha ao conferir hash do HBA apos copia.' }
$textoHashDepois = ($saidaHashDepois | Out-String).Trim()
if ($textoHashDepois -match '^([0-9A-Fa-f]{64})\s+') {
    $hashDepois = $Matches[1].ToUpperInvariant()
} else { throw 'Formato do segundo hash invalido.' }
if ($hashWindows -cne $hashAntes -or $hashWindows -cne $hashDepois) {
    throw 'Falha de integridade: HBA ativo e copia nao possuem o mesmo SHA-256.'
}

$manifesto = [ordered]@{
    etapa = 'FASE_21_C2_E_C_C_A'
    criadoEm = (Get-Date).ToString('o')
    container = $NomeContainer
    caminhoHbaOriginal = $caminhoHba
    arquivoBackup = (Resolve-Path -LiteralPath $arquivoBackup).Path
    comprimentoBytes = [long]$tamanho
    sha256 = $hashWindows
    uidOriginal = $uidOriginal
    gidOriginal = $gidOriginal
    modoOriginal = $modoOriginal
}
$manifesto | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $arquivoManifesto -Encoding UTF8

Write-Host '=== C2-E-C-C-A BACKUP HBA PASS - original copiado integralmente; hashes coincidem ==='
Write-Host "Backup: $arquivoBackup"
Write-Host "Manifesto: $arquivoManifesto"
Write-Host "Tamanho (bytes): $tamanho"
Write-Host "SHA-256: $hashWindows"
Write-Host 'NENHUMA ALTERACAO foi feita no pg_hba.conf ou no servidor.'
