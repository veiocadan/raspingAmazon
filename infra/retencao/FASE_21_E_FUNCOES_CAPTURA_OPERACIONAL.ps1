# FASE 21-E: ponte de captura para o processo Java supervisionado.
# Importacao passiva. Nao altera politicas, nao inicializa JVM, nao remove arquivos.
# Reutiliza B1 (ACL e redirecionamento), B3/B8/B9 (classificacao) e D2 (validacao).
Set-StrictMode -Version Latest

function Assert-21E-AutorizacaoCaptura {
    param([string]$Raiz=(Get-Location).ProviderPath)
    $startup = Get-Content -LiteralPath (Join-Path $Raiz 'config/operacao/continuous-startup-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    $retencao = Get-Content -LiteralPath (Join-Path $Raiz 'config/operacao/retention-policy.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    $revisao = Get-Content -LiteralPath (Join-Path $Raiz 'config/operacao/supervisor-log-integration-review.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    $catalogo = Get-Content -LiteralPath (Join-Path $Raiz 'config/operacao/operational-log-catalog.v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    if ($startup.execution_mode -cne 'supervised' -or
        $startup.daemon_start_authorized -ne $true -or
        $startup.external_actions_authorized -ne $true -or
        $startup.flyway_migrate_authorized -ne $false -or
        $startup.automatic_restart_authorized -ne $false -or
        $retencao.execution_mode -cne 'log_capture_only' -or
        $retencao.continuous_log_capture_authorized -ne $true -or
        $retencao.destructive_actions_authorized -ne $false -or
        $revisao.execution_mode -cne 'operational_approved' -or
        $revisao.supervisor_integration_authorized -ne $true -or
        $revisao.continuous_log_capture_authorized -ne $true -or
        $revisao.production_adapter_status -cne 'implemented_policy_gated' -or
        $revisao.destructive_actions_authorized -ne $false) {
        throw '21-E: captura operacional nao autorizada; daemon NAO iniciar'
    }
    # Catalogo congelado para classificacao e minimizacao; nao autoriza captura por si.
    Assert-21EB8-Catalogo -Catalogo $catalogo
    $destino = [string]$retencao.continuous_logs_proposal.external_directory
    if ($destino -cne 'C:\dev\rasping-amazon-operacao\logs' -or
        [long]$retencao.continuous_logs_proposal.proposed_max_segment_bytes -ne 16777216 -or
        [long]$retencao.continuous_logs_proposal.proposed_max_total_bytes -ne 536870912 -or
        [int]$retencao.continuous_logs_proposal.proposed_max_age_days -ne 30) {
        throw '21-E: destino ou limites divergentes da politica versionada'
    }
    return [pscustomobject]@{
        Catalogo=$catalogo; Diretorio=$destino
        SegmentoBytes=[long]16777216; TotalBytes=[long]536870912
    }
}

function New-21E-SessaoCapturaOperacional {
    param([Parameter(Mandatory=$true)][object]$Plano,
          [Parameter(Mandatory=$true)][System.Diagnostics.Process]$Processo,
          [Parameter(Mandatory=$true)][long]$CreationTicks)
    # A captura so pode ser iniciada depois da verificacao de PID + creation time
    # do supervisor. Nao usar os PID/ticks sinteticos dos testes D9-D11.
    if ($Processo.HasExited -or $Processo.Id -le 0 -or $CreationTicks -le 0) {
        throw '21-E: identidade da JVM nao confirmada'
    }
    $revalidado = Assert-21E-AutorizacaoCaptura
    if ($Plano.Diretorio -cne $revalidado.Diretorio) {
        throw '21-E: plano alterado antes de criar sessao'
    }
    $diretorio = $Plano.Diretorio
    # Nao criar pasta sem pai previamente preparado. Evita caminhos alternativos.
    Assert-21EB1-SemRedirecionamento -Caminho $diretorio
    $pai = [IO.Directory]::GetParent($diretorio)
    if ($null -eq $pai -or -not $pai.Exists) {
        throw '21-E: pasta pai operacional nao preparada'
    }
    Assert-21EB1-SemRedirecionamento -Caminho $pai.FullName
    if (-not (Test-Path -LiteralPath $diretorio -PathType Container)) {
        [void][IO.Directory]::CreateDirectory($diretorio,(New-21EB1-ACLPrivada))
    }
    Assert-21EB1-ACLPrivada -Diretorio $diretorio
    $nonce = [guid]::NewGuid().ToString('N')
    $nome = 'sessao-{0}-{1}-{2}' -f [long]$Processo.Id,$CreationTicks,$nonce
    $sessaoDir = Join-Path $diretorio $nome
    Assert-21EB1-SemRedirecionamento -Caminho $sessaoDir
    if (Test-Path -LiteralPath $sessaoDir) { throw '21-E: sessao existente' }
    [void][IO.Directory]::CreateDirectory($sessaoDir,(New-21EB1-ACLPrivada))
    Assert-21EB1-ACLPrivada -Diretorio $sessaoDir
    $reserva = Join-Path $sessaoDir 'owner.v1.json'
    $fluxoDono = [IO.FileStream]::new($reserva,[IO.FileMode]::CreateNew,
        [IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try {
        $identidade = ConvertTo-Json -InputObject ([ordered]@{
            schema_version=1; pid=[long]$Processo.Id
            creation_utc_ticks=$CreationTicks; nonce=$nonce
        }) -Compress
        $bytes = [Text.UTF8Encoding]::new($false,$true).GetBytes($identidade + "`n")
        $fluxoDono.Write($bytes,0,$bytes.Length)
        $fluxoDono.Flush($true)
    } catch {
        $fluxoDono.Dispose()
        throw '21-E: falha ao criar reserva da sessao; manter evidencias'
    }
    return [pscustomobject]@{
        Diretorio=$sessaoDir; Catalogo=$revalidado.Catalogo
        SegmentoMaximo=$revalidado.SegmentoBytes; TotalMaximo=$revalidado.TotalBytes
        Indice=[int]0; SegmentoBytes=[long]0; TotalBytes=[long]0
        Fluxo=$null; FluxoDono=$fluxoDono; Lacrada=$false
    }
}

function Close-21E-SessaoCapturaOperacional {
    param([Parameter(Mandatory=$true)][object]$Sessao)
    $Sessao.Lacrada = $true
    try {
        if ($null -ne $Sessao.Fluxo) {
            try { $Sessao.Fluxo.Flush($true) }
            finally { $Sessao.Fluxo.Dispose(); $Sessao.Fluxo=$null }
        }
    } finally {
        if ($null -ne $Sessao.FluxoDono) {
            $Sessao.FluxoDono.Dispose()
            $Sessao.FluxoDono=$null
        }
    }
    # Nunca remover reserva, diretorio ou segmento; nao executar retencao destrutiva.
}

function Write-21E-EventoOperacional {
    param([Parameter(Mandatory=$true)][object]$Sessao,
          [Parameter(Mandatory=$true)][string]$LinhaJson)
    if ($Sessao.Lacrada -or $null -eq $Sessao.FluxoDono -or
        -not $Sessao.FluxoDono.CanWrite) { throw '21-E: sessao encerrada' }
    try {
        [void](Assert-21ED2-EventoCatalogado -LinhaJson $LinhaJson -Catalogo $Sessao.Catalogo)
        Assert-21EB1-SemRedirecionamento -Caminho $Sessao.Diretorio
        Assert-21EB1-ACLPrivada -Diretorio $Sessao.Diretorio
        $bytes=[Text.UTF8Encoding]::new($false,$true).GetBytes($LinhaJson + "`n")
        if ($bytes.Length -gt $Sessao.SegmentoMaximo -or
            $bytes.Length -gt ($Sessao.TotalMaximo - $Sessao.TotalBytes)) {
            throw '21-E: quota excedida'
        }
        if ($null -eq $Sessao.Fluxo -or
            ($Sessao.SegmentoBytes + $bytes.Length) -gt $Sessao.SegmentoMaximo) {
            if ($null -ne $Sessao.Fluxo) {
                $Sessao.Fluxo.Flush($true)
                $Sessao.Fluxo.Dispose();$Sessao.Fluxo=$null
            }
            $Sessao.Indice=[int]$Sessao.Indice+1
            $caminho=Join-Path $Sessao.Diretorio ('operacional-{0:D6}.jsonl' -f $Sessao.Indice)
            Assert-21EB1-SemRedirecionamento -Caminho $caminho
            $Sessao.Fluxo=[IO.FileStream]::new($caminho,[IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,[IO.FileShare]::Read)
            $Sessao.SegmentoBytes=[long]0
        }
        $Sessao.Fluxo.Write($bytes,0,$bytes.Length)
        $Sessao.Fluxo.Flush($true)
        $Sessao.SegmentoBytes=[long]$Sessao.SegmentoBytes+$bytes.Length
        $Sessao.TotalBytes=[long]$Sessao.TotalBytes+$bytes.Length
    } catch {
        try { Close-21E-SessaoCapturaOperacional -Sessao $Sessao } catch { }
        throw '21-E: escrita recusada; sessao lacrada; evidencias preservadas'
    }
}

function New-21E-CanalOperacional {
    param([Parameter(Mandatory=$true)][object]$Leitor,
          [Parameter(Mandatory=$true)][ValidateSet('stdout','stderr')][string]$Nome)
    $canal=[pscustomobject]@{
        Leitor=$Leitor; Nome=$Nome; Buffer=(New-Object char[] 1024)
        Pendente=(New-Object System.Text.StringBuilder)
        Tarefa=$null; Eof=$false; Eventos=[int]0
    }
    $canal.Tarefa=$Leitor.ReadAsync($canal.Buffer,0,$canal.Buffer.Length)
    return $canal
}

function Receive-21E-CanalOperacional {
    param([Parameter(Mandatory=$true)][object]$Canal,
          [Parameter(Mandatory=$true)][object]$Sessao)
    $lidos=$Canal.Tarefa.GetAwaiter().GetResult()
    if ($lidos -eq 0) {
        if ($Canal.Pendente.Length -gt 0) {
            $linha=$Canal.Pendente.ToString().TrimEnd([char]13)
            $evento=Convert-21EB9-LinhaCatalogada -Linha $linha -Canal $Canal.Nome -Catalogo $Sessao.Catalogo
            Write-21E-EventoOperacional -Sessao $Sessao -LinhaJson $evento.LinhaJson
            $Canal.Eventos=[int]$Canal.Eventos+1
            [void]$Canal.Pendente.Clear()
        }
        $Canal.Eof=$true;$Canal.Tarefa=$null
        return
    }
    for ($i=0;$i -lt $lidos;$i++) {
        $caractere=$Canal.Buffer[$i]
        if ($caractere -eq [char]10) {
            $linha=$Canal.Pendente.ToString().TrimEnd([char]13)
            $evento=Convert-21EB9-LinhaCatalogada -Linha $linha -Canal $Canal.Nome -Catalogo $Sessao.Catalogo
            Write-21E-EventoOperacional -Sessao $Sessao -LinhaJson $evento.LinhaJson
            $Canal.Eventos=[int]$Canal.Eventos+1
            [void]$Canal.Pendente.Clear()
        } else {
            [void]$Canal.Pendente.Append($caractere)
            if ($Canal.Pendente.Length -gt 32768) { throw '21-E: linha excedeu limite' }
        }
    }
    $Canal.Tarefa=$Canal.Leitor.ReadAsync($Canal.Buffer,0,$Canal.Buffer.Length)
}

function Invoke-21E-CapturarProcessoSupervisionado {
    param([Parameter(Mandatory=$true)][System.Diagnostics.Process]$Processo,
          [Parameter(Mandatory=$true)][object]$Sessao,
          [Parameter(Mandatory=$true)][string]$CaminhoStop,
          [ValidateRange(15,600)][int]$PrazoParadaSegundos = 30)
    try {
        $stdout=New-21E-CanalOperacional -Leitor $Processo.StandardOutput -Nome stdout
        $stderr=New-21E-CanalOperacional -Leitor $Processo.StandardError -Nome stderr
        while (-not ($stdout.Eof -and $stderr.Eof)) {
            $pendentes=New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'
            if (-not $stdout.Eof) { $pendentes.Add($stdout.Tarefa) }
            if (-not $stderr.Eof) { $pendentes.Add($stderr.Tarefa) }
            $indice=[System.Threading.Tasks.Task]::WaitAny($pendentes.ToArray(),250)
            if ($indice -lt 0) { continue }
            if (-not $stdout.Eof -and
                [object]::ReferenceEquals($pendentes[$indice],$stdout.Tarefa)) {
                Receive-21E-CanalOperacional -Canal $stdout -Sessao $Sessao
            } else {
                Receive-21E-CanalOperacional -Canal $stderr -Sessao $Sessao
            }
        }
        $Processo.WaitForExit()
        if ($Processo.ExitCode -ne 0) {
            throw '21-E: JVM terminou com codigo nao zero'
        }
        return [pscustomobject]@{ Stdout=$stdout.Eventos; Stderr=$stderr.Eventos }
    } catch {
        # A JVM e a mesma confirmada pelo supervisor. Nunca usar Kill/taskkill.
        if (-not $Processo.HasExited) {
            try {
                if (-not (Test-Path -LiteralPath $CaminhoStop)) {
                    $stop=[IO.FileStream]::new($CaminhoStop,[IO.FileMode]::CreateNew,
                        [IO.FileAccess]::Write,[IO.FileShare]::None)
                    try {
                        $bytes=[Text.Encoding]::ASCII.GetBytes('STOP')
                        $stop.Write($bytes,0,$bytes.Length);$stop.Flush($true)
                    } finally { $stop.Dispose() }
                }
                [void]$Processo.WaitForExit($PrazoParadaSegundos*1000)
            } catch { }
        }
        throw '21-E: captura operacional falhou; parada cooperativa solicitada; verificar supervisao'
    } finally {
        Close-21E-SessaoCapturaOperacional -Sessao $Sessao
    }
}
