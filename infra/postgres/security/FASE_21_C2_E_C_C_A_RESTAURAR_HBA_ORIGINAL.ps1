# FASE 21-C2-E-C-C-A — Recuperacao de emergencia INDEPENDENTE de login SQL.
# ATENCAO: substitui o HBA atual pelo backup original e envia reload.
# NUNCA executar no gate de preparacao. Requer -ConfirmarRestauracao.
param(
    [Parameter(Mandatory = $true)]
    [string]$CaminhoManifesto,
    [switch]$ConfirmarRestauracao,
    [string]$CaminhoCompose = 'infra/compose.yml'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $ConfirmarRestauracao) {
    throw 'RESTAURACAO BLOQUEADA: falta -ConfirmarRestauracao (uso somente em emergencia autorizada).'
}
if (-not (Test-Path -LiteralPath $CaminhoManifesto -PathType Leaf)) {
    throw 'Manifesto de backup nao encontrado.'
}
if (-not (Test-Path -LiteralPath $CaminhoCompose -PathType Leaf)) {
    throw 'Compose nao encontrado. Execute na raiz do repositorio.'
}

$m = Get-Content -LiteralPath $CaminhoManifesto -Raw -Encoding UTF8 | ConvertFrom-Json
if ($m.etapa -cne 'FASE_21_C2_E_C_C_A' -or $m.container -cne 'rasping-amazon-postgres') {
    throw 'Manifesto inesperado: restauracao bloqueada.'
}
if ($m.caminhoHbaOriginal -notmatch '^/(?:[A-Za-z0-9_.-]+/)+pg_hba\.conf$' -or $m.caminhoHbaOriginal.Contains('/../')) {
    throw 'Caminho de restauracao nao permitido.'
}
if ($m.uidOriginal -notmatch '^\d+$' -or $m.gidOriginal -notmatch '^\d+$' -or $m.modoOriginal -notmatch '^[0-7]{3,4}$') {
    throw 'Metadados de ownership ou modo invalidos.'
}
if ($m.sha256 -notmatch '^[0-9A-Fa-f]{64}$') { throw 'SHA-256 invalido no manifesto.' }
if (-not (Test-Path -LiteralPath $m.arquivoBackup -PathType Leaf)) {
    throw 'Backup de HBA ausente; restauracao bloqueada.'
}
if ([long](Get-Item -LiteralPath $m.arquivoBackup).Length -ne [long]$m.comprimentoBytes) {
    throw 'Tamanho do backup diverge do manifesto.'
}
$sha = (Get-FileHash -LiteralPath $m.arquivoBackup -Algorithm SHA256).Hash
if ($sha -ine $m.sha256) { throw 'Checksum do backup diverge do manifesto.' }

# Este passo NAO depende de psql/autenticacao. O Docker permanece o caminho de emergencia.
$alvoDocker = [string]$m.container + ':' + [string]$m.caminhoHbaOriginal
& docker cp $m.arquivoBackup $alvoDocker
if ($LASTEXITCODE -ne 0) { throw 'Falha ao recolocar o HBA original no container.' }

$usuarioGrupo = "$($m.uidOriginal):$($m.gidOriginal)"
& docker compose -f $CaminhoCompose exec -T -u root postgres chown $usuarioGrupo $m.caminhoHbaOriginal
if ($LASTEXITCODE -ne 0) { throw 'Falha ao restaurar o proprietario original.' }
& docker compose -f $CaminhoCompose exec -T -u root postgres chmod $m.modoOriginal $m.caminhoHbaOriginal
if ($LASTEXITCODE -ne 0) { throw 'Falha ao restaurar as permissoes originais.' }

$hashCont = & docker compose -f $CaminhoCompose exec -T postgres sha256sum $m.caminhoHbaOriginal
if ($LASTEXITCODE -ne 0) { throw 'Falha ao verificar conteudo restaurado.' }
$hashContTexto = ($hashCont | Out-String).Trim()
if ($hashContTexto -match '^([0-9A-Fa-f]{64})\s+') {
    $hashRestaurado = $Matches[1]
} else { throw 'SHA-256 restaurado invalido.' }
if ($hashRestaurado -ine $m.sha256) { throw 'HBA restaurado nao corresponde ao backup.' }

& docker compose -f $CaminhoCompose exec -T -u postgres postgres sh -c 'pg_ctl -D "$PGDATA" reload'
if ($LASTEXITCODE -ne 0) {
    throw 'Arquivo restaurado, mas reload falhou. Investigue o PostgreSQL sem reiniciar automaticamente.'
}

Write-Host '=== C2-E-C-C-A RESTAURACAO ORIGINAL EXECUTADA - HBA copiado e reload solicitado ==='
Write-Host 'Validar pg_hba_file_rules e autenticacao real; reload nao prova nova politica carregada.'
