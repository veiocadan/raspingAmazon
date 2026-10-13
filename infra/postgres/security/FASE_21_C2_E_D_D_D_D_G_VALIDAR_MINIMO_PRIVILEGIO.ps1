# FASE 21-C2-E-D-D-D-D-G - prova negativa: launcher sem segredo migrator.
# Nenhum daemon e iniciado: launcher deve falhar pelo bloqueio de politica.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_A_FUNCOES_CONTROLES.ps1'
. 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_FUNCOES_SUPERVISOR.ps1'

Exigir-RaizProjeto-C
$plano = Ler-PlanoSupervisionado-C-C
Exigir-PartidaBloqueada-C -Plano $plano
if ($plano.initial_schedule_interval -cne 'PT1H' -or $plano.shutdown_grace_seconds -ne 30) {
    throw 'D-G: plano operacional divergente'
}
$registro = 'C:\dev\rasping-amazon-backups\supervisao\rasping-amazon-daemon.v1.json'
if ((Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-G: daemon produtivo ou registro inesperado'
}
Write-Host '=== C2-E-D-D-D-D-G POLITICA PASS - bloqueios mantidos e daemon ausente ==='

$launcher = 'infra/postgres/security/FASE_21_C2_E_D_D_D_C_C_INICIAR_SUPERVISIONADO.ps1'
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw 'D-G: launcher nao encontrado'
}
$codigoLauncher = [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($launcher))
if ($codigoLauncher.Contains('rasping_migrator.dpapi') -or
    $codigoLauncher.Contains('DB_MIGRATION_PASSWORD') -or
    $codigoLauncher.Contains('DB_MIGRATION_USER') -or
    $codigoLauncher -notmatch '(?m)^\s*\$runtime = Ler-SenhaDPAPI-C-C .*rasping_runtime[.]dpapi' -or
    $codigoLauncher -notmatch '(?m)^\s*Remover-VariaveisPrivilegiadas-C-C -Variaveis \$envs' -or
    $codigoLauncher -notmatch '(?m)^\s*\$envs\[''DB_USER''\] = ''rasping_runtime''') {
    throw 'D-G: launcher nao possui separacao minima de credenciais'
}
$limpeza = $codigoLauncher.IndexOf('Remover-VariaveisPrivilegiadas-C-C -Variaveis $envs', [System.StringComparison]::Ordinal)
$inicio = $codigoLauncher.IndexOf('[System.Diagnostics.Process]::Start($psi)', [System.StringComparison]::Ordinal)
if ($limpeza -lt 0 -or $inicio -lt 0 -or $limpeza -ge $inicio) {
    throw 'D-G: limpeza de ambiente nao precede processo filho'
}
Write-Host '=== C2-E-D-D-D-D-G LAUNCHER PASS - apenas runtime DPAPI e limpeza antes da JVM ==='

# Testar um ProcessStartInfo REAL, exclusivamente em memoria, sem Start().
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.UseShellExecute = $false
$vars = $psi.EnvironmentVariables
$vars['DB_PASSWORD'] = 'runtime-ficticio'
$vars['DB_USER'] = 'rasping_runtime'
$vars['DB_MIGRATION_PASSWORD'] = 'migrator-ficticio'
$vars['DB_MIGRATION_USER'] = 'rasping_migrator'
$vars['FLYWAY_PASSWORD'] = 'flyway-ficticio'
$vars['Flyway_USER'] = 'migrator-ficticio'
$vars['PGPASSWORD'] = 'postgres-ficticio'
$vars['PGPASSFILE'] = 'arquivo-ficticio'
$vars['CUSTOM_APP_SETTING'] = 'valor-conservado'
Remover-VariaveisPrivilegiadas-C-C -Variaveis $vars
foreach ($nome in @('DB_MIGRATION_PASSWORD','DB_MIGRATION_USER','FLYWAY_PASSWORD',
        'Flyway_USER','PGPASSWORD','PGPASSFILE')) {
    if ($vars.ContainsKey($nome)) { throw 'D-G: variavel privilegiada nao removida' }
}
if ($vars['DB_PASSWORD'] -cne 'runtime-ficticio' -or
    $vars['DB_USER'] -cne 'rasping_runtime' -or
    $vars['CUSTOM_APP_SETTING'] -cne 'valor-conservado') {
    throw 'D-G: limpeza danificou configuracao de runtime'
}
Write-Host '=== C2-E-D-D-D-D-G HERANCA PASS - migrator e Flyway removidos, runtime preservado ==='

$envExistia = Test-Path -LiteralPath '.env' -PathType Leaf
$envHash = if ($envExistia) { (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash } else { $null }
$anterior = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $saida = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher -ConfirmarPartida 2>&1 | Out-String)
    $codigo = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $anterior
}
# Nunca imprimir a saida bruta do launcher, para proteger o ambiente.
if ($codigo -eq 0 -or $saida -notmatch 'PARTIDA BLOQUEADA') {
    throw 'D-G: politica nao bloqueou o launcher como esperado'
}
Write-Host '=== C2-E-D-D-D-D-G BLOQUEIO PASS - tentativa real rejeitada antes de segredos e JVM ==='

if ((Test-Path -LiteralPath '.env' -PathType Leaf) -ne $envExistia -or
    ($envExistia -and (Get-FileHash -LiteralPath '.env' -Algorithm SHA256).Hash -cne $envHash) -or
    (Obter-StatusSupervisao-C -CaminhoRegistro $registro) -cne 'PARADO_SEM_REGISTRO') {
    throw 'D-G: ambiente ou estado do daemon alterados'
}
Write-Host '=== C2-E-D-D-D-D-G ENV PASS - .env e daemon preservados ==='
Write-Host '=== C2-E-D-D-D-D-G GATE PASS - credencial migrator excluida do ambiente do daemon ==='
Write-Host '=== C2-E-D-D-D-D-G BLOQUEIO ATIVO - nao iniciar daemon, nem efeitos externos ==='
