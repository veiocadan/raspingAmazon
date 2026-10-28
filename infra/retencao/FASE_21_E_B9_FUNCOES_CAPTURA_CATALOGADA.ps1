# FASE 21-E-B9: integra B8 ao capturador sintetico, sem alterar B1-B8.
# Importar este arquivo nao inicia processos, nao escreve em disco e nao habilita daemon.
Set-StrictMode -Version Latest

function Convert-21EB9-LinhaCatalogada {
    param(
        [AllowEmptyString()][Parameter(Mandatory=$true)][string]$Linha,
        [Parameter(Mandatory=$true)][ValidateSet('stdout','stderr')][string]$Canal,
        [Parameter(Mandatory=$true)][object]$Catalogo
    )
    # B8 descarta os textos livres e valida o codigo, componentes e resultado.
    $projetado = ConvertTo-21EB8-EventoSeguro -Linha $Linha -Canal $Canal -Catalogo $Catalogo
    if ($null -eq $projetado -or $projetado -isnot [pscustomobject]) {
        throw '21-E-B9: resultado do catalogo invalido'
    }
    if ($projetado.kind -ceq 'structured_allowlisted') {
        if ($Canal -cne 'stdout' -or $projetado.channel -cne 'stdout') {
            throw '21-E-B9: estrutura somente em stdout'
        }
        $seguro = [ordered]@{
            timestamp = $projetado.timestamp
            level = $projetado.level
            event = $projetado.event
            component = $projetado.component
            operation = $projetado.operation
            outcome = $projetado.outcome
        }
        # Nao incluir kind, channel, errorCodeRedacted, asin, integration ou texto livre.
        foreach ($campo in @('runId','jobId','candidateId','snapshotId',
            'evaluationId','publicationId','durationMs','jobType',
            'failureOrigin','failureType')) {
            if ($projetado.PSObject.Properties.Name -ccontains $campo) {
                $seguro[$campo] = $projetado.$campo
            }
        }
        # B3 exige os tres metadados de falha juntos. Valor constante, nao a entrada.
        if ($projetado.PSObject.Properties.Name -ccontains 'errorCodeRedacted') {
            if ($projetado.errorCodeRedacted -ne $true) {
                throw '21-E-B9: metadado de falha inesperado'
            }
            $seguro['errorCode'] = 'REDACTED'
        }
        $classificacao = 'STRUCTURED_CATALOGED'
    } elseif ($projetado.kind -ceq 'redacted_marker') {
        if ($projetado.channel -cne $Canal) {
            throw '21-E-B9: canal do marcador divergente'
        }
        $nivel = if ($Canal -ceq 'stderr') { 'ERROR' } else { 'WARN' }
        $seguro = [ordered]@{
            timestamp = [DateTimeOffset]::UtcNow.ToString('o')
            level = $nivel
            event = 'runtime.' + $Canal + '.redacted'
            component = 'runtime-capture'
            operation = 'safe-catalog-classification'
            outcome = 'REDACTED'
        }
        $classificacao = 'REDACTED'
    } else {
        throw '21-E-B9: classificacao inesperada'
    }
    $json = ConvertTo-Json -InputObject $seguro -Compress -Depth 4
    if (-not (Test-21EB3-EventoJavaSeguro -Linha $json)) {
        throw '21-E-B9: registro final fora do contrato B1'
    }
    return [pscustomobject]@{
        Classificacao = $classificacao
        LinhaJson = $json
    }
}

