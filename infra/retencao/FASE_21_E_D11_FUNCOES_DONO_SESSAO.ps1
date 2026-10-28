# FASE 21-E-D11 - exclusividade real por diretorio para escritor D2.
# Somente sandbox privado B1/target. Importar nao cria recursos.
# Requer importacao previa de B1, B8, D1, D2, D8, D9 e D10.
Set-StrictMode -Version Latest
$script:ED11Sessoes = @{}

function Assert-21ED11-Contexto {
    param([Parameter(Mandatory=$true)][string]$Raiz,
          [Parameter(Mandatory=$true)][string]$Cenario)
    [void](Assert-21ED9-Contexto -Raiz $Raiz)
    $caminho = Assert-21ED10-CenarioPrivado -Raiz $Raiz -Cenario $Cenario
    return $caminho
}

function New-21ED11-SessaoEnsaio {
    param([Parameter(Mandatory=$true)][string]$Raiz,
          [Parameter(Mandatory=$true)][string]$Cenario,
          [Parameter(Mandatory=$true)][string]$Nonce,
          [Parameter(Mandatory=$true)][long]$MaximoSegmentoBytes,
          [Parameter(Mandatory=$true)][long]$MaximoTotalBytes)
    $diretorio = Assert-21ED11-Contexto -Raiz $Raiz -Cenario $Cenario
    if ($Nonce -cnotmatch '^[0-9a-f]{32}$') {
        throw '21-E-D11: nonce invalido'
    }
    # Esta reserva e FIXA por diretorio: nonces diferentes NAO a contornam.
    $marcador = Join-Path $diretorio 'd11-dono-diretorio.v1.json'
    Assert-21EB1-SemRedirecionamento -Caminho $marcador
    if (Test-Path -LiteralPath $marcador) {
        throw '21-E-D11: diretorio ja reservado; nao reutilizar'
    }
    # D2 exige um diretorio originalmente vazio. Sua construcao nao escreve.
    # O dono ainda nao pode gravar eventos antes de conquistar CreateNew.
    $catalogo = Assert-21ED9-Contexto -Raiz $Raiz
    try {
        $sessaoD2 = New-21ED2-SessaoOffline -Diretorio $diretorio `
            -Catalogo $catalogo -MaximoSegmentoBytes $MaximoSegmentoBytes `
            -MaximoTotalBytes $MaximoTotalBytes
    } catch {
        if (Test-Path -LiteralPath $marcador) {
            throw '21-E-D11: diretorio ja reservado; nao reutilizar'
        }
        throw '21-E-D11: diretorio nao esta vazio ou seguro; nenhuma escrita iniciada'
    }
    $fluxoDono = $null
    $reservaD10 = $null
    try {
        try {
            $fluxoDono = [IO.FileStream]::new($marcador,[IO.FileMode]::CreateNew,
                [IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        } catch [IO.IOException] {
            throw '21-E-D11: diretorio ja reservado; nao reutilizar'
        }
        # A reserva D10 preserva PID, creation ticks e nonce do processo REAL.
        $reservaD10 = New-21ED10-ReservaAtomicaEnsaio -Raiz $Raiz `
            -Cenario $diretorio -Nonce $Nonce
        $conteudo = ConvertTo-Json -InputObject ([ordered]@{
            schema_version=1
            mode='offline_sandbox_only'
            owner_pid=[long]$reservaD10.Pid
            owner_creation_utc_ticks=[long]$reservaD10.CreationTicks
            nonce=$Nonce
            directory_claim='exclusive_create_new'
        }) -Compress
        $bytes = [Text.UTF8Encoding]::new($false,$true).GetBytes($conteudo + "`n")
        $fluxoDono.Write($bytes,0,$bytes.Length)
        $fluxoDono.Flush($true)
        $identidade = $diretorio.ToLowerInvariant()
        if ($script:ED11Sessoes.ContainsKey($identidade)) {
            throw '21-E-D11: diretorio ja consumido nesta execucao'
        }
        $obj = [pscustomobject]@{
            Modo='offline_sandbox_only'; Estado='ABERTA'
            Diretorio=$diretorio; Identidade=$identidade; Nonce=$Nonce
            SessaoD2=$sessaoD2; SessaoOriginal=$sessaoD2
            ReservaD10=$reservaD10; FluxoDono=$fluxoDono
            Registros=[int]0
        }
        $script:ED11Sessoes[$identidade] = $obj
        return $obj
    } catch {
        # Sem rollback destrutivo: qualquer marcador criado permanece.
        try { Close-21ED2-Sessao -Sessao $sessaoD2 } catch { }
        if ($null -ne $reservaD10) {
            try { Close-21ED10-ReservaAtomicaEnsaio -Reserva $reservaD10 } catch { }
        }
        if ($null -ne $fluxoDono) { try { $fluxoDono.Dispose() } catch { } }
        throw '21-E-D11: reserva recusada; evidencias e marcadores preservados'
    }
}

function Assert-21ED11-Viva {
    param([Parameter(Mandatory=$true)][object]$Sessao)
    if ($null -eq $Sessao -or $Sessao.Modo -cne 'offline_sandbox_only' -or
        $Sessao.Estado -cne 'ABERTA' -or
        -not $script:ED11Sessoes.ContainsKey([string]$Sessao.Identidade) -or
        -not [object]::ReferenceEquals($script:ED11Sessoes[[string]$Sessao.Identidade],$Sessao) -or
        -not [object]::ReferenceEquals($Sessao.SessaoD2,$Sessao.SessaoOriginal) -or
        $Sessao.SessaoD2.Lacrada -or $Sessao.ReservaD10.Encerrada -or
        $null -eq $Sessao.FluxoDono -or -not $Sessao.FluxoDono.CanWrite) {
        throw '21-E-D11: sessao nao e dona ou esta encerrada'
    }
    [void](Assert-21ED2-DiretorioSandbox -Diretorio $Sessao.Diretorio)
    return $true
}

function Write-21ED11-EventoEnsaio {
    param([Parameter(Mandatory=$true)][object]$Sessao,
          [Parameter(Mandatory=$true)][string]$LinhaJson)
    [void](Assert-21ED11-Viva -Sessao $Sessao)
    try {
        Write-21ED2-EventoCatalogado -Sessao $Sessao.SessaoD2 -LinhaJson $LinhaJson
        $Sessao.Registros = [int]$Sessao.Registros + 1
    } catch {
        $Sessao.Estado = 'LACRADA'
        try { Close-21ED2-Sessao -Sessao $Sessao.SessaoD2 } catch { }
        throw '21-E-D11: escritor recusou evento; sessao lacrada'
    }
}

function Close-21ED11-SessaoEnsaio {
    param([Parameter(Mandatory=$true)][object]$Sessao)
    if ($null -eq $Sessao -or $Sessao.Estado -ceq 'FECHADA' -or
        -not $script:ED11Sessoes.ContainsKey([string]$Sessao.Identidade) -or
        -not [object]::ReferenceEquals($script:ED11Sessoes[[string]$Sessao.Identidade],$Sessao)) {
        throw '21-E-D11: fechamento duplicado ou sessao desconhecida'
    }
    try {
        try { Close-21ED2-Sessao -Sessao $Sessao.SessaoD2 }
        finally {
            try { Close-21ED10-ReservaAtomicaEnsaio -Reserva $Sessao.ReservaD10 }
            finally {
                if ($null -ne $Sessao.FluxoDono) {
                    $Sessao.FluxoDono.Dispose()
                    $Sessao.FluxoDono=$null
                }
            }
        }
    } finally { $Sessao.Estado='FECHADA' }
    # Nunca excluir a reserva fixa nem a reserva identificada por nonce.
}

function New-21ED11-SessaoProdutiva {
    param([Parameter(Mandatory=$true)][string]$DiretorioExterno)
    # Nao inspecionar nem abrir o caminho recebido.
    throw '21-E-D11: sessao produtiva NAO IMPLEMENTADA e NAO AUTORIZADA'
}
