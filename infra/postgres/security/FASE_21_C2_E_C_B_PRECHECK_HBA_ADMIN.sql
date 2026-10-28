-- FASE 21-C2-E-C-B - Precheck ANTES de alterar pg_hba.conf.
-- O script le o HBA atual do cluster e metadados de autenticacao,
-- sem imprimir caminhos, enderecos, senhas ou hashes.
-- Nao altera dados, arquivo HBA, roles, parametros ou sessoes.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

DO $precheck$
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR current_user <> 'rasping'
       OR session_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-C-B: conexao administrativa inesperada';
    END IF;

    IF current_setting('server_version_num')::integer < 180000
       OR current_setting('server_version_num')::integer >= 190000 THEN
        RAISE EXCEPTION 'C2-E-C-B: precheck previsto para PostgreSQL 18';
    END IF;

    IF current_setting('password_encryption') <> 'scram-sha-256' THEN
        RAISE EXCEPTION 'C2-E-C-B: novas senhas nao estao configuradas para SCRAM';
    END IF;

    -- Verifica o FORMATO do segredo ja armazenado, sem devolver seu valor.
    -- Esta verificacao nao prova que a senha no ambiente corresponda ao hash.
    IF NOT EXISTS (
        SELECT 1
          FROM pg_catalog.pg_authid AS a
         WHERE a.rolname = 'rasping'
           AND a.rolcanlogin
           AND a.rolsuper
           AND a.rolpassword LIKE 'SCRAM-SHA-256$%'
           AND (a.rolvaliduntil IS NULL OR a.rolvaliduntil > CURRENT_TIMESTAMP)
    ) THEN
        RAISE EXCEPTION 'C2-E-C-B: login administrativo sem senha SCRAM valida';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_catalog.pg_hba_file_rules WHERE error IS NOT NULL) THEN
        RAISE EXCEPTION 'C2-E-C-B: HBA atual contem linha invalida';
    END IF;

    -- Falha fechada: um include, uma regra nova ou mudanca de ordem
    -- exige nova avaliacao humana da politica; nao ajustar automaticamente.
    IF (SELECT count(*) FROM pg_catalog.pg_hba_file_rules) <> 7 THEN
        RAISE EXCEPTION 'C2-E-C-B: inventario HBA diferente das 7 regras auditadas';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_hba_file_rules AS h
         WHERE h.file_name IS DISTINCT FROM current_setting('hba_file')
    ) THEN
        RAISE EXCEPTION 'C2-E-C-B: HBA inclui outro arquivo; revisar antes de alterar';
    END IF;

    -- Comparacao por posicao e escopo: duas regras de socket local,
    -- quatro regras host loopback e uma geral SCRAM.
    -- O precheck nao imprime a lista de enderecos ou o arquivo integral.
    IF EXISTS (
        WITH expected AS (
            SELECT *
              FROM (VALUES
                (1, 'local', 'all',         'all', NULL::text,  'trust'),
                (2, 'host',  'all',         'all', '127.0.0.1', 'trust'),
                (3, 'host',  'all',         'all', '::1',       'trust'),
                (4, 'local', 'replication', 'all', NULL::text,  'trust'),
                (5, 'host',  'replication', 'all', '127.0.0.1', 'trust'),
                (6, 'host',  'replication', 'all', '::1',       'trust'),
                (7, 'host',  'all',         'all', 'all',       'scram-sha-256')
              ) AS e(position, connection_type, database_name,
                     role_name, address_value, method)
        )
        SELECT 1
          FROM expected AS e
          FULL JOIN pg_catalog.pg_hba_file_rules AS h
            ON h.rule_number = e.position
         WHERE e.position IS NULL
            OR h.rule_number IS NULL
            OR h.type IS DISTINCT FROM e.connection_type
            OR h.database IS DISTINCT FROM ARRAY[e.database_name]::text[]
            OR h.user_name IS DISTINCT FROM ARRAY[e.role_name]::text[]
            OR h.address IS DISTINCT FROM e.address_value
            OR h.auth_method IS DISTINCT FROM e.method
    ) THEN
        RAISE EXCEPTION 'C2-E-C-B: ordem, metodo ou escopo HBA divergiu da auditoria';
    END IF;

    -- Verifica a mascara individual para cada host trust: /32 ou /128.
    IF EXISTS (
        SELECT 1
          FROM pg_catalog.pg_hba_file_rules AS h
         WHERE h.type = 'host'
           AND h.auth_method = 'trust'
           AND NOT (
               (h.address = '127.0.0.1'
                AND h.netmask IN ('255.255.255.255', '32'))
               OR
               (h.address = '::1'
                AND h.netmask IN ('ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff', '128'))
           )
    ) THEN
        RAISE EXCEPTION 'C2-E-C-B: regra host trust nao esta limitada a loopback unico';
    END IF;
END;
$precheck$;

\echo === C2-E-C-B.1 - Baseline HBA, sem enderecos ===
SELECT type AS tipo,
       auth_method AS metodo,
       count(*) AS quantidade
  FROM pg_catalog.pg_hba_file_rules
 WHERE error IS NULL
 GROUP BY type, auth_method
 ORDER BY type, auth_method;

\echo === C2-E-C-B.2 - Prontidao administrativa (sem senhas) ===
SELECT 'SCRAM-ARMAZENADO' AS credencial_admin,
       current_setting('password_encryption') AS algoritmo_novas_senhas,
       current_setting('ssl') AS tls_servidor,
       'NAO_MODIFICADO' AS estado_hba;

ROLLBACK;
\echo === C2-E-C-B HBA PRECHECK PASS - arquivo e autenticacao nao alterados ===
