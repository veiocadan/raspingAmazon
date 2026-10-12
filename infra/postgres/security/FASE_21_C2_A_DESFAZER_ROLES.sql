-- FASE 21-C2-A - Reversao restrita ao provisionamento inicial.
-- EXECUTAR APENAS antes de ativar LOGIN, criar membership, conceder GRANT
-- ou transferir ownership. Se houver dependencia, abortar e investigar.
-- Nenhuma senha, tabela ou dado e alterado diretamente por este script.

\set ON_ERROR_STOP on
\pset pager off

BEGIN;

DO $rollback$
DECLARE
    target_role text;
    r record;
BEGIN
    IF current_database() <> 'rasping_amazon' OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-A rollback: contexto de execucao inesperado';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = current_user AND rolsuper
    ) THEN
        RAISE EXCEPTION 'C2-A rollback: privilegio administrativo ausente';
    END IF;

    FOREACH target_role IN ARRAY ARRAY['rasping_runtime', 'rasping_migrator']
    LOOP
        SELECT * INTO r FROM pg_catalog.pg_roles WHERE rolname = target_role;

        IF FOUND THEN
            IF r.rolcanlogin OR r.rolsuper OR r.rolcreatedb OR r.rolcreaterole
                OR r.rolreplication OR r.rolbypassrls OR r.rolinherit THEN
                RAISE EXCEPTION 'C2-A rollback: role % modificada; abortando', target_role;
            END IF;

            IF EXISTS (
                SELECT 1 FROM pg_catalog.pg_auth_members m
                WHERE m.member = r.oid OR m.roleid = r.oid
            ) THEN
                RAISE EXCEPTION 'C2-A rollback: role % possui memberships', target_role;
            END IF;

            IF EXISTS (
                SELECT 1 FROM pg_catalog.pg_class c WHERE c.relowner = r.oid
            ) OR EXISTS (
                SELECT 1 FROM pg_catalog.pg_namespace n WHERE n.nspowner = r.oid
            ) THEN
                RAISE EXCEPTION 'C2-A rollback: role % possui objetos', target_role;
            END IF;

            EXECUTE format('DROP ROLE %I', target_role);
        END IF;
    END LOOP;
END;
$rollback$;

COMMIT;
\echo === C2-A: roles provisoriamente criadas foram removidas ===
