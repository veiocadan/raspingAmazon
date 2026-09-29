/*
 * FASE 18
 *
 * Liga a evidência persistente de entrega (publication_attempt)
 * à unidade durável de trabalho que a originou
 * (publication_outbox).
 *
 * publication_outbox e publication_attempt permanecem conceitos
 * diferentes:
 *
 * publication_outbox
 *     = trabalho reservado para entrega
 *
 * publication_attempt
 *     = evidência de uma execução contra PublicationChannel
 *
 * Uma mesma outbox poderá possuir múltiplas tentativas em fases
 * futuras, sem consumir uma segunda vaga de quota.
 */


/* ================================================================
 * OUTBOX TRACEABILITY
 * ================================================================ */

/*
 * Nullable para preservar compatibilidade com tentativas históricas
 * eventualmente criadas antes da introdução da outbox.
 *
 * Toda nova tentativa produzida pelo worker da FASE 18 utilizará
 * publication_outbox_id.
 */
ALTER TABLE publication_attempt
    ADD COLUMN publication_outbox_id BIGINT;


/*
 * A tentativa aponta para a unidade durável que originou
 * a execução externa.
 */
ALTER TABLE publication_attempt
    ADD CONSTRAINT fk_publication_attempt_outbox
        FOREIGN KEY (
            publication_outbox_id
        )
        REFERENCES publication_outbox (
            id
        );


/*
 * Dentro de uma mesma outbox, cada número de tentativa pode existir
 * no máximo uma vez.
 *
 * Como publication_outbox_id é nullable, linhas históricas sem
 * vínculo continuam compatíveis com PostgreSQL.
 */
ALTER TABLE publication_attempt
    ADD CONSTRAINT uq_publication_attempt_outbox_attempt
        UNIQUE (
            publication_outbox_id,
            attempt_number
        );


/*
 * Navegação eficiente:
 *
 * outbox
 *   ↓
 * tentativas em ordem
 */
CREATE INDEX idx_publication_attempt_outbox
    ON publication_attempt (
        publication_outbox_id,
        attempt_number,
        id
    )
    WHERE publication_outbox_id IS NOT NULL;
