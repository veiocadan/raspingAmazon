-- FASE 21-C2-E-B - Guarda contra ativacao precoce das identidades.
-- Somente leitura; verifica que o estado C2-E-A segue inalterado.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

DO $guard$
BEGIN
    IF current_database() <> 'rasping_amazon' OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-B: banco ou usuario inesperado';
    END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_roles
        WHERE rolname IN ('rasping_runtime','rasping_migrator')
          AND NOT rolcanlogin AND NOT rolinherit AND NOT rolsuper
          AND NOT rolcreatedb AND NOT rolcreaterole
          AND NOT rolreplication AND NOT rolbypassrls) <> 2 THEN
        RAISE EXCEPTION 'C2-E-B: alguma identidade tecnica foi alterada';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_authid
        WHERE rolname IN ('rasping_runtime','rasping_migrator')
          AND rolpassword IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'C2-E-B: credencial tecnica criada antes da autorizacao';
    END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_catalog.pg_roles r ON r.oid = c.relowner
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r','p')
          AND r.rolname = 'rasping_migrator') <> 29 THEN
        RAISE EXCEPTION 'C2-E-B: ownership de tabelas divergiu';
    END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_catalog.pg_roles r ON r.oid = c.relowner
        WHERE n.nspname = 'public'
          AND c.relkind = 'S'
          AND r.rolname = 'rasping_migrator') <> 26 THEN
        RAISE EXCEPTION 'C2-E-B: ownership de sequences divergiu';
    END IF;
    IF (SELECT pg_catalog.pg_get_userbyid(nspowner)
        FROM pg_catalog.pg_namespace WHERE nspname = 'public') <> 'rasping_migrator' THEN
        RAISE EXCEPTION 'C2-E-B: ownership de schema divergiu';
    END IF;
END;
$guard$;

\echo === C2-E-B.7 - Estado das roles tecnicas ===
SELECT rolname AS identidade, rolcanlogin AS pode_login,
       rolinherit AS herda, rolsuper AS superuser,
       rolcreatedb AS cria_banco, rolcreaterole AS cria_role
FROM pg_catalog.pg_roles
WHERE rolname IN ('rasping_runtime','rasping_migrator')
ORDER BY rolname;

ROLLBACK;
\echo === C2-E-B ROLES PASS - estado NOLOGIN e ownership preservados ===
