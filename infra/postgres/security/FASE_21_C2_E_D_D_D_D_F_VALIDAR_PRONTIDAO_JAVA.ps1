# FASE 21-C2-E-D-D-D-D-F - diagnostico JDBC Java isolado, READ ONLY.
# Nao invocar ContinuousProcessingMain; nao executar Flyway ou recovery.
param(
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$DiretorioSupervisao = 'C:\dev\rasping-amazon-backups\supervisao',
    [string]$RegistroProdutivo = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'

# Classificacao estritamente permitida: NUNCA exibir stdout/stderr brutos.
# O Java retorna somente o nome da classe da excecao, sem mensagem nem causa.
# Categorias nao equivalem a diagnostico definitivo da causa raiz.
function Classificar-FalhaJDBC-D-F {
    param(
        [AllowEmptyString()][string]$Saida,
        [AllowEmptyString()][string]$Erro,
        [int]$Codigo
    )
    if ($Saida -ceq 'READINESS_JDBC_READ_ONLY_PASS') {
        if ($Erro.Length -gt 0) { return 'STDERR_JVM_NAO_VAZIO' }
        if ($Codigo -ne 0) { return 'EXIT_CODE_INCONSISTENTE' }
        return 'SAIDA_INCONSISTENTE'
    }
    if ($Saida -cmatch '^Readiness failed: (?:PSQLException|SQLException|SQLTimeoutException|SQLNonTransientConnectionException|SQLTransientConnectionException) \(details suppressed\)$') {
        return 'EXCECAO_SQL_JDBC'
    }
    if ($Saida -ceq 'Readiness failed: IllegalStateException (details suppressed)') {
        return 'POLITICA_OU_PRECONDICAO_DO_BANCO'
    }
    if ($Saida -ceq 'Readiness failed: IllegalArgumentException (details suppressed)') {
        return 'CONFIGURACAO_INVALIDA'
    }
    if ($Saida -ceq 'Readiness failed: SecurityException (details suppressed)') {
        return 'ACESSO_RESTRITO'
    }
    if ($Saida -ceq 'Readiness failed: NullPointerException (details suppressed)') {
        return 'FALHA_INTERNA_JAVA'
    }
    if ($Saida -eq '' -and $Erro.Length -gt 0) {
        return 'JVM_SEM_SAIDA_COM_STDERR'
    }
    if ($Saida -eq '' -and $Codigo -ne 0) {
        return 'JVM_TERMINOU_SEM_SAIDA'
    }
    return 'SAIDA_JAVA_NAO_RECONHECIDA'
}

Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
Exigir-PartidaBloqueada-C -Plano $plano
if ($plano.initial_schedule_interval -cne 'PT1H' -or $plano.shutdown_grace_seconds -ne 30) {
    throw 'D-F: plano operacional divergente'
}
if ((Obter-StatusSupervisao-C -CaminhoRegistro $RegistroProdutivo) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-F: daemon produtivo ou registro inesperado'
}
$branch = ((& git branch --show-current 2>$null) | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw 'D-F: branch inesperada'
}
Write-Host '=== C2-E-D-D-D-D-F POLITICA PASS - preparation_only e daemon ausente ==='

$arquivos = @(
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/DaemonStartupPolicyGate.java',
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingReadinessMain.java',
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingReadinessProbe.java',
    'src/test/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingReadinessMainTest.java'
)
foreach ($arquivo in $arquivos) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('D-F: arquivo ausente ' + $arquivo)
    }
}
$main = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($arquivos[1]))
if ($main -match 'ContinuousProcessingBootstrap\s*\.\s*open|ContinuousProcessingApplication\s*\.\s*start|startupRecoveryService\s*\(') {
    throw 'D-F: diagnostico referencia bootstrap ou runtime produtivo'
}
$envExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = if ($envExistia) { (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash } else { $null }
Write-Host '=== C2-E-D-D-D-D-F CONTRATO PASS - entrada diagnostica separada do daemon ==='

& mvn -q '-Dtest=DaemonStartupPolicyGateTest,ContinuousProcessingReadinessMainTest' test
if ($LASTEXITCODE -ne 0) { throw 'D-F: testes Java reprovados' }
Write-Host '=== C2-E-D-D-D-D-F TESTES PASS - guardas e diagnostico isolado ==='

$supervisao = Exigir-CaminhoSupervisao-C-C -Diretorio $DiretorioSupervisao
Criar-DiretorioPrivado-C-C -Diretorio $supervisao
$cpFile = Join-Path $supervisao 'rasping-amazon-readiness-classpath.txt'
& mvn -q '-DincludeScope=runtime' ('-Dmdep.outputFile=' + $cpFile) dependency:build-classpath
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $cpFile -PathType Leaf)) {
    throw 'D-F: classpath nao gerado'
}
$dependencias = [System.IO.File]::ReadAllText($cpFile).Trim()
if ([string]::IsNullOrWhiteSpace($dependencias) -or $dependencias.Contains('"')) {
    throw 'D-F: classpath invalido'
}
$classpath = [System.IO.Path]::GetFullPath('target/classes') + ';' + $dependencias
$java = (Get-Command java.exe -ErrorAction Stop).Source
$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([char[]]@('\','/'))
$segredos = [System.IO.Path]::GetFullPath($DiretorioSegredos)
if ($segredos.StartsWith($raiz + [System.IO.Path]::DirectorySeparatorChar,
    [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'D-F: segredos dentro do repositorio'
}
if (-not (Test-Path -LiteralPath $segredos -PathType Container)) {
    throw 'D-F: credenciais DPAPI indisponiveis'
}

$senha = $null
$processo = $null
try {
    $senha = Ler-SenhaDPAPI-C-C -Caminho (Join-Path $segredos 'rasping_runtime.dpapi')
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '-cp "' + $classpath + '" com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingReadinessMain'
    $psi.WorkingDirectory = $raiz
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $vars = $psi.EnvironmentVariables
    $vars['DB_HOST'] = '127.0.0.1'
    $vars['DB_PORT'] = '5432'
    $vars['DB_NAME'] = 'rasping_amazon'
    $vars['DB_USER'] = 'rasping_runtime'
    $vars['DB_PASSWORD'] = $senha
    $processo = [System.Diagnostics.Process]::Start($psi)
    $vars.Remove('DB_PASSWORD')
    $senha = $null
    if ($null -eq $processo) { throw 'D-F: diagnostico Java nao iniciou' }
    if (-not $processo.WaitForExit(30000)) {
        throw ('D-F: diagnostico excedeu 30 segundos; inspecionar PID ' + $processo.Id + ' sem taskkill automatico')
    }
    $stdout = $processo.StandardOutput.ReadToEnd().Trim()
    $stderr = $processo.StandardError.ReadToEnd().Trim()
    if ($processo.ExitCode -ne 0 -or $stdout -cne 'READINESS_JDBC_READ_ONLY_PASS' -or $stderr.Length -ne 0) {
        $categoria = Classificar-FalhaJDBC-D-F -Saida $stdout -Erro $stderr -Codigo $processo.ExitCode
        $temStderr = if ($stderr.Length -gt 0) { 'SIM' } else { 'NAO' }
        # Somente tokens controlados pelo script. Nao imprimir os fluxos Java.
        Write-Host ('=== C2-E-D-D-D-D-F DIAGNOSTICO SEGURO - CATEGORIA=' + $categoria +
            ' EXIT_CODE=' + [string]$processo.ExitCode + ' STDERR_PRESENTE=' + $temStderr + ' ===')
        throw 'D-F: diagnostico JDBC reprovado; detalhes omitidos para proteger segredos'
    }
} finally {
    $senha = $null
    if ($null -ne $processo) { $processo.Dispose() }
}
Write-Host '=== C2-E-D-D-D-D-F JDBC PASS - transacao read only, role runtime e banco esperados ==='
Write-Host '=== C2-E-D-D-D-D-F BACKLOG PASS - schedule pausado e backlog vazio ==='

if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExistia -or
    ($envExistia -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash)) {
    throw 'D-F: .env alterado'
}
if ((Obter-StatusSupervisao-C -CaminhoRegistro $RegistroProdutivo) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-F: daemon ou registro produtivo apareceu'
}
Write-Host '=== C2-E-D-D-D-D-F ENV PASS - .env e daemon preservados ==='
Write-Host '=== C2-E-D-D-D-D-F GATE PASS - prontidao JDBC Java validada sem bootstrap produtivo ==='
Write-Host '=== C2-E-D-D-D-D-F BLOQUEIO ATIVO - nao iniciar daemon nem autorizar efeitos externos ==='
