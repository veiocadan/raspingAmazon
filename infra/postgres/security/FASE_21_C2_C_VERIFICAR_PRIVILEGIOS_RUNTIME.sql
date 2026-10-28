-- FASE 21-C2-C - Auditoria de grants runtime. Somente leitura.
\set ON_ERROR_STOP on
\pset pager off
\pset null '[NULL]'

BEGIN TRANSACTION READ ONLY;

\echo === C2-C.1: Contexto ===
SELECT current_database() AS database_name, current_user AS executed_by,
       current_setting('transaction_read_only') AS read_only;

\echo === C2-C.2: Roles administrativas e tecnicas ===
SELECT rolname AS role_name, rolcanlogin AS can_login, rolinherit AS inherits,
       rolsuper AS is_superuser, rolcreatedb AS can_create_database,
       rolcreaterole AS can_create_role, rolreplication AS replication,
       rolbypassrls AS bypass_row_security
FROM pg_catalog.pg_roles
WHERE rolname IN ('rasping', 'rasping_migrator', 'rasping_runtime')
ORDER BY rolname;

\echo === C2-C.3: Contagem de privilegios efetivos da role runtime ===
SELECT count(*) AS total_tables,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'SELECT')) AS selectable,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'INSERT')) AS insertable,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'UPDATE')) AS updatable,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'DELETE')) AS deletable,
       count(*) FILTER (WHERE pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'TRUNCATE')) AS truncatable
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p');

\echo === C2-C.4: Contagem de privilegios sobre sequences ===
SELECT count(*) AS total_sequences,
       count(*) FILTER (WHERE pg_catalog.has_sequence_privilege('rasping_runtime', c.oid, 'USAGE')) AS usable,
       count(*) FILTER (WHERE pg_catalog.has_sequence_privilege('rasping_runtime', c.oid, 'UPDATE')) AS updatable
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'S';

\echo === C2-C.5: Flyway e tabela historica excluidos ===
SELECT c.relname AS table_name,
       pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'SELECT') AS can_select,
       pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'INSERT') AS can_insert,
       pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'UPDATE') AS can_update,
       pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'DELETE') AS can_delete
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relname IN ('configuration_version', 'flyway_schema_history')
ORDER BY c.relname;

\echo === C2-C.6: Banco e schema ===
SELECT pg_catalog.has_database_privilege('rasping_runtime', 'rasping_amazon', 'CONNECT') AS can_connect,
       pg_catalog.has_schema_privilege('rasping_runtime', 'public', 'USAGE') AS can_use_public,
       pg_catalog.has_schema_privilege('rasping_runtime', 'public', 'CREATE') AS can_create_public;

\echo === C2-C.7: Assertion da matriz exata ===
DO $assertions$
DECLARE
    relation_name text;
    sequence_name text;
