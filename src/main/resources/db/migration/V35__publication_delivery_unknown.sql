/*
 * FASE 20-D1A
 *
 * Introduz o contrato persistente necessário para distinguir:
 *
 * - trabalho reivindicado mas ainda não enviado;
 * - tentativa externa iniciada;
 * - resultado externo confirmado;
 * - resultado externo desconhecido.
 *
 * O problema resolvido por esta migration é o seguinte:
 *
 * PROCESSING
 *     ↓
 * provider pode receber a mensagem
 *     ↓
 * processo cai antes de persistir o resultado
 *     ↓
 * outcome externo é desconhecido
 *
 * Esse estado NÃO pode voltar automaticamente para PENDING, pois isso
 * poderia duplicar uma publicação que o provider já aceitou.
 *
 * A migration também trata fail-closed qualquer PROCESSING legado que
 * exista no instante do upgrade. Antes desta versão o sistema não
 * persistia STARTED antes da chamada externa; portanto não existe forma
 * segura de provar que uma linha PROCESSING antiga ainda não atravessou
 * a fronteira externa.
 */


/* ================================================================
 * REMOVE CONSTRAINTS THAT WILL BE EVOLVED
 * ================================================================ */

ALTER TABLE publication_outbox
DROP CONSTRAINT ck_publication_outbox_status;

ALTER TABLE publication_outbox
DROP CONSTRAINT ck_publication_outbox_lock_state;

ALTER TABLE publication_outbox
DROP CONSTRAINT ck_publication_outbox_finished_state;

ALTER TABLE publication_attempt
DROP CONSTRAINT ck_publication_attempt_timing;


/* ================================================================
 * PUBLICATION ATTEMPT LIFECYCLE
 * ================================================================ */

/*
 * STARTED representa uma tentativa cujo início foi persistido antes da
 * chamada externa.
 *
 * Enquanto STARTED, finished_at deve permanecer NULL.
 */
ALTER TABLE publication_attempt
    ALTER COLUMN finished_at DROP NOT NULL;


/* ================================================================
 * FAIL-CLOSED UPGRADE OF LEGACY PROCESSING ROWS
 * ================================================================ */

/*
 * Qualquer PROCESSING existente neste upgrade pertence ao protocolo
 * antigo.
 *
 * No protocolo antigo não havia PublicationAttempt STARTED antes da
 * chamada ao provider.
 *
 * Consequentemente:
 *
 * PROCESSING legado
 *     NÃO significa
 * "sabemos que o provider não foi chamado".
 *
 * Para não produzir reenvio potencialmente duplicado, materializamos
 * uma tentativa DELIVERY_UNKNOWN para cada uma dessas linhas.
 */
INSERT INTO publication_attempt (
    publication_id,
    publication_outbox_id,
    channel,
    target,
    attempt_number,
    status,
    provider_reference,
    error_code,
    started_at,
    finished_at,
    created_at
)
SELECT
    outbox.publication_id,
    outbox.id,
    outbox.channel,
    outbox.destination,

    COALESCE(
        (
            SELECT MAX(attempt.attempt_number)
            FROM publication_attempt AS attempt
            WHERE attempt.publication_outbox_id = outbox.id
        ),
        0
    ) + 1,

    'DELIVERY_UNKNOWN',

    NULL,

    'PUBLICATION_DELIVERY_OUTCOME_UNKNOWN_DURING_V35_UPGRADE',

    COALESCE(
        outbox.locked_at,
        CURRENT_TIMESTAMP
    ),

    GREATEST(
        COALESCE(
            outbox.locked_at,
            CURRENT_TIMESTAMP
        ),
        CURRENT_TIMESTAMP
    ),

    CURRENT_TIMESTAMP

FROM publication_outbox AS outbox
WHERE outbox.status = 'PROCESSING';


/*
 * A própria outbox também passa para um estado terminal auditável.
 *
 * Não existe retry automático aqui.
 */
