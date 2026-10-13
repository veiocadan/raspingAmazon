# FASE 21-C2-E-D-D-D-D-D - gate passivo antes de qualquer inicializacao do daemon.
# Nao escreve no PostgreSQL, nao inicia o Java produtivo e nao autoriza efeitos externos.
param(
    [string]$DiretorioSegredos = 'C:\dev\rasping-amazon-backups\credenciais_c2edcb',
    [string]$NomeContainer = 'rasping-amazon-postgres',
    [string]$RegistroProdutivo = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'

Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
Exigir-PartidaBloqueada-C -Plano $plano
if ($plano.initial_schedule_interval -cne 'PT1H') {
    throw 'D-D: cadencia diferente da aprovacao de 1 hora'
}
if ((Obter-StatusSupervisao-C -CaminhoRegistro $RegistroProdutivo) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-D: daemon produtivo ou registro de supervisao detectado'
}
$branch = ((& git branch --show-current 2>$null) | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw 'D-D: branch inesperada'
}
$modificacoes = @(& git diff --name-only HEAD)
if ($LASTEXITCODE -ne 0 -or $modificacoes.Count -ne 0) {
    throw 'D-D: modificacoes rastreadas presentes; interromper'
}
Write-Host '=== C2-E-D-D-D-D-D POLITICA PASS - quatro bloqueios ativos e daemon ausente ==='

$envExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = if ($envExistia) { (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash } else { $null }
foreach ($nome in @('PROCESSING_SCHEDULE_KEY', 'PROCESSING_SOURCE_URI')) {
    if (-not [string]::IsNullOrWhiteSpace([System.Environment]::GetEnvironmentVariable($nome, 'Process'))) {
        throw ('D-D: override de identidade no ambiente de processo: ' + $nome)
    }
    if ($envExistia) {
        foreach ($linha in [System.IO.File]::ReadAllLines([System.IO.Path]::GetFullPath('.env'))) {
            if ($linha -match ('^\s*' + [regex]::Escape($nome) + '\s*=')) {
                throw ('D-D: override de identidade no arquivo .env: ' + $nome)
            }
        }
    }
}

$fontes = @(
    'src/main/java/com/raspingamazon/application/scheduling/EnsureProcessingScheduleUseCase.java',
    'src/main/java/com/raspingamazon/infrastructure/persistence/adapter/JdbcProcessingScheduleAdapter.java',
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingBootstrap.java',
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingMain.java',
    'src/main/java/com/raspingamazon/infrastructure/config/ContinuousProcessingEnvironmentConfigProvider.java',
    'src/test/java/com/raspingamazon/application/scheduling/EnsureProcessingSchedulePausedStartupTest.java',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_D_B_EXECUTAR_INVENTARIO.ps1',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_D_D_CONSULTAR_SCHEDULE.sh',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_D_D_VERIFICAR_SCHEDULE_READONLY.sql'
)
foreach ($arquivo in $fontes) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('D-D: arquivo obrigatorio ausente: ' + $arquivo)
    }
}
$useCase = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($fontes[0]))
$adapter = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($fontes[1]))
$bootstrap = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($fontes[2]))
$main = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($fontes[3]))
$provider = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($fontes[4]))
if ($useCase -notmatch '(?s)return\s+schedulePort\s*\.saveIfAbsent\s*\(' -or
    $adapter -notmatch '(?s)ON\s+CONFLICT\s*\(schedule_key\)\s*DO\s+NOTHING' -or
    $adapter -notmatch 'validateExistingIdentity\s*\(' -or
    $bootstrap -notmatch 'ensureProcessingScheduleUseCase\s*\.execute\s*\(' -or
    $bootstrap -notmatch '(?s)startupRecoveryService\s*\(\s*\)\s*\.recover\s*\(' -or
    $main -notmatch '(?s)ContinuousProcessingBootstrap\s*(?:\.\s*open\s*\(|::\s*open\b)' -or
    $provider -notmatch '"amazon-deals"' -or
    $provider -notmatch 'https://www\.amazon\.com\.br/deals') {
    throw 'D-D: contrato Java divergente; revisar o bootstrap antes da ativacao'
}
Write-Host '=== C2-E-D-D-D-D-D CONTRATO PASS - schedule existente preservado; recovery identificado ==='

