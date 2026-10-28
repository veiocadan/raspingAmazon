# FASE 21-E-B4, revisao B5: leitura concorrente com buffers limitados.
# Somente filho PowerShell sintetico, sem acesso ao daemon real.
# Carregamento do arquivo nao inicia processo nem realiza I/O.
Set-StrictMode -Version Latest

function New-21EB4-CanalLimitado {
    param(
        [Parameter(Mandatory=$true)][object]$Leitor,
        [Parameter(Mandatory=$true)][ValidateSet('stdout','stderr')][string]$Nome,
        [Parameter(Mandatory=$true)][object]$Sessao
    )
    $canal = [pscustomobject]@{
        Nome = $Nome
        Leitor = $Leitor
        Sessao = $Sessao
        Buffer = (New-Object char[] 1024)
        Pendente = (New-Object System.Text.StringBuilder)
        Tarefa = $null
        Eof = $false
        Eventos = 0
        Estruturados = 0
        Redigidos = 0
    }
    $canal.Tarefa = $Leitor.ReadAsync($canal.Buffer, 0, $canal.Buffer.Length)
    return $canal
}

function Write-21EB4-LinhaLimitada {
    param(
        [Parameter(Mandatory=$true)][object]$Canal,
        [AllowEmptyString()][Parameter(Mandatory=$true)][string]$Linha,
        [Parameter(Mandatory=$true)][object]$OutraSessao,
        [Parameter(Mandatory=$true)][long]$MaximoCombinadoBytes
    )
    $evento = Convert-21EB3-LinhaConsole -Linha $Linha -Canal $Canal.Nome
    if ($Canal.Nome -ceq 'stderr' -and $evento.Classificacao -cne 'REDACTED') {
        throw '21-E-B4: stderr sem redacao'
    }
    if (@('STRUCTURED','REDACTED') -cnotcontains $evento.Classificacao) {
        throw '21-E-B4: classificacao desconhecida'
    }
    $bytes = [Text.UTF8Encoding]::new($false,$true).GetByteCount($evento.LinhaJson + "`n")
    $atual = [long]$Canal.Sessao.TotalBytes + [long]$OutraSessao.TotalBytes
    if ($atual -gt $MaximoCombinadoBytes -or
        $bytes -gt ($MaximoCombinadoBytes - $atual)) {
        throw '21-E-B4: limite combinado atingido sem excluir evidencias'
    }
    Write-21EB1-EventoSintetico -Sessao $Canal.Sessao -LinhaJson $evento.LinhaJson
    $Canal.Eventos = [int]$Canal.Eventos + 1
    if ($evento.Classificacao -ceq 'STRUCTURED') {
        $Canal.Estruturados = [int]$Canal.Estruturados + 1
    } else {
        $Canal.Redigidos = [int]$Canal.Redigidos + 1
    }
}

function Receive-21EB4-BlocoLimitado {
    param(
        [Parameter(Mandatory=$true)][object]$Canal,
        [Parameter(Mandatory=$true)][object]$OutraSessao,
        [Parameter(Mandatory=$true)][long]$MaximoCombinadoBytes,
        [ValidateRange(128,32768)][int]$MaximoLinhaCaracteres = 32768
    )
    $lidos = $Canal.Tarefa.GetAwaiter().GetResult()
    if ($lidos -eq 0) {
        if ($Canal.Pendente.Length -gt 0) {
            $final = $Canal.Pendente.ToString().TrimEnd([char]13)
            Write-21EB4-LinhaLimitada -Canal $Canal -Linha $final `
                -OutraSessao $OutraSessao -MaximoCombinadoBytes $MaximoCombinadoBytes
            [void]$Canal.Pendente.Clear()
        }
        $Canal.Eof = $true
        $Canal.Tarefa = $null
        return
    }
    for ($i = 0; $i -lt $lidos; $i++) {
        $caractere = $Canal.Buffer[$i]
        if ($caractere -eq [char]10) {
            $linha = $Canal.Pendente.ToString().TrimEnd([char]13)
            Write-21EB4-LinhaLimitada -Canal $Canal -Linha $linha `
                -OutraSessao $OutraSessao -MaximoCombinadoBytes $MaximoCombinadoBytes
            [void]$Canal.Pendente.Clear()
        } else {
            [void]$Canal.Pendente.Append($caractere)
            if ($Canal.Pendente.Length -gt $MaximoLinhaCaracteres) {
                throw '21-E-B4: linha excede limite de leitura segura'
            }
        }
    }
    $Canal.Tarefa = $Canal.Leitor.ReadAsync($Canal.Buffer, 0, $Canal.Buffer.Length)
}

