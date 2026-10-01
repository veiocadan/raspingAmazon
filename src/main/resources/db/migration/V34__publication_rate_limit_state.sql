/*
 * FASE 19
 *
 * Estado persistente de rate limiting para integrações externas
 * de publicação.
 *
 * O objetivo desta estrutura é permitir que múltiplos workers/JVMs
 * reservem slots de envio sem depender de estado em memória.
 *
 * A chave representa a integração física, não necessariamente
 * o nome lógico do canal.
 *
 * Exemplos previstos:
 *
 * TELEGRAM_BOT_API
 *     <- TELEGRAM
 *     <- WHATSAPP_MANUAL
 *
 * WHATSAPP_CLOUD_API
 *     <- WHATSAPP
 *
 * Dessa forma, TELEGRAM e WHATSAPP_MANUAL podem compartilhar a
 * mesma capacidade quando utilizarem o mesmo transporte externo.
 */

CREATE TABLE publication_rate_limit_state (

    integration_key VARCHAR(100) PRIMARY KEY,

    /*
     * Primeiro instante ainda não reservado.
     *
     * Uma nova reserva recebe:
     *
     * max(requested_at, next_allowed_at)
     *
     * e avança este campo pelo intervalo configurado.
     */
    next_allowed_at TIMESTAMPTZ NOT NULL,

    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_publication_rate_limit_integration_key
        CHECK (
            length(btrim(integration_key)) > 0
        )
);
