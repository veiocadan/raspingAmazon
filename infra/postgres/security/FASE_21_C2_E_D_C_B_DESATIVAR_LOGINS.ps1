# C2-E-D-C-B: REVERSAO ADMINISTRATIVA, somente se expressamente autorizada.
# Nao altera ownership, grants, HBA, Compose nem o usuario administrador rasping.
param([switch]$ConfirmarReversao)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmarReversao) { throw 'C2-E-D-C-B: reversao exige -ConfirmarReversao' }
if (-not (Test-Path -LiteralPath 'infra/compose.yml' -PathType Leaf)) {
    throw 'C2-E-D-C-B: execute na raiz do repositorio'
}
$script = @'
\set ON_ERROR_STOP on
BEGIN;
DO $reverter$
BEGIN
    IF current_database() <> 'rasping_amazon' OR session_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-D-C-B: banco ou administrador divergente';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_stat_activity
         WHERE datname=current_database() AND pid<>pg_backend_pid()
           AND backend_type='client backend') THEN
        RAISE EXCEPTION 'C2-E-D-C-B: conexoes de aplicacao ativas';
    END IF;
END;
$reverter$;
ALTER ROLE rasping_runtime WITH NOLOGIN PASSWORD NULL;
ALTER ROLE rasping_migrator WITH NOLOGIN PASSWORD NULL;
COMMIT;
'@
$script | & docker compose -f infra/compose.yml exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5 exec psql -X -w -h postgres -U rasping -d rasping_amazon -v ON_ERROR_STOP=1 -q -f -' 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'C2-E-D-C-B: falha na reversao; nao repetir automaticamente' }
Write-Host '=== C2-E-D-C-B REVERSAO PASS - duas roles NOLOGIN e sem senhas ==='
Write-Host '=== Credenciais DPAPI antigas devem ser tratadas como INVALIDAS; nao excluir sem autorizacao ==='
