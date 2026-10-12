-- FASE 21-C2-E-C-A: gate somente leitura antes da restricao de porta Docker.
-- Nao verifica por SQL o bind do host, que deve ser verificado via Compose/Docker.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'

BEGIN TRANSACTION READ ONLY;

\echo === C2-E-C-A.1 - Estado essencial antes da mudanca de rede ===
SELECT current_database() AS banco,
       session_user AS autenticado_como,
       current_setting('server_version') AS versao,
       current_setting('password_encryption') AS politica_hash,
       current_setting('listen_addresses') AS escuta_interna;

\echo === C2-E-C-A.2 - Roles tecnicas ===
SELECT rolname AS role_name,
       rolcanlogin AS pode_login,
       rolsuper AS superuser,
       rolcreatedb AS cria_banco,
       rolcreaterole AS cria_role
FROM pg_catalog.pg_roles
WHERE rolname IN ('rasping_migrator', 'rasping_runtime')
ORDER BY rolname;

\echo === C2-E-C-A.3 - Quantidade de objetos e proprietarios ===
SELECT c.relkind AS tipo,
       pg_catalog.pg_get_userbyid(c.relowner) AS proprietario,
       count(*) AS quantidade
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r','p','S')
GROUP BY c.relkind, c.relowner
ORDER BY c.relkind;

\echo === C2-E-C-A.4 - Resumo HBA: enderecos omitidos ===
SELECT type AS tipo,
       auth_method AS autenticacao,
       count(*) AS regras
FROM pg_catalog.pg_hba_file_rules
WHERE error IS NULL
GROUP BY type, auth_method
ORDER BY type, auth_method;

DO $gate$
DECLARE
    v_public_owner text;
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR session_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-C-A: banco ou usuario inesperado';
    END IF;

    IF current_setting('password_encryption') <> 'scram-sha-256' THEN
        RAISE EXCEPTION 'C2-E-C-A: configuracao de hash de senhas divergente';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_roles
        WHERE rolname IN ('rasping_migrator','rasping_runtime')
          AND NOT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb
          AND NOT rolcreaterole) <> 2 THEN
        RAISE EXCEPTION 'C2-E-C-A: atributos das roles divergentes';
    END IF;

    SELECT pg_catalog.pg_get_userbyid(n.nspowner)
      INTO v_public_owner
    FROM pg_catalog.pg_namespace n
    WHERE n.nspname = 'public';

    IF v_public_owner IS DISTINCT FROM 'rasping_migrator' THEN
        RAISE EXCEPTION 'C2-E-C-A: proprietario do schema public divergente';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
          AND pg_catalog.pg_get_userbyid(c.relowner) = 'rasping_migrator') <> 29 THEN
        RAISE EXCEPTION 'C2-E-C-A: inventario/owner das tabelas divergente';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'S'
          AND pg_catalog.pg_get_userbyid(c.relowner) = 'rasping_migrator') <> 26 THEN
        RAISE EXCEPTION 'C2-E-C-A: inventario/owner das sequences divergente';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_catalog.pg_hba_file_rules WHERE error IS NOT NULL) THEN
        RAISE EXCEPTION 'C2-E-C-A: arquivo HBA com regra invalida';
    END IF;
END
$gate$;

ROLLBACK;
\echo === C2-E-C-A PRECHECK PASS - nenhuma alteracao executada ===
