-- FASE 21-F — contagens para comparar o banco original e a restauracao.
-- Executado somente como rasping_migrator na origem, sem alteracoes.
\set ON_ERROR_STOP on
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT (SELECT count(*) FROM public.processing_job)::text || '|' ||
       (SELECT count(*) FROM public.processing_run)::text || '|' ||
       (SELECT count(*) FROM public.publication_outbox)::text || '|' ||
       (SELECT count(*) FROM public.processing_schedule)::text || '|' ||
       (SELECT count(*) FROM public.flyway_schema_history)::text || '|' ||
       (SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname = 'public')::text;
ROLLBACK;
