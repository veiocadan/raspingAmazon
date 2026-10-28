-- D-D: verifica o estado persistido do agendamento sem qualquer escrita.
-- Identidade exigida: rasping_runtime. Nunca executar com superuser.
\set ON_ERROR_STOP on
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '20s';
DO $verificar$
BEGIN
    IF current_user <> 'rasping_runtime'
       OR session_user <> 'rasping_runtime'
       OR current_database() <> 'rasping_amazon'
       OR inet_client_addr() IS NULL
       OR current_setting('transaction_read_only') <> 'on'
    THEN
        RAISE EXCEPTION 'D-D: identidade ou modo readonly inesperado';
    END IF;

    IF NOT pg_catalog.has_table_privilege(current_user, 'public.processing_schedule', 'SELECT')
       OR NOT pg_catalog.has_table_privilege(current_user, 'public.processing_job', 'SELECT')
       OR NOT pg_catalog.has_table_privilege(current_user, 'public.processing_run', 'SELECT')
       OR NOT pg_catalog.has_table_privilege(current_user, 'public.publication_outbox', 'SELECT')
    THEN
        RAISE EXCEPTION 'D-D: privilegios de leitura incompletos';
    END IF;

    IF (SELECT COUNT(*) FROM public.processing_schedule) <> 1
       OR NOT EXISTS (
            SELECT 1 FROM public.processing_schedule
            WHERE schedule_key = 'amazon-deals'
              AND source_uri = 'https://www.amazon.com.br/deals'
              AND enabled IS FALSE
              AND interval_ms = 3600000
              AND next_run_at IS NOT NULL
              AND lease_owner IS NULL
              AND lease_expires_at IS NULL
              AND last_scheduled_for IS NULL
              AND last_processing_run_id IS NULL
       )
    THEN
        RAISE EXCEPTION 'D-D: agendamento ausente, alterado ou habilitado';
    END IF;

    IF EXISTS (SELECT 1 FROM public.processing_job)
       OR EXISTS (SELECT 1 FROM public.processing_run)
       OR EXISTS (SELECT 1 FROM public.publication_outbox)
    THEN
        RAISE EXCEPTION 'D-D: backlog presente; revisar antes da partida';
    END IF;
END
$verificar$;
SELECT 'D_D_BOOTSTRAP_SCHEDULE_READONLY_PASS' AS resultado;
ROLLBACK;
