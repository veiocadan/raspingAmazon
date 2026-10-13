# FASE 21-C2-E-D-D-D-D-A - smoke supervisionado sem daemon produtivo.
# Java de teste isolado; nao acessa PostgreSQL, rede, segredos nem configuracao .env.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'

Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
if ($plano.execution_mode -cne 'preparation_only' -or
    $plano.daemon_start_authorized -or $plano.flyway_migrate_authorized -or
    $plano.external_actions_authorized -or $plano.automatic_restart_authorized) {
    throw 'D-A: configuracao deve estar integralmente bloqueada; nao realizar teste'
}
Write-Host '=== C2-E-D-D-D-D-A POLITICA PASS - partida produtiva e efeitos externos bloqueados ==='

$registroReal = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registroReal) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-A: existe daemon ou registro real; nao realizar teste isolado'
}
Write-Host '=== C2-E-D-D-D-D-A DAEMON PASS - nenhum processo produtivo detectado ==='

$branch = ((& git branch --show-current 2>$null) | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw 'D-A: branch inesperada; nao prosseguir'
}
$javaFonte = 'src/test/java/com/raspingamazon/infrastructure/bootstrap/SupervisedLifecycleSmokeMain.java'
if (-not (Test-Path -LiteralPath $javaFonte -PathType Leaf)) {
    throw 'D-A: arquivo Java de smoke ausente'
}
$fonte = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($javaFonte))
if ($fonte -notmatch 'class\s+SupervisedLifecycleSmokeMain' -or
    $fonte -notmatch 'SupervisedStopSignal[.]startIfConfigured' -or
    $fonte -match 'ContinuousProcessingBootstrap[.]open|ContinuousProcessingMain[.]main|Flyway[.]migrate|ProcessBuilder') {
    throw 'D-A: contrato do processo isolado invalido; nao executar Java'
}
Write-Host '=== C2-E-D-D-D-D-A CONTRATO PASS - somente processo Java de teste, sem bootstrap ==='

