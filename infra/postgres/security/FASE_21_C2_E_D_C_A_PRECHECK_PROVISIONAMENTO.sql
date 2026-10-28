-- FASE 21-C2-E-D-C-A: pre-verificacao das identidades antes de gerar senhas.
-- Somente leitura. Executado como administrador existente autenticado por SCRAM.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

DO $gate$
DECLARE
    tbl record;
    seq record;
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR session_user <> 'rasping' OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-D-C-A: banco ou identidade administrativa inesperados';
    END IF;

    IF current_setting('server_version_num')::integer < 180000
       OR current_setting('server_version_num')::integer >= 190000
       OR current_setting('password_encryption') <> 'scram-sha-256' THEN
        RAISE EXCEPTION 'C2-E-D-C-A: contrato PostgreSQL 18 e SCRAM divergente';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_authid a
         WHERE a.rolname = 'rasping' AND a.rolsuper AND a.rolcanlogin
           AND a.rolpassword LIKE 'SCRAM-SHA-256$%'
           AND (a.rolvaliduntil IS NULL OR a.rolvaliduntil > CURRENT_TIMESTAMP)
    ) THEN
        RAISE EXCEPTION 'C2-E-D-C-A: credencial SCRAM administrativa indisponivel';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_hba_file_rules) <> 7
       OR (SELECT count(*) FROM pg_catalog.pg_hba_file_rules
            WHERE auth_method = 'scram-sha-256' AND type = 'host') <> 5
       OR (SELECT count(*) FROM pg_catalog.pg_hba_file_rules
            WHERE auth_method = 'scram-sha-256' AND type = 'local') <> 2
       OR EXISTS (
          SELECT 1 FROM pg_catalog.pg_hba_file_rules h
           WHERE h.error IS NOT NULL OR h.auth_method IS DISTINCT FROM 'scram-sha-256'
             OR h.file_name IS DISTINCT FROM current_setting('hba_file')
       ) THEN
        RAISE EXCEPTION 'C2-E-D-C-A: HBA divergiu da politica SCRAM validada';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_authid a
         WHERE a.rolname IN ('rasping_runtime', 'rasping_migrator')
           AND NOT a.rolcanlogin AND NOT a.rolinherit AND NOT a.rolsuper
           AND NOT a.rolcreatedb AND NOT a.rolcreaterole
           AND NOT a.rolreplication AND NOT a.rolbypassrls
           AND a.rolpassword IS NULL
           AND (a.rolvaliduntil IS NULL OR a.rolvaliduntil > CURRENT_TIMESTAMP)
    ) <> 2 THEN
        RAISE EXCEPTION 'C2-E-D-C-A: roles tecnicas nao estao NOLOGIN e sem senha';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_auth_members am
         WHERE am.member IN (SELECT oid FROM pg_catalog.pg_roles
                              WHERE rolname IN ('rasping_runtime','rasping_migrator'))
            OR am.roleid IN (SELECT oid FROM pg_catalog.pg_roles
                              WHERE rolname IN ('rasping_runtime','rasping_migrator'))
    ) THEN
        RAISE EXCEPTION 'C2-E-D-C-A: memberships inesperadas nas roles tecnicas';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_namespace n
         WHERE n.nspname = 'public'
           AND n.nspowner = (SELECT oid FROM pg_catalog.pg_roles
                              WHERE rolname = 'rasping_migrator')
    ) THEN
        RAISE EXCEPTION 'C2-E-D-C-A: schema public nao pertence ao migrator';
    END IF;

    -- Isolamento MATERIALIZED dos OIDs para nao invocar has_sequence_privilege
    -- com OIDs que representem tabelas ou objetos internos.
    WITH objects AS MATERIALIZED (
        SELECT c.oid, c.relowner
          FROM pg_catalog.pg_class c
          JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
    )
    SELECT count(*) AS total,
           count(*) FILTER (WHERE relowner = (SELECT oid FROM pg_catalog.pg_roles
                                              WHERE rolname = 'rasping_migrator')) AS owned,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'SELECT')) AS selectable,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'INSERT')) AS insertable,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'UPDATE')) AS updatable,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'DELETE')) AS deletable,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'TRUNCATE')) AS truncatable
      INTO tbl FROM objects;

    IF tbl.total <> 29 OR tbl.owned <> 29 OR tbl.selectable <> 27
       OR tbl.insertable <> 25 OR tbl.updatable <> 12 OR tbl.deletable <> 0
       OR tbl.truncatable <> 0 THEN
        RAISE EXCEPTION 'C2-E-D-C-A: ownership ou matriz runtime das tabelas divergente';
    END IF;

    WITH objects AS MATERIALIZED (
        SELECT c.oid, c.relowner
          FROM pg_catalog.pg_class c
          JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public' AND c.relkind = 'S'
    )
    SELECT count(*) AS total,
           count(*) FILTER (WHERE relowner = (SELECT oid FROM pg_catalog.pg_roles
                                              WHERE rolname = 'rasping_migrator')) AS owned,
           count(*) FILTER (WHERE pg_catalog.has_sequence_privilege('rasping_runtime', oid, 'USAGE')) AS usable,
           count(*) FILTER (WHERE pg_catalog.has_sequence_privilege('rasping_runtime', oid, 'UPDATE')) AS updatable
      INTO seq FROM objects;

    IF seq.total <> 26 OR seq.owned <> 26 OR seq.usable <> 23 OR seq.updatable <> 0 THEN
        RAISE EXCEPTION 'C2-E-D-C-A: ownership ou matriz runtime das sequences divergente';
    END IF;

    IF pg_catalog.has_schema_privilege('rasping_runtime','public','CREATE')
       OR pg_catalog.has_database_privilege('rasping_runtime',current_database(),'CREATE')
       OR pg_catalog.has_table_privilege('rasping_runtime','public.flyway_schema_history','SELECT')
       OR pg_catalog.has_table_privilege('rasping_runtime','public.flyway_schema_history','INSERT')
       OR pg_catalog.has_table_privilege('rasping_runtime','public.configuration_version','SELECT') THEN
        RAISE EXCEPTION 'C2-E-D-C-A: runtime possui privilegios de migracao';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_stat_activity
         WHERE datname = current_database() AND pid <> pg_backend_pid()
           AND backend_type = 'client backend'
    ) THEN
        RAISE EXCEPTION 'C2-E-D-C-A: existem conexoes de aplicacao ativas';
    END IF;
