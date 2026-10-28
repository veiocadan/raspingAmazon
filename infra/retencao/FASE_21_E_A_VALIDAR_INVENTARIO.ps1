# FASE 21-E-A — Gate de inventario estritamente de leitura.
# Nao inicia Java, nao abre PostgreSQL, nao cria diretorios e nao apaga arquivos.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path)
if (-not (Test-Path -LiteralPath (Join-Path $raiz '.git') -PathType Container) -or
    -not (Test-Path -LiteralPath (Join-Path $raiz 'pom.xml') -PathType Leaf)) {
    throw '21-E-A: executar na raiz do repositorio'
}
$branch = (& git branch --show-current 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw '21-E-A: branch diferente da vigente da FASE 21'
}
. (Join-Path $PSScriptRoot 'FASE_21_E_A_FUNCOES_RETENCAO.ps1')
$caminho = Join-Path $raiz 'config/operacao/retention-policy.v1.json'
if (-not (Test-Path -LiteralPath $caminho -PathType Leaf)) {
    throw '21-E-A: politica versionada ausente'
}
$politica = Get-Content -LiteralPath $caminho -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
Assert-21EA-Politica -Politica $politica
$startup = Join-Path $raiz 'config/operacao/continuous-startup-policy.v1.json'
$plano = Get-Content -LiteralPath $startup -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
if ($plano.execution_mode -cne 'preparation_only' -or
    $plano.daemon_start_authorized -ne $false -or
    $plano.flyway_migrate_authorized -ne $false -or
    $plano.external_actions_authorized -ne $false -or
    $plano.automatic_restart_authorized -ne $false) {
    throw '21-E-A: startup nao esta no estado bloqueado'
}
Write-Host 'PASS - Politica inventarial valida e startup bloqueado.'
$instante = [datetime]::UtcNow
foreach ($regra in $politica.local_rules) {
    $resultado = Get-21EA-ResumoInventario -RaizRepositorio $raiz -Regra $regra -AgoraUtc $instante
    Write-Host ('ESCOPO={0}; ESTADO={1}; ARQUIVOS={2}; BYTES={3}; ANTERIORES_PRAZO_PROPOSTO={4}; REDIRECIONAMENTOS_IGNORADOS={5}' -f
        $resultado.escopo, $resultado.estado, $resultado.arquivos, $resultado.bytes,
        $resultado.anteriores_ao_prazo_proposto, $resultado.reparse_points_ignorados)
}
Write-Host 'PROTEGIDO - Arquivos de supervisao, credenciais, PostgreSQL e backups NAO INSPECIONADOS.'
Write-Host 'PASS - Inventario somente-leitura. Nenhuma retencao ou rotacao automatica esta ativa.'
