# FASE 21-E-D9 - adaptador de sessao de captura, exclusivamente em sandbox.
# Importar NAO cria diretorios, nao inicia processos e nao abre logs operacionais.
# Dependencias injetadas pelo script de teste: B1, B8, D1, D2 e D8.
Set-StrictMode -Version Latest

# Identidades queimadas durante esta execucao PowerShell: nao reutilizar a
# mesma combinacao PID/ticks/nonce mesmo depois de fechar a sessao.
# Este registro em memoria NAO substitui reserva atomica entre processos.
$script:ED9Identidades = @{}

function Assert-21ED9-Contexto {
    param([Parameter(Mandatory=$true)][string]$Raiz)
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        throw '21-E-D9: Windows obrigatorio para teste de ACL NTFS'
    }
    $esperada = [IO.Path]::GetFullPath((Get-Location).ProviderPath)
    if (-not [IO.Path]::GetFullPath($Raiz).Equals($esperada,[StringComparison]::OrdinalIgnoreCase)) {
        throw '21-E-D9: raiz diferente do projeto em execucao'
    }
    $branch = ((& git branch --show-current) | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
        throw '21-E-D9: branch incorreta'
    }
    if (-not (Assert-21EB1-Contexto -Raiz $esperada)) {
        throw '21-E-D9: sandbox B1 indisponivel'
    }
    $startup = Get-Content -LiteralPath (Join-Path $esperada 'config/operacao/continuous-startup-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    $retencao = Get-Content -LiteralPath (Join-Path $esperada 'config/operacao/retention-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    $catalogo = Get-Content -LiteralPath (Join-Path $esperada 'config/operacao/operational-log-catalog.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    $revisao = Get-Content -LiteralPath (Join-Path $esperada 'config/operacao/supervisor-log-integration-review.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    [void](Assert-21ED8-Contratos -Startup $startup -Retencao $retencao -Catalogo $catalogo -Revisao $revisao -RaizProjeto $esperada)
    Assert-21ED1-FontesIntactas -Raiz $esperada -Contrato $revisao
    return $catalogo
}

function Assert-21ED9-IdentidadePlanejada {
    param(
        [Parameter(Mandatory=$true)][long]$PidSimulado,
        [Parameter(Mandatory=$true)][long]$CreationTicksSimulados,
        [Parameter(Mandatory=$true)][string]$Nonce
    )
    # PID/ticks sinteticos: NAO comprova identidade de processo real.
    if ($PidSimulado -lt 1 -or $CreationTicksSimulados -lt 1 -or
        $Nonce -cnotmatch '^[0-9a-f]{32}$') {
        throw '21-E-D9: identidade sintetica invalida'
    }
    $base = 'C:\dev\rasping-amazon-operacao\logs'
    $nome = ('sessao-{0}-{1}-{2}' -f $PidSimulado,$CreationTicksSimulados,$Nonce)
    $caminhoProjetado = [IO.Path]::Combine($base,$nome)
    [void](Assert-21ED8-SessaoPlanejada -DiretorioBase $base `
        -DiretorioSessao $caminhoProjetado -PidVerificado $PidSimulado `
        -CreationTicksVerificados $CreationTicksSimulados -Nonce $Nonce `
        -SessaoPreexistente:$false -ReservaExclusiva:$true)
    # Caminho externo somente uma string. Nenhuma operacao de FS sobre ele.
    return ('{0}|{1}|{2}' -f $PidSimulado,$CreationTicksSimulados,$Nonce)
}

function Assert-21ED9-AdaptadorVivo {
    param([Parameter(Mandatory=$true)][object]$Adaptador)
    if ($null -eq $Adaptador -or $Adaptador.PSObject.Properties.Name -cnotcontains 'Identidade' -or
        $Adaptador.PSObject.Properties.Name -cnotcontains 'Sessao' -or
        $Adaptador.PSObject.Properties.Name -cnotcontains 'Estado' -or
        $Adaptador.PSObject.Properties.Name -cnotcontains 'Modo' -or
        $Adaptador.Modo -cne 'ensaio_sandbox_only' -or
        $Adaptador.Estado -cne 'ABERTA' -or
        -not $script:ED9Identidades.ContainsKey([string]$Adaptador.Identidade) -or
        -not [object]::ReferenceEquals($script:ED9Identidades[[string]$Adaptador.Identidade],$Adaptador) -or
        $Adaptador.Sessao.Lacrada) {
        throw '21-E-D9: adaptador fechado, falso ou reutilizado'
    }
    # Impede a troca do objeto de sessao e o redirecionamento de destino.
    if (-not [object]::ReferenceEquals($Adaptador.Sessao,$Adaptador.SessaoOriginal)) {
        throw '21-E-D9: sessao de escrita substituida'
    }
    [void](Assert-21ED2-DiretorioSandbox -Diretorio $Adaptador.Sessao.Diretorio)
    return $true
}

function New-21ED9-AdaptadorEnsaio {
    param(
        [Parameter(Mandatory=$true)][string]$Raiz,
        [Parameter(Mandatory=$true)][long]$PidSimulado,
        [Parameter(Mandatory=$true)][long]$CreationTicksSimulados,
        [Parameter(Mandatory=$true)][string]$Nonce,
        [Parameter(Mandatory=$true)][long]$MaximoSegmentoBytes,
        [Parameter(Mandatory=$true)][long]$MaximoTotalBytes
    )
    $catalogo = Assert-21ED9-Contexto -Raiz $Raiz
    $identidade = Assert-21ED9-IdentidadePlanejada -PidSimulado $PidSimulado `
        -CreationTicksSimulados $CreationTicksSimulados -Nonce $Nonce
    if ($script:ED9Identidades.ContainsKey($identidade)) {
        throw '21-E-D9: identidade ja consumida nesta execucao'
    }
    if ($MaximoSegmentoBytes -lt 128 -or $MaximoTotalBytes -lt $MaximoSegmentoBytes -or
        $MaximoSegmentoBytes -gt 16777216 -or $MaximoTotalBytes -gt 536870912) {
        throw '21-E-D9: limites fora do contrato'
    }
    # Exclusivamente B1 cria um cenario privado novo e D2 aceita diretorio vazio.
    $diretorio = New-21EB1-Sandbox -Raiz $Raiz
    $sessao = New-21ED2-SessaoOffline -Diretorio $diretorio -Catalogo $catalogo `
        -MaximoSegmentoBytes $MaximoSegmentoBytes -MaximoTotalBytes $MaximoTotalBytes
    $adaptador = [pscustomobject]@{
        Modo = 'ensaio_sandbox_only'
        Estado = 'ABERTA'
        Identidade = $identidade
        Sessao = $sessao
        SessaoOriginal = $sessao
        Diretorio = $diretorio
        Registros = [int]0
    }
    $script:ED9Identidades[$identidade] = $adaptador
    return $adaptador
}

function Write-21ED9-RegistroEnsaio {
    param(
        [Parameter(Mandatory=$true)][object]$Adaptador,
        [Parameter(Mandatory=$true)][string]$LinhaJson
    )
    [void](Assert-21ED9-AdaptadorVivo -Adaptador $Adaptador)
    try {
        # D2 revalida schema, catalogo, ACL, integridade e limites a cada escrita.
        Write-21ED2-EventoCatalogado -Sessao $Adaptador.Sessao -LinhaJson $LinhaJson
        $Adaptador.Registros = [int]$Adaptador.Registros + 1
    } catch {
        $Adaptador.Estado = 'LACRADA'
        try { Close-21ED2-Sessao -Sessao $Adaptador.Sessao } catch { }
        throw '21-E-D9: escrita isolada recusada; sessao lacrada'
    }
}

function Close-21ED9-AdaptadorEnsaio {
    param([Parameter(Mandatory=$true)][object]$Adaptador)
    if ($null -eq $Adaptador -or
        $Adaptador.PSObject.Properties.Name -cnotcontains 'Identidade' -or
        -not $script:ED9Identidades.ContainsKey([string]$Adaptador.Identidade) -or
        -not [object]::ReferenceEquals($script:ED9Identidades[[string]$Adaptador.Identidade],$Adaptador)) {
        throw '21-E-D9: encerramento de adaptador desconhecido'
    }
    if ($Adaptador.Estado -cne 'FECHADA') {
        try { Close-21ED2-Sessao -Sessao $Adaptador.Sessao }
        finally { $Adaptador.Estado = 'FECHADA' }
    }
}

function New-21ED9-AdaptadorProdutivo {
    param([Parameter(Mandatory=$true)][string]$DiretorioExterno)
    # Sem qualquer Test-Path, Get-Acl, CreateDirectory ou abertura do alvo.
    # Nunca habilitar por alternancia de flag: requer decisao e gate posteriores.
    throw '21-E-D9: adaptador PRODUTIVO nao implementado e nao autorizado'
}
