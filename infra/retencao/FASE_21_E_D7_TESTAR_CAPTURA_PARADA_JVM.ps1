# FASE 21-E-D7 - JVM artificial com captura B9/D3, escritor D2 e parada Java real.
# TESTE OFFLINE. Nao importa nem executa ContinuousProcessingMain ou supervisor.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$raiz = (Get-Location).ProviderPath
if ((& git branch --show-current).Trim() -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw '21-E-D7: branch inesperada'
}

foreach ($arquivo in @(
    'infra/retencao/FASE_21_E_A_FUNCOES_RETENCAO.ps1',
    'infra/retencao/FASE_21_E_B1_FUNCOES_SEGMENTACAO.ps1',
    'infra/retencao/FASE_21_E_B3_FUNCOES_COMPATIBILIDADE.ps1',
    'infra/retencao/FASE_21_E_B8_FUNCOES_CATALOGO.ps1',
    'infra/retencao/FASE_21_E_B9_FUNCOES_CAPTURA_CATALOGADA.ps1',
    'infra/retencao/FASE_21_E_D2_FUNCOES_ESCRITOR_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_D3_FUNCOES_PONTE_OFFLINE.ps1',
    'infra/retencao/FASE_21_E_D4_FUNCOES_PARADA_SINTETICA.ps1'
)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('21-E-D7: dependencia ausente: ' + $arquivo)
    }
    . (Resolve-Path -LiteralPath $arquivo -ErrorAction Stop).ProviderPath
}

function Assert-21ED7-Teste {
    param([bool]$Condicao,[string]$Nome)
    if (-not $Condicao) { throw ('21-E-D7: gate reprovado: ' + $Nome) }
}

$catalogo = Assert-21ED4-Contexto -Raiz $raiz
$fonteJava = 'src/test/java/com/raspingamazon/infrastructure/bootstrap/Phase21EJavaCapturedStopProbeMain.java'
$hashFonteEsperado = '6BED213F40C41D9C88E3475423323C9A6210525BC3431D3B939A92C25ACBDE34'
if (-not (Test-Path -LiteralPath $fonteJava -PathType Leaf) -or
    (Get-FileHash -LiteralPath $fonteJava -Algorithm SHA256).Hash -cne $hashFonteEsperado) {
    throw '21-E-D7: fonte Java de teste ausente ou adulterada'
}
$fonteParada = 'src/main/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignal.java'
& git diff --quiet -- $fonteParada
if ($LASTEXITCODE -ne 0) { throw '21-E-D7: fonte de parada Java alterada' }
& git diff --cached --quiet -- $fonteParada
if ($LASTEXITCODE -ne 0) { throw '21-E-D7: fonte de parada Java staged' }

$classe = Join-Path $raiz 'target/test-classes/com/raspingamazon/infrastructure/bootstrap/Phase21EJavaCapturedStopProbeMain.class'
if (-not (Test-Path -LiteralPath $classe -PathType Leaf)) {
    throw '21-E-D7: fixture nao compilada; executar mvnw.cmd -o -q -DskipTests test-compile'
}
$java = (Get-Command java.exe -ErrorAction Stop).Source
$classpath = (Join-Path $raiz 'target/test-classes') + ';' + (Join-Path $raiz 'target/classes')
foreach ($valor in @($java,$classpath,$raiz)) {
    if ($valor.Contains('"')) { throw '21-E-D7: caminho com aspas recusado' }
}
Write-Host 'PASS - Politicas, catalogo, branch e fonte Java D7 intactos.'

