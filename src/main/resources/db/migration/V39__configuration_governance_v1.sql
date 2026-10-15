/*
 * FASE 21-D - Fechamento inicial da governanca de configuracao.
 *
 * 1. Corrige a classificacao da auditoria V38 do scheduler.
 * 2. Impede alteracoes de valores e reutilizacao de versoes historicas
 *    nos perfis operacionais de selecao, quota e cadencia.
 * 3. Registra proveniencia diretamente na linha versionada: nao cria
 *    historico paralelo aos perfis existentes.
 *
 * Nao executa DML em registros existentes nem autoriza o daemon.
 */

/* A alteracao rotineira de next_run_at, mesmo quando ha contexto de
 * motivo na transacao, nao e uma alteracao administrativa de politica.
 * A funcao original foi introduzida pela V38, que permanece intocada.
 */
CREATE OR REPLACE FUNCTION public.record_processing_schedule_admin_change()
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

    IF NOT enabled_changed AND NOT interval_changed THEN
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
        schedule_key, change_type, db_session_role, declared_actor,
        change_reason, previous_enabled, new_enabled,
        previous_interval_ms, new_interval_ms,
        previous_next_run_at, new_next_run_at
    ) VALUES (
        NEW.schedule_key, action_value, session_user, actor_value,
        reason_value, OLD.enabled, NEW.enabled,
        OLD.interval_ms, NEW.interval_ms,
        OLD.next_run_at, NEW.next_run_at
    );

    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.record_processing_schedule_admin_change() FROM PUBLIC;

/* Os campos adicionados a seguir ficam NULL nas versoes anteriores a
 * V39: inventar autor/referencia historicos seria evidência falsa.
 * Version, valores e created_at originais permanecem intactos.
 */
ALTER TABLE public.publication_selection_profile
    ADD COLUMN activation_previous_version TEXT,
    ADD COLUMN activation_actor TEXT,
    ADD COLUMN activation_reference TEXT,
    ADD COLUMN activated_at TIMESTAMPTZ,
    ADD COLUMN deactivated_at TIMESTAMPTZ,
    ADD COLUMN deactivation_actor TEXT,
    ADD COLUMN deactivation_reference TEXT,
    ADD CONSTRAINT ck_selection_activation_metadata_consistent CHECK (
        (activated_at IS NULL AND activation_actor IS NULL
         AND activation_reference IS NULL AND activation_previous_version IS NULL)
        OR (activated_at IS NOT NULL AND activation_actor IS NOT NULL
            AND activation_reference IS NOT NULL)
    ),
    ADD CONSTRAINT ck_selection_deactivation_metadata_consistent CHECK (
        (deactivated_at IS NULL AND deactivation_actor IS NULL
         AND deactivation_reference IS NULL)
        OR (deactivated_at IS NOT NULL AND deactivation_actor IS NOT NULL
            AND deactivation_reference IS NOT NULL)
    );

ALTER TABLE public.publication_quota_profile
    ADD COLUMN activation_previous_version TEXT,
    ADD COLUMN activation_actor TEXT,
    ADD COLUMN activation_reference TEXT,
    ADD COLUMN activated_at TIMESTAMPTZ,
    ADD COLUMN deactivated_at TIMESTAMPTZ,
    ADD COLUMN deactivation_actor TEXT,
    ADD COLUMN deactivation_reference TEXT,
    ADD CONSTRAINT ck_quota_activation_metadata_consistent CHECK (
        (activated_at IS NULL AND activation_actor IS NULL
         AND activation_reference IS NULL AND activation_previous_version IS NULL)
        OR (activated_at IS NOT NULL AND activation_actor IS NOT NULL
            AND activation_reference IS NOT NULL)
    ),
    ADD CONSTRAINT ck_quota_deactivation_metadata_consistent CHECK (
        (deactivated_at IS NULL AND deactivation_actor IS NULL
         AND deactivation_reference IS NULL)
        OR (deactivated_at IS NOT NULL AND deactivation_actor IS NOT NULL
            AND deactivation_reference IS NOT NULL)
    );

