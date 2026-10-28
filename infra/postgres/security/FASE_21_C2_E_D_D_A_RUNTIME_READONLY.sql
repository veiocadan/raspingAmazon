-- C2-E-D-D-A: role runtime REAL, sem escrita. Executar apenas pela etapa automatizada.
-- Nao criar dados ou alterar estado; nao executar como administrador.
\set ON_ERROR_STOP on
BEGIN;
SET TRANSACTION READ ONLY;
DO $verificar$
DECLARE
    tabelas integer;
    pode_ler integer;
    pode_inserir integer;
    pode_atualizar integer;
    pode_excluir integer;
    pode_truncar integer;
    sequences integer;
    seq_usar integer;
    seq_atualizar integer;
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR current_user <> 'rasping_runtime'
       OR session_user <> 'rasping_runtime'
       OR inet_client_addr() IS NULL
       OR current_setting('transaction_read_only') <> 'on' THEN
        RAISE EXCEPTION 'C2-E-D-D-A: contexto do runtime divergente';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles r
        WHERE r.rolname='rasping_runtime' AND r.rolcanlogin
          AND NOT r.rolsuper AND NOT r.rolinherit AND NOT r.rolcreatedb
          AND NOT r.rolcreaterole AND NOT r.rolreplication AND NOT r.rolbypassrls
    ) THEN
        RAISE EXCEPTION 'C2-E-D-D-A: atributos runtime divergentes';
    END IF;

    IF NOT pg_catalog.has_database_privilege(current_user, current_database(), 'CONNECT')
       OR NOT pg_catalog.has_schema_privilege(current_user, 'public', 'USAGE')
       OR pg_catalog.has_schema_privilege(current_user, 'public', 'CREATE')
       OR pg_catalog.has_database_privilege(current_user, current_database(), 'CREATE') THEN
        RAISE EXCEPTION 'C2-E-D-D-A: privilegios estruturais runtime divergentes';
    END IF;

    SELECT count(*),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user,c.oid,'SELECT')),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user,c.oid,'INSERT')),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user,c.oid,'UPDATE')),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user,c.oid,'DELETE')),
           count(*) FILTER (WHERE pg_catalog.has_table_privilege(current_user,c.oid,'TRUNCATE'))
      INTO tabelas, pode_ler, pode_inserir, pode_atualizar, pode_excluir, pode_truncar
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind IN ('r','p');

    IF (tabelas,pode_ler,pode_inserir,pode_atualizar,pode_excluir,pode_truncar)
       IS DISTINCT FROM (29,27,25,12,0,0) THEN
        RAISE EXCEPTION 'C2-E-D-D-A: matriz runtime de tabelas divergente';
    END IF;

    -- Protecao explicita para relkind: nao chamar has_sequence_privilege em TOAST ou tabelas.
    SELECT count(*),
           count(*) FILTER (WHERE CASE WHEN c.relkind='S' THEN pg_catalog.has_sequence_privilege(current_user,c.oid,'USAGE') ELSE false END),
           count(*) FILTER (WHERE CASE WHEN c.relkind='S' THEN pg_catalog.has_sequence_privilege(current_user,c.oid,'UPDATE') ELSE false END)
      INTO sequences, seq_usar, seq_atualizar
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind='S';

    IF (sequences,seq_usar,seq_atualizar) IS DISTINCT FROM (26,23,0) THEN
        RAISE EXCEPTION 'C2-E-D-D-A: matriz runtime de sequences divergente';
    END IF;

    IF pg_catalog.has_table_privilege(current_user,'public.flyway_schema_history','SELECT')
       OR pg_catalog.has_table_privilege(current_user,'public.configuration_version','SELECT') THEN
        RAISE EXCEPTION 'C2-E-D-D-A: runtime acessa tabelas reservadas a migracao';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members m
               WHERE m.member = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname='rasping_runtime')) THEN
        RAISE EXCEPTION 'C2-E-D-D-A: membership runtime inesperada';
    END IF;

    -- Consultas SQL efetivas, nao apenas has_table_privilege. Nao exibem dados.
    PERFORM 1 FROM public.processing_job LIMIT 1;
    PERFORM 1 FROM public.processing_schedule LIMIT 1;
    PERFORM 1 FROM public.publication_outbox LIMIT 1;
END;
$verificar$;
ROLLBACK;
