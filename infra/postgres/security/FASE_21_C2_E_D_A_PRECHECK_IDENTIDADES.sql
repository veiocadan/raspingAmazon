-- FASE 21-C2-E-D-A: auditoria da prontidao das identidades.
-- SOMENTE LEITURA. Nao habilita LOGIN, nao altera senha nem permissao.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

DO $precheck$
DECLARE
    table_stats record;
    sequence_stats record;
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR current_user <> 'rasping' OR session_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-D-A: identidade ou database administrativa inesperada';
    END IF;

    IF current_setting('server_version_num')::integer < 180000
       OR current_setting('server_version_num')::integer >= 190000 THEN
        RAISE EXCEPTION 'C2-E-D-A: versao PostgreSQL diferente do contrato 18.x';
    END IF;

    IF current_setting('password_encryption') <> 'scram-sha-256' THEN
        RAISE EXCEPTION 'C2-E-D-A: novas senhas nao seriam armazenadas com SCRAM';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_authid AS a
        WHERE a.rolname = 'rasping' AND a.rolsuper AND a.rolcanlogin
          AND a.rolpassword LIKE 'SCRAM-SHA-256$%'
          AND (a.rolvaliduntil IS NULL OR a.rolvaliduntil > CURRENT_TIMESTAMP)
    ) THEN
        RAISE EXCEPTION 'C2-E-D-A: administrador sem credencial SCRAM valida';
    END IF;

    -- O conteudo em disco deve coincidir com a politica HBA aprovada.
    -- Os testes de autenticacao REAL da etapa C2-E-C-C-B ja foram executados.
    IF (SELECT count(*) FROM pg_catalog.pg_hba_file_rules) <> 7
       OR EXISTS (
           SELECT 1 FROM pg_catalog.pg_hba_file_rules AS h
           WHERE h.error IS NOT NULL
              OR h.auth_method IS DISTINCT FROM 'scram-sha-256'
              OR h.file_name IS DISTINCT FROM current_setting('hba_file')
       ) THEN
        RAISE EXCEPTION 'C2-E-D-A: HBA atual divergiu da politica de 7 regras SCRAM';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_authid AS a
        WHERE a.rolname IN ('rasping_migrator', 'rasping_runtime')
          AND NOT a.rolcanlogin AND NOT a.rolinherit
          AND NOT a.rolsuper AND NOT a.rolcreatedb
          AND NOT a.rolcreaterole AND NOT a.rolreplication
          AND NOT a.rolbypassrls AND a.rolpassword IS NULL) <> 2 THEN
        RAISE EXCEPTION 'C2-E-D-A: roles tecnicas deixaram de ser NOLOGIN sem senha';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_auth_members AS m
        WHERE m.member IN (
                SELECT oid FROM pg_catalog.pg_roles
                WHERE rolname IN ('rasping_migrator', 'rasping_runtime')
              )
           OR m.roleid IN (
                SELECT oid FROM pg_catalog.pg_roles
                WHERE rolname IN ('rasping_migrator', 'rasping_runtime')
              )
    ) THEN
        RAISE EXCEPTION 'C2-E-D-A: memberships inesperadas nas roles tecnicas';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_namespace AS n
        WHERE n.nspname = 'public'
          AND n.nspowner = (SELECT oid FROM pg_catalog.pg_roles
                             WHERE rolname = 'rasping_migrator')
    ) THEN
        RAISE EXCEPTION 'C2-E-D-A: schema public deixou de pertencer ao migrator';
    END IF;

    -- CTEs MATERIALIZED isolam exclusivamente tabelas e sequences publicas.
    -- Evitam a avaliacao de privilegios de sequence em OIDs de outros objetos.
    WITH runtime_tables AS MATERIALIZED (
        SELECT c.oid, c.relowner
        FROM pg_catalog.pg_class AS c
        JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
    )
    SELECT count(*) AS total,
           count(*) FILTER (WHERE relowner =
               (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = 'rasping_migrator')) AS owned,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'SELECT')) AS can_select,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'INSERT')) AS can_insert,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'UPDATE')) AS can_update,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'DELETE')) AS can_delete,
           count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', oid, 'TRUNCATE')) AS can_truncate
    INTO table_stats
    FROM runtime_tables;

    IF table_stats.total <> 29 OR table_stats.owned <> 29
       OR table_stats.can_select <> 27 OR table_stats.can_insert <> 25
       OR table_stats.can_update <> 12 OR table_stats.can_delete <> 0
       OR table_stats.can_truncate <> 0 THEN
        RAISE EXCEPTION 'C2-E-D-A: ownership ou grants runtime nas tabelas divergentes';
    END IF;

    WITH runtime_sequences AS MATERIALIZED (
        SELECT c.oid, c.relowner
        FROM pg_catalog.pg_class AS c
        JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'S'
    )
    SELECT count(*) AS total,
           count(*) FILTER (WHERE relowner =
               (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = 'rasping_migrator')) AS owned,
           count(*) FILTER (WHERE pg_catalog.has_sequence_privilege('rasping_runtime', oid, 'USAGE')) AS can_use,
           count(*) FILTER (WHERE pg_catalog.has_sequence_privilege('rasping_runtime', oid, 'UPDATE')) AS can_update
    INTO sequence_stats
    FROM runtime_sequences;

    IF sequence_stats.total <> 26 OR sequence_stats.owned <> 26
       OR sequence_stats.can_use <> 23 OR sequence_stats.can_update <> 0 THEN
        RAISE EXCEPTION 'C2-E-D-A: ownership ou grants runtime nas sequences divergentes';
    END IF;

    IF pg_catalog.has_schema_privilege('rasping_runtime', 'public', 'CREATE')
       OR pg_catalog.has_database_privilege('rasping_runtime', current_database(), 'CREATE')
       OR pg_catalog.has_table_privilege('rasping_runtime', 'public.flyway_schema_history', 'SELECT')
       OR pg_catalog.has_table_privilege('rasping_runtime', 'public.flyway_schema_history', 'INSERT')
       OR pg_catalog.has_table_privilege('rasping_runtime', 'public.configuration_version', 'SELECT') THEN
        RAISE EXCEPTION 'C2-E-D-A: runtime possui capacidade de migracao ou acesso a historico';
    END IF;
END;
$precheck$;

\echo === C2-E-D-A.1 - Roles prontas para ativacao futura (sem senhas) ===
SELECT r.rolname AS role_name,
       r.rolcanlogin AS can_login,
       r.rolinherit AS inherits,
       r.rolsuper AS is_superuser,
       r.rolcreatedb AS can_create_database,
       r.rolcreaterole AS can_create_role
  FROM pg_catalog.pg_roles AS r
 WHERE r.rolname IN ('rasping_migrator','rasping_runtime')
 ORDER BY r.rolname;

\echo === C2-E-D-A.2 - Politica atual e ownership ===
SELECT h.type AS tipo, h.auth_method AS metodo, count(*) AS regras
  FROM pg_catalog.pg_hba_file_rules AS h
 GROUP BY h.type, h.auth_method
 ORDER BY h.type, h.auth_method;
SELECT 'public' AS schema_name,
       pg_catalog.pg_get_userbyid(n.nspowner) AS schema_owner
  FROM pg_catalog.pg_namespace AS n WHERE n.nspname = 'public';

ROLLBACK;
\echo === C2-E-D-A PRECHECK IDENTIDADES PASS - nenhuma role, senha ou dado alterado ===