ALTER TABLE public.publication_cadence_profile
    ADD COLUMN activation_previous_version TEXT,
    ADD COLUMN activation_actor TEXT,
    ADD COLUMN activation_reference TEXT,
    ADD COLUMN activated_at TIMESTAMPTZ,
    ADD COLUMN deactivated_at TIMESTAMPTZ,
    ADD COLUMN deactivation_actor TEXT,
    ADD COLUMN deactivation_reference TEXT,
    ADD CONSTRAINT ck_cadence_activation_metadata_consistent CHECK (
        (activated_at IS NULL AND activation_actor IS NULL
         AND activation_reference IS NULL AND activation_previous_version IS NULL)
        OR (activated_at IS NOT NULL AND activation_actor IS NOT NULL
            AND activation_reference IS NOT NULL)
    ),
    ADD CONSTRAINT ck_cadence_deactivation_metadata_consistent CHECK (
        (deactivated_at IS NULL AND deactivation_actor IS NULL
         AND deactivation_reference IS NULL)
        OR (deactivated_at IS NOT NULL AND deactivation_actor IS NOT NULL
            AND deactivation_reference IS NOT NULL)
    );

/* Captura o evento de ativacao no proprio registro de versao.
 * O predecessor e a ultima versao gravada no mesmo escopo, que os
 * sincronizadores existentes desativam antes do novo INSERT.
 * O ator declarado por GUC nao equivale a uma identidade autenticada.
 * Sem contexto explicito, a credencial do banco e a nova versao
 * oferecem proveniencia/referencia verificavel sem inventar motivo.
 */
CREATE FUNCTION public.record_versioned_publication_profile_activation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
DECLARE
    previous_version_value TEXT;
    actor_value TEXT;
    reference_value TEXT;
BEGIN
    IF TG_TABLE_SCHEMA <> 'public'
       OR TG_TABLE_NAME NOT IN (
           'publication_selection_profile',
           'publication_quota_profile',
           'publication_cadence_profile'
       ) THEN
        RAISE EXCEPTION 'Unexpected versioned profile table';
    END IF;

    /* Insercoes historicas inativas sao aceitas sem inventar ativacao. */
    IF NOT NEW.active THEN
        NEW.activation_previous_version := NULL;
        NEW.activation_actor := NULL;
        NEW.activation_reference := NULL;
        NEW.activated_at := NULL;
        NEW.deactivated_at := NULL;
        NEW.deactivation_actor := NULL;
        NEW.deactivation_reference := NULL;
        RETURN NEW;
    END IF;

    actor_value := NULLIF(btrim(current_setting('rasping.config_actor', true)), '');
    reference_value := NULLIF(btrim(current_setting('rasping.config_reason', true)), '');

    IF actor_value IS NULL THEN
        actor_value := 'db-role:' || session_user;
    END IF;
    IF reference_value IS NULL THEN
        reference_value := 'profile-version:' || NEW.version;
    END IF;

    IF length(actor_value) > 120 OR length(reference_value) > 300
       OR actor_value ~ '[[:cntrl:]]' OR reference_value ~ '[[:cntrl:]]' THEN
        RAISE EXCEPTION 'Invalid publication profile activation metadata';
    END IF;

    EXECUTE format(
        'SELECT version FROM public.%I '
        || 'WHERE channel = $1 AND destination = $2 '
        || 'ORDER BY id DESC LIMIT 1',
        TG_TABLE_NAME
    ) INTO previous_version_value USING NEW.channel, NEW.destination;

    NEW.activation_previous_version := previous_version_value;
    NEW.activation_actor := actor_value;
    NEW.activation_reference := reference_value;
    NEW.activated_at := clock_timestamp();
    NEW.deactivated_at := NULL;
    NEW.deactivation_actor := NULL;
    NEW.deactivation_reference := NULL;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.record_versioned_publication_profile_activation() FROM PUBLIC;

