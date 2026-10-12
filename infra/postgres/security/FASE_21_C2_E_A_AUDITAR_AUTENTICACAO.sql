-- FASE 21-C2-E-A - AUDITORIA DA AUTENTICACAO, SEM MODIFICACOES.
-- Executar somente conectado como rasping em rasping_amazon.
-- Nao exibe hashes nem senhas do PostgreSQL.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

\echo === C2-E-A.1 - Contexto de autenticacao ===
SELECT current_database() AS database_name,
       session_user AS authenticated_as,
       current_user AS executed_as,
       current_setting('password_encryption') AS password_encryption;

DO $guard$
BEGIN
    IF current_database() <> 'rasping_amazon' OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-A: banco ou identidade administrativa incorretos';
    END IF;
    IF NOT (SELECT rolsuper FROM pg_catalog.pg_roles WHERE rolname = 'rasping') THEN
        RAISE EXCEPTION 'C2-E-A: administrador rasping deve ser SUPERUSER neste gate';
    END IF;
    IF current_setting('password_encryption') <> 'scram-sha-256' THEN
        RAISE EXCEPTION 'C2-E-A: password_encryption deve ser scram-sha-256 antes da criacao de senhas';
    END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_roles
        WHERE rolname IN ('rasping_runtime','rasping_migrator')
          AND NOT rolcanlogin AND NOT rolinherit AND NOT rolsuper
          AND NOT rolcreatedb AND NOT rolcreaterole
          AND NOT rolreplication AND NOT rolbypassrls) <> 2 THEN
        RAISE EXCEPTION 'C2-E-A: roles tecnicas nao estao no estado NOLOGIN/NOINHERIT esperado';
    END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_authid
        WHERE rolname IN ('rasping_runtime','rasping_migrator')
          AND rolpassword IS NOT NULL) <> 0 THEN
        RAISE EXCEPTION 'C2-E-A: alguma role tecnica ja possui senha; investigar antes de prosseguir';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_auth_members m
        JOIN pg_catalog.pg_roles r ON r.oid = m.roleid
        JOIN pg_catalog.pg_roles u ON u.oid = m.member
        WHERE r.rolname IN ('rasping_runtime','rasping_migrator')
           OR u.rolname IN ('rasping_runtime','rasping_migrator')
    ) THEN
        RAISE EXCEPTION 'C2-E-A: membership inesperada em role tecnica';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_hba_file_rules WHERE error IS NOT NULL) THEN
        RAISE EXCEPTION 'C2-E-A: pg_hba.conf contem regra invalida';
    END IF;
END;
$guard$;

\echo === C2-E-A.2 - Roles, sem hashes nem senhas ===
SELECT r.rolname AS role_name,
       r.rolcanlogin AS can_login,
       r.rolinherit AS inherits,
       r.rolsuper AS is_superuser,
       r.rolcreatedb AS can_create_db,
       r.rolcreaterole AS can_create_role,
       r.rolbypassrls AS bypass_rls,
       (a.rolpassword IS NOT NULL) AS password_configured
FROM pg_catalog.pg_roles r
JOIN pg_catalog.pg_authid a ON a.oid = r.oid
WHERE r.rolname IN ('rasping_runtime','rasping_migrator')
ORDER BY r.rolname;

\echo === C2-E-A.3 - Metodos de autenticacao configurados (sem enderecos) ===
SELECT type AS connection_type,
       auth_method,
       count(*) AS rule_count
FROM pg_catalog.pg_hba_file_rules
WHERE error IS NULL
GROUP BY type, auth_method
ORDER BY type, auth_method;

\echo === C2-E-A.4 - ALERTA se autenticacao host usar trust/password/md5 ===
SELECT CASE
    WHEN EXISTS (
        SELECT 1 FROM pg_catalog.pg_hba_file_rules
        WHERE type LIKE 'host%'
          AND auth_method IN ('trust','password','md5')
    ) THEN 'REVISAR: regra host com autenticacao menos restritiva detectada'
    ELSE 'OK: nenhuma regra host trust/password/md5 detectada'
END AS host_auth_observation;

ROLLBACK;
\echo === C2-E-A AUTENTICACAO PASS - nenhuma credencial criada ou alterada ===
