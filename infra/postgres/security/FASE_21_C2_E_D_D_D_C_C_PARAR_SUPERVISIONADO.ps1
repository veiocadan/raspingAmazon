# FASE 21-C2-E-D-D-D-C-C - parada cooperativa do daemon, sem taskkill.
param(
    [switch]$ConfirmarParada,
    [string]$DiretorioSupervisao = 'C:\dev\rasping-amazon-backups\supervisao'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmarParada) { throw 'C2-E-D-D-D-C-C: parada exige -ConfirmarParada' }
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
$dir = Exigir-CaminhoSupervisao-C-C -Diretorio $DiretorioSupervisao
$registro = Join-Path $dir 'rasping-amazon-daemon.v1.json'
$stop = Join-Path $dir 'rasping-amazon-daemon.stop.request'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'ATIVO_IDENTIFICADO') {
    throw 'C2-E-D-D-D-C-C: nenhum daemon supervisionado comprovado; nao criar pedido de parada'
}
Exigir-ACLPrivada-C-C -Diretorio $dir
if (Test-Path -LiteralPath $stop) { throw 'C2-E-D-D-D-C-C: pedido de parada preexistente; investigar' }
$identidade = Ler-RegistroSupervisao-C -Caminho $registro
if ($null -eq $identidade) { throw 'C2-E-D-D-D-C-C: registro desapareceu; nao agir' }
try { $processo = Get-Process -Id ([int]$identidade.pid) -ErrorAction Stop }
catch { throw 'C2-E-D-D-D-C-C: processo encerrou antes da parada; inspecionar registro' }
try {
    # Nova verificacao de PID + horario, mitigando troca de processo antes do pedido.
    if ((Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'ATIVO_IDENTIFICADO') {
        throw 'C2-E-D-D-D-C-C: identidade do daemon mudou; nao solicitar parada'
    }
    $arquivo = [System.IO.File]::Open($stop,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None)
    try {
        $bytes = [System.Text.Encoding]::ASCII.GetBytes('STOP')
        $arquivo.Write($bytes,0,$bytes.Length)
    } finally { $arquivo.Dispose() }
    Write-Host '=== C2-E-D-D-D-C-C PARADA SOLICITADA - arquivo de sinal criado sem taskkill ==='
    $milissegundos = [int]$plano.shutdown_grace_seconds * 1000
    if (-not $processo.WaitForExit($milissegundos)) {
        throw 'C2-E-D-D-D-C-C: prazo de parada expirou; processo nao sera morto automaticamente; investigar'
    }
    Write-Host '=== C2-E-D-D-D-C-C PARADA PASS - processo Java original encerrou ==='
    # Evitar apagar registro/solicitacao de uma nova execucao concorrente.
    if (Test-Path -LiteralPath $registro -PathType Leaf) {
        $final = Ler-RegistroSupervisao-C -Caminho $registro
        if ([long]$final.pid -eq [long]$identidade.pid -and
            [long]$final.creation_utc_ticks -eq [long]$identidade.creation_utc_ticks) {
            [System.IO.File]::Delete($registro)
            if (Test-Path -LiteralPath $stop -PathType Leaf) { [System.IO.File]::Delete($stop) }
        }
    }
} finally { $processo.Dispose() }
