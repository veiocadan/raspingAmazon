-- FASE 21-C2-D - REVERTER OWNERSHIP PARA rasping - USAR SOMENTE SOB DECISAO EXPRESSA
-- Inventario exato: 29 tabelas e 26 sequences; roles NOLOGIN.
\set ON_ERROR_STOP on
\pset pager off

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
DO $revert$
DECLARE
    expected_tables text[] := ARRAY[
        'configuration_version',
        'deal_candidate',
        'deal_evaluation',
        'deal_evaluation_momentum_audit',
        'deal_evaluation_rule_result',
        'deal_evaluation_score_factor',
        'filter_profile',
        'flyway_schema_history',
        'integration_observation',
        'offer_evidence',
        'offer_payment_condition',
        'offer_payment_condition_method',
        'offer_snapshot',
        'processing_job',
        'processing_job_reprocess_event',
        'processing_run',
        'processing_schedule',
        'product',
        'publication',
        'publication_attempt',
        'publication_cadence_profile',
        'publication_delivery_resolution_event',
        'publication_outbox',
        'publication_quota_profile',
        'publication_rate_limit_state',
        'publication_selection_decision',
        'publication_selection_profile',
        'publication_selection_run',
        'score_profile'
    ]::text[];
    expected_sequences text[] := ARRAY[
        'configuration_version_id_seq',
        'deal_candidate_id_seq',
        'deal_evaluation_id_seq',
        'deal_evaluation_momentum_audit_id_seq',
        'deal_evaluation_rule_result_id_seq',
        'deal_evaluation_score_factor_id_seq',
        'filter_profile_id_seq',
        'integration_observation_id_seq',
        'offer_evidence_id_seq',
        'offer_payment_condition_id_seq',
        'offer_payment_condition_method_id_seq',
        'offer_snapshot_id_seq',
        'processing_job_id_seq',
        'processing_job_reprocess_event_id_seq',
        'processing_run_id_seq',
        'product_id_seq',
        'publication_attempt_id_seq',
        'publication_cadence_profile_id_seq',
        'publication_delivery_resolution_event_id_seq',
        'publication_id_seq',
        'publication_outbox_id_seq',
        'publication_quota_profile_id_seq',
        'publication_selection_decision_id_seq',
        'publication_selection_profile_id_seq',
        'publication_selection_run_id_seq',
        'score_profile_id_seq'
    ]::text[];
    object_name text;
    owner_name text;
