-- FASE 21-C2-C - Concessao minima preliminar para rasping_runtime.
-- Nao cria LOGIN, senha, membership, ownership ou altera o Flyway.
-- Nao concede DELETE, TRUNCATE, CREATE, ALTER, DROP, REFERENCES, TRIGGER.
-- Esta fase deixa a role NOLOGIN; o aplicativo segue usando rasping.
\set ON_ERROR_STOP on
\pset pager off

BEGIN;

DO $preflight$
DECLARE
    runtime_role pg_catalog.pg_roles%ROWTYPE;
    migrator_role pg_catalog.pg_roles%ROWTYPE;
BEGIN
    IF current_database() <> 'rasping_amazon' OR current_user <> 'rasping' THEN
        RAISE EXCEPTION 'C2-C: contexto de conexao inesperado';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = 'rasping' AND rolsuper
    ) THEN
        RAISE EXCEPTION 'C2-C: administrador nao e superuser';
    END IF;

    SELECT * INTO runtime_role
    FROM pg_catalog.pg_roles WHERE rolname = 'rasping_runtime';
    SELECT * INTO migrator_role
    FROM pg_catalog.pg_roles WHERE rolname = 'rasping_migrator';

    IF runtime_role.oid IS NULL OR migrator_role.oid IS NULL THEN
        RAISE EXCEPTION 'C2-C: roles da C2-A ausentes';
    END IF;

    IF runtime_role.rolcanlogin OR runtime_role.rolinherit OR runtime_role.rolsuper
        OR runtime_role.rolcreatedb OR runtime_role.rolcreaterole
        OR runtime_role.rolreplication OR runtime_role.rolbypassrls THEN
        RAISE EXCEPTION 'C2-C: atributos elevados ou LOGIN no runtime';
    END IF;

    IF migrator_role.rolcanlogin OR migrator_role.rolinherit OR migrator_role.rolsuper
        OR migrator_role.rolcreatedb OR migrator_role.rolcreaterole
        OR migrator_role.rolreplication OR migrator_role.rolbypassrls THEN
        RAISE EXCEPTION 'C2-C: atributos inesperados no migrator';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_auth_members m
        WHERE m.member IN (runtime_role.oid, migrator_role.oid)
           OR m.roleid IN (runtime_role.oid, migrator_role.oid)
    ) THEN
        RAISE EXCEPTION 'C2-C: memberships inesperadas';
    END IF;

    IF (SELECT count(*) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')) <> 29
       OR (SELECT count(*) FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'S') <> 26 THEN
        RAISE EXCEPTION 'C2-C: quantidade de objetos diverge da C2-B';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'S')
          AND pg_catalog.pg_get_userbyid(c.relowner) <> 'rasping'
    ) THEN
        RAISE EXCEPTION 'C2-C: ownership diverge do baseline C2-B';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('v', 'm', 'f')
    ) THEN
        RAISE EXCEPTION 'C2-C: views/foreign tables nao catalogadas; revisar grants';
    END IF;
END;
$preflight$;

GRANT CONNECT ON DATABASE rasping_amazon TO rasping_runtime;
GRANT USAGE ON SCHEMA public TO rasping_runtime;

-- SELECT: somente objetos operacionais, sem configuration_version ou Flyway.
GRANT SELECT ON TABLE
    public.deal_candidate,
    public.deal_evaluation,
    public.deal_evaluation_momentum_audit,
    public.deal_evaluation_rule_result,
    public.deal_evaluation_score_factor,
    public.filter_profile,
    public.integration_observation,
    public.offer_evidence,
    public.offer_payment_condition,
    public.offer_payment_condition_method,
    public.offer_snapshot,
    public.processing_job,
    public.processing_job_reprocess_event,
    public.processing_run,
    public.processing_schedule,
    public.product,
    public.publication,
    public.publication_attempt,
    public.publication_cadence_profile,
    public.publication_delivery_resolution_event,
    public.publication_outbox,
    public.publication_quota_profile,
    public.publication_rate_limit_state,
    public.publication_selection_decision,
    public.publication_selection_profile,
    public.publication_selection_run,
    public.score_profile
TO rasping_runtime;

-- INSERT: objetos persistidos em fluxos de coleta, avaliacao, fila e publicacao.
GRANT INSERT ON TABLE
    public.deal_candidate,
    public.deal_evaluation,
    public.deal_evaluation_momentum_audit,
    public.deal_evaluation_rule_result,
    public.deal_evaluation_score_factor,
    public.integration_observation,
    public.offer_evidence,
    public.offer_payment_condition,
    public.offer_payment_condition_method,
    public.offer_snapshot,
    public.processing_job,
    public.processing_job_reprocess_event,
    public.processing_run,
    public.processing_schedule,
    public.product,
    public.publication,
    public.publication_attempt,
    public.publication_cadence_profile,
    public.publication_delivery_resolution_event,
    public.publication_outbox,
    public.publication_quota_profile,
    public.publication_rate_limit_state,
    public.publication_selection_decision,
    public.publication_selection_profile,
    public.publication_selection_run
TO rasping_runtime;

-- UPDATE: apenas objetos efetivamente atualizados pelos adapters JDBC.
GRANT UPDATE ON TABLE
    public.deal_candidate,
    public.processing_job,
    public.processing_run,
    public.processing_schedule,
    public.product,
    public.publication,
    public.publication_attempt,
    public.publication_cadence_profile,
    public.publication_outbox,
    public.publication_quota_profile,
    public.publication_rate_limit_state,
    public.publication_selection_profile
TO rasping_runtime;

-- USAGE/nextval das sequences operacionais. Nenhum UPDATE/SELECT explicito.
GRANT USAGE ON SEQUENCE
    public.deal_candidate_id_seq,
    public.deal_evaluation_id_seq,
    public.deal_evaluation_momentum_audit_id_seq,
    public.deal_evaluation_rule_result_id_seq,
    public.deal_evaluation_score_factor_id_seq,
    public.integration_observation_id_seq,
    public.offer_evidence_id_seq,
    public.offer_payment_condition_id_seq,
    public.offer_payment_condition_method_id_seq,
    public.offer_snapshot_id_seq,
    public.processing_job_id_seq,
    public.processing_job_reprocess_event_id_seq,
    public.processing_run_id_seq,
    public.product_id_seq,
    public.publication_attempt_id_seq,
    public.publication_cadence_profile_id_seq,
    public.publication_delivery_resolution_event_id_seq,
    public.publication_id_seq,
    public.publication_outbox_id_seq,
    public.publication_quota_profile_id_seq,
    public.publication_selection_decision_id_seq,
    public.publication_selection_profile_id_seq,
    public.publication_selection_run_id_seq
TO rasping_runtime;

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

COMMIT;
\echo === C2-C: grants explicitos concedidos; runtime segue NOLOGIN ===
