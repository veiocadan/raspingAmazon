/*
 * FASE 21-D. Registro append-only de alteracoes administrativas do scheduler.
 * Nao altera V23 nem modifica os dados historicos. Nenhum backfill retroativo.
 */
CREATE TABLE public.processing_schedule_change_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schedule_key TEXT NOT NULL,
    change_type TEXT NOT NULL,
    db_session_role TEXT NOT NULL,
    declared_actor TEXT NOT NULL,
    change_reason TEXT NOT NULL,
    previous_enabled BOOLEAN NOT NULL,
    new_enabled BOOLEAN NOT NULL,
    previous_interval_ms BIGINT NOT NULL,
    new_interval_ms BIGINT NOT NULL,
    previous_next_run_at TIMESTAMPTZ NOT NULL,
    new_next_run_at TIMESTAMPTZ NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_schedule_audit_change_type
        CHECK (change_type IN ('PAUSE', 'RESUME', 'CHANGE_INTERVAL', 'MULTIPLE_FIELDS_CHANGED')),
    CONSTRAINT ck_schedule_audit_actor CHECK (btrim(declared_actor) <> ''),
    CONSTRAINT ck_schedule_audit_reason CHECK (btrim(change_reason) <> ''),
    CONSTRAINT ck_schedule_audit_interval CHECK (previous_interval_ms > 0 AND new_interval_ms > 0)
);

CREATE INDEX idx_schedule_admin_audit_key_time
    ON public.processing_schedule_change_audit (schedule_key, changed_at DESC, id DESC);

REVOKE ALL ON TABLE public.processing_schedule_change_audit FROM PUBLIC;

/* Triggers imutaveis impedem UPDATE/DELETE mesmo com concessoes acidentais. */
CREATE FUNCTION public.reject_processing_schedule_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
BEGIN
    RAISE EXCEPTION 'Processing schedule audit history is append-only';
END;
$$;

REVOKE ALL ON FUNCTION public.reject_processing_schedule_audit_mutation() FROM PUBLIC;

CREATE TRIGGER trg_schedule_audit_immutable
BEFORE UPDATE OR DELETE ON public.processing_schedule_change_audit
FOR EACH ROW EXECUTE FUNCTION public.reject_processing_schedule_audit_mutation();

/* O trigger grava o fato na MESMA transacao do UPDATE original.
 * O SECURITY DEFINER permite inserir com o papel runtime restrito,
 * sem conceder INSERT direto em historico de auditoria.
 */
CREATE FUNCTION public.record_processing_schedule_admin_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
DECLARE
    actor_value TEXT;
    reason_value TEXT;
    action_value TEXT;
    enabled_changed BOOLEAN;
    interval_changed BOOLEAN;
BEGIN
    enabled_changed := OLD.enabled IS DISTINCT FROM NEW.enabled;
    interval_changed := OLD.interval_ms IS DISTINCT FROM NEW.interval_ms;

    /* A simples passagem de janela pelo scheduler nao e alteracao administrativa. */
    IF NOT enabled_changed AND NOT interval_changed AND (
        OLD.next_run_at IS NOT DISTINCT FROM NEW.next_run_at
        OR NULLIF(btrim(current_setting('rasping.schedule_reason', true)), '') IS NULL
    ) THEN
        RETURN NEW;
    END IF;

    actor_value := NULLIF(btrim(current_setting('rasping.schedule_actor', true)), '');
    reason_value := NULLIF(btrim(current_setting('rasping.schedule_reason', true)), '');
    IF actor_value IS NULL THEN
        actor_value := 'db-role:' || session_user;
    END IF;
    IF reason_value IS NULL THEN
        reason_value := 'direct-database-mutation-without-declared-reason';
    END IF;
    IF length(actor_value) > 120 OR length(reason_value) > 300
       OR actor_value ~ '[[:cntrl:]]' OR reason_value ~ '[[:cntrl:]]' THEN
        RAISE EXCEPTION 'Invalid schedule audit metadata';
    END IF;

    action_value := CASE
        WHEN enabled_changed AND interval_changed THEN 'MULTIPLE_FIELDS_CHANGED'
        WHEN enabled_changed AND NEW.enabled THEN 'RESUME'
        WHEN enabled_changed AND NOT NEW.enabled THEN 'PAUSE'
        ELSE 'CHANGE_INTERVAL'
    END;

    INSERT INTO public.processing_schedule_change_audit (
        schedule_key, change_type, db_session_role, declared_actor, change_reason,
        previous_enabled, new_enabled, previous_interval_ms, new_interval_ms,
        previous_next_run_at, new_next_run_at
    ) VALUES (
        NEW.schedule_key, action_value, session_user, actor_value, reason_value,
        OLD.enabled, NEW.enabled, OLD.interval_ms, NEW.interval_ms,
        OLD.next_run_at, NEW.next_run_at
    );

    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.record_processing_schedule_admin_change() FROM PUBLIC;

CREATE TRIGGER trg_processing_schedule_admin_change
AFTER UPDATE OF enabled, interval_ms, next_run_at ON public.processing_schedule
FOR EACH ROW EXECUTE FUNCTION public.record_processing_schedule_admin_change();

/* SELECT operacional se o usuario runtime existir. Compatibilidade com CI
 * efemero que nao provisionou os papeis reais do ambiente local.
 */
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rasping_runtime') THEN
        GRANT SELECT ON TABLE public.processing_schedule_change_audit TO rasping_runtime;
    END IF;
END;
$$;