BEGIN

    IF current_database() <> 'rasping_amazon' OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-D: executar apenas em rasping_amazon como rasping';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_roles WHERE rolname = 'rasping' AND rolsuper) <> 1 THEN
        RAISE EXCEPTION 'C2-D: rasping nao e superuser';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_roles
        WHERE rolname IN ('rasping_migrator', 'rasping_runtime')) <> 2 THEN
        RAISE EXCEPTION 'C2-D: roles tecnicas ausentes';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname IN ('rasping_migrator', 'rasping_runtime')
        AND (rolcanlogin OR rolinherit OR rolsuper OR rolcreatedb OR rolcreaterole
             OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'C2-D: roles tecnicas nao atendem contrato NOLOGIN/NOINHERIT';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_auth_members m
        JOIN pg_catalog.pg_roles r ON r.oid IN (m.member, m.roleid)
        WHERE r.rolname IN ('rasping_migrator', 'rasping_runtime')
    ) THEN
        RAISE EXCEPTION 'C2-D: membership inesperado';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('v','m','f')
    ) THEN
        RAISE EXCEPTION 'C2-D: view, materialized view ou foreign table nao inventariada';
    END IF;

    IF (SELECT array_agg(c.relname::text ORDER BY c.relname::text) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r','p'))
        IS DISTINCT FROM
        (SELECT array_agg(item_name ORDER BY item_name) FROM unnest(expected_tables) AS expected(item_name)) THEN
        RAISE EXCEPTION 'C2-D: inventario das 29 tabelas divergiu';
    END IF;

    IF (SELECT array_agg(c.relname::text ORDER BY c.relname::text) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'S')
        IS DISTINCT FROM
        (SELECT array_agg(item_name ORDER BY item_name) FROM unnest(expected_sequences) AS expected(item_name)) THEN
        RAISE EXCEPTION 'C2-D: inventario das 26 sequences divergiu';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'S'
          AND NOT EXISTS (
            SELECT 1 FROM pg_catalog.pg_depend dep
            WHERE dep.classid = 'pg_catalog.pg_class'::regclass
              AND dep.objid = c.oid AND dep.refclassid = 'pg_catalog.pg_class'::regclass
              AND dep.deptype IN ('a','i')
          )
    ) THEN
        RAISE EXCEPTION 'C2-D: sequence sem dependencia OWNED BY catalogada';
    END IF;

    IF NOT pg_catalog.has_database_privilege('rasping_runtime', 'rasping_amazon', 'CONNECT')
      OR NOT pg_catalog.has_schema_privilege('rasping_runtime', 'public', 'USAGE')
      OR pg_catalog.has_schema_privilege('rasping_runtime', 'public', 'CREATE') THEN
        RAISE EXCEPTION 'C2-D: privilegios base da role runtime divergentes';
    END IF;

    IF (SELECT count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'SELECT')) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p')) <> 27
    OR (SELECT count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'INSERT')) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p')) <> 25
    OR (SELECT count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'UPDATE')) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p')) <> 12
    OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p')
          AND (pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'DELETE')
          OR pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'TRUNCATE'))
    )
    OR (
        WITH valid_sequences AS MATERIALIZED (
            SELECT c.oid
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public'
              AND c.relkind = 'S'
        )
        SELECT count(*)
        FROM valid_sequences s
        WHERE pg_catalog.has_sequence_privilege(
            'rasping_runtime', s.oid, 'USAGE'
        )
    ) <> 23
    OR EXISTS (
        WITH valid_sequences AS MATERIALIZED (
            SELECT c.oid
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public'
              AND c.relkind = 'S'
        )
        SELECT 1
        FROM valid_sequences s
        WHERE pg_catalog.has_sequence_privilege(
            'rasping_runtime', s.oid, 'UPDATE'
        )
    ) THEN
        RAISE EXCEPTION 'C2-D: matriz de grants C2-C divergiu';
    END IF;

    IF pg_catalog.has_table_privilege('rasping_runtime', 'public.flyway_schema_history', 'SELECT')
    OR pg_catalog.has_table_privilege('rasping_runtime', 'public.configuration_version', 'SELECT') THEN
        RAISE EXCEPTION 'C2-D: runtime nao pode acessar historico Flyway/configuracao';
    END IF;

    IF (SELECT pg_catalog.pg_get_userbyid(n.nspowner) FROM pg_catalog.pg_namespace n
        WHERE n.nspname = 'public') <> 'rasping_migrator' THEN
        RAISE EXCEPTION 'C2-D: dono do schema public inesperado';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r','p','S')
          AND pg_catalog.pg_get_userbyid(c.relowner) <> 'rasping_migrator'
    ) THEN
        RAISE EXCEPTION 'C2-D: ownership das tabelas/sequences divergente';
    END IF;

    FOREACH object_name IN ARRAY expected_tables LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO rasping', object_name);
    END LOOP;

    FOREACH object_name IN ARRAY expected_sequences LOOP
        SELECT pg_catalog.pg_get_userbyid(c.relowner) INTO owner_name
        FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = object_name AND c.relkind = 'S';
        IF owner_name = 'rasping_migrator' THEN
            EXECUTE format('ALTER SEQUENCE public.%I OWNER TO rasping', object_name);
        ELSIF owner_name <> 'rasping' OR owner_name IS NULL THEN
            RAISE EXCEPTION 'C2-D: owner inesperado de sequence %', object_name;
        END IF;
    END LOOP;

    ALTER SCHEMA public OWNER TO pg_database_owner;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p','S')
          AND pg_catalog.pg_get_userbyid(c.relowner) <> 'rasping'
    ) OR (SELECT pg_catalog.pg_get_userbyid(n.nspowner) FROM pg_catalog.pg_namespace n
        WHERE n.nspname = 'public') <> 'pg_database_owner' THEN
        RAISE EXCEPTION 'C2-D: rollback incompleto';
    END IF;
END;
$revert$;
COMMIT;
\echo === C2-D ROLLBACK COMMIT - proprietarios restaurados ===
