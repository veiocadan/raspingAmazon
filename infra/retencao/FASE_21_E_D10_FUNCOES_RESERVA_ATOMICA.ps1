# FASE 21-E-D10 - primitiva de reserva atomica somente no sandbox B1.
# Importar nao cria arquivos/processos e nao consulta diretorios externos.
# Dependencias injetadas pelo chamador: B1 e D9.
Set-StrictMode -Version Latest

function Assert-21ED10-CenarioPrivado {
    param([Parameter(Mandatory=$true)][string]$Raiz,
          [Parameter(Mandatory=$true)][string]$Cenario)
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw '21-E-D10: Windows requerido para este gate'
    }
    $raizAbs = [IO.Path]::GetFullPath($Raiz)
    $raizAtual = [IO.Path]::GetFullPath((Get-Location).ProviderPath)
    if (-not $raizAbs.Equals($raizAtual,[StringComparison]::OrdinalIgnoreCase)) {
        throw '21-E-D10: raiz incorreta'
    }
    $base = [IO.Path]::GetFullPath((Join-Path $raizAbs 'target/retencao-21eb1-testes'))
    $absoluto = [IO.Path]::GetFullPath($Cenario)
    $pai = [IO.Directory]::GetParent($absoluto)
    if ($null -eq $pai -or
        -not $pai.FullName.Equals($base,[StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($absoluto) -cnotmatch '^cenario-[0-9a-f]{32}$') {
        throw '21-E-D10: somente cenario B1 direto sob target'
    }
    [void](Assert-21EB1-Contexto -Raiz $raizAbs)
    Assert-21EB1-SemRedirecionamento -Caminho $absoluto
    if (-not (Test-Path -LiteralPath $absoluto -PathType Container)) {
        throw '21-E-D10: cenario privado inexistente'
    }
    Assert-21EB1-ACLPrivada -Diretorio $absoluto
    return $absoluto
}

function New-21ED10-ReservaAtomicaEnsaio {
    param([Parameter(Mandatory=$true)][string]$Raiz,
          [Parameter(Mandatory=$true)][string]$Cenario,
          [Parameter(Mandatory=$true)][string]$Nonce)
    # Nao e um adaptador produtivo. Nunca aceita um caminho externo.
    $diretorio = Assert-21ED10-CenarioPrivado -Raiz $Raiz -Cenario $Cenario
    if ($Nonce -cnotmatch '^[0-9a-f]{32}$') {
        throw '21-E-D10: nonce invalido'
    }
    $processoAtual = [Diagnostics.Process]::GetCurrentProcess()
    try {
        $pidReal = [long]$processoAtual.Id
        $ticksReais = [long]$processoAtual.StartTime.ToUniversalTime().Ticks
    } finally { $processoAtual.Dispose() }
    [void](Assert-21ED9-IdentidadePlanejada -PidSimulado $pidReal `
        -CreationTicksSimulados $ticksReais -Nonce $Nonce)
    $caminho = Join-Path $diretorio ('reserva-' + $Nonce + '.v1.json')
    Assert-21EB1-SemRedirecionamento -Caminho $caminho
    $fluxo = $null
    try {
        # CreateNew e a unica decisao de exclusividade: precheck nao bastaria.
        # O arquivo reservado permanece mesmo se o processo encerrar.
        $fluxo = [IO.FileStream]::new($caminho,[IO.FileMode]::CreateNew,
            [IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    } catch [IO.IOException] {
        if (Test-Path -LiteralPath $caminho -PathType Leaf) {
            throw '21-E-D10: reserva ja existente; nao reutilizar'
        }
        throw '21-E-D10: impossivel criar reserva atomica'
    }
    try {
        $conteudo = ConvertTo-Json -InputObject ([ordered]@{
            schema_version=1
            owner_pid=$pidReal
            owner_creation_utc_ticks=$ticksReais
            nonce=$Nonce
            mode='offline_sandbox_only'
        }) -Compress
        $bytes = [Text.UTF8Encoding]::new($false,$true).GetBytes($conteudo + "`n")
        $fluxo.Write($bytes,0,$bytes.Length)
        $fluxo.Flush($true)
        return [pscustomobject]@{
            Caminho=$caminho; Pid=$pidReal; CreationTicks=$ticksReais
            Nonce=$Nonce; Fluxo=$fluxo; Encerrada=$false
        }
    } catch {
        $fluxo.Dispose()
        # Fail closed: o marcador ja criado nao e removido, mesmo se incompleto.
        throw '21-E-D10: falha ao persistir reserva; marcador preservado'
    }
}

function Close-21ED10-ReservaAtomicaEnsaio {
    param([Parameter(Mandatory=$true)][object]$Reserva)
    if ($Reserva.Encerrada -or $null -eq $Reserva.Fluxo) {
        throw '21-E-D10: reserva ja fechada'
    }
    try { $Reserva.Fluxo.Flush($true) }
    finally {
        $Reserva.Fluxo.Dispose()
        $Reserva.Fluxo = $null
        $Reserva.Encerrada = $true
    }
    # Nao excluir nem reutilizar o arquivo de reserva.
}

function New-21ED10-ReservaProdutiva {
    param([Parameter(Mandatory=$true)][string]$DiretorioExterno)
    # Nao tocar no caminho recebido; o modo produtivo nao existe.
    throw '21-E-D10: reserva produtiva NAO IMPLEMENTADA e NAO AUTORIZADA'
}
