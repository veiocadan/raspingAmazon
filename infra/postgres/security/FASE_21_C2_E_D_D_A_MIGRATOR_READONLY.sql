-- C2-E-D-D-A: role migrator REAL, verificacao de schema/Flyway sem executar DDL.
\set ON_ERROR_STOP on
BEGIN;
SET TRANSACTION READ ONLY;
DO $verificar$
DECLARE
    tabelas integer;
    sequences integer;
BEGIN
    IF current_database() <> 'rasping_amazon'
       OR current_user <> 'rasping_migrator'
       OR session_user <> 'rasping_migrator'
       OR inet_client_addr() IS NULL
       OR current_setting('transaction_read_only') <> 'on' THEN
        RAISE EXCEPTION 'C2-E-D-D-A: contexto do migrator divergente';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles r
        WHERE r.rolname='rasping_migrator' AND r.rolcanlogin
          AND NOT r.rolsuper AND NOT r.rolinherit AND NOT r.rolcreatedb
          AND NOT r.rolcreaterole AND NOT r.rolreplication AND NOT r.rolbypassrls
    ) THEN
        RAISE EXCEPTION 'C2-E-D-D-A: atributos migrator divergentes';
    END IF;

    IF (SELECT pg_catalog.pg_get_userbyid(n.nspowner)
          FROM pg_catalog.pg_namespace n WHERE n.nspname='public') <> current_user
       OR NOT pg_catalog.has_database_privilege(current_user,current_database(),'CONNECT')
       OR NOT pg_catalog.has_schema_privilege(current_user,'public','USAGE')
       OR NOT pg_catalog.has_schema_privilege(current_user,'public','CREATE')
       OR pg_catalog.has_database_privilege(current_user,current_database(),'CREATE') THEN
        RAISE EXCEPTION 'C2-E-D-D-A: owner/privilegios migrator divergentes';
    END IF;

    SELECT count(*) INTO tabelas
      FROM pg_catalog.pg_class c
      JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
      WHERE n.nspname='public' AND c.relkind IN ('r','p')
        AND pg_catalog.pg_get_userbyid(c.relowner)=current_user;
    SELECT count(*) INTO sequences
      FROM pg_catalog.pg_class c
      JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
      WHERE n.nspname='public' AND c.relkind='S'
        AND pg_catalog.pg_get_userbyid(c.relowner)=current_user;

    IF tabelas<>29 OR sequences<>26
       OR NOT pg_catalog.has_table_privilege(current_user,'public.flyway_schema_history','SELECT')
       OR NOT pg_catalog.has_table_privilege(current_user,'public.flyway_schema_history','INSERT')
       OR NOT pg_catalog.has_table_privilege(current_user,'public.flyway_schema_history','UPDATE') THEN
        RAISE EXCEPTION 'C2-E-D-D-A: propriedade/matriz Flyway divergentes';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members m
               WHERE m.member = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname='rasping_migrator')) THEN
        RAISE EXCEPTION 'C2-E-D-D-A: membership migrator inesperada';
    END IF;

    -- Comprova SELECT real na tabela Flyway sem mostrar conteudo.
    PERFORM 1 FROM public.flyway_schema_history LIMIT 1;
END;
$verificar$;
ROLLBACK;
