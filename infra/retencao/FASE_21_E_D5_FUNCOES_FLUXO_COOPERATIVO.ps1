# FASE 21-E-D5: captura catalogada e parada cooperativa no MESMO filho sintetico.
# Importar nao inicia processos, nao cria arquivos e nao altera autorizacoes.
Set-StrictMode -Version Latest

function Invoke-21ED5-CapturaComParadaOffline {
    param(
        [Parameter(Mandatory=$true)][object]$Sessao,
        [Parameter(Mandatory=$true)][object]$Catalogo
    )
    # Esta funcao nao aceita executavel, classe Java, PID, diretorio externo,
    # codigo livre de filho ou parametros que habilitem operacao real.
    [void](Assert-21ED4-Contexto -Raiz (Get-Location).ProviderPath)
    [void](Assert-21ED3-SessaoInicial -Sessao $Sessao -Catalogo $Catalogo)
    $dir = Assert-21ED2-DiretorioSandbox -Diretorio $Sessao.Diretorio
    $def = @($Catalogo.events | Where-Object { $_.code -ceq 'processing.job.claimed' })
    if ($def.Count -ne 1) { throw '21-E-D5: evento de referencia ausente' }
    $linha = ConvertTo-Json -Compress -InputObject ([ordered]@{
        timestamp=[DateTimeOffset]::UtcNow.ToString('o');
        level=$def[0].level;event=$def[0].code;
        component=$def[0].component;operation=$def[0].operation;
        outcome='CLAIMED';runId=[long]7;jobId=[long]11
    })
    # O filho sempre expira sozinho; seu prazo finito evita processos sem dono.
    # Toda a saida eh inventada e nenhum dado operacional real eh acessado.
    $codigo = @'
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::Out.WriteLine('__JSON__')
[Console]::Out.Flush()
[Console]::Error.WriteLine('TOKEN_FICTICIO_D5_NAO_PERSISTIR')
[Console]::Error.Flush()
$pronto = '__PRONTO__'
$sinal = '__SINAL__'
$marca = [System.IO.FileStream]::new($pronto,[System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,[System.IO.FileShare]::None)
$marca.Dispose()
$fim = [DateTime]::UtcNow.AddSeconds(12)
while ([DateTime]::UtcNow -lt $fim) {
    if (Test-Path -LiteralPath $sinal) {
        $item = Get-Item -LiteralPath $sinal -Force -ErrorAction Stop
        if ($item.PSIsContainer -or ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { exit 76 }
        exit 0
    }
    [System.Threading.Thread]::Sleep(100)
}
exit 75
'@
    $pronto = Join-Path $dir 'filho-d4-pronto.marker'
    $sinal = Join-Path $dir 'parada-sintetica-d4.request'
    foreach ($valor in @($linha,$pronto,$sinal)) {
        if ($valor.Contains("'")) { throw '21-E-D5: valor inesperado no filho sintetico' }
    }
    $codigo = $codigo.Replace('__JSON__',$linha).Replace('__PRONTO__',$pronto).Replace('__SINAL__',$sinal)
    $exe = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw '21-E-D5: PowerShell indisponivel' }
    $processo = New-Object System.Diagnostics.Process
    $iniciado = $false
    $concluido = $false
    try {
        $arquivo = New-21EB9-ArquivoFilhoSintetico -CodigoFilho $codigo `
            -SessaoStdout ([pscustomobject]@{ Diretorio = $dir })
        if ($arquivo.Contains('"')) { throw '21-E-D5: caminho do filho invalido' }
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $exe
        $psi.Arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -OutputFormat Text -File "' + $arquivo + '"'
        $psi.WorkingDirectory = (Get-Location).ProviderPath
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $psi.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false,$true)
        $psi.StandardErrorEncoding = [System.Text.Encoding]::GetEncoding(28591)
        foreach ($chave in @($psi.EnvironmentVariables.Keys)) {
            if ($chave -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_)') {
                [void]$psi.EnvironmentVariables.Remove($chave)
            }
        }
        $processo.StartInfo = $psi
        $iniciado = $processo.Start()
        if (-not $iniciado) { throw '21-E-D5: filho sintetico nao iniciou' }
        Wait-21ED4-FilhoPronto -Processo $processo -Diretorio $dir -PrazoSegundos 7
        $out = New-21ED3-Canal -Leitor $processo.StandardOutput -Nome stdout
        $err = New-21ED3-Canal -Leitor $processo.StandardError -Nome stderr
        $limite = [DateTime]::UtcNow.AddSeconds(8)
        while ($out.Eventos -lt 1 -or $err.Eventos -lt 1) {
            if ([DateTime]::UtcNow -ge $limite) { throw '21-E-D5: captura sintetica incompleta' }
            $tarefas = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'
            if (-not $out.Eof) { $tarefas.Add($out.Tarefa) }
            if (-not $err.Eof) { $tarefas.Add($err.Tarefa) }
            if ($tarefas.Count -eq 0) { throw '21-E-D5: canais terminaram precocemente' }
            $indice = [System.Threading.Tasks.Task]::WaitAny($tarefas.ToArray(),200)
            if ($indice -lt 0) { continue }
            $tarefa = $tarefas[$indice]
            if (-not $out.Eof -and [object]::ReferenceEquals($tarefa,$out.Tarefa)) {
                Receive-21ED3-Bloco -Canal $out -Sessao $Sessao -Catalogo $Catalogo -MaximoLinhaCaracteres 32768
            } else {
                Receive-21ED3-Bloco -Canal $err -Sessao $Sessao -Catalogo $Catalogo -MaximoLinhaCaracteres 32768
            }
        }
        if ($out.Eventos -ne 1 -or $err.Eventos -ne 1 -or
            $out.Catalogados -ne 1 -or $err.Redigidos -ne 1) {
            throw '21-E-D5: contadores inesperados'
        }
        if (Test-Path -LiteralPath $sinal) { throw '21-E-D5: parada inesperada antes da falha' }
        # Falha sintetica intencional na validacao do escritor D2. Nenhum
        # segredo aparece no erro ou nos segmentos persistidos.
        $falha = $false
        try {
            Write-21ED2-EventoCatalogado -Sessao $Sessao `
                -LinhaJson '{"timestamp":"2026-10-27T20:00:00Z","secret":"TOKEN_FICTICIO_D5_NAO_PERSISTIR"}'
        } catch { $falha = $true }
        if (-not $falha -or -not $Sessao.Lacrada) {
            throw '21-E-D5: falha do escritor nao lacrou sessao'
        }
        $pedido = New-21ED4-PedidoParada -Sessao $Sessao
        if (-not (Test-Path -LiteralPath $pedido -PathType Leaf)) {
            throw '21-E-D5: sinal nao criado'
        }
        $codigoSaida = Wait-21ED4-EncerramentoNatural -Processo $processo -PrazoSegundos 6
        if ($codigoSaida -ne 0) { throw '21-E-D5: filho nao parou cooperativamente' }
        $concluido = $true
        return [pscustomobject]@{
            Diretorio = $dir
            StdoutEventos = [int]$out.Eventos
            StderrEventos = [int]$err.Eventos
            Catalogados = [int]$out.Catalogados
            Marcadores = [int]$err.Redigidos
            CodigoSaida = [int]$codigoSaida
            TotalBytes = [long]$Sessao.TotalBytes
            Lacrada = [bool]$Sessao.Lacrada
            Pedido = $pedido
        }
    } catch {
        # Nao revelar exceptions originais, mensagens do filho nem conteudo bruto.
        throw '21-E-D5: captura e parada sinteticas reprovadas; conferir evidencias privadas'
    } finally {
        $pendente = $false
        try {
            if ($iniciado -and -not $processo.HasExited) {
                # Filho com prazo autonomo de 12s. Nunca executar Kill/taskkill.
                $pendente = -not $processo.WaitForExit(15000)
            }
        } finally {
            try { Close-21ED2-Sessao -Sessao $Sessao }
            finally { $processo.Dispose() }
        }
        if ($pendente) { throw '21-E-D5: processo sintetico nao terminou; inspecao manual obrigatoria' }
    }
}
