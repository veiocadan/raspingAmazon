-- FASE 21-C2-A - Provisionamento controlado de identidades PostgreSQL.
-- Somente cria roles tecnicas sem LOGIN e sem privilegios administrativos.
-- Nao altera ownership, GRANT, REVOKE, senhas, dados nem Flyway.
-- Pre-requisito: C1 concluida e execucao como o administrador rasping.

\set ON_ERROR_STOP on
\pset pager off

BEGIN;

DO $provision$
DECLARE
    target_role text;
    existing_role record;
BEGIN
    IF current_database() <> 'rasping_amazon' THEN
        RAISE EXCEPTION 'C2-A: banco inesperado; abortando';
    END IF;

    IF current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-A: conectar como administrador rasping; abortando';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_roles
        WHERE rolname = current_user AND rolsuper
    ) THEN
        RAISE EXCEPTION 'C2-A: conexao nao possui privilegios administrativos';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_namespace
        WHERE nspname = 'public'
    ) THEN
        RAISE EXCEPTION 'C2-A: schema public ausente';
    END IF;

    FOREACH target_role IN ARRAY ARRAY['rasping_migrator', 'rasping_runtime']
    LOOP
        SELECT * INTO existing_role
        FROM pg_catalog.pg_roles
        WHERE rolname = target_role;

        IF NOT FOUND THEN
            EXECUTE format(
                'CREATE ROLE %I NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
                target_role
            );
        ELSE
            IF existing_role.rolcanlogin
                OR existing_role.rolinherit
                OR existing_role.rolsuper
                OR existing_role.rolcreatedb
                OR existing_role.rolcreaterole
                OR existing_role.rolreplication
                OR existing_role.rolbypassrls THEN
                RAISE EXCEPTION 'C2-A: role existente % com atributos inesperados',
                    target_role;
            END IF;

            IF EXISTS (
                SELECT 1 FROM pg_catalog.pg_auth_members m
                WHERE m.member = existing_role.oid OR m.roleid = existing_role.oid
            ) THEN
                RAISE EXCEPTION 'C2-A: role existente % possui memberships; revisar manualmente',
                    target_role;
            END IF;

            IF EXISTS (
                SELECT 1 FROM pg_catalog.pg_class c
                WHERE c.relowner = existing_role.oid
            ) OR EXISTS (
                SELECT 1 FROM pg_catalog.pg_namespace n
                WHERE n.nspowner = existing_role.oid
            ) THEN
                RAISE EXCEPTION 'C2-A: role existente % possui objetos; revisar manualmente',
                    target_role;
            END IF;
        END IF;
    END LOOP;
END;
$provision$;

COMMIT;

\echo === C2-A: roles sem LOGIN provisionadas, sem mudanca no runtime ===