$envExiste = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = $null
if ($envExiste) { $envHash = (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash }

# Compila sem executar testes, nem scripts de migracao.
& mvn -B -q '-DskipTests' test-compile
if ($LASTEXITCODE -ne 0) { throw 'D-A: compilacao Maven reprovada; nenhum smoke iniciado' }
Write-Host '=== C2-E-D-D-D-D-A COMPILACAO PASS - classe de smoke compilada, testes nao executados ==='

$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([char[]]@('\','/'))
$dir = Join-Path $tempBase ('rasping-amazon-supervisor-smoke-' + [guid]::NewGuid().ToString('N'))
$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([char[]]@('\','/'))
if ($dir.StartsWith($raiz + [System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'D-A: diretorio temporario inesperadamente dentro do repositorio'
}
$processo = $null
$pronto = Join-Path $dir 'smoke.ready'
$stop = Join-Path $dir 'smoke.stop.request'
$testeConcluido = $false
$processoTerminou = $true
try {
    Criar-DiretorioPrivado-C-C -Diretorio $dir
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $classes = [System.IO.Path]::GetFullPath('target/classes')
    $testClasses = [System.IO.Path]::GetFullPath('target/test-classes')
    if (-not (Test-Path -LiteralPath $classes -PathType Container) -or
        -not (Test-Path -LiteralPath $testClasses -PathType Container) -or
        ($classes + $testClasses).Contains('"')) {
        throw 'D-A: classpath do smoke invalido'
    }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '-cp "' + $classes + ';' + $testClasses + '" com.raspingamazon.infrastructure.bootstrap.SupervisedLifecycleSmokeMain'
    $psi.WorkingDirectory = $raiz
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    # Ambiente permitido reduzido; nenhuma credencial ou variavel DB_* herdada.
    $envs = $psi.EnvironmentVariables
    $envs.Clear()
    $envs['SystemRoot'] = [string]$env:SystemRoot
    $envs['WINDIR'] = [string]$env:SystemRoot
    $envs['TEMP'] = $dir
    $envs['TMP'] = $dir
    $envs['PATH'] = (Split-Path -Parent $java) + ';' + (Join-Path $env:SystemRoot 'System32')
    $envs['RASPING_SUPERVISED_STOP_REQUEST_FILE'] = $stop
    $envs['RASPING_SUPERVISED_SMOKE_READY_FILE'] = $pronto
    $processo = [System.Diagnostics.Process]::Start($psi)
    if ($null -eq $processo) { throw 'D-A: falha ao criar processo de smoke isolado' }

    $limite = [DateTime]::UtcNow.AddSeconds(15)
    while (-not (Test-Path -LiteralPath $pronto -PathType Leaf)) {
        if ($processo.HasExited) { throw 'D-A: Java isolado terminou antes da prontidao' }
        if ([DateTime]::UtcNow -gt $limite) { throw 'D-A: prontidao Java nao confirmada' }
        Start-Sleep -Milliseconds 100
    }
    $cim = Get-CimInstance Win32_Process -Filter ('ProcessId = ' + $processo.Id) -ErrorAction Stop
    if ($null -eq $cim -or [string]$cim.CommandLine -notmatch 'com[.]raspingamazon[.]infrastructure[.]bootstrap[.]SupervisedLifecycleSmokeMain' -or
        [string]$cim.CommandLine -match 'ContinuousProcessingMain' -or $processo.HasExited) {
        throw 'D-A: identidade exclusiva do processo de teste nao comprovada'
    }
    $ticks = ([datetime]$cim.CreationDate).ToUniversalTime().Ticks
    if ($ticks -le 0 -or [int]$cim.ProcessId -ne [int]$processo.Id) {
        throw 'D-A: identidade PID e criacao do smoke nao validada'
    }
    Write-Host '=== C2-E-D-D-D-D-A PROCESSO PASS - PID e criacao do Java isolado comprovados ==='

    $file = [System.IO.File]::Open($stop,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None)
    try {
        $bytes = [System.Text.Encoding]::ASCII.GetBytes('STOP')
        $file.Write($bytes,0,$bytes.Length)
    } finally { $file.Dispose() }
    Write-Host '=== C2-E-D-D-D-D-A SINAL PASS - parada criada exclusivamente para processo de teste ==='

    if (-not $processo.WaitForExit(30000)) { throw 'D-A: processo isolado nao encerrou no prazo; nao executar partida real' }
    if ($processo.ExitCode -ne 0) { throw 'D-A: processo isolado encerrou com falha' }
    $testeConcluido = $true
    Write-Host '=== C2-E-D-D-D-D-A PARADA PASS - JVM de teste encerrou sem taskkill ==='
} finally {
    if ($null -ne $processo) {
        if (-not $processo.HasExited) {
            # Somente o pedido cooperativo do processo de teste pode ser criado.
            if ((Test-Path -LiteralPath $dir -PathType Container) -and
                -not (Test-Path -LiteralPath $stop)) {
                try { [System.IO.File]::WriteAllText($stop,'STOP',[System.Text.Encoding]::ASCII) } catch { }
            }
            [void]$processo.WaitForExit(10000)
        }
        if (-not $processo.HasExited) {
            Write-Host '=== C2-E-D-D-D-D-A ALERTA - processo de smoke ainda vivo; diretorio de teste preservado ==='
        }
        $processoTerminou = $processo.HasExited
        $processo.Dispose()
    }
    if ($processoTerminou -and (Test-Path -LiteralPath $dir -PathType Container)) {
        [System.IO.Directory]::Delete($dir,$true)
    }
}

if (-not $testeConcluido) { throw 'D-A: smoke nao concluido' }
if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExiste) {
    throw 'D-A: existencia do arquivo .env mudou'
}
if ($envExiste -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash) {
    throw 'D-A: hash do arquivo .env mudou'
}
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registroReal) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-A: processo ou registro produtivo presente apos smoke; investigar'
}
Write-Host '=== C2-E-D-D-D-D-A ENV PASS - .env e registro produtivo preservados ==='
Write-Host '=== C2-E-D-D-D-D-A GATE PASS - ciclo supervisionado isolado comprovado ==='
Write-Host '=== C2-E-D-D-D-D-A BLOQUEIO ATIVO - ainda nao iniciar o daemon produtivo ==='