# A D2 permite gravar somente no sandbox B1; o diretorio externo nunca e acessado.
$diretorio = New-21EB1-Sandbox -Raiz $raiz
$sessao = New-21ED2-SessaoOffline -Diretorio $diretorio -Catalogo $catalogo `
    -MaximoSegmentoBytes 512 -MaximoTotalBytes 4096
[void](Assert-21ED3-SessaoInicial -Sessao $sessao -Catalogo $catalogo)
$pedido = Join-Path $diretorio 'java-d7-stop.request'
$pronto = Join-Path $diretorio 'java-d7-ready.marker'
foreach ($argumento in @($pedido,$pronto)) {
    if ($argumento.Contains('"')) { throw '21-E-D7: argumento invalido' }
    Assert-21EB1-SemRedirecionamento -Caminho $argumento
}
$processo = New-Object System.Diagnostics.Process
$iniciado = $false
$saidaNatural = $false
try {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '-cp "' + $classpath + '" com.raspingamazon.infrastructure.bootstrap.Phase21EJavaCapturedStopProbeMain "' + $pedido + '" "' + $pronto + '" 12000'
    $psi.WorkingDirectory = $raiz
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false,$true)
    # stderr eh tratado como dado nao confiavel; aceita qualquer byte sem expor texto.
    $psi.StandardErrorEncoding = [System.Text.Encoding]::GetEncoding(28591)
    foreach ($nome in @($psi.EnvironmentVariables.Keys)) {
        if ($nome -match '(?i)(PASSWORD|TOKEN|SECRET|CREDENTIAL|API_KEY|AUTHORIZATION|^DB_|^PG|^AMAZON_|^TELEGRAM_|^WHATSAPP_|^RASPING_|^AWS_|^AZURE_|^GITHUB_|^JAVA_TOOL_OPTIONS$|^JDK_JAVA_OPTIONS$|^_JAVA_OPTIONS$|^CLASSPATH$)') {
            [void]$psi.EnvironmentVariables.Remove($nome)
        }
    }
    $processo.StartInfo = $psi
    $iniciado = $processo.Start()
    if (-not $iniciado) { throw '21-E-D7: fixture JVM nao iniciada' }

    # Iniciar leitura concorrente antes da espera de readiness para evitar bloqueio de pipe.
    $stdout = New-21ED3-Canal -Leitor $processo.StandardOutput -Nome stdout
    $stderr = New-21ED3-Canal -Leitor $processo.StandardError -Nome stderr
    $prazo = [DateTime]::UtcNow.AddSeconds(9)
    $estaPronto = $false
    while ([DateTime]::UtcNow -lt $prazo) {
        if (Test-Path -LiteralPath $pronto -PathType Leaf) {
            $estaPronto = $true
            break
        }
        if ($processo.HasExited) { break }
        [System.Threading.Thread]::Sleep(50)
    }
    Assert-21ED7-Teste ($estaPronto -and -not $processo.HasExited) 'ready-da-jvm'
    Assert-21ED7-Teste (-not (Test-Path -LiteralPath $pedido)) 'sem-pedido-antes-falha'

    $prazo = [DateTime]::UtcNow.AddSeconds(7)
    while ($stdout.Eventos -lt 1 -or $stderr.Eventos -lt 1) {
        if ([DateTime]::UtcNow -ge $prazo) {
            throw '21-E-D7: captura JVM incompleta'
        }
        $tarefas = New-Object 'System.Collections.Generic.List[System.Threading.Tasks.Task]'
        if (-not $stdout.Eof) { $tarefas.Add($stdout.Tarefa) }
        if (-not $stderr.Eof) { $tarefas.Add($stderr.Tarefa) }
        if ($tarefas.Count -eq 0) { throw '21-E-D7: canais terminaram antes das evidencias' }
        $indice = [System.Threading.Tasks.Task]::WaitAny($tarefas.ToArray(),200)
        if ($indice -lt 0) { continue }
        $tarefa = $tarefas[$indice]
        if (-not $stdout.Eof -and [object]::ReferenceEquals($tarefa,$stdout.Tarefa)) {
            Receive-21ED3-Bloco -Canal $stdout -Sessao $sessao -Catalogo $catalogo
        } else {
            Receive-21ED3-Bloco -Canal $stderr -Sessao $sessao -Catalogo $catalogo
        }
    }
    Assert-21ED7-Teste ($stdout.Eventos -eq 1 -and $stderr.Eventos -eq 1 -and
        $stdout.Catalogados -eq 1 -and $stdout.Redigidos -eq 0 -and
        $stderr.Redigidos -eq 1 -and
        $stdout.Pendente.Length -eq 0 -and $stderr.Pendente.Length -eq 0) 'captura-jvm-unica'

    # Falha artificial real do validador D2. O evento malformado NUNCA e persistido.
    $falhou = $false
    try {
        Write-21ED2-EventoCatalogado -Sessao $sessao `
            -LinhaJson '{"timestamp":"2026-10-27T20:00:00Z","secret":"TOKEN_FICTICIO_D7_NAO_PERSISTIR"}'
    } catch { $falhou = $true }
    Assert-21ED7-Teste ($falhou -and $sessao.Lacrada) 'lacre-do-escritor'
    Assert-21EB1-SemRedirecionamento -Caminho $pedido
    # Criacao exclusiva: nunca reutilizar pedido de outra sessao ou sobrescrever.
    $fluxoPedido = [System.IO.FileStream]::new($pedido,
        [System.IO.FileMode]::CreateNew,[System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None)
    $fluxoPedido.Dispose()
    Assert-21ED7-Teste (Test-Path -LiteralPath $pedido -PathType Leaf) 'pedido-cooperativo-criado'
    if (-not $processo.WaitForExit(15000)) {
        throw '21-E-D7: JVM nao encerrou; inspecao manual obrigatoria'
    }
    Assert-21ED7-Teste ($processo.ExitCode -eq 0) 'encerramento-cooperativo-java'
    $saidaNatural = $true

    # Depois do lacre so e admitido EOF. Dados extras seriam uma falha fechada.
    foreach ($canal in @($stdout,$stderr)) {
        if ($canal.Eof) { continue }
        $restante = $canal.Tarefa.GetAwaiter().GetResult()
        Assert-21ED7-Teste ($restante -eq 0) 'nenhum-dado-apos-lacre'
    }

    $segmentos = @(Get-ChildItem -LiteralPath $diretorio -File -Filter 'operacional-*.jsonl' -ErrorAction Stop | Sort-Object Name)
    $linhas = @($segmentos | ForEach-Object { Get-Content -LiteralPath $_.FullName -Encoding UTF8 -ErrorAction Stop })
    Assert-21ED7-Teste ($linhas.Count -eq 2 -and $segmentos.Count -ge 1) 'dois-registros-seguros'
    $objetos = @($linhas | ForEach-Object { ConvertFrom-Json -InputObject $_ -ErrorAction Stop })
    $reais = @($objetos | Where-Object { $_.event -ceq 'processing.job.claimed' })
    $marcadores = @($objetos | Where-Object { $_.event -ceq 'runtime.stderr.redacted' })
    Assert-21ED7-Teste ($reais.Count -eq 1 -and $marcadores.Count -eq 1) 'catalogo-e-redacao'
    Assert-21ED7-Teste ($reais[0].runId -eq 7 -and $reais[0].jobId -eq 11) 'correlacao'
    Assert-21ED7-Teste (-not (($linhas -join "`n") -match 'TOKEN_FICTICIO_D7|secret|stack.trace')) 'sem-texto-bruto'
    Assert-21ED7-Teste ($sessao.TotalBytes -eq [long](($segmentos | Measure-Object -Property Length -Sum).Sum)) 'integridade-bytes'
    $rejeitouReuso = $false
    try { Write-21ED2-EventoCatalogado -Sessao $sessao -LinhaJson $linhas[0] }
    catch { $rejeitouReuso = $true }
    Assert-21ED7-Teste $rejeitouReuso 'lacre-irreversivel'

    Write-Host 'PASS - JVM fixture emitiu 1 evento catalogado e 1 stderr redigido na mesma captura.'
    Write-Host 'PASS - Escritor D2 lacrado por falha artificial; evidencias anteriores preservadas.'
    Write-Host 'PASS - SupervisedStopSignal reconheceu pedido privado; mesma JVM saiu com codigo 0.'
    Write-Host 'PASS - Fluxos drenados sem conteudo extra; segredo ficticio ausente dos segmentos.'
    Write-Host 'PASS - Regravacao apos lacre rejeitada, sem substituicao de historico.'
} finally {
    $pendente = $false
    try {
        if ($iniciado -and -not $processo.HasExited) {
            # A fixture Java termina sozinha em ate 12s; nunca Kill/taskkill.
            $pendente = -not $processo.WaitForExit(16000)
        }
    } finally {
        try { Close-21ED2-Sessao -Sessao $sessao }
        finally { $processo.Dispose() }
    }
    if ($pendente) {
        throw '21-E-D7: JVM fixture pendente; investigar sem iniciar outro processo'
    }
}

Assert-21ED7-Teste $saidaNatural 'sucesso-final'
Write-Host 'PROTEGIDO - Apenas JVM fixture em target, sem ContinuousProcessingMain, supervisor, JDBC, rede ou backups.'
Write-Host 'PROTEGIDO - Nenhum Kill, taskkill, exclusao, commit ou captura externa.'
Write-Host 'PASS - Gate 21-E-D7 JVM offline aprovado; integracao produtiva continua BLOQUEADA.'
