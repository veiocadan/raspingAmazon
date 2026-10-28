# Fixture D10: apenas processo PowerShell filho, sem daemon ou ambiente externo.
param(
    [Parameter(Mandatory=$true)][ValidateSet('dono','concorrente','reuso','independente')][string]$Modo,
    [Parameter(Mandatory=$true)][string]$Raiz,
    [Parameter(Mandatory=$true)][string]$Cenario,
    [Parameter(Mandatory=$true)][string]$Nonce
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try {
    foreach ($arquivo in @(
        'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
        'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
        'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
        'infra/retencao/FASE_21_E_D1_FUNCOES_PREINTEGRACAO.ps1',
        'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
        'infra/retencao/FASE_21_E_D8_FUNCOES_PREPARACAO_EXTERNA.ps1',
        'infra/retencao/FASE_21_E_D9_FUNCOES_ADAPTADOR_ISOLADO.ps1',
        'infra/retencao/FASE_21_E_D10_FUNCOES_RESERVA_ATOMICA.ps1'
    )) {
        . (Resolve-Path -LiteralPath (Join-Path $Raiz $arquivo) -ErrorAction Stop).ProviderPath
    }
    [void](Assert-21ED9-Contexto -Raiz $Raiz)
    if ($Modo -in @('concorrente','reuso')) {
        try {
            $indevida = New-21ED10-ReservaAtomicaEnsaio -Raiz $Raiz -Cenario $Cenario -Nonce $Nonce
            Close-21ED10-ReservaAtomicaEnsaio -Reserva $indevida
            exit 24 # Acesso indevido: a mesma reserva foi aceita.
        } catch {
            if ($_.Exception.Message -ceq '21-E-D10: reserva ja existente; nao reutilizar') {
                exit 23 # Colisao reconhecida exatamente.
            }
            exit 31 # Outra falha nao e sucesso da prova.
        }
    }
    $reserva = New-21ED10-ReservaAtomicaEnsaio -Raiz $Raiz -Cenario $Cenario -Nonce $Nonce
    try {
        if ($Modo -ceq 'dono') {
            $pronto = Join-Path $Cenario 'd10-pronto.marker'
            $liberar = Join-Path $Cenario 'd10-liberar.marker'
            Assert-21EB1-SemRedirecionamento -Caminho $pronto
            Assert-21EB1-SemRedirecionamento -Caminho $liberar
            $s = [IO.FileStream]::new($pronto,[IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,[IO.FileShare]::None)
            $s.Dispose()
            $limite = [DateTime]::UtcNow.AddSeconds(12)
            while (-not (Test-Path -LiteralPath $liberar -PathType Leaf)) {
                if ([DateTime]::UtcNow -ge $limite) { exit 25 }
                [Threading.Thread]::Sleep(50)
            }
        }
    } finally {
        Close-21ED10-ReservaAtomicaEnsaio -Reserva $reserva
    }
    exit 0
} catch {
    # Nenhuma mensagem bruta de excecao e persistida ou exibida.
    exit 30
}
