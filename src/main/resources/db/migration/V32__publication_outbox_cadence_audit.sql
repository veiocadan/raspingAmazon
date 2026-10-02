/*
 * FASE 19
 *
 * Auditoria e proteção concorrente da cadência aplicada
 * às reservas primárias da publication_outbox.
 *
 * Linhas históricas anteriores a esta migration permanecem com
 * cadence_profile_version = NULL.
 *
 * Entregas derivadas, como WHATSAPP_MANUAL, também permanecem
 * sem reserva própria de cadência.
 */

ALTER TABLE publication_outbox
    ADD COLUMN cadence_profile_version TEXT;


/*
 * Quando presente, a versão precisa ser semanticamente válida.
 */
ALTER TABLE publication_outbox
    ADD CONSTRAINT ck_publication_outbox_cadence_profile_version
        CHECK (
            cadence_profile_version IS NULL
            OR CHAR_LENGTH(TRIM(cadence_profile_version)) > 0
        );


/*
 * A versão aplicada deve existir para o mesmo escopo operacional.
 *
 * Como a coluna é nullable:
 *
 * - histórico anterior à cadência continua válido;
 * - entregas derivadas continuam sem política própria;
 * - novas reservas primárias gerenciadas pela cadência
 *   apontam para uma versão auditável.
 */
ALTER TABLE publication_outbox
    ADD CONSTRAINT fk_publication_outbox_cadence_profile
        FOREIGN KEY (
            channel,
            destination,
            cadence_profile_version
        )
        REFERENCES publication_cadence_profile (
            channel,
            destination,
            version
        );


/*
 * Autoridade final contra duas reservas primárias gerenciadas
 * pela cadência ocuparem exatamente o mesmo horário.
 *
 * O lock transacional da quota deve evitar esse conflito no fluxo
 * normal. O índice permanece como última linha de defesa no banco.
 *
 * Linhas legadas e entregas derivadas não participam deste índice.
 */
CREATE UNIQUE INDEX
    uq_publication_outbox_primary_cadence_slot
    ON publication_outbox (
        channel,
        destination,
        quota_date,
        available_at
    )
    WHERE quota_profile_version IS NOT NULL
      AND cadence_profile_version IS NOT NULL;


/*
 * Navegação auditável da versão de cadência utilizada.
 */
CREATE INDEX idx_publication_outbox_cadence_profile
    ON publication_outbox (
        channel,
        destination,
        cadence_profile_version,
        quota_date,
        available_at
    )
    WHERE cadence_profile_version IS NOT NULL;
