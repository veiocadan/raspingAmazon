# FASE 21-C2-E-D-D-D-C-C - launcher de processo Java supervisionado.
# BLOQUEADO enquanto config/operacao/continuous-startup-policy.v1.json estiver preparation_only.
# NAO executar ate fase posterior autorizar explicitamente efeitos externos.
param(
    [switch]$ConfirmarPartida,
    [string]$DiretorioSupervisao = 'C:\dev\rasping-amazon-backups\supervisao',
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_I_FUNCOES_TRANSICAO.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_C_C_POLITICA_FUNCOES.ps1'
Exigir-RaizProjeto-C
Exigir-Arquivos-C-C
$plano = Ler-PlanoSupervisionado-C-C
if (-not $ConfirmarPartida) { throw 'C2-E-D-D-D-C-C: partida exige -ConfirmarPartida' }
# PRIMEIRO bloqueio: na politica atual nenhuma acao de startup ocorre.
Exigir-PartidaAutorizada-C-C -Plano $plano
Exigir-GitPronto-C-C

$dir = Exigir-CaminhoSupervisao-C-C -Diretorio $DiretorioSupervisao
$registro = Join-Path $dir 'rasping-amazon-daemon.v1.json'
$stop = Join-Path $dir 'rasping-amazon-daemon.stop.request'
$reserva = Join-Path $dir 'rasping-amazon-daemon.starting.v1'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'C2-E-D-D-D-C-C: daemon ativo ou registro suspeito; nao iniciar'
}
Exigir-SemSolicitacaoAntiga-C-C -Caminho $stop
Criar-DiretorioPrivado-C-C -Diretorio $dir

$politicaAlertas = Ler-PoliticaAlertasVersionada -Caminho 'config/operacao/alert-policy.v1.json'
$classpathPath = Join-Path $dir 'rasping-amazon-classpath.txt'
# Compilacao ocorre SEM credenciais no ambiente e sem executar testes/migracoes.
& mvn -q '-DskipTests' package
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-D-C-C: build Maven reprovado; daemon nao iniciado' }
& mvn -q '-DincludeScope=runtime' ('-Dmdep.outputFile=' + $classpathPath) dependency:build-classpath
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-D-D-C-C: classpath Maven nao gerado; daemon nao iniciado' }
if (-not (Test-Path -LiteralPath $classpathPath -PathType Leaf)) { throw 'C2-E-D-D-D-C-C: arquivo de classpath ausente' }
$dependencias = [System.IO.File]::ReadAllText($classpathPath).Trim()
$classpath = ([System.IO.Path]::GetFullPath('target/classes') + ';' + $dependencias)
if ($classpath.Contains('"') -or [string]::IsNullOrWhiteSpace($dependencias)) {
    throw 'C2-E-D-D-D-C-C: classpath invalido; daemon nao iniciado'
}

