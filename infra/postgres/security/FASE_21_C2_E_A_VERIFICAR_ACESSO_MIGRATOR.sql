-- FASE 21-C2-E-A - SIMULACAO DA ROLE MIGRATOR POR SET LOCAL ROLE.
-- O teste e de leitura. Nao executa Flyway nem qualquer DDL.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;
SET LOCAL ROLE rasping_migrator;

\echo === C2-E-A MIGRATOR.1 - Identidade simulada ===
SELECT current_database() AS database_name,
       session_user AS authenticated_as,
       current_user AS effective_role,
       current_setting('transaction_read_only') AS read_only;

DO $migrator$
DECLARE
    tab_count int;
    seq_count int;
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR session_user <> 'rasping' OR current_user <> 'rasping_migrator' THEN
        RAISE EXCEPTION 'C2-E-A migrator: contexto incorreto';
    END IF;
    IF NOT pg_catalog.has_database_privilege(current_database(), 'CONNECT')
       OR NOT pg_catalog.has_schema_privilege('public', 'USAGE')
       OR NOT pg_catalog.has_schema_privilege('public', 'CREATE') THEN
        RAISE EXCEPTION 'C2-E-A migrator: acesso ao schema ou database insuficiente';
    END IF;
    IF (SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication
               OR rolbypassrls OR rolcanlogin
        FROM pg_catalog.pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'C2-E-A migrator: atributos administrativos ou LOGIN inesperados';
    END IF;
    IF (SELECT pg_catalog.pg_get_userbyid(n.nspowner)
        FROM pg_catalog.pg_namespace n WHERE n.nspname='public') <> 'rasping_migrator' THEN
        RAISE EXCEPTION 'C2-E-A migrator: ownership do schema public divergente';
    END IF;
    SELECT count(*) FILTER (WHERE c.relkind IN ('r','p')),
           count(*) FILTER (WHERE c.relkind='S')
      INTO tab_count,seq_count
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public';
    IF tab_count <> 29 OR seq_count <> 26 THEN
        RAISE EXCEPTION 'C2-E-A migrator: inventario de objetos mudou';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p','S')
          AND pg_catalog.pg_get_userbyid(c.relowner) <> 'rasping_migrator'
    ) THEN
        RAISE EXCEPTION 'C2-E-A migrator: existe objeto com outro proprietario';
    END IF;
    IF NOT pg_catalog.has_table_privilege('public.flyway_schema_history','SELECT')
       OR NOT pg_catalog.has_table_privilege('public.flyway_schema_history','INSERT')
       OR NOT pg_catalog.has_table_privilege('public.configuration_version','SELECT') THEN
        RAISE EXCEPTION 'C2-E-A migrator: acesso aos metadados de migracao insuficiente';
    END IF;
    -- Checar permissao real de leitura sem retornar linhas ou dados.
    PERFORM 1 FROM public.flyway_schema_history LIMIT 0;
    PERFORM 1 FROM public.configuration_version LIMIT 0;
END;
$migrator$;

\echo === C2-E-A MIGRATOR.2 - Ownership e acesso ao schema ===
SELECT pg_catalog.pg_get_userbyid(n.nspowner) AS schema_owner,
       pg_catalog.has_schema_privilege('public','USAGE') AS can_use_schema,
       pg_catalog.has_schema_privilege('public','CREATE') AS can_create_in_schema
FROM pg_catalog.pg_namespace n
WHERE n.nspname='public';

SELECT CASE WHEN c.relkind IN ('r','p') THEN 'TABLE' ELSE 'SEQUENCE' END AS object_type,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner_role,
       count(*) AS object_count
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN ('r','p','S')
GROUP BY CASE WHEN c.relkind IN ('r','p') THEN 'TABLE' ELSE 'SEQUENCE' END,
         pg_catalog.pg_get_userbyid(c.relowner)
ORDER BY object_type;

ROLLBACK;
\echo === C2-E-A MIGRATOR PASS - simulacao read-only, nenhum DDL executado ===
