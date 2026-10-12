# FASE 21-C2-E-D-D-D-C-A - status sem alterar processos ou registros.
param(
    [string]$CaminhoRegistro = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$funcoes = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
if (-not (Test-Path -LiteralPath $funcoes -PathType Leaf)) {
    throw 'C2-E-D-D-D-C-A: funcoes de supervisionamento nao encontradas'
}
. $funcoes
Exigir-RaizProjeto-C
$estado = Obter-StatusSupervisao-C -CaminhoRegistro $CaminhoRegistro
if ($estado -ceq 'PARADO_SEM_REGISTRO') {
    Write-Host '=== C2-E-D-D-D-C-A STATUS PASS - nenhum daemon Java detectado e registro ausente ==='
} elseif ($estado -ceq 'ATIVO_IDENTIFICADO') {
    # Status verdadeiro sem exibir PID nem linha de comando. Nunca mata processos.
    Write-Host '=== C2-E-D-D-D-C-A STATUS ATIVO - daemon supervisionado identificado; nao iniciar outro ==='
} else {
    throw 'C2-E-D-D-D-C-A: estado operacional inesperado'
}
