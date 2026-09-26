/*
 * FASE 14
 *
 * Índices destinados à consulta operacional de Publication.
 *
 * A interface operacional é somente leitura e não participa
 * do ciclo de vida da publicação.
 *
 * Ordenação principal:
 *
 * created_at DESC
 * id DESC
 */


/* ================================================================
 * GLOBAL OPERATIONAL ORDER
 * ================================================================ */

CREATE INDEX idx_publication_operational_order
    ON publication (
        created_at DESC,
        id DESC
    );


/* ================================================================
 * STATUS + OPERATIONAL ORDER
 * ================================================================ */

/*
 * publication.status já possui índice global desde V1.
 *
 * Entretanto, a interface operacional consulta frequentemente
 * um estado específico preservando a ordenação cronológica.
 *
 * Exemplos:
 *
 * CREATED
 * READY
 * PUBLISHED
 * FAILED
 */
CREATE INDEX idx_publication_operational_status_order
    ON publication (
        status,
        created_at DESC,
        id DESC
    );
