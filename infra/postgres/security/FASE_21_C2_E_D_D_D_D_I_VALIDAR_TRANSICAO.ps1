# FASE 21-C2-E-D-D-D-D-I - validacao sem iniciar daemon ou acessar segredos.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_I_FUNCOES_TRANSICAO.ps1'
Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
Exigir-PartidaBloqueada-C -Plano $plano
if ($plano.initial_schedule_interval -cne 'PT1H' -or $plano.shutdown_grace_seconds -ne 30) {
    throw 'D-I: plano operacional divergente'
}
$registro = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-I: daemon ou registro produtivo inesperado'
}
Write-Host '=== C2-E-D-D-D-D-I POLITICA PASS - nenhum daemon e quatro bloqueios ativos ==='

$launcher = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1'
$conteudo = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($launcher))
$posReserva = $conteudo.IndexOf('Criar-ReservaDePartida-D-I -Caminho $reserva',[System.StringComparison]::Ordinal)
$posSenha = $conteudo.IndexOf('Ler-SenhaDPAPI-C-C',[System.StringComparison]::Ordinal)
$posStart = $conteudo.IndexOf('[System.Diagnostics.Process]::Start($psi)',[System.StringComparison]::Ordinal)
$posConfirmacao = $conteudo.IndexOf('Confirmar-ProcessoJavaCriado-D-I -Processo $processo',[System.StringComparison]::Ordinal)
$posRegistro = $conteudo.IndexOf('[System.IO.File]::Move($temp,$registro)',[System.StringComparison]::Ordinal)
if ($posReserva -lt 0 -or $posSenha -lt 0 -or $posStart -lt 0 -or
    $posConfirmacao -lt 0 -or $posRegistro -lt 0 -or
    -not ($posReserva -lt $posSenha -and $posSenha -lt $posStart -and
         $posStart -lt $posConfirmacao -and $posConfirmacao -lt $posRegistro) -or
    -not $conteudo.Contains('Resolver-PartidaNaoRegistrada-D-I') -or
    -not $conteudo.Contains('creation_utc_ticks -eq [long]$ticks')) {
    throw 'D-I: transicao entre reserva, JVM e registro nao demonstrada'
}
Write-Host '=== C2-E-D-D-D-D-I ORDEM PASS - reserva antes de segredo e Start, PID conferido antes de registro ==='

# Exercitar reserva atomica com arquivos temporarios, fora do Git.
# Nao chama Start(), nao abre banco e nao acessa credenciais DPAPI.
$testeDir = Join-Path ([System.IO.Path]::GetTempPath()) ('ra-supervisao-d-i-' + [guid]::NewGuid().ToString('N'))
[void][System.IO.Directory]::CreateDirectory($testeDir)
$testeReserva = Join-Path $testeDir 'starting.v1'
try {
    $token = Criar-ReservaDePartida-D-I -Caminho $testeReserva
    if (-not (Test-Path -LiteralPath $testeReserva -PathType Leaf)) {
        throw 'D-I: nao foi possivel materializar reserva de teste'
    }
    $colisao = $false
    try { $outro = Criar-ReservaDePartida-D-I -Caminho $testeReserva }
    catch [System.IO.IOException] { $colisao = $true }
    if (-not $colisao) { throw 'D-I: partida concorrente nao foi bloqueada' }

    $tokenInvalidoRejeitado = $false
    try { Remover-ReservaPropria-D-I -Caminho $testeReserva -Token ([guid]::NewGuid().ToString('N')) }
    catch { $tokenInvalidoRejeitado = $true }
    if (-not $tokenInvalidoRejeitado -or -not (Test-Path -LiteralPath $testeReserva)) {
        throw 'D-I: limpeza de reserva alheia nao foi bloqueada'
    }
    $limpo = Resolver-PartidaNaoRegistrada-D-I -Processo $null -InicioTentado $false `
        -CaminhoReserva $testeReserva -Token $token `
        -CaminhoStop (Join-Path $testeDir 'stop') -PrazoSegundos 30
    if (-not $limpo -or (Test-Path -LiteralPath $testeReserva)) {
        throw 'D-I: nao liberou reserva quando Start nao foi tentado'
    }
    Write-Host '=== C2-E-D-D-D-D-I RESERVA PASS - exclusao mutua e limpeza por identidade ==='

    $token = Criar-ReservaDePartida-D-I -Caminho $testeReserva
    $limpo = Resolver-PartidaNaoRegistrada-D-I -Processo $null -InicioTentado $true `
        -CaminhoReserva $testeReserva -Token $token `
        -CaminhoStop (Join-Path $testeDir 'stop') -PrazoSegundos 30
    if ($limpo -or -not (Test-Path -LiteralPath $testeReserva)) {
        throw 'D-I: tentativa sem handle foi falsamente limpa'
    }
    Remover-ReservaPropria-D-I -Caminho $testeReserva -Token $token
    Write-Host '=== C2-E-D-D-D-D-I FALHA PASS - reserva retida quando inicio e inconclusivo ==='
} finally {
    # Testes exclusivamente locais, em diretorio criado por este gate.
    if (Test-Path -LiteralPath $testeDir) { [System.IO.Directory]::Delete($testeDir,$true) }
}

$envExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = if ($envExistia) { (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash } else { $null }
$anterior = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $saida = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher -ConfirmarPartida 2>&1 | Out-String)
    $codigo = $LASTEXITCODE
} finally { $ErrorActionPreference = $anterior }
if ($codigo -eq 0 -or $saida -notmatch 'PARTIDA BLOQUEADA') {
    throw 'D-I: tentativa real nao foi bloqueada na politica'
}
Write-Host '=== C2-E-D-D-D-D-I NEGATIVO PASS - launcher recusou inicio sem JVM ==='
if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExistia -or
    ($envExistia -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash) -or
    (Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-I: ambiente ou processo alterado'
}
Write-Host '=== C2-E-D-D-D-D-I ENV PASS - .env e daemon preservados ==='
Write-Host '=== C2-E-D-D-D-D-I GATE PASS - transicao protegida sem partida produtiva ==='
Write-Host '=== C2-E-D-D-D-D-I BLOQUEIO ATIVO - nao iniciar daemon nem efeitos externos ==='
