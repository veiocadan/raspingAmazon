-- FASE 21-C2-E-C-C-B - Conferencia somente leitura do ARQUIVO HBA no disco.
-- A carga efetiva depende tambem dos testes positivos e negativos de login.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

DO $assert$
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR session_user <> 'rasping'
       OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-C-C-B: identidade administrativa inesperada';
    END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_hba_file_rules) <> 7
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_hba_file_rules WHERE error IS NOT NULL) THEN
        RAISE EXCEPTION 'C2-E-C-C-B: HBA nao possui sete regras validas';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM pg_catalog.pg_hba_file_rules
         WHERE file_name IS DISTINCT FROM current_setting('hba_file')
            OR auth_method IS DISTINCT FROM 'scram-sha-256'
            OR options IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'C2-E-C-C-B: politica contem include, metodo ou opcoes inesperados';
    END IF;
    IF EXISTS (
        WITH regras AS (
            SELECT rule_number, type, database, user_name, address, netmask
              FROM pg_catalog.pg_hba_file_rules
        ), esperado AS (
            SELECT * FROM (VALUES
                (1, 'local', 'all',         'all', NULL::text),
                (2, 'host',  'all',         'all', '127.0.0.1'),
                (3, 'host',  'all',         'all', '::1'),
                (4, 'local', 'replication', 'all', NULL::text),
                (5, 'host',  'replication', 'all', '127.0.0.1'),
                (6, 'host',  'replication', 'all', '::1'),
                (7, 'host',  'all',         'all', 'all')
            ) AS x(ordem, tipo, banco, identidade, endereco)
        )
        SELECT 1 FROM esperado e
        FULL JOIN regras r ON r.rule_number = e.ordem
        WHERE r.rule_number IS NULL OR e.ordem IS NULL
           OR r.type IS DISTINCT FROM e.tipo
           OR r.database IS DISTINCT FROM ARRAY[e.banco]::text[]
           OR r.user_name IS DISTINCT FROM ARRAY[e.identidade]::text[]
           OR r.address IS DISTINCT FROM e.endereco
    ) THEN
        RAISE EXCEPTION 'C2-E-C-C-B: ordem, origem ou escopo divergem da proposta';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_hba_file_rules
         WHERE type = 'host'
           AND address = '127.0.0.1'
           AND netmask IS DISTINCT FROM '255.255.255.255'
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_hba_file_rules
         WHERE type = 'host'
           AND address = '::1'
           AND netmask IS DISTINCT FROM 'ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff'
    ) THEN
        RAISE EXCEPTION 'C2-E-C-C-B: mascara de loopback diverge da proposta';
    END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_roles
        WHERE rolname IN ('rasping_runtime', 'rasping_migrator')
          AND NOT rolcanlogin AND NOT rolsuper) <> 2 THEN
        RAISE EXCEPTION 'C2-E-C-C-B: identidades tecnicas alteradas inesperadamente';
    END IF;
END;
$assert$;

\echo === C2-E-C-C-B.1 - Metodos HBA validados (sem enderecos) ===
SELECT type AS tipo, auth_method AS autenticacao, count(*) AS total
  FROM pg_catalog.pg_hba_file_rules
 GROUP BY type, auth_method
 ORDER BY type, auth_method;
ROLLBACK;
\echo === C2-E-C-C-B HBA ARQUIVO PASS - conferir tambem autenticacao real ===
