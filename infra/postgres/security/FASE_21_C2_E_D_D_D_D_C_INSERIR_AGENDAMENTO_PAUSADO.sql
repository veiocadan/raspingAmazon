-- D-C: unica operacao de criacao inicial com schedule pausado.
-- Nao executar sem aprovacao posterior explicita. Identidade: rasping_runtime.
\set ON_ERROR_STOP on
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE READ WRITE;
SET LOCAL statement_timeout = '15s';
SET LOCAL lock_timeout = '5s';
DO $guard$
BEGIN
    IF current_user <> 'rasping_runtime'
       OR session_user <> 'rasping_runtime'
       OR current_database() <> 'rasping_amazon'
       OR inet_client_addr() IS NULL
    THEN
        RAISE EXCEPTION 'D-C: identidade invalida';
    END IF;
    IF NOT pg_catalog.has_table_privilege(current_user,'public.processing_schedule','INSERT')
       OR NOT pg_catalog.has_table_privilege(current_user,'public.processing_schedule','SELECT')
    THEN
        RAISE EXCEPTION 'D-C: privilegios runtime insuficientes';
    END IF;
    IF EXISTS (SELECT 1 FROM public.processing_schedule)
       OR EXISTS (SELECT 1 FROM public.processing_job)
       OR EXISTS (SELECT 1 FROM public.processing_run)
       OR EXISTS (SELECT 1 FROM public.publication_outbox)
    THEN
        RAISE EXCEPTION 'D-C: agendamentos ou backlog existentes; abortar';
    END IF;
END
$guard$;

INSERT INTO public.processing_schedule (
    schedule_key, source_uri, enabled, interval_ms, next_run_at,
    lease_owner, lease_expires_at, last_scheduled_for, last_processing_run_id,
    created_at, updated_at
) VALUES (
    'amazon-deals', 'https://www.amazon.com.br/deals', FALSE, 3600000,
    CURRENT_TIMESTAMP, NULL, NULL, NULL, NULL,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

DO $check$
BEGIN
    IF (SELECT COUNT(*) FROM public.processing_schedule) <> 1
        OR NOT EXISTS (
            SELECT 1 FROM public.processing_schedule
            WHERE schedule_key = 'amazon-deals'
              AND source_uri = 'https://www.amazon.com.br/deals'
              AND enabled IS FALSE
              AND interval_ms = 3600000
              AND lease_owner IS NULL
              AND lease_expires_at IS NULL
              AND last_scheduled_for IS NULL
              AND last_processing_run_id IS NULL
        )
        OR EXISTS (SELECT 1 FROM public.processing_job)
        OR EXISTS (SELECT 1 FROM public.processing_run)
        OR EXISTS (SELECT 1 FROM public.publication_outbox)
    THEN
        RAISE EXCEPTION 'D-C: verificacao transacional nao satisfeita';
    END IF;
END
$check$;
COMMIT;
SELECT 'D_C_AGENDAMENTO_PAUSADO_COMMIT_PASS' AS resultado;