function Invoke-21EB4-CapturaMistaSintetica {
    param(
        [Parameter(Mandatory=$true)][string]$CodigoFilho,
        [Parameter(Mandatory=$true)][object]$SessaoStdout,
        [Parameter(Mandatory=$true)][object]$SessaoStderr,
        [Parameter(Mandatory=$true)][long]$MaximoCombinadoBytes,
        [ValidateRange(5,120)][int]$TimeoutSegundos = 45
    )
    if ([string]::IsNullOrWhiteSpace($CodigoFilho) -or
        $CodigoFilho.Length -gt 16384 -or $MaximoCombinadoBytes -lt 128 -or
        [object]::ReferenceEquals($SessaoStdout,$SessaoStderr) -or
        $SessaoStdout.Encerrada -or $SessaoStderr.Encerrada -or
        $SessaoStdout.TotalBytes -ne 0 -or $SessaoStderr.TotalBytes -ne 0) {
        throw '21-E-B4: parametros sinteticos invalidos'
    }
    $executavel = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $executavel -PathType Leaf)) {
        throw '21-E-B4: executavel sintetico indisponivel'
    }
    $codificado = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($CodigoFilho))
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $executavel
    $psi.Arguments = '-NoLogo -NoProfile -NonInteractive -EncodedCommand ' + $codificado
    $psi.WorkingDirectory = (Get-Location).ProviderPath
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [Text.UTF8Encoding]::new($false, $true)
    $psi.StandardErrorEncoding = [Text.UTF8Encoding]::new($false, $true)
    foreach ($chave in @($psi.EnvironmentVariables.Keys)) {
        if ($chave -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_)') {
            $psi.EnvironmentVariables.Remove($chave)
        }
    }
    $processo = New-Object System.Diagnostics.Process
    $processo.StartInfo = $psi
    $iniciado = $false
    $prazo = [DateTime]::UtcNow.AddSeconds($TimeoutSegundos)
    try {
        $iniciado = $processo.Start()
        if (-not $iniciado) { throw '21-E-B4: filho sintetico nao iniciou' }
        $outCanal = New-21EB4-CanalLimitado -Leitor $processo.StandardOutput `
            -Nome stdout -Sessao $SessaoStdout
        $errCanal = New-21EB4-CanalLimitado -Leitor $processo.StandardError `
            -Nome stderr -Sessao $SessaoStderr
        while (-not ($outCanal.Eof -and $errCanal.Eof)) {
            if ([DateTime]::UtcNow -ge $prazo) {
                throw '21-E-B4: timeout sintetico; evidencia parcial preservada'
            }
            $pendentes = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'
            if (-not $outCanal.Eof) { $pendentes.Add($outCanal.Tarefa) }
            if (-not $errCanal.Eof) { $pendentes.Add($errCanal.Tarefa) }
            $indice = [System.Threading.Tasks.Task]::WaitAny($pendentes.ToArray(), 250)
            if ($indice -lt 0) { continue }
            $concluida = $pendentes[$indice]
            if (-not $outCanal.Eof -and [object]::ReferenceEquals($concluida,$outCanal.Tarefa)) {
                Receive-21EB4-BlocoLimitado -Canal $outCanal -OutraSessao $SessaoStderr `
                    -MaximoCombinadoBytes $MaximoCombinadoBytes
            } else {
                Receive-21EB4-BlocoLimitado -Canal $errCanal -OutraSessao $SessaoStdout `
                    -MaximoCombinadoBytes $MaximoCombinadoBytes
            }
        }
        if (-not $processo.WaitForExit(5000)) {
            throw '21-E-B4: filho sintetico nao finalizou'
        }
        if ($processo.ExitCode -ne 0) {
            throw '21-E-B4: processo sintetico com codigo nao zero; evidencias preservadas'
        }
        return [pscustomobject]@{
            StdoutEventos = $outCanal.Eventos
            StderrEventos = $errCanal.Eventos
            StdoutEstruturados = $outCanal.Estruturados
            StdoutRedigidos = $outCanal.Redigidos
            BytesTotais = [long]($SessaoStdout.TotalBytes + $SessaoStderr.TotalBytes)
            ExitCode = $processo.ExitCode
        }
    } finally {
        if ($iniciado) {
            try {
                if (-not $processo.HasExited) {
                    # Somente filho sintetico instanciado por esta chamada.
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