$inventario = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_B_EXECUTAR_INVENTARIO.ps1' 2>$null)
if ($LASTEXITCODE -ne 0) { throw 'D-D: inventario D-B reprovado' }
if (@($inventario | Where-Object { $_ -clike '*INVENTARIO PASS*' }).Count -ne 1) {
    throw 'D-D: inventario nao confirmou sucesso'
}
$contagens = @{}
foreach ($linha in $inventario) {
    if ([string]$linha -cmatch '^=== C2-E-D-D-D-D-B CONTAGEM ([a-z_]+)=([0-9]+) ===$') {
        if ($contagens.ContainsKey($Matches[1])) { throw 'D-D: contagem duplicada' }
        $contagens[$Matches[1]] = [long]::Parse($Matches[2])
    }
}
$esperados = @{
    jobs_dead = 0; jobs_pending = 0; jobs_retry_wait = 0; jobs_running = 0; jobs_total = 0;
    outbox_delivery_unknown = 0; outbox_processing = 0; outbox_total = 0;
    runs_running = 0; runs_total = 0;
    schedules_enabled = 0; schedules_enabled_due = 0; schedules_total = 1
}
if ($contagens.Count -ne $esperados.Count) { throw 'D-D: inventario incompleto' }
foreach ($nome in $esperados.Keys) {
    if (-not $contagens.ContainsKey($nome) -or $contagens[$nome] -ne $esperados[$nome]) {
        throw ('D-D: contagem inesperada: ' + $nome)
    }
}
Write-Host '=== C2-E-D-D-D-D-D BACKLOG PASS - 1 schedule pausado, sem jobs, runs ou outbox ==='

$raiz = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([char[]]@('\','/'))
$caminhoSegredos = [System.IO.Path]::GetFullPath($DiretorioSegredos)
if ($caminhoSegredos.StartsWith($raiz + [System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'D-D: pasta de credenciais dentro do repositorio'
}
if (-not (Test-Path -LiteralPath $caminhoSegredos -PathType Container)) {
    throw 'D-D: pasta de segredos DPAPI indisponivel'
}
$sql = $fontes[8]
$sh = $fontes[7]
foreach ($arquivo in @($sql,$sh)) {
    $nome = [System.IO.Path]::GetFileName($arquivo)
    & docker cp $arquivo ($NomeContainer + ':/tmp/' + $nome) | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'D-D: falha ao copiar SQL ou shell publico' }
}
$segredo = $null
try {
    $segredo = Ler-SenhaDPAPI-C-C -Caminho (Join-Path $caminhoSegredos 'rasping_runtime.dpapi')
    $consulta = @($segredo | & docker compose -f infra/compose.yml exec -T postgres sh '/tmp/FASE_21_C2_E_D_D_D_D_D_CONSULTAR_SCHEDULE.sh' 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'D-D: consulta SQL readonly reprovada' }
} finally {
    Remove-Variable segredo -ErrorAction SilentlyContinue
}
if ($consulta.Count -ne 1 -or [string]$consulta[0] -cne 'D_D_BOOTSTRAP_SCHEDULE_READONLY_PASS') {
    throw 'D-D: resposta de verificacao SQL inesperada'
}
Write-Host '=== C2-E-D-D-D-D-D SQL READ ONLY PASS - identidade, pausa e integridade confirmadas ==='

& mvn -q '-Dtest=EnsureProcessingSchedulePausedStartupTest' test
if ($LASTEXITCODE -ne 0) { throw 'D-D: teste Java de contrato reprovado; nao iniciar daemon' }
Write-Host '=== C2-E-D-D-D-D-D JAVA PASS - 2 testes de preservacao de pausa ==='

if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExistia) {
    throw 'D-D: existencia de .env alterada'
}
if ($envExistia -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash) {
    throw 'D-D: conteudo de .env alterado'
}
if ((Obter-StatusSupervisao-C -CaminhoRegistro $RegistroProdutivo) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-D: processo produtivo apareceu durante teste'
}
Write-Host '=== C2-E-D-D-D-D-D ENV PASS - .env preservado e nenhum daemon produtivo ==='
Write-Host '=== C2-E-D-D-D-D-D GATE PASS - contrato de bootstrap pausado verificado sem partida real ==='
Write-Host '=== C2-E-D-D-D-D-D BLOQUEIO ATIVO - nao iniciar daemon, publicar ou executar Flyway migrate ==='