function Write-21EB9-LinhaCatalogada {
    param(
        [Parameter(Mandatory=$true)][object]$Canal,
        [AllowEmptyString()][Parameter(Mandatory=$true)][string]$Linha,
        [Parameter(Mandatory=$true)][object]$OutraSessao,
        [Parameter(Mandatory=$true)][object]$Catalogo,
        [Parameter(Mandatory=$true)][long]$MaximoCombinadoBytes
    )
    if ($Canal.Sessao.Encerrada -or $OutraSessao.Encerrada) {
        throw '21-E-B9: sessao lacrada; gravacao proibida'
    }
    try {
        $evento = Convert-21EB9-LinhaCatalogada -Linha $Linha -Canal $Canal.Nome -Catalogo $Catalogo
        if ($Canal.Nome -ceq 'stderr' -and $evento.Classificacao -cne 'REDACTED') {
            throw '21-E-B9: stderr deve ser redigido'
        }
        $bytes = [Text.UTF8Encoding]::new($false,$true).GetByteCount($evento.LinhaJson + "`n")
        $uso = [long]$Canal.Sessao.TotalBytes + [long]$OutraSessao.TotalBytes
        if ($MaximoCombinadoBytes -lt 128 -or $uso -gt $MaximoCombinadoBytes -or
            $bytes -gt ($MaximoCombinadoBytes - $uso)) {
            throw '21-E-B9: limite combinado atingido'
        }
        Write-21EB6-EventoLacrado -Sessao $Canal.Sessao -LinhaJson $evento.LinhaJson
        $Canal.Eventos = [int]$Canal.Eventos + 1
        if ($evento.Classificacao -ceq 'STRUCTURED_CATALOGED') {
            $Canal.Estruturados = [int]$Canal.Estruturados + 1
        } else {
            $Canal.Redigidos = [int]$Canal.Redigidos + 1
        }
    } catch {
        # Reutiliza o lacre B6/B1 e nunca exibe a excecao do filesystem ou a linha bruta.
        try { Close-21EB1-Sessao -Sessao $Canal.Sessao }
        catch { $Canal.Sessao.Encerrada = $true }
        try { Close-21EB1-Sessao -Sessao $OutraSessao }
        catch { $OutraSessao.Encerrada = $true }
        throw '21-E-B9: falha na captura; dois canais lacrados'
    }
}

function Receive-21EB9-BlocoCatalogado {
    param(
        [Parameter(Mandatory=$true)][object]$Canal,
        [Parameter(Mandatory=$true)][object]$OutraSessao,
        [Parameter(Mandatory=$true)][object]$Catalogo,
        [Parameter(Mandatory=$true)][long]$MaximoCombinadoBytes,
        [ValidateRange(128,32768)][int]$MaximoLinhaCaracteres = 32768
    )
    $lidos = $Canal.Tarefa.GetAwaiter().GetResult()
    if ($lidos -eq 0) {
        if ($Canal.Pendente.Length -gt 0) {
            $final = $Canal.Pendente.ToString().TrimEnd([char]13)
            Write-21EB9-LinhaCatalogada -Canal $Canal -Linha $final `
                -OutraSessao $OutraSessao -Catalogo $Catalogo -MaximoCombinadoBytes $MaximoCombinadoBytes
            [void]$Canal.Pendente.Clear()
        }
        $Canal.Eof = $true
        $Canal.Tarefa = $null
        return
    }
    for ($i=0; $i -lt $lidos; $i++) {
        $caractere = $Canal.Buffer[$i]
        if ($caractere -eq [char]10) {
            $linha = $Canal.Pendente.ToString().TrimEnd([char]13)
            Write-21EB9-LinhaCatalogada -Canal $Canal -Linha $linha `
                -OutraSessao $OutraSessao -Catalogo $Catalogo -MaximoCombinadoBytes $MaximoCombinadoBytes
            [void]$Canal.Pendente.Clear()
        } else {
            [void]$Canal.Pendente.Append($caractere)
            if ($Canal.Pendente.Length -gt $MaximoLinhaCaracteres) {
                throw '21-E-B9: linha excede limite'
            }
        }
    }
    $Canal.Tarefa = $Canal.Leitor.ReadAsync($Canal.Buffer,0,$Canal.Buffer.Length)
}


