# FASE 21-C2-E-D-D-D-D-C — somente leitura, nenhuma escrita no banco.
param(
    [string]$Plano = 'config/operacao/continuous-startup-policy.v1.json'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ((Get-Location).Path.TrimEnd('\') -cne 'C:\dev\rasping-amazon') {
    throw 'D-C: execute na raiz C:\dev\rasping-amazon'
}
$branch = ((& git branch --show-current 2>$null) | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cne 'feat/fase-21-seguranca-governanca-v1') {
    throw 'D-C: branch Git incorreta'
}
$mudancas = @(& git diff --name-only HEAD)
if ($LASTEXITCODE -ne 0 -or $mudancas.Count -ne 0) {
    throw 'D-C: modificacoes rastreadas; nao prosseguir'
}
$artefatos = @(
    $Plano,
    'src/main/java/com/raspingamazon/application/scheduling/EnsureProcessingScheduleUseCase.java',
    'src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingBootstrap.java',
    'src/main/java/com/raspingamazon/infrastructure/persistence/adapter/JdbcProcessingScheduleAdapter.java',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_D_B_EXECUTAR_INVENTARIO.ps1',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_D_C_CRIAR_AGENDAMENTO_PAUSADO.ps1',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_D_C_INSERIR_AGENDAMENTO_PAUSADO.sql',
    'infra/postgres/security/FASE_21_C2_E_D_D_D_D_C_EXECUTAR_INSERCAO.sh'
)
foreach ($arquivo in $artefatos) {
    if (-not (Test-Path -LiteralPath $arquivo -PathType Leaf)) {
        throw ('D-C: arquivo obrigatorio ausente: ' + $arquivo)
    }
}
$planoJson = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($Plano)) | ConvertFrom-Json
if ($planoJson.execution_mode -cne 'preparation_only' -or
    $planoJson.initial_schedule_interval -cne 'PT1H' -or
    $planoJson.daemon_start_authorized -ne $false -or
    $planoJson.flyway_migrate_authorized -ne $false -or
    $planoJson.external_actions_authorized -ne $false -or
    $planoJson.automatic_restart_authorized -ne $false) {
    throw 'D-C: politica de bloqueio ou cadencia inicial divergente'
}
Write-Host '=== C2-E-D-D-D-D-C PLANO PASS - bloqueios mantidos e cadencia PT1H ==='
$caso = [System.IO.File]::ReadAllText('src/main/java/com/raspingamazon/application/scheduling/EnsureProcessingScheduleUseCase.java')
$bootstrap = [System.IO.File]::ReadAllText('src/main/java/com/raspingamazon/infrastructure/bootstrap/ContinuousProcessingBootstrap.java')
$adapter = [System.IO.File]::ReadAllText('src/main/java/com/raspingamazon/infrastructure/persistence/adapter/JdbcProcessingScheduleAdapter.java')
if ($caso -notmatch '(?s)new\s+ProcessingSchedule\s*\(\s*validatedScheduleKey\s*,\s*validatedSource\s*,\s*true\s*,\s*validatedInterval\s*,\s*now' -or
    $bootstrap -notmatch 'ensureProcessingScheduleUseCase\s*\.execute\s*\(' -or
    $adapter -notmatch 'ON CONFLICT \(schedule_key\)' -or
    $adapter -notmatch 'DO NOTHING') {
    throw 'D-C: contrato do schedule Java mudou; revisar antes da escrita'
}
Write-Host '=== C2-E-D-D-D-D-C RISCO PASS - bootstrap habilitaria uma coleta imediata ==='
$variaveis = @('PROCESSING_SCHEDULE_KEY','PROCESSING_SOURCE_URI')
foreach ($nome in $variaveis) {
    $valor = [System.Environment]::GetEnvironmentVariable($nome,'Process')
    if (-not [string]::IsNullOrWhiteSpace($valor)) {
        throw ('D-C: ' + $nome + ' definido no processo; revisar contrato antes de criar agendamento')
    }
    if (Test-Path -LiteralPath '.env' -PathType Leaf) {
        $conteudo = [System.IO.File]::ReadAllLines([System.IO.Path]::GetFullPath('.env'))
        foreach ($linha in $conteudo) {
            if ($linha -match ('^\s*' + [regex]::Escape($nome) + '\s*=')) {
                throw ('D-C: ' + $nome + ' presente no .env; revisar antes de criar agendamento')
            }
        }
    }
}
if ($caso -notmatch 'true' -or
    -not (Test-Path 'src/main/java/com/raspingamazon/infrastructure/config/ContinuousProcessingEnvironmentConfigProvider.java')) {
    throw 'D-C: contrato runtime ausente'
}
$provider = [System.IO.File]::ReadAllText('src/main/java/com/raspingamazon/infrastructure/config/ContinuousProcessingEnvironmentConfigProvider.java')
if ($provider -notmatch '"amazon-deals"' -or $provider -notmatch 'https://www\.amazon\.com\.br/deals') {
    throw 'D-C: key ou source default divergente; revisar configuracao'
}
Write-Host '=== C2-E-D-D-D-D-C IDENTIDADE PASS - origem e chave de agendamento conferidas ==='
$saida = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'infra/postgres/security/FASE_21_C2_E_D_D_D_D_B_EXECUTAR_INVENTARIO.ps1' 2>$null)
if ($LASTEXITCODE -ne 0) { throw 'D-C: inventario read-only D-B reprovado' }
$contagens = @{}
foreach ($linha in $saida) {
    if ([string]$linha -cmatch '^=== C2-E-D-D-D-D-B CONTAGEM ([a-z_]+)=([0-9]+) ===$') {
        if ($contagens.ContainsKey($Matches[1])) { throw 'D-C: contagem duplicada' }
        $contagens[$Matches[1]] = [long]::Parse($Matches[2])
    }
}
$esperadas = @('jobs_dead','jobs_pending','jobs_retry_wait','jobs_running','jobs_total',
              'outbox_delivery_unknown','outbox_processing','outbox_total',
              'runs_running','runs_total','schedules_enabled','schedules_enabled_due','schedules_total')
if ($contagens.Count -ne $esperadas.Count) { throw 'D-C: inventario incompleto' }
foreach ($nome in $esperadas) {
    if (-not $contagens.ContainsKey($nome) -or $contagens[$nome] -ne 0) {
        throw ('D-C: banco nao vazio ou estado inesperado: ' + $nome)
    }
}
if (@($saida | Where-Object { $_ -clike '*INVENTARIO PASS*' }).Count -ne 1) {
    throw 'D-C: D-B nao confirmou gate de leitura'
}
Write-Host '=== C2-E-D-D-D-D-C BACKLOG PASS - 0 jobs, 0 runs, 0 outbox e 0 schedules ==='
Write-Host '=== C2-E-D-D-D-D-C PRECHECK PASS - nenhuma escrita, nenhum processo Java iniciado ==='
Write-Host '=== C2-E-D-D-D-D-C BLOQUEIO ATIVO - nao executar criacao de agendamento nem daemon ainda ==='
