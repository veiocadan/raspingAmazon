-- FASE 21-C2-E-B - Inventario e alcance de regras HBA.
-- PostgreSQL 18; somente leitura, sem exibir IPs, senhas ou hashes.
-- Nao altera o arquivo pg_hba.conf nem recarrega o servidor.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'
BEGIN TRANSACTION READ ONLY;

DO $guard$
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR session_user <> 'rasping'
       OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-E-B: conectar como rasping em rasping_amazon';
    END IF;
    IF current_setting('server_version_num')::integer < 180000 THEN
        RAISE EXCEPTION 'C2-E-B: auditoria preparada para PostgreSQL 18';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_hba_file_rules WHERE error IS NOT NULL) THEN
        RAISE EXCEPTION 'C2-E-B: ha regras HBA com erro de sintaxe; corrigir antes de avancar';
    END IF;
END;
$guard$;

\echo === C2-E-B.1 - Regras ordenadas (enderecos omitidos) ===
WITH hba AS MATERIALIZED (
    SELECT h.rule_number, h.file_name, h.line_number,
           h.type, h.database, h.user_name, h.address, h.netmask, h.auth_method
    FROM pg_catalog.pg_hba_file_rules AS h
    WHERE h.error IS NULL
), escopo AS (
    SELECT h.*,
           CASE
               WHEN 'all' = ANY(h.database) THEN 'TODOS_BANCOS'
               WHEN 'rasping_amazon' = ANY(h.database) THEN 'BANCO_APLICACAO_EXPLICITO'
               WHEN 'replication' = ANY(h.database) THEN 'REPLICACAO_OU_MISTO'
               WHEN 'sameuser' = ANY(h.database)
                    OR 'samerole' = ANY(h.database)
                    OR 'samegroup' = ANY(h.database) THEN 'MATCH_DINAMICO_REVISAR'
               ELSE 'OUTRO_OU_INDIRETO_REVISAR'
           END AS escopo_banco,
           CASE
               WHEN 'all' = ANY(h.user_name) THEN 'TODAS_ROLES'
               WHEN 'rasping' = ANY(h.user_name)
                    AND ('rasping_runtime' = ANY(h.user_name)
                         OR 'rasping_migrator' = ANY(h.user_name)) THEN 'ADMIN_E_TECNICAS_EXPLICITAS'
               WHEN 'rasping' = ANY(h.user_name) THEN 'ADMIN_EXPLICITO'
               WHEN 'rasping_runtime' = ANY(h.user_name)
                    OR 'rasping_migrator' = ANY(h.user_name) THEN 'ROLE_TECNICA_EXPLICITA'
               WHEN EXISTS (
                   SELECT 1
                   FROM unnest(h.user_name) AS x(nome)
                   WHERE left(x.nome, 1) IN ('+', '@', '/')
               ) THEN 'MEMBROS_ARQUIVO_OU_REGEX_REVISAR'
               ELSE 'OUTRAS_ROLES_OU_INDIRETO'
           END AS escopo_role,
           CASE
               WHEN h.type = 'local' THEN 'SOCKET_UNIX'
               WHEN h.address = 'all'
                    OR h.address IN ('0.0.0.0/0', '::/0') THEN 'QUALQUER_ORIGEM'
               WHEN h.address = 'samehost' THEN 'ENDERECOS_DO_SERVIDOR_REVISAR'
               WHEN h.address = 'samenet' THEN 'REDES_CONECTADAS_REVISAR'
               WHEN h.address IN ('127.0.0.1', '127.0.0.1/32', '::1', '::1/128')
                    AND (h.netmask IS NULL OR h.netmask IN
                       ('255.255.255.255', 'ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff'))
                    THEN 'LOOPBACK_UNICO'
               WHEN h.address IS NULL THEN 'SEM_ENDERECO_REVISAR'
               WHEN h.address ~ '^127[.]' OR h.address LIKE '::1/%'
                    THEN 'POSSIVEL_LOOPBACK_REVISAR_MASCARA'
               ELSE 'CIDR_IP_OU_HOSTNAME_REVISAR'
           END AS escopo_origem
    FROM hba AS h
)
SELECT rule_number AS ordem, line_number AS linha,
       CASE WHEN file_name = current_setting('hba_file')
            THEN 'ARQUIVO_PRINCIPAL' ELSE 'ARQUIVO_INCLUIDO_OU_CAMINHO_DIFERENTE' END AS origem_regra,
       type AS tipo_conexao, auth_method AS autenticacao,
       escopo_banco, escopo_role, escopo_origem,
       (netmask IS NOT NULL) AS usa_mascara_separada
FROM escopo
ORDER BY ordem NULLS LAST, linha;

\echo === C2-E-B.2 - Totais por tipo e metodo ===
SELECT type AS tipo_conexao, auth_method AS autenticacao, count(*) AS total
FROM pg_catalog.pg_hba_file_rules
WHERE error IS NULL
GROUP BY type, auth_method
ORDER BY type, auth_method;

\echo === C2-E-B.3 - Ordem das regras host trust ===
SELECT rule_number AS ordem, line_number AS linha,
       type AS tipo_conexao, auth_method AS autenticacao,
       CASE WHEN 'all' = ANY(database) THEN 'TODOS_BANCOS'
            WHEN 'rasping_amazon' = ANY(database) THEN 'BANCO_APLICACAO_EXPLICITO'
            WHEN 'replication' = ANY(database) THEN 'REPLICACAO_OU_MISTO'
            ELSE 'OUTRO_OU_INDIRETO' END AS escopo_banco,
       CASE WHEN 'all' = ANY(user_name) THEN 'TODAS_ROLES'
            WHEN 'rasping' = ANY(user_name) THEN 'ADMIN_EXPLICITO'
            WHEN 'rasping_runtime' = ANY(user_name)
                 OR 'rasping_migrator' = ANY(user_name) THEN 'ROLES_TECNICAS_EXPLICITAS'
            ELSE 'OUTRAS_ROLES_OU_INDIRETO' END AS escopo_role,
       CASE WHEN address = 'all' OR address IN ('0.0.0.0/0','::/0') THEN 'QUALQUER_ORIGEM'
            WHEN address IN ('127.0.0.1','127.0.0.1/32','::1','::1/128')
                 AND (netmask IS NULL OR netmask IN
                     ('255.255.255.255','ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff'))
                 THEN 'LOOPBACK_UNICO'
            WHEN address IN ('samehost','samenet') THEN 'REDE_DINAMICA_REVISAR'
            ELSE 'CIDR_IP_OU_HOSTNAME_REVISAR' END AS escopo_origem
FROM pg_catalog.pg_hba_file_rules
WHERE error IS NULL AND type LIKE 'host%' AND auth_method = 'trust'
ORDER BY rule_number;

ROLLBACK;
\echo === C2-E-B INVENTARIO HBA CONCLUIDO - analise humana ainda necessaria ===
