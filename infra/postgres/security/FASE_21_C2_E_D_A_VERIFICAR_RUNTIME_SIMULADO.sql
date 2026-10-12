-- FASE 21-C2-E-D-A: prova de segregacao em contexto da role runtime.
-- SET LOCAL ROLE nao concede LOGIN. Transacao READ ONLY; nenhum DML executado.
\set ON_ERROR_STOP on
\pset pager off
BEGIN TRANSACTION READ ONLY;
SET LOCAL ROLE rasping_runtime;

DO $runtime$
DECLARE
    tables record;
    sequences record;
BEGIN
    IF session_user <> 'rasping'
       OR current_user <> 'rasping_runtime'
       OR current_database() <> 'rasping_amazon' THEN
        RAISE EXCEPTION 'C2-E-D-A: simulacao nao esta sob a role runtime';
    END IF;

    IF pg_catalog.has_schema_privilege(current_user, 'public', 'CREATE')
       OR pg_catalog.has_database_privilege(current_user, current_database(), 'CREATE')
       OR pg_catalog.has_table_privilege(current_user, 'public.flyway_schema_history', 'SELECT')
       OR pg_catalog.has_table_privilege(current_user, 'public.configuration_version', 'SELECT') THEN
        RAISE EXCEPTION 'C2-E-D-A: isolamento Flyway/schema comprometido';
    END IF;

    WITH runtime_tables AS MATERIALIZED (
        SELECT c.oid FROM pg_catalog.pg_class AS c
        JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
    )
    SELECT count(*) AS total,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user, oid, 'SELECT')) AS selectable,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user, oid, 'INSERT')) AS insertable,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user, oid, 'UPDATE')) AS updatable,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user, oid, 'DELETE')) AS deletable,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user, oid, 'TRUNCATE')) AS truncatable
    INTO tables FROM runtime_tables;

    WITH runtime_sequences AS MATERIALIZED (
        SELECT c.oid FROM pg_catalog.pg_class AS c
        JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind='S'
    )
    SELECT count(*) AS total,
           count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(current_user, oid, 'USAGE')) AS usable,
           count(*) FILTER (WHERE pg_catalog.has_sequence_privilege(current_user, oid, 'UPDATE')) AS updatable
    INTO sequences FROM runtime_sequences;

    IF tables.total <> 29 OR tables.selectable <> 27
       OR tables.insertable <> 25 OR tables.updatable <> 12
       OR tables.deletable <> 0 OR tables.truncatable <> 0
       OR sequences.total <> 26 OR sequences.usable <> 23
       OR sequences.updatable <> 0 THEN
        RAISE EXCEPTION 'C2-E-D-A: privilegios efetivos do runtime divergentes';
    END IF;
END;
$runtime$;

\echo === C2-E-D-A.3 - Sessao simulada sem login tecnico ===
SELECT session_user AS authenticated_as,
       current_user AS effective_role,
       current_setting('transaction_read_only') AS read_only;
ROLLBACK;
\echo === C2-E-D-A RUNTIME SIMULADO PASS - nenhum dado alterado ===