BEGIN
    -- Os 29 objetos e as 26 sequences ja foram verificados no preflight.
    -- Matriz positiva exata: a role recebe somente privilegios modelados.
    FOREACH relation_name IN ARRAY ARRAY[
    'deal_candidate',
    'deal_evaluation',
    'deal_evaluation_momentum_audit',
    'deal_evaluation_rule_result',
    'deal_evaluation_score_factor',
    'filter_profile',
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
]::text[] LOOP
        IF NOT pg_catalog.has_table_privilege('rasping_runtime',
            format('public.%I', relation_name), 'SELECT') THEN
            RAISE EXCEPTION 'C2-C: SELECT ausente em %', relation_name;
        END IF;
    END LOOP;

    FOREACH relation_name IN ARRAY ARRAY[
    'deal_candidate',
    'deal_evaluation',
    'deal_evaluation_momentum_audit',
    'deal_evaluation_rule_result',
    'deal_evaluation_score_factor',
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
    'publication_selection_run'
]::text[] LOOP
        IF NOT pg_catalog.has_table_privilege('rasping_runtime',
            format('public.%I', relation_name), 'INSERT') THEN
            RAISE EXCEPTION 'C2-C: INSERT ausente em %', relation_name;
        END IF;
    END LOOP;

    FOREACH relation_name IN ARRAY ARRAY[
    'deal_candidate',
    'processing_job',
    'processing_run',
    'processing_schedule',
    'product',
    'publication',
    'publication_attempt',
    'publication_cadence_profile',
    'publication_outbox',
    'publication_quota_profile',
    'publication_rate_limit_state',
    'publication_selection_profile'
]::text[] LOOP
        IF NOT pg_catalog.has_table_privilege('rasping_runtime',
            format('public.%I', relation_name), 'UPDATE') THEN
            RAISE EXCEPTION 'C2-C: UPDATE ausente em %', relation_name;
        END IF;
    END LOOP;

    FOREACH sequence_name IN ARRAY ARRAY[
    'deal_candidate_id_seq',
    'deal_evaluation_id_seq',
    'deal_evaluation_momentum_audit_id_seq',
    'deal_evaluation_rule_result_id_seq',
    'deal_evaluation_score_factor_id_seq',
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
    'publication_selection_run_id_seq'
]::text[] LOOP
        IF NOT pg_catalog.has_sequence_privilege('rasping_runtime',
            format('public.%I', sequence_name), 'USAGE') THEN
            RAISE EXCEPTION 'C2-C: USAGE ausente na sequence %', sequence_name;
        END IF;
    END LOOP;

    IF NOT pg_catalog.has_database_privilege('rasping_runtime',
        'rasping_amazon', 'CONNECT')
       OR NOT pg_catalog.has_schema_privilege('rasping_runtime',
        'public', 'USAGE')
       OR pg_catalog.has_schema_privilege('rasping_runtime',
        'public', 'CREATE') THEN
        RAISE EXCEPTION 'C2-C: privilegios database/schema divergentes';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND (
            pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'DELETE')
            OR pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'TRUNCATE')
            OR pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'REFERENCES')
            OR pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'TRIGGER')
          )
    ) THEN
        RAISE EXCEPTION 'C2-C: privilegio perigoso de tabela detectado';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND (
            (c.relname <> ALL(ARRAY[
    'deal_candidate',
    'deal_evaluation',
    'deal_evaluation_momentum_audit',
    'deal_evaluation_rule_result',
    'deal_evaluation_score_factor',
    'filter_profile',
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
]::text[])
                AND pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'SELECT'))
            OR (c.relname <> ALL(ARRAY[
    'deal_candidate',
    'deal_evaluation',
    'deal_evaluation_momentum_audit',
    'deal_evaluation_rule_result',
    'deal_evaluation_score_factor',
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
    'publication_selection_run'
]::text[])
                AND pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'INSERT'))
            OR (c.relname <> ALL(ARRAY[
    'deal_candidate',
    'processing_job',
    'processing_run',
    'processing_schedule',
    'product',
    'publication',
    'publication_attempt',
    'publication_cadence_profile',
    'publication_outbox',
    'publication_quota_profile',
    'publication_rate_limit_state',
    'publication_selection_profile'
]::text[])
                AND pg_catalog.has_table_privilege('rasping_runtime', c.oid, 'UPDATE'))
          )
    ) THEN
        RAISE EXCEPTION 'C2-C: DML fora da matriz aprovada';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'S'
          AND (
            pg_catalog.has_sequence_privilege('rasping_runtime', c.oid, 'UPDATE')
            OR (c.relname <> ALL(ARRAY[
    'deal_candidate_id_seq',
    'deal_evaluation_id_seq',
    'deal_evaluation_momentum_audit_id_seq',
    'deal_evaluation_rule_result_id_seq',
    'deal_evaluation_score_factor_id_seq',
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
    'publication_selection_run_id_seq'
]::text[]) AND
                pg_catalog.has_sequence_privilege('rasping_runtime', c.oid, 'USAGE'))
          )
    ) THEN
        RAISE EXCEPTION 'C2-C: privilegio de sequence fora da matriz';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND (
            pg_catalog.has_table_privilege('rasping_migrator', c.oid, 'SELECT')
            OR pg_catalog.has_table_privilege('rasping_migrator', c.oid, 'INSERT')
            OR pg_catalog.has_table_privilege('rasping_migrator', c.oid, 'UPDATE')
            OR pg_catalog.has_table_privilege('rasping_migrator', c.oid, 'DELETE')
          )
    ) THEN
        RAISE EXCEPTION 'C2-C: migrator ganhou DML prematuramente';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'S'
          AND (
            pg_catalog.has_sequence_privilege('rasping_migrator', c.oid, 'USAGE')
            OR pg_catalog.has_sequence_privilege('rasping_migrator', c.oid, 'SELECT')
            OR pg_catalog.has_sequence_privilege('rasping_migrator', c.oid, 'UPDATE')
          )
    ) THEN
        RAISE EXCEPTION 'C2-C: migrator ganhou acesso a sequences prematuramente';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles r
        WHERE r.rolname IN ('rasping_runtime', 'rasping_migrator')
          AND (r.rolcanlogin OR r.rolsuper OR r.rolcreatedb
               OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'C2-C: role ativada ou promovida indevidamente';
    END IF;
END;
$assertions$;
SELECT 'PASS: grants estao dentro da matriz C2-C e roles continuam NOLOGIN' AS status;

ROLLBACK;
\echo === C2-C: auditoria concluida, sem alteracoes ===
