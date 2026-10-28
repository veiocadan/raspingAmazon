-- FASE 21-C2-E-D-D-D-D-B: SOMENTE SELECT e transacao read only.
-- Nao alterar nem executar como superuser; autenticacao obrigatoria de rasping_runtime.
\set ON_ERROR_STOP on
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '30s';
DO $verificacao$
BEGIN
    IF current_user <> 'rasping_runtime'
       OR session_user <> 'rasping_runtime'
       OR current_database() <> 'rasping_amazon'
       OR inet_client_addr() IS NULL
       OR current_setting('transaction_read_only') <> 'on'
    THEN
       RAISE EXCEPTION 'D-B: identidade ou read-only incorretos';
    END IF;
    IF NOT pg_catalog.has_table_privilege(current_user,'public.processing_job','SELECT')
       OR NOT pg_catalog.has_table_privilege(current_user,'public.processing_schedule','SELECT')
       OR NOT pg_catalog.has_table_privilege(current_user,'public.publication_outbox','SELECT')
       OR NOT pg_catalog.has_table_privilege(current_user,'public.processing_run','SELECT')
    THEN
       RAISE EXCEPTION 'D-B: ACL de leitura faltando';
    END IF;
END;
$verificacao$;

-- Uma única linha de resultado por métrica. Não exportar entidades nem dados pessoais.
WITH
job_counts AS MATERIALIZED (
    SELECT COUNT(*)::bigint AS total,
           COUNT(*) FILTER (WHERE status = 'PENDING')::bigint AS pending,
           COUNT(*) FILTER (WHERE status = 'RETRY_WAIT')::bigint AS retry_wait,
           COUNT(*) FILTER (WHERE status = 'RUNNING')::bigint AS running,
           COUNT(*) FILTER (WHERE status = 'DEAD')::bigint AS dead
    FROM public.processing_job
),
outbox_counts AS MATERIALIZED (
    SELECT COUNT(*)::bigint AS total,
           COUNT(*) FILTER (WHERE status = 'PROCESSING')::bigint AS processing,
           COUNT(*) FILTER (WHERE status = 'DELIVERY_UNKNOWN')::bigint AS delivery_unknown
    FROM public.publication_outbox
),
schedule_counts AS MATERIALIZED (
    SELECT COUNT(*)::bigint AS total,
           COUNT(*) FILTER (WHERE enabled)::bigint AS enabled,
           COUNT(*) FILTER (
               WHERE enabled AND next_run_at <= CURRENT_TIMESTAMP
                 AND (lease_owner IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP)
           )::bigint AS enabled_due
    FROM public.processing_schedule
),
run_counts AS MATERIALIZED (
    SELECT COUNT(*)::bigint AS total,
           COUNT(*) FILTER (WHERE status = 'RUNNING')::bigint AS running
    FROM public.processing_run
),
metrics AS (
    SELECT 'jobs_total' AS metric, total AS amount FROM job_counts
    UNION ALL SELECT 'jobs_pending', pending FROM job_counts
    UNION ALL SELECT 'jobs_retry_wait', retry_wait FROM job_counts
    UNION ALL SELECT 'jobs_running', running FROM job_counts
    UNION ALL SELECT 'jobs_dead', dead FROM job_counts
    UNION ALL SELECT 'outbox_total', total FROM outbox_counts
    UNION ALL SELECT 'outbox_processing', processing FROM outbox_counts
    UNION ALL SELECT 'outbox_delivery_unknown', delivery_unknown FROM outbox_counts
    UNION ALL SELECT 'schedules_total', total FROM schedule_counts
    UNION ALL SELECT 'schedules_enabled', enabled FROM schedule_counts
    UNION ALL SELECT 'schedules_enabled_due', enabled_due FROM schedule_counts
    UNION ALL SELECT 'runs_total', total FROM run_counts
    UNION ALL SELECT 'runs_running', running FROM run_counts
)
SELECT metric, amount FROM metrics ORDER BY metric;
ROLLBACK;
