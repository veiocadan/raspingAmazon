# FASE 21-E-D3: ponte de captura B9 -> escritor D2, somente filho sintetico.
# Importar este arquivo nao inicia processos, nao grava e nao autoriza o daemon.
Set-StrictMode -Version Latest

function Assert-21ED3-SessaoInicial {
    param(
        [Parameter(Mandatory=$true)][object]$Sessao,
        [Parameter(Mandatory=$true)][object]$Catalogo
    )
    [void](Assert-21ED2-Contexto -Raiz (Get-Location).ProviderPath)
    Assert-21EB8-Catalogo -Catalogo $Catalogo
    $dir = Assert-21ED2-DiretorioSandbox -Diretorio $Sessao.Diretorio
    if ($Sessao.Lacrada -or $Sessao.Indice -ne 0 -or
        $Sessao.TotalBytes -ne 0 -or $null -ne $Sessao.Fluxo -or
        $Sessao.SegmentoBytes -ne 0 -or
        @($Sessao.Catalogo.events).Count -ne 6 -or
        @((Get-ChildItem -LiteralPath $dir -Force -ErrorAction Stop)).Count -ne 0) {
        throw '21-E-D3: sessao offline nao exclusiva ou ja utilizada'
    }
    return $dir
}

function New-21ED3-Canal {
    param(
        [Parameter(Mandatory=$true)][object]$Leitor,
        [Parameter(Mandatory=$true)][ValidateSet('stdout','stderr')][string]$Nome
    )
    $canal = [pscustomobject]@{
        Nome = $Nome
        Leitor = $Leitor
        Buffer = (New-Object char[] 1024)
        Pendente = (New-Object System.Text.StringBuilder)
        Tarefa = $null
        Eof = $false
        Eventos = 0
        Catalogados = 0
        Redigidos = 0
    }
    $canal.Tarefa = $Leitor.ReadAsync($canal.Buffer,0,$canal.Buffer.Length)
    return $canal
}

function Write-21ED3-Linha {
    param(
        [Parameter(Mandatory=$true)][object]$Canal,
        [Parameter(Mandatory=$true)][object]$Sessao,
        [Parameter(Mandatory=$true)][object]$Catalogo,
        [AllowEmptyString()][Parameter(Mandatory=$true)][string]$Linha
    )
    if ($Sessao.Lacrada) { throw '21-E-D3: sessao ja lacrada' }
    $evento = Convert-21EB9-LinhaCatalogada -Linha $Linha -Canal $Canal.Nome -Catalogo $Catalogo
    if ($Canal.Nome -ceq 'stderr' -and $evento.Classificacao -cne 'REDACTED') {
        throw '21-E-D3: stderr nao redigido'
    }
    # O escritor D2 valida novamente JSON, catalogo, limites, ACL e colisao.
    Write-21ED2-EventoCatalogado -Sessao $Sessao -LinhaJson $evento.LinhaJson
    $Canal.Eventos = [int]$Canal.Eventos + 1
    if ($evento.Classificacao -ceq 'STRUCTURED_CATALOGED') {
        $Canal.Catalogados = [int]$Canal.Catalogados + 1
    } elseif ($evento.Classificacao -ceq 'REDACTED') {
        $Canal.Redigidos = [int]$Canal.Redigidos + 1
    } else {
        throw '21-E-D3: classificacao desconhecida'
    }
}

function Receive-21ED3-Bloco {
    param(
        [Parameter(Mandatory=$true)][object]$Canal,
        [Parameter(Mandatory=$true)][object]$Sessao,
        [Parameter(Mandatory=$true)][object]$Catalogo,
        [ValidateRange(128,32768)][int]$MaximoLinhaCaracteres = 32768
    )
    $lidos = $Canal.Tarefa.GetAwaiter().GetResult()
    if ($lidos -eq 0) {
        if ($Canal.Pendente.Length -gt 0) {
            $final = $Canal.Pendente.ToString().TrimEnd([char]13)
            Write-21ED3-Linha -Canal $Canal -Sessao $Sessao -Catalogo $Catalogo -Linha $final
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
            Write-21ED3-Linha -Canal $Canal -Sessao $Sessao -Catalogo $Catalogo -Linha $linha
            [void]$Canal.Pendente.Clear()
        } else {
            [void]$Canal.Pendente.Append($caractere)
            if ($Canal.Pendente.Length -gt $MaximoLinhaCaracteres) {
                throw '21-E-D3: linha sintetica acima do limite'
            }
        }
    }
    $Canal.Tarefa = $Canal.Leitor.ReadAsync($Canal.Buffer,0,$Canal.Buffer.Length)
}