UPDATE publication_outbox
SET
    status = 'DELIVERY_UNKNOWN',
    locked_at = NULL,
    locked_by = NULL,
    updated_at = CURRENT_TIMESTAMP,
    finished_at = CURRENT_TIMESTAMP
WHERE status = 'PROCESSING';


/* ================================================================
 * ATTEMPT STATUS CONTRACT
 * ================================================================ */

ALTER TABLE publication_attempt
    ADD CONSTRAINT ck_publication_attempt_status
        CHECK (
            status IN (
                       'STARTED',
                       'SUCCESS',
                       'FAILED_TRANSIENT',
                       'FAILED_PERMANENT',
                       'DELIVERY_UNKNOWN'
                )
            );


/*
 * STARTED:
 *
 *     external outcome ainda não existe localmente
 *     finished_at = NULL
 *
 * qualquer estado terminal:
 *
 *     finished_at obrigatório
 *     finished_at >= started_at
 */
ALTER TABLE publication_attempt
    ADD CONSTRAINT ck_publication_attempt_timing
        CHECK (
            (
                status = 'STARTED'
                    AND finished_at IS NULL
                )
                OR
            (
                status IN (
                           'SUCCESS',
                           'FAILED_TRANSIENT',
                           'FAILED_PERMANENT',
                           'DELIVERY_UNKNOWN'
                    )
                    AND finished_at IS NOT NULL
                    AND finished_at >= started_at
                )
            );


/*
 * Uma outbox não pode possuir duas chamadas externas simultaneamente
 * abertas.
 *
 * Tentativas históricas terminadas continuam podendo existir em número
 * arbitrário por outbox.
 */
CREATE UNIQUE INDEX uq_publication_attempt_active_started
    ON publication_attempt (
                            publication_outbox_id
        )
    WHERE
        publication_outbox_id IS NOT NULL
        AND status = 'STARTED';


/* ================================================================
 * OUTBOX STATUS CONTRACT
 * ================================================================ */

ALTER TABLE publication_outbox
    ADD CONSTRAINT ck_publication_outbox_status
        CHECK (
            status IN (
                       'PENDING',
                       'PROCESSING',
                       'SUCCEEDED',
                       'FAILED_TRANSIENT',
                       'FAILED_PERMANENT',
                       'DELIVERY_UNKNOWN'
                )
            );


/*
 * Somente PROCESSING possui lease.
 *
 * DELIVERY_UNKNOWN é terminal e portanto nunca permanece pertencendo a
 * um worker.
 */
ALTER TABLE publication_outbox
    ADD CONSTRAINT ck_publication_outbox_lock_state
        CHECK (
            (
                status = 'PROCESSING'
                    AND locked_at IS NOT NULL
                    AND locked_by IS NOT NULL
                    AND CHAR_LENGTH(TRIM(locked_by)) > 0
                    AND finished_at IS NULL
                )
                OR
            (
                status <> 'PROCESSING'
                    AND locked_at IS NULL
                    AND locked_by IS NULL
                )
            );


ALTER TABLE publication_outbox
    ADD CONSTRAINT ck_publication_outbox_finished_state
        CHECK (
            (
                status IN (
                           'PENDING',
                           'PROCESSING'
                    )
                    AND finished_at IS NULL
                )
                OR
            (
                status IN (
                           'SUCCEEDED',
                           'FAILED_TRANSIENT',
                           'FAILED_PERMANENT',
                           'DELIVERY_UNKNOWN'
                    )
                    AND finished_at IS NOT NULL
                )
            );


/* ================================================================
 * RECOVERY SUPPORT
 * ================================================================ */

/*
 * A 20-D1B utilizará este índice para localizar eficientemente uma
 * tentativa STARTED associada a uma outbox cujo lease expirou.
 */
CREATE INDEX idx_publication_attempt_started_outbox
    ON publication_attempt (
                            publication_outbox_id,
                            started_at,
                            id
        )
    WHERE status = 'STARTED';