$runtime = $null; $processo = $null
$envs = $null
$registroCriado = $false
$tokenReserva = $null
$inicioTentado = $false
try {
    # Reserva criada atomicamente ANTES de ler DPAPI e ANTES de Start().
    # Se existir reserva de tentativa anterior, falhar fechado para
    # impedir JVM sem registro ou duas partidas simultaneas.
    $tokenReserva = Criar-ReservaDePartida-D-I -Caminho $reserva
    # O daemon usa APENAS a identidade rasping_runtime. A senha de migracao
    # nao e acessada nem enviada ao processo filho.
    $runtime = Ler-SenhaDPAPI-C-C (Join-Path $DiretorioSegredos 'rasping_runtime.dpapi')
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '-cp "' + $classpath + '" com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMain'
    $psi.WorkingDirectory = [System.IO.Path]::GetFullPath((Get-Location).Path)
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $envs = $psi.EnvironmentVariables
    # ProcessStartInfo herda o ambiente do PowerShell: remova quaisquer
    # credenciais/parametros de migracao ANTES da criacao da JVM filha.
    Remover-VariaveisPrivilegiadas-C-C -Variaveis $envs
    $envs['DB_HOST'] = '127.0.0.1'
    $envs['DB_PORT'] = '5432'
    $envs['DB_NAME'] = 'rasping_amazon'
    $envs['DB_USER'] = 'rasping_runtime'
    $envs['DB_PASSWORD'] = $runtime
    $envs['PROCESSING_SCHEDULE_INTERVAL'] = $plano.initial_schedule_interval
    $envs['RASPING_SUPERVISED_STOP_REQUEST_FILE'] = $stop
    foreach ($chave in @('ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD',
        'ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS',
        'ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS',
        'ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION',
        'ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES')) {
        $envs[$chave] = [string]$politicaAlertas[$chave]
    }
    # Senhas estao SOMENTE no ambiente do processo Java filho, nao em argumentos.
    $inicioTentado = $true
    $processo = [System.Diagnostics.Process]::Start($psi)
    if ($null -eq $processo) { throw 'C2-E-D-D-D-C-C: processo Java nao foi criado' }
    $envs.Remove('DB_PASSWORD')
    $runtime = $null

    # Confirmar a identidade antes de gravar PID. A reserva permanece
    # em disco durante esta janela de vulnerabilidade.
    $ticks = Confirmar-ProcessoJavaCriado-D-I -Processo $processo -ClassePrincipal 'com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMain'
    if ($processo.HasExited) { throw 'C2-E-D-D-D-C-C: daemon terminou antes de registrar supervisao' }
    $obj = [ordered]@{ schema_version = 1; pid = [long]$processo.Id;
        creation_utc_ticks = [long]$ticks;
        java_main_class = 'com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMain' }
    $temp = Join-Path $dir ('rasping-amazon-register-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [System.IO.File]::WriteAllText($temp, ($obj | ConvertTo-Json -Compress),[System.Text.Encoding]::UTF8)
        if (Test-Path -LiteralPath $registro) { throw 'C2-E-D-D-D-C-C: registro apareceu durante partida' }
        [System.IO.File]::Move($temp,$registro)
        $registroCriado = $true
        Remover-ReservaPropria-D-I -Caminho $reserva -Token $tokenReserva
        $tokenReserva = $null
    } finally {
        if (Test-Path -LiteralPath $temp) { [System.IO.File]::Delete($temp) }
    }
    Write-Host '=== C2-E-D-D-D-C-C PARTIDA SUPERVISIONADA - daemon identificado; para encerrar usar script PARAR_SUPERVISIONADO ==='
    # A janela supervisionadora espera o termino. Nao reinicia e nao mata Java.
    $processo.WaitForExit()
    if ($processo.ExitCode -ne 0) { throw 'C2-E-D-D-D-C-C: daemon terminou com falha; verificar estado antes de nova partida' }
    Write-Host '=== C2-E-D-D-D-C-C ENCERRAMENTO PASS - daemon terminou normalmente ==='
} finally {
    $runtime = $null
    if ($null -ne $envs) { [void]$envs.Remove('DB_PASSWORD') }
    if ($null -ne $tokenReserva) {
        try {
            if ($registroCriado) {
                # Registro persistido: nao solicitar parada durante
                # um erro secundario de limpeza do marcador.
                Remover-ReservaPropria-D-I -Caminho $reserva -Token $tokenReserva
                Write-Host '=== C2-E-D-D-D-D-I REGISTRO PASS - reserva liberada com PID registrado ==='
            } else {
                $recuperada = Resolver-PartidaNaoRegistrada-D-I -Processo $processo `
                    -InicioTentado $inicioTentado -CaminhoReserva $reserva `
                    -Token $tokenReserva -CaminhoStop $stop `
                    -PrazoSegundos ([int]$plano.shutdown_grace_seconds)
                if ($recuperada) {
                    Write-Host '=== C2-E-D-D-D-D-I RECUPERACAO PASS - sem processo Java nao registrado ==='
                } else {
                    Write-Host '=== C2-E-D-D-D-D-I ATENCAO - reserva retida; inspecao manual obrigatoria; nao reiniciar ==='
                }
            }
        } catch {
            # Nao revelar falha interna, que poderia conter credenciais.
            Write-Host '=== C2-E-D-D-D-D-I ATENCAO - recuperacao inconclusiva; nao reiniciar ==='
        }
    }
    # Nunca remover registro se Java ainda estiver vivo: facilita parada segura em outra janela.
    if ($null -ne $processo -and $processo.HasExited -and $registroCriado) {
        if (Test-Path -LiteralPath $registro -PathType Leaf) {
            try {
                $atual = [System.IO.File]::ReadAllText($registro) | ConvertFrom-Json
                if ([long]$atual.pid -eq [long]$processo.Id -and
                    [long]$atual.creation_utc_ticks -eq [long]$ticks -and
                    [string]$atual.java_main_class -ceq 'com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingMain') {
                    [System.IO.File]::Delete($registro)
                }
            } catch { Write-Host '=== C2-E-D-D-D-C-C ATENCAO - registro exige inspecao manual ===' }
        }
        # Pedido de parada so deve ser removido quando processo original terminou.
        if (-not (Test-Path -LiteralPath $registro -PathType Leaf) -and
            (Test-Path -LiteralPath $stop -PathType Leaf)) { [System.IO.File]::Delete($stop) }
    }
    if ($null -ne $processo) { $processo.Dispose() }
}
