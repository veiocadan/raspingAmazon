# FASE 21-E-B2 - captura exclusivamente de processos PowerShell SINTETICOS.
# Importar este arquivo nao cria processos, arquivos nem conexoes.
# Nao usar para iniciar o daemon real ou modificar o supervisor.
Set-StrictMode -Version Latest

function Assert-21EB2-LinhaEsperada {
    param(
        [Parameter(Mandatory=$true)][string]$Linha,
        [Parameter(Mandatory=$true)][ValidateSet('stdout','stderr')][string]$Canal
    )
    if ($Linha.Length -gt 32768 -or $Linha.Contains("`r") -or $Linha.Contains("`n")) {
        throw '21-E-B2: linha sintetica invalida ou longa demais'
    }
    try { $objeto = $Linha | ConvertFrom-Json -ErrorAction Stop }
    catch { throw '21-E-B2: JSON sintetico invalido' }
    if ($null -eq $objeto -or $objeto -isnot [pscustomobject] -or
        $objeto.PSObject.Properties.Name -cnotcontains 'event' -or
        $objeto.event -isnot [string]) {
        throw '21-E-B2: contrato de evento ausente'
    }
    $prefixo = '^fase21eb2\.' + $Canal + '\.[0-9]{4}$'
    if ($objeto.event -cnotmatch $prefixo) {
        throw '21-E-B2: origem de evento incompativel com o canal sintetico'
    }
    if ($objeto.PSObject.Properties.Name -cnotcontains 'level' -or
        (($Canal -ceq 'stdout') -and ($objeto.level -cne 'INFO')) -or
        (($Canal -ceq 'stderr') -and ($objeto.level -cne 'ERROR'))) {
        throw '21-E-B2: nivel incompativel com o canal sintetico'
    }
}

function Invoke-21EB2-CapturaSintetica {
    param(
        [Parameter(Mandatory=$true)][string]$CodigoFilho,
        [Parameter(Mandatory=$true)][object]$SessaoStdout,
        [Parameter(Mandatory=$true)][object]$SessaoStderr,
        [Parameter(Mandatory=$true)][long]$MaximoCombinadoBytes,
        [ValidateRange(5,120)][int]$TimeoutSegundos = 45
    )
    if ($MaximoCombinadoBytes -lt 128 -or
        [object]::ReferenceEquals($SessaoStdout, $SessaoStderr) -or
        [string]::IsNullOrWhiteSpace($CodigoFilho)) {
        throw '21-E-B2: parametros inseguros de captura'
    }
    if ($SessaoStdout.Encerrada -or $SessaoStderr.Encerrada -or
        $SessaoStdout.TotalBytes -ne 0 -or $SessaoStderr.TotalBytes -ne 0) {
        throw '21-E-B2: sessoes precisam estar vazias e abertas'
    }
    $executavel = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $executavel -PathType Leaf)) {
        throw '21-E-B2: PowerShell do Windows nao encontrado'
    }
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($CodigoFilho))
    $configuracao = New-Object System.Diagnostics.ProcessStartInfo
    $configuracao.FileName = $executavel
    $configuracao.Arguments = '-NoLogo -NoProfile -NonInteractive -EncodedCommand ' + $encoded
    $configuracao.UseShellExecute = $false
    $configuracao.CreateNoWindow = $true
    $configuracao.RedirectStandardOutput = $true
    $configuracao.RedirectStandardError = $true
    $configuracao.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $configuracao.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $configuracao.WorkingDirectory = (Get-Location).ProviderPath
    $processo = New-Object System.Diagnostics.Process
    $processo.StartInfo = $configuracao
    $iniciado = $false
    $saidas = 0
    $erros = 0
    $outEof = $false
    $errEof = $false
    $limiteUtc = [DateTime]::UtcNow.AddSeconds($TimeoutSegundos)
    $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
    try {
        $iniciado = $processo.Start()
        if (-not $iniciado) { throw '21-E-B2: processo sintetico nao iniciou' }
        # Um ReadLineAsync pendente por stream; nao acumula a saida toda em memoria.
        $outTask = $processo.StandardOutput.ReadLineAsync()
        $errTask = $processo.StandardError.ReadLineAsync()
        while (-not ($outEof -and $errEof)) {
            if ([DateTime]::UtcNow -ge $limiteUtc) {
                throw '21-E-B2: tempo limite de captura sintetica'
            }
            $pendentes = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'
            if (-not $outEof) { $pendentes.Add($outTask) }
            if (-not $errEof) { $pendentes.Add($errTask) }
            $indice = [System.Threading.Tasks.Task]::WaitAny($pendentes.ToArray(), 500)
            if ($indice -lt 0) { continue }
            $concluida = $pendentes[$indice]
            if (-not $outEof -and [object]::ReferenceEquals($concluida, $outTask)) {
                $linha = $outTask.GetAwaiter().GetResult()
                if ($null -eq $linha) {
                    $outEof = $true
                } else {
                    Assert-21EB2-LinhaEsperada -Linha $linha -Canal stdout
                    $bytes = $utf8.GetByteCount($linha + "`n")
                    if (($SessaoStdout.TotalBytes + $SessaoStderr.TotalBytes + $bytes) -gt $MaximoCombinadoBytes) {
                        throw '21-E-B2: limite combinado atingido; sem descarte'
                    }
                    Write-21EB1-EventoSintetico -Sessao $SessaoStdout -LinhaJson $linha
                    $saidas++
                    $outTask = $processo.StandardOutput.ReadLineAsync()
                }
            } else {
                $linha = $errTask.GetAwaiter().GetResult()
                if ($null -eq $linha) {
                    $errEof = $true
                } else {
                    Assert-21EB2-LinhaEsperada -Linha $linha -Canal stderr
                    $bytes = $utf8.GetByteCount($linha + "`n")
                    if (($SessaoStdout.TotalBytes + $SessaoStderr.TotalBytes + $bytes) -gt $MaximoCombinadoBytes) {
                        throw '21-E-B2: limite combinado atingido; sem descarte'
                    }
                    Write-21EB1-EventoSintetico -Sessao $SessaoStderr -LinhaJson $linha
                    $erros++
                    $errTask = $processo.StandardError.ReadLineAsync()
                }
            }
        }
        if (-not $processo.WaitForExit(5000)) {
            throw '21-E-B2: processo sintetico nao finalizou'
        }
        if ($processo.ExitCode -ne 0) {
            throw '21-E-B2: codigo de saida nao zero; evidencias preservadas'
        }
        return [pscustomobject]@{
            StdoutEventos = $saidas
            StderrEventos = $erros
            BytesTotais = [long]($SessaoStdout.TotalBytes + $SessaoStderr.TotalBytes)
            ExitCode = $processo.ExitCode
        }
    } finally {
        # Somente o processo sintetico CRIADO por esta chamada pode ser encerrado.
        if ($iniciado) {
            try {
                if (-not $processo.HasExited) {
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