/* Somente a desativacao verdadeira -> falsa e legitima sem nova versao.
 * Ao desativar, a propria linha recebe proveniencia da transicao; os
 * campos de negocio e os metadados de ativacao nunca mudam.
 * Assim, uma desativacao direta sem substituicao tambem deixa evidencia.
 */
CREATE FUNCTION public.reject_versioned_publication_profile_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
DECLARE
    actor_value TEXT;
    reference_value TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Versioned publication profile history is append-only';
    END IF;

    IF OLD.active AND NOT NEW.active
       AND (to_jsonb(OLD) - 'active' - 'deactivated_at'
            - 'deactivation_actor' - 'deactivation_reference')
         = (to_jsonb(NEW) - 'active' - 'deactivated_at'
            - 'deactivation_actor' - 'deactivation_reference') THEN

        actor_value := NULLIF(btrim(current_setting('rasping.config_actor', true)), '');
        reference_value := NULLIF(btrim(current_setting('rasping.config_reason', true)), '');
        IF actor_value IS NULL THEN
            actor_value := 'db-role:' || session_user;
        END IF;
        IF reference_value IS NULL THEN
            reference_value := 'profile-deactivation:' || OLD.version;
        END IF;

        IF length(actor_value) > 120 OR length(reference_value) > 300
           OR actor_value ~ '[[:cntrl:]]' OR reference_value ~ '[[:cntrl:]]' THEN
            RAISE EXCEPTION 'Invalid publication profile deactivation metadata';
        END IF;

        NEW.deactivated_at := clock_timestamp();
        NEW.deactivation_actor := actor_value;
        NEW.deactivation_reference := reference_value;
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'Versioned publication profile is immutable; use a new version';
END;
$$;

REVOKE ALL ON FUNCTION public.reject_versioned_publication_profile_mutation() FROM PUBLIC;

CREATE TRIGGER trg_selection_profile_activation_provenance
BEFORE INSERT ON public.publication_selection_profile
FOR EACH ROW EXECUTE FUNCTION public.record_versioned_publication_profile_activation();

CREATE TRIGGER trg_quota_profile_activation_provenance
BEFORE INSERT ON public.publication_quota_profile
FOR EACH ROW EXECUTE FUNCTION public.record_versioned_publication_profile_activation();

CREATE TRIGGER trg_cadence_profile_activation_provenance
BEFORE INSERT ON public.publication_cadence_profile
FOR EACH ROW EXECUTE FUNCTION public.record_versioned_publication_profile_activation();

CREATE TRIGGER trg_selection_profile_immutable
BEFORE UPDATE OR DELETE ON public.publication_selection_profile
FOR EACH ROW EXECUTE FUNCTION public.reject_versioned_publication_profile_mutation();

CREATE TRIGGER trg_quota_profile_immutable
BEFORE UPDATE OR DELETE ON public.publication_quota_profile
FOR EACH ROW EXECUTE FUNCTION public.reject_versioned_publication_profile_mutation();

CREATE TRIGGER trg_cadence_profile_immutable
BEFORE UPDATE OR DELETE ON public.publication_cadence_profile
FOR EACH ROW EXECUTE FUNCTION public.reject_versioned_publication_profile_mutation();

COMMENT ON COLUMN public.publication_selection_profile.activation_previous_version IS
    'Previously recorded profile version in this scope; NULL for first activation or pre-V39 legacy rows';
COMMENT ON COLUMN public.publication_quota_profile.activation_previous_version IS
    'Previously recorded profile version in this scope; NULL for first activation or pre-V39 legacy rows';
COMMENT ON COLUMN public.publication_cadence_profile.activation_previous_version IS
    'Previously recorded profile version in this scope; NULL for first activation or pre-V39 legacy rows';
