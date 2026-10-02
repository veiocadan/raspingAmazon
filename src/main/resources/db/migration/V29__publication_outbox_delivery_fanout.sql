/*
 * FASE 19
 *
 * Evolução da publication_outbox para suportar múltiplas entregas
 * derivadas da mesma posição selecionada.
 *
 * Motivação:
 *
 * uma Publication selecionada para o Telegram público pode originar:
 *
 * - uma entrega principal TELEGRAM, que representa a reserva real
 *   da quota de publicação;
 *
 * - uma ou mais entregas espelho, como WHATSAPP_MANUAL, que reutilizam
 *   a mesma seleção e o mesmo conteúdo canônico, mas não consomem uma
 *   segunda vaga de quota.
 *
 * Nenhuma SelectionRun artificial é criada para os espelhos.
 */


/* ================================================================
 * QUOTA OPCIONAL PARA ENTREGAS DERIVADAS
 * ================================================================ */

/*
 * Entregas primárias continuam preenchendo:
 *
 * quota_profile_version
 * quota_date
 *
 * Entregas derivadas não reservam quota e portanto armazenam ambos
 * como NULL.
 */
ALTER TABLE publication_outbox
    ALTER COLUMN quota_profile_version DROP NOT NULL,
    ALTER COLUMN quota_date DROP NOT NULL;


/*
 * Os dois valores de quota formam uma unidade semântica:
 *
 * ambos presentes
 *     -> entrega que reservou quota;
 *
 * ambos ausentes
 *     -> entrega derivada que não reserva quota.
 *
 * Não é permitido estado parcial.
 */
ALTER TABLE publication_outbox
    ADD CONSTRAINT ck_publication_outbox_quota_reservation_pair
        CHECK (
            (
                quota_profile_version IS NOT NULL
                AND quota_date IS NOT NULL
            )
            OR
            (
                quota_profile_version IS NULL
                AND quota_date IS NULL
            )
        );


/* ================================================================
 * FAN-OUT DA MESMA POSIÇÃO SELECIONADA
 * ================================================================ */

/*
 * A FASE 18 permitia exatamente uma outbox para cada posição
 * selecionada.
 *
 * A FASE 19 permite múltiplas entregas físicas da mesma seleção,
 * desde que channel + destination sejam distintos.
 */
ALTER TABLE publication_outbox
    DROP CONSTRAINT uq_publication_outbox_selection_position;

ALTER TABLE publication_outbox
    ADD CONSTRAINT uq_publication_outbox_selection_position
        UNIQUE (
            selection_run_id,
            selection_position,
            channel,
            destination
        );


/*
 * Apesar do fan-out, continua existindo no máximo UMA entrega
 * que efetivamente reserva quota por posição selecionada.
 *
 * As entregas derivadas possuem quota_profile_version NULL e,
 * portanto, ficam fora deste índice parcial.
 */
CREATE UNIQUE INDEX
    uq_publication_outbox_quota_reservation_position
    ON publication_outbox (
        selection_run_id,
        selection_position
    )
    WHERE quota_profile_version IS NOT NULL;


/* ================================================================
 * DOCUMENTAÇÃO DE SEMÂNTICA
 * ================================================================ */

COMMENT ON COLUMN publication_outbox.quota_profile_version IS
    'Quota profile da entrega que reservou vaga. NULL para entregas derivadas que reutilizam a seleção sem reservar quota adicional.';

COMMENT ON COLUMN publication_outbox.quota_date IS
    'Dia operacional da reserva de quota. NULL para entregas derivadas sem reserva adicional.';
