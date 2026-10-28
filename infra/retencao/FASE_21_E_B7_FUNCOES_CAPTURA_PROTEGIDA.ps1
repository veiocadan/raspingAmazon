# FASE 21-E-B7. Integracao exclusivamente sintetica de B1+B3+B4/B5+B6.
# Carregar este arquivo nao inicia processo, nao cria arquivos e nao ativa daemon.
Set-StrictMode -Version Latest

function Write-21EB7-LinhaProtegida {
    param(
        [Parameter(Mandatory=$true)][object]$Canal,
        [AllowEmptyString()][Parameter(Mandatory=$true)][string]$Linha,
        [Parameter(Mandatory=$true)][object]$OutraSessao,
        [Parameter(Mandatory=$true)][long]$MaximoCombinadoBytes
    )
    if ($Canal.Sessao.Encerrada -or $OutraSessao.Encerrada) {
        throw '21-E-B7: sessao encerrada'
    }
    try {
        # Nunca persistir textos originais. B6 projeta campos livres para metadados minimos.
        $evento = Convert-21EB6-LinhaMinimizada -Linha $Linha -Canal $Canal.Nome
        if (@('STRUCTURED_MINIMIZED','REDACTED') -cnotcontains $evento.Classificacao -or
            ($Canal.Nome -ceq 'stderr' -and $evento.Classificacao -cne 'REDACTED')) {
            throw '21-E-B7: classificacao nao permitida'
        }
        $bytes = [Text.UTF8Encoding]::new($false, $true).GetByteCount($evento.LinhaJson + "`n")
        $atual = [long]$Canal.Sessao.TotalBytes + [long]$OutraSessao.TotalBytes
        if ($atual -gt $MaximoCombinadoBytes -or
            $bytes -gt ($MaximoCombinadoBytes - $atual)) {
            throw '21-E-B7: limite combinado atingido sem exclusao'
        }
        Write-21EB6-EventoLacrado -Sessao $Canal.Sessao -LinhaJson $evento.LinhaJson
        $Canal.Eventos = [int]$Canal.Eventos + 1
        if ($evento.Classificacao -ceq 'STRUCTURED_MINIMIZED') {
            $Canal.Estruturados = [int]$Canal.Estruturados + 1
        } else {
            $Canal.Redigidos = [int]$Canal.Redigidos + 1
        }
    } catch {
        # Lacrar ambos os canais, inclusive quando apenas um falhou.
        try { Close-21EB1-Sessao -Sessao $Canal.Sessao } catch { $Canal.Sessao.Encerrada = $true }
        try { Close-21EB1-Sessao -Sessao $OutraSessao } catch { $OutraSessao.Encerrada = $true }
        # Nunca expor detalhes da excecao do sistema de arquivos ou a entrada original.
        throw '21-E-B7: captura bloqueada; ambos canais lacrados sem exclusao'
    }
}

function Receive-21EB7-BlocoProtegido {
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
            Write-21EB7-LinhaProtegida -Canal $Canal -Linha $final `
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
            Write-21EB7-LinhaProtegida -Canal $Canal -Linha $linha `
                -OutraSessao $OutraSessao -MaximoCombinadoBytes $MaximoCombinadoBytes
            [void]$Canal.Pendente.Clear()
        } else {
            [void]$Canal.Pendente.Append($caractere)
            if ($Canal.Pendente.Length -gt $MaximoLinhaCaracteres) {
                throw '21-E-B7: linha excede limite de leitura segura'
            }
        }
    }
    $Canal.Tarefa = $Canal.Leitor.ReadAsync($Canal.Buffer, 0, $Canal.Buffer.Length)
}

function Invoke-21EB7-CapturaProtegidaSintetica {
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
        throw '21-E-B7: parametros sinteticos invalidos'
    }
    $executavel = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $executavel -PathType Leaf)) {
        throw '21-E-B7: executavel sintetico indisponivel'
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
        if ($chave -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_)') {
            $psi.EnvironmentVariables.Remove($chave)
        }
    }
    $processo = New-Object System.Diagnostics.Process
    $processo.StartInfo = $psi
    $iniciado = $false
    $prazo = [DateTime]::UtcNow.AddSeconds($TimeoutSegundos)
    try {
        $iniciado = $processo.Start()
        if (-not $iniciado) { throw '21-E-B7: processo sintetico nao iniciou' }
        $outCanal = New-21EB4-CanalLimitado -Leitor $processo.StandardOutput `
            -Nome stdout -Sessao $SessaoStdout
        $errCanal = New-21EB4-CanalLimitado -Leitor $processo.StandardError `
            -Nome stderr -Sessao $SessaoStderr
        while (-not ($outCanal.Eof -and $errCanal.Eof)) {
            if ([DateTime]::UtcNow -ge $prazo) {
                throw '21-E-B7: timeout sintetico; evidencia parcial preservada'
            }
            $pendentes = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'
            if (-not $outCanal.Eof) { $pendentes.Add($outCanal.Tarefa) }
            if (-not $errCanal.Eof) { $pendentes.Add($errCanal.Tarefa) }
            $indice = [System.Threading.Tasks.Task]::WaitAny($pendentes.ToArray(), 250)
            if ($indice -lt 0) { continue }
            $concluida = $pendentes[$indice]
            if (-not $outCanal.Eof -and [object]::ReferenceEquals($concluida,$outCanal.Tarefa)) {
                Receive-21EB7-BlocoProtegido -Canal $outCanal -OutraSessao $SessaoStderr `
                    -MaximoCombinadoBytes $MaximoCombinadoBytes
            } else {
                Receive-21EB7-BlocoProtegido -Canal $errCanal -OutraSessao $SessaoStdout `
                    -MaximoCombinadoBytes $MaximoCombinadoBytes
            }
        }
        if (-not $processo.WaitForExit(5000)) {
            throw '21-E-B7: processo sintetico nao finalizou'
        }
        if ($processo.ExitCode -ne 0) {
            throw '21-E-B7: processo sintetico com codigo nao zero; evidencias preservadas'
        }
        return [pscustomobject]@{
            StdoutEventos = $outCanal.Eventos
            StderrEventos = $errCanal.Eventos
            StdoutMinimizados = $outCanal.Estruturados
            StdoutRedigidos = $outCanal.Redigidos
            StderrRedigidos = $errCanal.Redigidos
            BytesTotais = [long]($SessaoStdout.TotalBytes + $SessaoStderr.TotalBytes)
            ExitCode = $processo.ExitCode
        }
    } finally {
        if ($iniciado) {
            try {
                if (-not $processo.HasExited) {
                    # Somente processo filho criado por esta chamada; nunca o supervisor.
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
