/*
 * FASE 19
 *
 * Suporte à leitura do último available_at que realmente
 * ocupa quota no escopo primário de publicação.
 *
 * Entregas derivadas introduzidas na FASE 19 possuem:
 *
 * quota_profile_version = NULL
 * quota_date = NULL
 *
 * e não participam da quota/cadência primária.
 */

CREATE INDEX idx_publication_outbox_quota_last_available
    ON publication_outbox (
        channel,
        destination,
        quota_date,
        available_at DESC
    )
    WHERE quota_profile_version IS NOT NULL;
