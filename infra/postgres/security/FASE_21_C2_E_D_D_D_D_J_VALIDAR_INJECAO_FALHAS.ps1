# FASE 21-C2-E-D-D-D-D-J - injecao de falha, somente com JVM de teste.
# Nao e launcher produtivo. Nao le DPAPI, nao conecta JDBC e nao cria jobs.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_I_FUNCOES_TRANSICAO.ps1'
Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
Exigir-PartidaBloqueada-C -Plano $plano
if ($plano.execution_mode -cne 'preparation_only' -or
    $plano.daemon_start_authorized -or $plano.flyway_migrate_authorized -or
    $plano.external_actions_authorized -or $plano.automatic_restart_authorized -or
    $plano.shutdown_grace_seconds -ne 30) {
    throw 'D-J: politica produtiva divergente; abortar teste isolado'
}
$registroReal = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registroReal) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-J: daemon ou registro produtivo encontrado; abortar'
}
Write-Host '=== C2-E-D-D-D-D-J POLITICA PASS - daemon produtivo bloqueado e ausente ==='

$fonteSmoke = 'src/test/java/com/raspingamazon/infrastructure/bootstrap/SupervisedLifecycleSmokeMain.java'
$fonteStop = 'src/main/java/com/raspingamazon/infrastructure/bootstrap/SupervisedStopSignal.java'
$launcher = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1'
foreach ($arquivo in @($fonteSmoke,$fonteStop,$launcher)) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) { throw 'D-J: fonte ou launcher necessario ausente' }
}
$javaFonte = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($fonteSmoke))
if ($javaFonte -notmatch 'class\s+SupervisedLifecycleSmokeMain' -or
    $javaFonte -notmatch 'SupervisedStopSignal[.]startIfConfigured' -or
    $javaFonte -match 'ContinuousProcessingBootstrap[.]open|ContinuousProcessingMain[.]main|Flyway[.]migrate|ProcessBuilder|DatabaseConnection[.]open') {
    throw 'D-J: fonte da JVM isolada descumpre contrato de ausencia de efeitos externos'
}
$launcherFonte = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($launcher))
if ($launcherFonte -notmatch 'Resolver-PartidaNaoRegistrada-D-I\s+-Processo\s+\$processo' -or
    $launcherFonte -match 'Resolver-PartidaNaoRegistrada-D-I\s+-Processo\s+\$processo[\s\S]{0,180}-ClassePrincipal' -or
    $launcherFonte -notmatch 'Exigir-PartidaAutorizada-C-C') {
    throw 'D-J: launcher de producao nao utiliza identidade padrao ou politica obrigatoria'
}
Write-Host '=== C2-E-D-D-D-D-J CONTRATO PASS - smoke Java isolado e classe produtiva obrigatoria ==='

$envExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = if ($envExistia) { (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash } else { $null }
& mvn -B -q '-DskipTests' test-compile
if ($LASTEXITCODE -ne 0) { throw 'D-J: compilacao da classe de teste falhou' }
Write-Host '=== C2-E-D-D-D-D-J COMPILACAO PASS - test-compile sem executar daemon ==='

$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path)
$classes = [System.IO.Path]::GetFullPath('target/classes')
$testClasses = [System.IO.Path]::GetFullPath('target/test-classes')
if (-not (Test-Path -LiteralPath $classes -PathType Container) -or
    -not (Test-Path -LiteralPath $testClasses -PathType Container) -or
    ($classes + $testClasses).Contains('"')) { throw 'D-J: classes compiladas invalidas' }