# Exclusivamente para testes sinteticos: preserva o filho como arquivo sob
# sandbox privado B1. -File evita o protocolo CLIXML que -EncodedCommand pode
# acrescentar ao stderr, sem filtrar ou descartar linhas do processo filho.
function New-21EB9-ArquivoFilhoSintetico {
    param(
        [Parameter(Mandatory=$true)][string]$CodigoFilho,
        [Parameter(Mandatory=$true)][object]$SessaoStdout
    )
    $diretorio = [System.IO.Path]::GetFullPath($SessaoStdout.Diretorio)
    $raiz = (Get-Location).ProviderPath
    $base = [System.IO.Path]::GetFullPath(
        (Join-Path $raiz 'target/retencao-21eb1-testes')
    ).TrimEnd([char[]]@('\','/'))
    $pai = [System.IO.Directory]::GetParent($diretorio)
    if ($null -eq $pai -or
        -not $pai.FullName.Equals($base, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not ([System.IO.Path]::GetFileName($diretorio) -match '^cenario-[0-9a-f]{32}$')) {
        throw '21-E-B9: caminho de filho sintetico nao autorizado'
    }
    Assert-21EB1-SemRedirecionamento -Caminho $diretorio
    Assert-21EB1-ACLPrivada -Diretorio $diretorio
    $nome = 'filho-sintetico-' + [guid]::NewGuid().ToString('N') + '.ps1'
    $caminho = Join-Path $diretorio $nome
    Assert-21EB1-SemRedirecionamento -Caminho $caminho
    $utf8 = [System.Text.UTF8Encoding]::new($true,$true)
    $bom = $utf8.GetPreamble()
    $corpo = $utf8.GetBytes($CodigoFilho)
    # Sem sobrescrita e sem exclusao; ACL privada herdada do diretorio B1.
    $fluxo = [System.IO.FileStream]::new(
        $caminho,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None
    )
    try {
        $fluxo.Write($bom,0,$bom.Length)
        $fluxo.Write($corpo,0,$corpo.Length)
        $fluxo.Flush($true)
    } finally {
        $fluxo.Dispose()
    }
    return $caminho
}

function Invoke-21EB9-CapturaCatalogadaSintetica {
    param(
        [Parameter(Mandatory=$true)][string]$CodigoFilho,
        [Parameter(Mandatory=$true)][object]$SessaoStdout,
        [Parameter(Mandatory=$true)][object]$SessaoStderr,
        [Parameter(Mandatory=$true)][object]$Catalogo,
        [Parameter(Mandatory=$true)][long]$MaximoCombinadoBytes,
        [ValidateRange(5,120)][int]$TimeoutSegundos = 45
    )
    # Impede inclusive o inicio do filho em qualquer configuracao nao autorizada.
    if (-not (Assert-21EB1-Contexto -Raiz (Get-Location).ProviderPath)) {
        throw '21-E-B9: ambiente nao autorizado'
    }
    Assert-21EB8-Catalogo -Catalogo $Catalogo
    if ([string]::IsNullOrWhiteSpace($CodigoFilho) -or
        $CodigoFilho.Length -gt 16384 -or $MaximoCombinadoBytes -lt 128 -or
        [object]::ReferenceEquals($SessaoStdout,$SessaoStderr) -or
        $SessaoStdout.Encerrada -or $SessaoStderr.Encerrada -or
        $SessaoStdout.TotalBytes -ne 0 -or $SessaoStderr.TotalBytes -ne 0) {
        throw '21-E-B9: parametros sinteticos invalidos'
    }
    $executavel = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $executavel -PathType Leaf)) {
        throw '21-E-B9: PowerShell sintetico indisponivel'
    }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $executavel
    # -File configurado dentro de try, depois de criar o arquivo no sandbox.
    # -ExecutionPolicy Bypass vale SOMENTE para o processo filho sintetico.
    $psi.WorkingDirectory = (Get-Location).ProviderPath
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    # stdout: registros JSON somente em UTF-8 estrito; bytes invalidos devem falhar fechados.
    $psi.StandardOutputEncoding = [Text.UTF8Encoding]::new($false,$true)
    # stderr: saida diagnostica nao confiavel de PowerShell/Windows pode usar pagina
    # de codigo local. ISO-8859-1 decodifica qualquer byte sem perdas por fallback;
    # nenhum caractere lido aqui e persistido, somente marcadores B8 redigidos.
    # Nao usar este decoder para stdout nem para dados estruturados.
    $psi.StandardErrorEncoding = [Text.Encoding]::GetEncoding(28591)
    foreach ($chave in @($psi.EnvironmentVariables.Keys)) {
        if ($chave -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_)') {
            $psi.EnvironmentVariables.Remove($chave)
        }
    }
    $processo = New-Object System.Diagnostics.Process
    $processo.StartInfo = $psi
    $iniciado = $false
    $prazo = [DateTime]::UtcNow.AddSeconds($TimeoutSegundos)
    try {
        $caminhoFilho = New-21EB9-ArquivoFilhoSintetico -CodigoFilho $CodigoFilho `
            -SessaoStdout $SessaoStdout
        if ($caminhoFilho.Contains('"')) {
            throw '21-E-B9: caminho sintetico invalido'
        }
        $psi.Arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -OutputFormat Text -File "' +
            $caminhoFilho + '"'
        $iniciado = $processo.Start()
        if (-not $iniciado) { throw '21-E-B9: processo sintetico nao iniciado' }
        $outCanal = New-21EB4-CanalLimitado -Leitor $processo.StandardOutput -Nome stdout -Sessao $SessaoStdout
        $errCanal = New-21EB4-CanalLimitado -Leitor $processo.StandardError -Nome stderr -Sessao $SessaoStderr
        while (-not ($outCanal.Eof -and $errCanal.Eof)) {
            if ([DateTime]::UtcNow -ge $prazo) {
                throw '21-E-B9: timeout sintetico; evidencia parcial preservada'
            }
            $pendentes = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'
            if (-not $outCanal.Eof) { $pendentes.Add($outCanal.Tarefa) }
            if (-not $errCanal.Eof) { $pendentes.Add($errCanal.Tarefa) }
            $indice = [System.Threading.Tasks.Task]::WaitAny($pendentes.ToArray(),250)
            if ($indice -lt 0) { continue }
            $concluida = $pendentes[$indice]
            if (-not $outCanal.Eof -and [object]::ReferenceEquals($concluida,$outCanal.Tarefa)) {
                Receive-21EB9-BlocoCatalogado -Canal $outCanal -OutraSessao $SessaoStderr `
                    -Catalogo $Catalogo -MaximoCombinadoBytes $MaximoCombinadoBytes
            } else {
                Receive-21EB9-BlocoCatalogado -Canal $errCanal -OutraSessao $SessaoStdout `
                    -Catalogo $Catalogo -MaximoCombinadoBytes $MaximoCombinadoBytes
            }
        }
        if (-not $processo.WaitForExit(5000)) {
            throw '21-E-B9: processo sintetico nao encerrou'
        }
        if ($processo.ExitCode -ne 0) {
            throw '21-E-B9: codigo de saida sintetico nao zero; evidencias preservadas'
        }
        return [pscustomobject]@{
            StdoutEventos = $outCanal.Eventos
            StderrEventos = $errCanal.Eventos
            StdoutCatalogados = $outCanal.Estruturados
            StdoutRedigidos = $outCanal.Redigidos
            StderrRedigidos = $errCanal.Redigidos
            BytesTotais = [long]($SessaoStdout.TotalBytes + $SessaoStderr.TotalBytes)
            ExitCode = $processo.ExitCode
        }
    } finally {
        if ($iniciado) {
            try {
                if (-not $processo.HasExited) {
                    # Excecao controlada: mata SOMENTE o processo filho sintetico criado aqui.
                    $processo.Kill()
                    [void]$processo.WaitForExit(5000)
                }
            } catch { }
        }
        try { Close-21EB1-Sessao -Sessao $SessaoStdout }
        finally {
            try { Close-21EB1-Sessao -Sessao $SessaoStderr }
            finally { $processo.Dispose() }
        }
    }
}
