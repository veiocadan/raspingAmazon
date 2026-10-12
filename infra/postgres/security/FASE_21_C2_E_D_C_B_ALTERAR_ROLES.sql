-- C2-E-D-C-B: template privado. Os marcadores sao substituidos SOMENTE em memoria.
-- NUNCA executar este arquivo diretamente ou registrar SQL preenchido em logs.
\set ON_ERROR_STOP on
BEGIN;
DO $validacao$
BEGIN
    IF current_database() <> 'rasping_amazon' OR session_user <> 'rasping'
       OR NOT (SELECT rolsuper FROM pg_catalog.pg_roles WHERE rolname='rasping') THEN
        RAISE EXCEPTION 'C2-E-D-C-B: identidade administrativa ou banco divergente';
    END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_authid a
        WHERE a.rolname IN ('rasping_runtime','rasping_migrator')
          AND NOT a.rolcanlogin AND a.rolpassword IS NULL
          AND NOT a.rolsuper AND NOT a.rolinherit AND NOT a.rolcreatedb
          AND NOT a.rolcreaterole AND NOT a.rolreplication AND NOT a.rolbypassrls) <> 2 THEN
        RAISE EXCEPTION 'C2-E-D-C-B: roles tecnicas nao estao no estado inicial esperado';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_stat_activity
        WHERE datname=current_database() AND pid<>pg_backend_pid()
          AND backend_type='client backend') THEN
        RAISE EXCEPTION 'C2-E-D-C-B: conexoes de aplicacao existentes';
    END IF;
END;
$validacao$;
-- Senhas hexadecimais geradas com RNG criptografico e nunca armazenadas em SQL de disco.
ALTER ROLE rasping_runtime WITH LOGIN PASSWORD '__RUNTIME_SECRET__';
ALTER ROLE rasping_migrator WITH LOGIN PASSWORD '__MIGRATOR_SECRET__';
COMMIT;
