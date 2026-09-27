/*
 * FASE 16.5
 *
 * Observações duráveis das fronteiras de integração.
 *
 * Esta tabela não substitui processing_job, processing_run ou os logs
 * estruturados. Ela registra fatos próprios das integrações:
 *
 * - qual integração foi chamada;
 * - qual operação foi executada;
 * - quando ocorreu;
 * - quanto tempo levou;
 * - sucesso ou falha;
 * - origem e classificação da falha;
 * - status HTTP, quando existente;
 * - correlação conhecida naquele ponto.
 *
 * Nenhum payload HTTP, HTML, header, cookie, token ou mensagem livre
 * é persistido.
 */

CREATE TABLE integration_observation (
                                         id BIGSERIAL PRIMARY KEY,

                                         observed_at TIMESTAMPTZ NOT NULL,

                                         integration VARCHAR(100) NOT NULL,
                                         operation VARCHAR(100) NOT NULL,
                                         outcome VARCHAR(16) NOT NULL,

                                         duration_ms BIGINT NOT NULL,

                                         processing_run_id BIGINT,
                                         processing_job_id BIGINT,
                                         job_type VARCHAR(32),
                                         deal_candidate_id BIGINT,
                                         offer_snapshot_id BIGINT,
                                         deal_evaluation_id BIGINT,
                                         publication_id BIGINT,

                                         asin VARCHAR(32),

                                         failure_origin VARCHAR(16),
                                         failure_type VARCHAR(16),
                                         error_code VARCHAR(120),

                                         http_status_code INTEGER,

                                         CONSTRAINT ck_integration_observation_integration
                                             CHECK (BTRIM(integration) <> ''),

                                         CONSTRAINT ck_integration_observation_operation
                                             CHECK (BTRIM(operation) <> ''),

                                         CONSTRAINT ck_integration_observation_outcome
                                             CHECK (
                                                 outcome IN (
                                                             'SUCCESS',
                                                             'FAILURE'
                                                     )
                                                 ),

                                         CONSTRAINT ck_integration_observation_duration
                                             CHECK (duration_ms >= 0),

                                         CONSTRAINT ck_integration_observation_job_type
                                             CHECK (
                                                 job_type IS NULL
                                                     OR job_type IN (
                                                                     'COLLECT_DEALS',
                                                                     'ENRICH_DEAL',
                                                                     'EVALUATE_DEAL'
                                                     )
                                                 ),

                                         CONSTRAINT ck_integration_observation_asin
                                             CHECK (
                                                 asin IS NULL
                                                     OR BTRIM(asin) <> ''
                                                 ),

                                         CONSTRAINT ck_integration_observation_failure_origin
                                             CHECK (
                                                 failure_origin IS NULL
                                                     OR failure_origin IN (
                                                                           'EXTERNAL',
                                                                           'INTERNAL'
                                                     )
                                                 ),

                                         CONSTRAINT ck_integration_observation_failure_type
                                             CHECK (
                                                 failure_type IS NULL
                                                     OR failure_type IN (
                                                                         'TRANSIENT',
                                                                         'PERMANENT'
                                                     )
                                                 ),

                                         CONSTRAINT ck_integration_observation_error_code
                                             CHECK (
                                                 error_code IS NULL
                                                     OR BTRIM(error_code) <> ''
                                                 ),

                                         CONSTRAINT ck_integration_observation_http_status
                                             CHECK (
                                                 http_status_code IS NULL
                                                     OR (
                                                     http_status_code >= 100
                                                         AND http_status_code <= 599
                                                     )
                                                 ),

    /*
     * Uma observação de sucesso não carrega classificação de falha.
     *
     * Uma observação de falha sempre informa:
     *
     * - origem;
     * - tipo;
     * - código.
     *
     * O status HTTP continua opcional porque falhas de DNS, timeout,
     * conexão etc. podem acontecer antes de existir resposta HTTP.
     */
                                         CONSTRAINT ck_integration_observation_failure_metadata
                                             CHECK (
                                                 (
                                                     outcome = 'SUCCESS'
                                                         AND failure_origin IS NULL
                                                         AND failure_type IS NULL
                                                         AND error_code IS NULL
                                                     )
                                                     OR
                                                 (
                                                     outcome = 'FAILURE'
                                                         AND failure_origin IS NOT NULL
                                                         AND failure_type IS NOT NULL
                                                         AND error_code IS NOT NULL
                                                     )
                                                 ),

                                         CONSTRAINT fk_integration_observation_processing_run
                                             FOREIGN KEY (processing_run_id)
                                                 REFERENCES processing_run (id),

                                         CONSTRAINT fk_integration_observation_processing_job
                                             FOREIGN KEY (processing_job_id)
                                                 REFERENCES processing_job (id),

                                         CONSTRAINT fk_integration_observation_deal_candidate
                                             FOREIGN KEY (deal_candidate_id)
                                                 REFERENCES deal_candidate (id),

                                         CONSTRAINT fk_integration_observation_offer_snapshot
                                             FOREIGN KEY (offer_snapshot_id)
                                                 REFERENCES offer_snapshot (id),

                                         CONSTRAINT fk_integration_observation_deal_evaluation
                                             FOREIGN KEY (deal_evaluation_id)
                                                 REFERENCES deal_evaluation (id),

                                         CONSTRAINT fk_integration_observation_publication
                                             FOREIGN KEY (publication_id)
                                                 REFERENCES publication (id)
);

/*
 * Dashboard e métricas por integração no tempo.
 */
CREATE INDEX idx_integration_observation_integration_time
    ON integration_observation (
                                integration,
                                observed_at DESC
        );

/*
 * Alertas e análise de falhas recorrentes.
 */
CREATE INDEX idx_integration_observation_failure_time
    ON integration_observation (
                                integration,
                                failure_origin,
                                failure_type,
                                observed_at DESC
        )
    WHERE outcome = 'FAILURE';

/*
 * Correlação com uma execução.
 */
CREATE INDEX idx_integration_observation_run_time
    ON integration_observation (
                                processing_run_id,
                                observed_at DESC
        )
    WHERE processing_run_id IS NOT NULL;

/*
 * Correlação com uma unidade durável de trabalho.
 */
CREATE INDEX idx_integration_observation_job_time
    ON integration_observation (
                                processing_job_id,
                                observed_at DESC
        )
    WHERE processing_job_id IS NOT NULL;

/*
 * Investigação operacional por produto.
 *
 * ASIN continua sendo atributo de correlação, não identidade da
 * observação.
 */
CREATE INDEX idx_integration_observation_asin_time
    ON integration_observation (
                                asin,
                                observed_at DESC
        )
    WHERE asin IS NOT NULL;
