-- FASE 21-C2-E-A - SIMULACAO DOS PRIVILEGIOS DA ROLE RUNTIME.
-- Nao autentica como rasping_runtime; simula sua identidade por SET LOCAL ROLE.
-- Nenhuma consulta le dados de negocio; DML testado so por metadados.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;
SET LOCAL ROLE rasping_runtime;

\echo === C2-E-A RUNTIME.1 - Identidade simulada ===
SELECT current_database() AS database_name,
       session_user AS authenticated_as,
       current_user AS effective_role,
       current_setting('transaction_read_only') AS read_only;

DO $runtime$
DECLARE
    tables_total int;
    tables_select int;
    tables_insert int;
    tables_update int;
    tables_delete int;
    tables_truncate int;
    seq_total int;
    seq_usage int;
    seq_update int;
    relation_name text;
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR session_user <> 'rasping'
       OR current_user <> 'rasping_runtime' THEN
        RAISE EXCEPTION 'C2-E-A runtime: contexto incorreto';
    END IF;
    IF NOT pg_catalog.has_database_privilege(current_database(), 'CONNECT')
       OR NOT pg_catalog.has_schema_privilege('public', 'USAGE')
       OR pg_catalog.has_schema_privilege('public', 'CREATE') THEN
        RAISE EXCEPTION 'C2-E-A runtime: privilegios sobre database/schema divergentes';
    END IF;
    IF (SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolcanlogin
        FROM pg_catalog.pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'C2-E-A runtime: atributos administrativos ou LOGIN inesperados';
    END IF;

    WITH objects AS MATERIALIZED (
        SELECT c.oid, c.relname
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p')
    )
    SELECT count(*),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'SELECT')),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'INSERT')),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'UPDATE')),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'DELETE')),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'TRUNCATE'))
      INTO tables_total,tables_select,tables_insert,tables_update,tables_delete,tables_truncate
      FROM objects;

    WITH sequences AS MATERIALIZED (
        SELECT c.oid FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind='S'
    )
    SELECT count(*),
           count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(oid,'USAGE')),
           count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(oid,'UPDATE'))
      INTO seq_total,seq_usage,seq_update
      FROM sequences;

    IF tables_total <> 29 OR tables_select <> 27 OR tables_insert <> 25
       OR tables_update <> 12 OR tables_delete <> 0 OR tables_truncate <> 0
       OR seq_total <> 26 OR seq_usage <> 23 OR seq_update <> 0 THEN
        RAISE EXCEPTION 'C2-E-A runtime: matriz de privilegios divergente';
    END IF;
    IF pg_catalog.has_table_privilege('public.flyway_schema_history','SELECT')
       OR pg_catalog.has_table_privilege('public.flyway_schema_history','INSERT')
       OR pg_catalog.has_table_privilege('public.configuration_version','SELECT')
       OR pg_catalog.has_table_privilege('public.configuration_version','INSERT') THEN
        RAISE EXCEPTION 'C2-E-A runtime: acesso indevido a tabelas de migracao';
    END IF;

    -- Prova que SELECT e executavel nas 27 tabelas, sem acessar linhas.
    FOR relation_name IN
       SELECT c.relname
       FROM pg_catalog.pg_class c
       JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
       WHERE n.nspname='public' AND c.relkind IN ('r','p')
         AND c.relname NOT IN ('configuration_version','flyway_schema_history')
       ORDER BY c.relname
    LOOP
       EXECUTE format('SELECT 1 FROM public.%I LIMIT 0', relation_name);
    END LOOP;
END;
$runtime$;

\echo === C2-E-A RUNTIME.2 - Privilegios em tabelas ===
WITH objects AS MATERIALIZED (
    SELECT c.oid FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind IN ('r','p')
)
SELECT count(*) AS total_tables,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'SELECT')) AS selectable,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'INSERT')) AS insertable,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'UPDATE')) AS updatable,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'DELETE')) AS deletable,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege(oid,'TRUNCATE')) AS truncatable
FROM objects;

\echo === C2-E-A RUNTIME.3 - Privilegios em sequences ===
WITH sequences AS MATERIALIZED (
    SELECT c.oid FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind='S'
)
SELECT count(*) AS total_sequences,
       count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(oid,'USAGE')) AS usable,
       count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(oid,'UPDATE')) AS updatable
FROM sequences;

ROLLBACK;
\echo === C2-E-A RUNTIME PASS - simulacao read-only, nenhum dado alterado ===
