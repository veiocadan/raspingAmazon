-- FASE 21-C2-E-B - Superficie PostgreSQL e observacoes de seguranca.
-- Somente leitura; nao divulga a lista de enderecos de escuta, clientes ou segredos.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

DO $guard$
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR session_user <> 'rasping' OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-B: banco ou identidade incorretos';
    END IF;
END;
$guard$;

\echo === C2-E-B.4 - Escuta e criptografia ===
SELECT CASE
           WHEN current_setting('listen_addresses') = '*'
                THEN 'TODAS_INTERFACES'
           WHEN current_setting('listen_addresses') IN ('localhost','127.0.0.1','::1')
                THEN 'APENAS_LOOPBACK_CONFIGURADO'
           WHEN current_setting('listen_addresses') = '' THEN 'SEM_TCP'
           ELSE 'CONFIGURACAO_ESPECIFICA_REVISAR'
       END AS escuta_tcp,
       current_setting('port') AS porta_interna,
       current_setting('ssl') AS ssl_habilitado,
       current_setting('password_encryption') AS algoritmo_senhas,
       pg_catalog.pg_conf_load_time() AS configuracao_carregada_em;

\echo === C2-E-B.5 - Risco host trust e contagem de regras ===
SELECT count(*) FILTER (WHERE type LIKE 'host%' AND auth_method = 'trust') AS host_trust,
       count(*) FILTER (WHERE type = 'local' AND auth_method = 'trust') AS socket_trust,
       count(*) FILTER (WHERE type LIKE 'host%' AND auth_method = 'scram-sha-256') AS host_scram,
       count(*) FILTER (WHERE type LIKE 'host%' AND auth_method IN ('md5','password')) AS host_senha_legada,
       count(*) FILTER (WHERE error IS NOT NULL) AS regras_invalidas
FROM pg_catalog.pg_hba_file_rules;

\echo === C2-E-B.6 - Avaliacao preliminar (nao e aprovacao de seguranca) ===
SELECT CASE
           WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_hba_file_rules WHERE error IS NOT NULL)
               THEN 'BLOQUEADO: REGRAS_INVALIDAS'
           WHEN EXISTS (
               SELECT 1 FROM pg_catalog.pg_hba_file_rules
               WHERE error IS NULL AND type LIKE 'host%' AND auth_method = 'trust'
           ) THEN 'PENDENTE: REVISAR HOST TRUST E ORDEM DAS REGRAS'
           WHEN EXISTS (
               SELECT 1 FROM pg_catalog.pg_hba_file_rules
               WHERE error IS NULL AND type LIKE 'host%' AND auth_method IN ('md5','password')
           ) THEN 'PENDENTE: REVISAR AUTENTICACAO HOST LEGADA'
           ELSE 'SEM_HOST_TRUST_IDENTIFICADO: VALIDAR PORTAS E CONEXOES'
       END AS estado_preliminar;

ROLLBACK;
\echo === C2-E-B SUPERFICIE CONCLUIDA - nenhum parametro alterado ===