END;
$gate$;

\echo === C2-E-D-C-A.1 - Roles tecnicas (sem hashes) ===
SELECT r.rolname AS role_name, r.rolcanlogin AS can_login,
       r.rolinherit AS inherits, r.rolsuper AS is_superuser
  FROM pg_catalog.pg_roles r
 WHERE r.rolname IN ('rasping_runtime','rasping_migrator')
 ORDER BY r.rolname;
\echo === C2-E-D-C-A.2 - Autenticacao HBA em disco ===
SELECT type, auth_method, count(*) AS rules
  FROM pg_catalog.pg_hba_file_rules
 GROUP BY type, auth_method ORDER BY type, auth_method;
\echo === C2-E-D-C-A.3 - Ownership dos objetos public ===
SELECT CASE WHEN c.relkind='S' THEN 'SEQUENCE' ELSE 'TABLE' END AS object_type,
       pg_catalog.pg_get_userbyid(c.relowner) AS owner_role,
       count(*) AS object_count
  FROM pg_catalog.pg_class c
  JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname='public' AND c.relkind IN ('r','p','S')
 GROUP BY 1, 2 ORDER BY 1, 2;
ROLLBACK;
\echo === C2-E-D-C-A SQL PRECHECK PASS - nenhuma credencial ou permissao alterada ===
