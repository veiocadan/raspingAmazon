# FASE 21-C2-E-D-D-D-D-I - transicao segura entre criar JVM e registrar PID.
# Importar este arquivo NAO inicia nem encerra processos.
Set-StrictMode -Version Latest

function Criar-ReservaDePartida-D-I {
    param([Parameter(Mandatory=$true)][string]$Caminho)

    # CreateNew e atomico: duas janelas nunca reservam o mesmo daemon.
    $token = [guid]::NewGuid().ToString('N')
    $conteudo = 'rasping-amazon-starting-v1:' + $token
    $arquivo = [System.IO.File]::Open(
        $Caminho,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None
    )
    try {
        $bytes = [System.Text.Encoding]::ASCII.GetBytes($conteudo)
        $arquivo.Write($bytes,0,$bytes.Length)
        $arquivo.Flush($true)
    } finally {
        $arquivo.Dispose()
    }
    return $token
}

function Remover-ReservaPropria-D-I {
    param(
        [Parameter(Mandatory=$true)][string]$Caminho,
        [Parameter(Mandatory=$true)][string]$Token
    )
    if (-not (Test-Path -LiteralPath $Caminho -PathType Leaf)) {
        throw 'D-I: reserva de partida desapareceu; nao limpar estado concorrente'
    }
    $conteudo = [System.IO.File]::ReadAllText($Caminho,[System.Text.Encoding]::ASCII)
    if ($conteudo -cne ('rasping-amazon-starting-v1:' + $Token)) {
        throw 'D-I: identidade da reserva mudou; nao limpar estado concorrente'
    }
    [System.IO.File]::Delete($Caminho)
}

function Confirmar-ProcessoJavaCriado-D-I {
    param(
        [Parameter(Mandatory=$true)][System.Diagnostics.Process]$Processo,
        [Parameter(Mandatory=$true)][string]$ClassePrincipal
    )
    if ($Processo.HasExited) {
        throw 'D-I: processo Java encerrou durante verificacao de identidade'
    }
    $cim = Get-CimInstance Win32_Process -Filter ('ProcessId = ' + $Processo.Id) -ErrorAction Stop
    if ($null -eq $cim -or [long]$cim.ProcessId -ne [long]$Processo.Id) {
        throw 'D-I: PID nao comprovado pela API Windows'
    }
    $padrao = '(?:^|\s)' + [regex]::Escape($ClassePrincipal) + '(?:\s|$)'
    if ([string]::IsNullOrWhiteSpace([string]$cim.CommandLine) -or
        [string]$cim.CommandLine -notmatch $padrao) {
        throw 'D-I: classe Java nao comprovada pela API Windows'
    }
    $ticks = ([datetime]$cim.CreationDate).ToUniversalTime().Ticks
    if ($ticks -le 0 -or $Processo.HasExited) {
        throw 'D-I: horario de criacao nao comprovado ou processo encerrado'
    }
    return [long]$ticks
}

function Resolver-PartidaNaoRegistrada-D-I {
    param(
        [AllowNull()][System.Diagnostics.Process]$Processo,
        [Parameter(Mandatory=$true)][bool]$InicioTentado,
        [Parameter(Mandatory=$true)][string]$CaminhoReserva,
        [Parameter(Mandatory=$true)][string]$Token,
        [Parameter(Mandatory=$true)][string]$CaminhoStop,
        [Parameter(Mandatory=$true)][int]$PrazoSegundos,
        # Ponto de teste restrito: o launcher produtivo NAO informa esse parametro.
        # Nunca aceitar classes arbitrarias nem alterar o valor padrao.
        [ValidateSet(
            'com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMain',
            'com.raspingamazon.infrastructure.bootstrap.SupervisedLifecycleSmokeMain'
        )]
        [string]$ClassePrincipal = 'com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMain'
    )
    if ($PrazoSegundos -le 0 -or $PrazoSegundos -gt 30) {
        throw 'D-I: prazo de parada inesperado'
    }

    if ($null -eq $Processo) {
        if (-not $InicioTentado) {
            Remover-ReservaPropria-D-I -Caminho $CaminhoReserva -Token $Token
            return $true
        }
        # Start() pode ter criado filho mesmo se houve falha ao devolver handle.
        # Nao retirar a reserva: auditoria manual obrigatoria.
        return $false
    }

    if ($Processo.HasExited) {
        Remover-ReservaPropria-D-I -Caminho $CaminhoReserva -Token $Token
        return $true
    }

    # Nunca usar taskkill/Stop-Process. Somente sinal se a identidade da
    # mesma JVM for demonstrada por PID, classe Java e creation time.
    try {
        $ticks = Confirmar-ProcessoJavaCriado-D-I -Processo $Processo -ClassePrincipal $ClassePrincipal
        if ($ticks -le 0) { return $false }
    } catch {
        return $false
    }

    if (Test-Path -LiteralPath $CaminhoStop) {
        # Um stop preexistente e um estado suspeito; nao substituir/remover.
        return $false
    }

    $criouStop = $false
    try {
        $arquivo = [System.IO.File]::Open(
            $CaminhoStop,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::None
        )
        try {
            $bytes = [System.Text.Encoding]::ASCII.GetBytes('STOP')
            $arquivo.Write($bytes,0,$bytes.Length)
            $arquivo.Flush($true)
            $criouStop = $true
        } finally {
            $arquivo.Dispose()
        }
        if (-not $Processo.WaitForExit($PrazoSegundos * 1000)) {
            return $false
        }
        Remover-ReservaPropria-D-I -Caminho $CaminhoReserva -Token $Token
        return $true
    } finally {
        # Somente retirar o pedido criado por este processo, e apenas
        # depois de confirmar encerramento da JVM original.
        if ($criouStop -and $Processo.HasExited -and
            (Test-Path -LiteralPath $CaminhoStop -PathType Leaf)) {
            [System.IO.File]::Delete($CaminhoStop)
        }
    }
}
