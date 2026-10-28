# FASE 21-E-A — Testes offline do contrato. Modifica apenas objetos em memoria.
# Nao inicializa daemon, nao apaga arquivos, nao cria fixtures em disco.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path)
if (-not (Test-Path -LiteralPath (Join-Path $raiz 'pom.xml') -PathType Leaf)) {
    throw '21-E-A: executar na raiz do repositorio'
}
. (Join-Path $PSScriptRoot 'FASE_21_E_A_FUNCOES_RETENCAO.ps1')
$original = Get-Content -LiteralPath (Join-Path $raiz 'config/operacao/retention-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
Assert-21EA-Politica -Politica $original
Write-Host 'PASS - Contrato original aceito.'
$casos = @(
    'autorizar-exclusao', 'ativar-captura', 'alterar-modo',
    'caminho-traversal', 'prazo-zero', 'prazo-github',
    'limite-zero', 'propriedade-surpresa', 'duplicar-escopo'
)
foreach ($caso in $casos) {
    $p = ($original | ConvertTo-Json -Depth 10) | ConvertFrom-Json
    switch ($caso) {
        'autorizar-exclusao' { $p.destructive_actions_authorized = $true }
        'ativar-captura' { $p.continuous_log_capture_authorized = $true }
        'alterar-modo' { $p.execution_mode = 'enforce' }
        'caminho-traversal' { $p.local_rules[0].relative_path = '../backups' }
        'prazo-zero' { $p.local_rules[0].proposed_max_age_days = 0 }
        'prazo-github' { $p.github_probe_artifact_days = 4 }
        'limite-zero' { $p.continuous_logs_proposal.proposed_max_segment_bytes = 0 }
        'propriedade-surpresa' { $p | Add-Member -NotePropertyName 'allow_delete' -NotePropertyValue $true }
        'duplicar-escopo' { $p.local_rules[1].id = $p.local_rules[0].id }
    }
    $rejeitado = $false
    try { Assert-21EA-Politica -Politica $p } catch { $rejeitado = $true }
    if (-not $rejeitado) { throw "21-E-A: guard falhou no caso $caso" }
    Write-Host "PASS - Configuracao proibida rejeitada: $caso"
}
Write-Host 'PASS - 9 guardas negativas verificadas em memoria.'