function Invoke-21ED3-CapturaEscritorOffline {
    param(
        [Parameter(Mandatory=$true)][string]$CodigoFilho,
        [Parameter(Mandatory=$true)][object]$Sessao,
        [Parameter(Mandatory=$true)][object]$Catalogo,
        [ValidateRange(2,120)][int]$TimeoutSegundos = 45,
        [ValidateRange(128,32768)][int]$MaximoLinhaCaracteres = 32768
    )
    # Nunca aceita executavel, PID, classpath, diretorio externo ou segredo como parametro.
    # A unica execucao possivel eh um filho PowerShell artificial criado no sandbox.
    if ([string]::IsNullOrWhiteSpace($CodigoFilho) -or $CodigoFilho.Length -gt 16384) {
        throw '21-E-D3: codigo sintetico invalido'
    }
    $dir = Assert-21ED3-SessaoInicial -Sessao $Sessao -Catalogo $Catalogo
    $exe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
        throw '21-E-D3: PowerShell sintetico indisponivel'
    }
    $processo = New-Object System.Diagnostics.Process
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.WorkingDirectory = (Get-Location).ProviderPath
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false,$true)
    # stderr nao confiavel: qualquer byte deve gerar apenas marcador, nunca texto bruto.
    $psi.StandardErrorEncoding = [System.Text.Encoding]::GetEncoding(28591)
    foreach ($chave in @($psi.EnvironmentVariables.Keys)) {
        if ($chave -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_)') {
            $psi.EnvironmentVariables.Remove($chave)
        }
    }
    $processo.StartInfo = $psi
    $iniciado = $false
    try {
        # D2 exige diretorio vazio; o filho artificial eh criado somente DEPOIS.
        $arquivo = New-21EB9-ArquivoFilhoSintetico -CodigoFilho $CodigoFilho `
            -SessaoStdout ([pscustomobject]@{ Diretorio = $dir })
        if ($arquivo.Contains('"')) { throw '21-E-D3: caminho sintetico invalido' }
        $psi.Arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -OutputFormat Text -File "' + $arquivo + '"'
        $iniciado = $processo.Start()
        if (-not $iniciado) { throw '21-E-D3: processo sintetico nao iniciou' }
        $stdout = New-21ED3-Canal -Leitor $processo.StandardOutput -Nome stdout
        $stderr = New-21ED3-Canal -Leitor $processo.StandardError -Nome stderr
        $prazo = [DateTime]::UtcNow.AddSeconds($TimeoutSegundos)
        while (-not ($stdout.Eof -and $stderr.Eof)) {
            if ([DateTime]::UtcNow -ge $prazo) { throw '21-E-D3: timeout sintetico' }
            $tarefas = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'
            if (-not $stdout.Eof) { $tarefas.Add($stdout.Tarefa) }
            if (-not $stderr.Eof) { $tarefas.Add($stderr.Tarefa) }
            $indice = [System.Threading.Tasks.Task]::WaitAny($tarefas.ToArray(),250)
            if ($indice -lt 0) { continue }
            $concluida = $tarefas[$indice]
            if (-not $stdout.Eof -and [object]::ReferenceEquals($concluida,$stdout.Tarefa)) {
                Receive-21ED3-Bloco -Canal $stdout -Sessao $Sessao `
                    -Catalogo $Catalogo -MaximoLinhaCaracteres $MaximoLinhaCaracteres
            } else {
                Receive-21ED3-Bloco -Canal $stderr -Sessao $Sessao `
                    -Catalogo $Catalogo -MaximoLinhaCaracteres $MaximoLinhaCaracteres
            }
        }
        if (-not $processo.WaitForExit(5000) -or $processo.ExitCode -ne 0) {
            throw '21-E-D3: saida sintetica nao zero ou incompleta'
        }
        return [pscustomobject]@{
            StdoutEventos = $stdout.Eventos
            StderrEventos = $stderr.Eventos
            StdoutCatalogados = $stdout.Catalogados
            StdoutRedigidos = $stdout.Redigidos
            StderrRedigidos = $stderr.Redigidos
            TotalBytes = [long]$Sessao.TotalBytes
            ExitCode = [int]$processo.ExitCode
        }
    } catch {
        # Nao revelar conteudo bruto, excecoes de sistema nem segredos.
        throw '21-E-D3: captura offline rejeitada; evidencia anterior preservada'
    } finally {
        if ($iniciado) {
            try {
                if (-not $processo.HasExited) {
                    # Exclusivamente o filho PowerShell sintetico pertencente ao teste.
                    $processo.Kill()
                    [void]$processo.WaitForExit(5000)
                }
            } catch { }
        }
        try { Close-21ED2-Sessao -Sessao $Sessao }
        finally { $processo.Dispose() }
    }
}
