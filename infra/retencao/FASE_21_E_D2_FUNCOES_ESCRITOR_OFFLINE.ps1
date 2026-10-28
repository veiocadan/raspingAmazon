# FASE 21-E-D2 — prototipo de escritor segmentado catalogado, somente offline.
# Importar estas funcoes NAO cria arquivos, processos nem autoriza capturas.
Set-StrictMode -Version Latest

function Assert-21ED2-Contexto {
    param([Parameter(Mandatory=$true)][string]$Raiz)
    if (-not (Assert-21EB1-Contexto -Raiz $Raiz)) {
        throw '21-E-D2: ambiente de preparacao recusado'
    }
    $catalogo = Get-Content -LiteralPath (Join-Path $Raiz 'config/operacao/operational-log-catalog.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    Assert-21EB8-Catalogo -Catalogo $catalogo
    return $catalogo
}

function Assert-21ED2-EventoCatalogado {
    param(
        [Parameter(Mandatory=$true)][string]$LinhaJson,
        [Parameter(Mandatory=$true)][object]$Catalogo
    )
    Assert-21EB8-Catalogo -Catalogo $Catalogo
    if ($LinhaJson.Length -gt 32768 -or $LinhaJson.Contains("`n") -or $LinhaJson.Contains("`r")) {
        throw '21-E-D2: tamanho ou linha invalida'
    }
    try { $obj = ConvertFrom-Json -InputObject $LinhaJson -ErrorAction Stop }
    catch { throw '21-E-D2: JSON recusado' }
    if ($null -eq $obj -or $obj -isnot [pscustomobject]) {
        throw '21-E-D2: objeto de evento invalido'
    }
    # Somente objetos JSON planos com chaves ASCII literais.
    # As saidas B9 usam campos fixos sem escapes: recusar barras invertidas,
    # chaves duplicadas, chaves com escapes e formatos ambiguos.
    if ($LinhaJson.Contains('\')) { throw '21-E-D2: JSON escapado nao autorizado' }
    $nomes = @($obj.PSObject.Properties.Name)
    $chavesBrutas = [regex]::Matches($LinhaJson, '"([A-Za-z][A-Za-z0-9]*)"\s*:')
    if ($chavesBrutas.Count -ne $nomes.Count) {
        throw '21-E-D2: chaves JSON ambiguas ou duplicadas'
    }
    $vistos = @{}
    foreach ($achado in $chavesBrutas) {
        $nomeBruto = $achado.Groups[1].Value
        if ($nomes -cnotcontains $nomeBruto -or $vistos.ContainsKey($nomeBruto)) {
            throw '21-E-D2: chave JSON nao permitida ou duplicada'
        }
        $vistos[$nomeBruto] = $true
    }
    $permitidos = @('timestamp','level','event','component','operation','outcome',
        'runId','jobId','jobType','candidateId','snapshotId','evaluationId',
        'publicationId','durationMs','failureOrigin','failureType','errorCode')
    foreach ($nome in $nomes) {
        if ($permitidos -cnotcontains $nome) { throw '21-E-D2: campo nao catalogado' }
    }
    foreach ($campo in @('timestamp','level','event','component','operation','outcome')) {
        if ($nomes -cnotcontains $campo -or $obj.$campo -isnot [string] -or
            [string]::IsNullOrWhiteSpace($obj.$campo)) { throw '21-E-D2: campo obrigatorio invalido' }
    }
    $dt = [DateTimeOffset]::MinValue
    $cultura = [System.Globalization.CultureInfo]::InvariantCulture
    $estilo = [System.Globalization.DateTimeStyles]::None
    if ($obj.timestamp -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$' -or
        -not [DateTimeOffset]::TryParse($obj.timestamp, $cultura, $estilo, [ref]$dt)) {
        throw '21-E-D2: timestamp invalido'
    }
    $marcador = $obj.event -cin @('runtime.stdout.redacted','runtime.stderr.redacted')
    if ($marcador) {
        $nivel = if ($obj.event -ceq 'runtime.stderr.redacted') { 'ERROR' } else { 'WARN' }
        if ($obj.level -cne $nivel -or $obj.component -cne 'runtime-capture' -or
            $obj.operation -cne 'safe-catalog-classification' -or
            $obj.outcome -cne 'REDACTED' -or $nomes.Count -ne 6) {
            throw '21-E-D2: marcador nao confiavel'
        }
        return $true
    }
    $definicao = $null
    foreach ($item in @($Catalogo.events)) {
        if ($obj.event -ceq $item.code) { $definicao = $item; break }
    }
    if ($null -eq $definicao -or $obj.level -cne $definicao.level -or
        $obj.component -cne $definicao.component -or
        $obj.operation -cne $definicao.operation -or
        @($definicao.outcomes) -cnotcontains $obj.outcome) {
        throw '21-E-D2: evento nao autorizado pelo catalogo'
    }
    foreach ($campo in @('runId','jobId','candidateId','snapshotId','evaluationId','publicationId','durationMs')) {
        if ($nomes -ccontains $campo) {
            $numero = $obj.$campo
            if (($numero -isnot [long] -and $numero -isnot [int]) -or
                $numero -lt $(if ($campo -ceq 'durationMs') {0} else {1})) {
                throw '21-E-D2: correlacao numerica invalida'
            }
        }
    }
    if ($nomes -ccontains 'jobType' -and
        ($obj.jobType -isnot [string] -or
         @('COLLECT_DEALS','ENRICH_DEAL','EVALUATE_DEAL','PUBLICATION_DISPATCH') -cnotcontains $obj.jobType)) {
        throw '21-E-D2: tipo de job invalido'
    }
    $nFalha = @($nomes | Where-Object { @('failureOrigin','failureType','errorCode') -ccontains $_ }).Count
    if ($nFalha -ne 0 -and $nFalha -ne 3) {
        throw '21-E-D2: metadados incompletos'
    }
    if ($nFalha -eq 3) {
        if (@('EXTERNAL','INTERNAL') -cnotcontains $obj.failureOrigin -or
            @('TRANSIENT','PERMANENT') -cnotcontains $obj.failureType -or
            $obj.errorCode -cne 'REDACTED') {
            throw '21-E-D2: metadados de falha nao minimizados'
        }
    }
    return $true
}

function Assert-21ED2-DiretorioSandbox {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    $absoluto = [System.IO.Path]::GetFullPath($Diretorio)
    $base = [System.IO.Path]::GetFullPath((Join-Path (Get-Location).ProviderPath 'target/retencao-21eb1-testes')).TrimEnd([char[]]@('\','/'))
    $pai = [System.IO.Directory]::GetParent($absoluto)
    if ($null -eq $pai -or -not $pai.FullName.Equals($base,[System.StringComparison]::OrdinalIgnoreCase) -or
        [System.IO.Path]::GetFileName($absoluto) -cnotmatch '^cenario-[0-9a-f]{32}$') {
        throw '21-E-D2: escrita permitida SOMENTE no sandbox B1 sob target'
    }
    Assert-21EB1-SemRedirecionamento -Caminho $absoluto
    Assert-21EB1-ACLPrivada -Diretorio $absoluto
    return $absoluto
}

function New-21ED2-SessaoOffline {
    param(
        [Parameter(Mandatory=$true)][string]$Diretorio,
        [Parameter(Mandatory=$true)][object]$Catalogo,
        [Parameter(Mandatory=$true)][long]$MaximoSegmentoBytes,
        [Parameter(Mandatory=$true)][long]$MaximoTotalBytes
    )
    [void](Assert-21ED2-Contexto -Raiz (Get-Location).ProviderPath)
    Assert-21EB8-Catalogo -Catalogo $Catalogo
    if ($MaximoSegmentoBytes -lt 128 -or $MaximoTotalBytes -lt $MaximoSegmentoBytes -or
        $MaximoTotalBytes -gt 536870912 -or $MaximoSegmentoBytes -gt 16777216) {
        throw '21-E-D2: limites de escrita fora da politica'
    }
    $absoluto = Assert-21ED2-DiretorioSandbox -Diretorio $Diretorio
    if (@(Get-ChildItem -LiteralPath $absoluto -Force -ErrorAction Stop).Count -ne 0) {
        throw '21-E-D2: sessao exige diretorio vazio e exclusivo'
    }
    # Sem CreateDirectory aqui: a unica forma autorizada de criar o cenario e New-21EB1-Sandbox.
    return [pscustomobject]@{
        Diretorio = $absoluto; Catalogo = $Catalogo
        SegmentoMaximo = $MaximoSegmentoBytes; TotalMaximo = $MaximoTotalBytes
        Indice = 0; SegmentoBytes = [long]0; TotalBytes = [long]0
        Fluxo = $null; Lacrada = $false
    }
}

function Close-21ED2-Sessao {
    param([Parameter(Mandatory=$true)][object]$Sessao)
    try {
        if ($null -ne $Sessao.Fluxo) {
            $Sessao.Fluxo.Flush($true)
        }
    } finally {
        if ($null -ne $Sessao.Fluxo) {
            $Sessao.Fluxo.Dispose()
            $Sessao.Fluxo = $null
        }
        $Sessao.Lacrada = $true
    }
}

function Write-21ED2-EventoCatalogado {
    param(
        [Parameter(Mandatory=$true)][object]$Sessao,
        [Parameter(Mandatory=$true)][string]$LinhaJson
    )
    if ($Sessao.Lacrada) { throw '21-E-D2: sessao lacrada' }
    try {
        [void](Assert-21ED2-Contexto -Raiz (Get-Location).ProviderPath)
        [void](Assert-21ED2-DiretorioSandbox -Diretorio $Sessao.Diretorio)
        [void](Assert-21ED2-EventoCatalogado -LinhaJson $LinhaJson -Catalogo $Sessao.Catalogo)
        $bytes = [System.Text.UTF8Encoding]::new($false,$true).GetBytes($LinhaJson + "`n")
        if ($bytes.Length -gt $Sessao.SegmentoMaximo -or
            $Sessao.TotalBytes -gt $Sessao.TotalMaximo -or
            $bytes.Length -gt ($Sessao.TotalMaximo - $Sessao.TotalBytes)) {
            throw '21-E-D2: limite de segmento ou total excedido'
        }
        if ($null -eq $Sessao.Fluxo -or
            ($Sessao.SegmentoBytes + $bytes.Length) -gt $Sessao.SegmentoMaximo) {
            if ($null -ne $Sessao.Fluxo) {
                $Sessao.Fluxo.Flush($true)
                $Sessao.Fluxo.Dispose()
                $Sessao.Fluxo = $null
            }
            $Sessao.Indice = [int]$Sessao.Indice + 1
            $nome = 'operacional-{0:D6}.jsonl' -f $Sessao.Indice
            $caminho = Join-Path $Sessao.Diretorio $nome
            Assert-21EB1-SemRedirecionamento -Caminho $caminho
            $Sessao.Fluxo = [System.IO.FileStream]::new(
                $caminho,[System.IO.FileMode]::CreateNew,
                [System.IO.FileAccess]::Write,[System.IO.FileShare]::Read
            )
            $Sessao.SegmentoBytes = [long]0
        }
        $Sessao.Fluxo.Write($bytes,0,$bytes.Length)
        $Sessao.Fluxo.Flush($true)
        $Sessao.SegmentoBytes = [long]$Sessao.SegmentoBytes + $bytes.Length
        $Sessao.TotalBytes = [long]$Sessao.TotalBytes + $bytes.Length
    } catch {
        $Sessao.Lacrada = $true
        if ($null -ne $Sessao.Fluxo) {
            try { $Sessao.Fluxo.Dispose() } catch { }
            $Sessao.Fluxo = $null
        }
        # Nao refletir excecao original nem conteudo nao confiavel.
        throw '21-E-D2: gravacao recusada; sessao lacrada e evidencias preservadas'
    }
}

function New-21ED2-SessaoProdutiva {
    param([Parameter(Mandatory=$true)][string]$Diretorio)
    # A implementacao produtiva depende de ativacao formal futura e nao faz parte desta etapa.
    # Nao criar pasta, nao ler segredos, nao abrir fluxos, nao conectar ao supervisor.
    throw '21-E-D2: escritor produtivo NAO AUTORIZADO; somente sandbox isolado'
}
