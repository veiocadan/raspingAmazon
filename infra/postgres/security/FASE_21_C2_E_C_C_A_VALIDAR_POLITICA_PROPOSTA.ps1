# FASE 21-C2-E-C-C-A — Verificacao estatica da politica proposta.
# Somente leitura. Nao instala, altera nem recarrega pg_hba.conf.
param(
    [string]$CaminhoPolitica = "infra/postgres/security/FASE_21_C2_E_C_B_POLITICA_HBA_PROPOSTA.conf"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $CaminhoPolitica -PathType Leaf)) {
    throw "Politica proposta nao encontrada."
}

$regras = @(
    Get-Content -LiteralPath $CaminhoPolitica -Encoding UTF8 |
        Where-Object { $_ -notmatch '^\s*(#|$)' } |
        ForEach-Object { [regex]::Replace($_.Trim(), '\s+', ' ') }
)

$esperado = @(
    'local all all scram-sha-256',
    'host all all 127.0.0.1/32 scram-sha-256',
    'host all all ::1/128 scram-sha-256',
    'local replication all scram-sha-256',
    'host replication all 127.0.0.1/32 scram-sha-256',
    'host replication all ::1/128 scram-sha-256',
    'host all all all scram-sha-256'
)

if ($regras.Count -ne $esperado.Count) {
    throw "Politica divergente: a quantidade de regras nao e sete."
}

for ($i = 0; $i -lt $esperado.Count; $i++) {
    if (-not [string]::Equals($regras[$i], $esperado[$i], [System.StringComparison]::Ordinal)) {
        throw "Politica divergente: regra na posicao $($i + 1)."
    }
}

Write-Host '=== C2-E-C-C-A POLITICA PASS - sete regras SCRAM na ordem esperada; arquivo nao aplicado ==='
Write-Host 'OBSERVACAO: validacao estatica. Nao comprova que o PostgreSQL carregara a politica.'