$java = (Get-Command java.exe -ErrorAction Stop).Source
$classeSmoke = 'com.raspingamazon.infrastructure.bootstrap.SupervisedLifecycleSmokeMain'
$temporarios = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$dir = Join-Path $temporarios ('rasping-amazon-d-j-' + [guid]::NewGuid().ToString('N'))
if ($dir.StartsWith($raiz.TrimEnd([char[]]@('\','/')) + [System.IO.Path]::DirectorySeparatorChar,
    [System.StringComparison]::OrdinalIgnoreCase)) { throw 'D-J: pasta isolada dentro do repositorio' }
$processo = $null
$tentativaConcluida = $false
$processoTerminou = $true
$reserva = Join-Path $dir 'isolado.starting.v1'
$stop = Join-Path $dir 'isolado.stop.request'
$ready = Join-Path $dir 'isolado.ready'
try {
    Criar-DiretorioPrivado-C-C -Diretorio $dir

    # Caso 1: falha anterior ao Start(). A propria reserva pode ser retirada.
    $token = Criar-ReservaDePartida-D-I -Caminho $reserva
    $limpo = Resolver-PartidaNaoRegistrada-D-I -Processo $null -InicioTentado $false `
        -CaminhoReserva $reserva -Token $token -CaminhoStop $stop -PrazoSegundos 30
    if (-not $limpo -or (Test-Path -LiteralPath $reserva) -or (Test-Path -LiteralPath $stop)) {
        throw 'D-J: falha antes de Start nao limpou exclusivamente sua propria reserva'
    }
    Write-Host '=== C2-E-D-D-D-D-J PRESTART PASS - falha antes da JVM libera reserva ==='

    # Caso 2: Start pode ter ocorrido, mas nao devolveu handle. Reserva retida.
    $token = Criar-ReservaDePartida-D-I -Caminho $reserva
    $limpo = Resolver-PartidaNaoRegistrada-D-I -Processo $null -InicioTentado $true `
        -CaminhoReserva $reserva -Token $token -CaminhoStop $stop -PrazoSegundos 30
    if ($limpo -or -not (Test-Path -LiteralPath $reserva) -or
        (Test-Path -LiteralPath $stop)) { throw 'D-J: tentativa sem handle liberou reserva insegura' }
    Remover-ReservaPropria-D-I -Caminho $reserva -Token $token
    Write-Host '=== C2-E-D-D-D-D-J SEM_HANDLE PASS - tentativa inconclusiva mantem reserva ==='

    # Caso 3: Start concluido, registro nao gravado. Classe de teste sem
    # banco/segredos, com ambiente filho explicitamente reduzido.
    $token = Criar-ReservaDePartida-D-I -Caminho $reserva
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '-cp "' + $classes + ';' + $testClasses + '" ' + $classeSmoke
    $psi.WorkingDirectory = $raiz
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $envs = $psi.EnvironmentVariables
    $envs.Clear()
    $envs['SystemRoot'] = [string]$env:SystemRoot
    $envs['WINDIR'] = [string]$env:SystemRoot
    $envs['TEMP'] = $dir
    $envs['TMP'] = $dir
    $envs['PATH'] = (Split-Path -Parent $java) + ';' + (Join-Path $env:SystemRoot 'System32')
    $envs['RASPING_SUPERVISED_STOP_REQUEST_FILE'] = $stop
    $envs['RASPING_SUPERVISED_SMOKE_READY_FILE'] = $ready
    $processo = [System.Diagnostics.Process]::Start($psi)
    if ($null -eq $processo) { throw 'D-J: JVM de teste nao foi criada' }

    $limite = [DateTime]::UtcNow.AddSeconds(15)
    while (-not (Test-Path -LiteralPath $ready -PathType Leaf)) {
        if ($processo.HasExited) { throw 'D-J: smoke Java terminou antes da prontidao' }
        if ([DateTime]::UtcNow -gt $limite) { throw 'D-J: smoke Java nao sinalizou prontidao' }
        Start-Sleep -Milliseconds 100
    }
    $ticks = Confirmar-ProcessoJavaCriado-D-I -Processo $processo -ClassePrincipal $classeSmoke
    if ($ticks -le 0) { throw 'D-J: identidade da JVM de teste nao comprovada' }
    Write-Host '=== C2-E-D-D-D-D-J IDENTIDADE PASS - PID, classe e hora de criacao comprovados ==='

    # Caso 4: processo correto, mas classe do daemon de producao NAO bate.
    # A funcao deve falhar FECHADO sem criar stop ou retirar reserva.
    $limpo = Resolver-PartidaNaoRegistrada-D-I -Processo $processo -InicioTentado $true `
        -CaminhoReserva $reserva -Token $token -CaminhoStop $stop -PrazoSegundos 30
    if ($limpo -or -not (Test-Path -LiteralPath $reserva) -or
        (Test-Path -LiteralPath $stop) -or $processo.HasExited) {
        throw 'D-J: identidade diferente produziu falsa recuperacao'
    }
    Write-Host '=== C2-E-D-D-D-D-J IDENTIDADE_NEGATIVA PASS - recusa sinal para classe diferente ==='

    # Caso 5: injetar falha de registro na JVM de teste ja pronta. A
    # recuperacao coopera somente apos confirmar a classe do smoke.
    $limpo = Resolver-PartidaNaoRegistrada-D-I -Processo $processo -InicioTentado $true `
        -CaminhoReserva $reserva -Token $token -CaminhoStop $stop -PrazoSegundos 30 `
        -ClassePrincipal $classeSmoke
    if (-not $limpo -or -not $processo.HasExited -or $processo.ExitCode -ne 0 -or
        (Test-Path -LiteralPath $reserva) -or (Test-Path -LiteralPath $stop)) {
        throw 'D-J: recuperacao cooperativa da JVM isolada nao terminou corretamente'
    }
    Write-Host '=== C2-E-D-D-D-D-J RECUPERACAO PASS - JVM isolada encerrou sem taskkill; reserva retirada ==='

    # Caso 6: identificador original de processo ja finalizado.
    $token = Criar-ReservaDePartida-D-I -Caminho $reserva
    $limpo = Resolver-PartidaNaoRegistrada-D-I -Processo $processo -InicioTentado $true `
        -CaminhoReserva $reserva -Token $token -CaminhoStop $stop -PrazoSegundos 30
    if (-not $limpo -or (Test-Path -LiteralPath $reserva) -or
        (Test-Path -LiteralPath $stop)) { throw 'D-J: reserva nao foi limpa apos termino comprovado' }
    Write-Host '=== C2-E-D-D-D-D-J FINALIZADO PASS - processo ja encerrado libera reserva ==='
    $tentativaConcluida = $true
} finally {
    if ($null -ne $processo) {
        if (-not $processo.HasExited) {
            # Encerrar SOMENTE a JVM de teste por parada cooperativa.
            if (-not (Test-Path -LiteralPath $stop -PathType Leaf)) {
                try {
                    [System.IO.File]::WriteAllText($stop,'STOP',[System.Text.Encoding]::ASCII)
                } catch { }
            }
            [void]$processo.WaitForExit(10000)
        }
        $processoTerminou = $processo.HasExited
        if (-not $processoTerminou) {
            Write-Host '=== C2-E-D-D-D-D-J ALERTA - JVM DE TESTE ainda viva, pasta preservada; inspecao manual ==='
        }
        $processo.Dispose()
    }
    if ($processoTerminou -and (Test-Path -LiteralPath $dir -PathType Container)) {
        [System.IO.Directory]::Delete($dir,$true)
    }
}
if (-not $tentativaConcluida) { throw 'D-J: injecao isolada incompleta' }
if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExistia -or
    ($envExistia -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash) -or
    (Obter-StatusSupervisao-C -CaminhoRegistro $registroReal) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-J: ambiente produtivo alterado ou daemon inesperado'
}
Write-Host '=== C2-E-D-D-D-D-J ENV PASS - .env e registro produtivo preservados ==='
Write-Host '=== C2-E-D-D-D-D-J GATE PASS - falhas entre Start e PID verificadas em JVM isolada ==='
Write-Host '=== C2-E-D-D-D-D-J BLOQUEIO ATIVO - nao iniciar daemon nem efeitos externos ==='
