package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.observability.alert.OperationalAlert;
import com.raspingamazon.application.operation.observability.alert.OperationalAlertPolicy;
import com.raspingamazon.application.operation.observability.alert.OperationalAlertType;
import com.raspingamazon.application.operation.observability.alert.port.OperationalAlertQueryPort;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapter PostgreSQL que deriva alertas operacionais a partir dos
 * fatos já persistidos pelo sistema.
 *
 * <p>Nenhum alerta é persistido por este adapter. Cada chamada
 * representa uma avaliação pontual do estado operacional atual.</p>
 *
 * <p>As regras derivadas são:</p>
 *
 * <ol>
 *     <li>falhas externas repetidas dentro da janela configurada;</li>
 *     <li>jobs DEAD agrupados pela ProcessingRun alcançável;</li>
 *     <li>última ProcessingRun COMPLETED sem candidatos;</li>
 *     <li>queda suspeita da última coleta em relação às N runs
 *         COMPLETED imediatamente anteriores.</li>
 * </ol>
 *
 * <p>Esta classe não agenda execução, não faz polling, não altera
 * retry, não modifica jobs e não recalcula regras comerciais.</p>
 */
public final class JdbcOperationalAlertQueryAdapter
    implements OperationalAlertQueryPort {

    private static final String SQL =
        """
        WITH alert_parameters AS (
            SELECT
                CAST(? AS TIMESTAMPTZ)
                    AS failure_window_start,
                CAST(? AS TIMESTAMPTZ)
                    AS evaluated_at,
                CAST(? AS INTEGER)
                    AS failure_threshold,
                CAST(? AS INTEGER)
                    AS lookback_runs,
                CAST(? AS NUMERIC)
                    AS drop_fraction,
                CAST(? AS BIGINT)
                    AS minimum_baseline_candidates
        ),

        /*
         * =========================================================
         * REPEATED EXTERNAL FAILURES
         * =========================================================
         *
         * Conta somente fatos explicitamente registrados como
         * FAILURE + EXTERNAL dentro da janela da política.
         */
        external_failure_counts AS (
            SELECT
                observation.integration,
                COUNT(*)::BIGINT
                    AS failure_count
            FROM integration_observation observation
            CROSS JOIN alert_parameters parameters
            WHERE observation.observed_at
                    >= parameters.failure_window_start
              AND observation.observed_at
                    <= parameters.evaluated_at
              AND observation.outcome = 'FAILURE'
              AND observation.failure_origin = 'EXTERNAL'
            GROUP BY
                observation.integration
        ),

        external_failure_alerts AS (
            SELECT
                'REPEATED_EXTERNAL_FAILURES'::TEXT
                    AS alert_type,
                NULL::BIGINT
                    AS run_id,
                counts.integration
                    AS integration,
                counts.failure_count
                    AS observed_count,
                NULL::BIGINT
                    AS reference_count
            FROM external_failure_counts counts
            CROSS JOIN alert_parameters parameters
            WHERE counts.failure_count
                    >= parameters.failure_threshold
        ),

        /*
         * =========================================================
         * PROCESSING JOB -> PROCESSING RUN LINEAGE
         * =========================================================
         *
         * COLLECT_DEALS:
         *     processing_job.processing_run_id
         *
         * ENRICH_DEAL:
         *     processing_job.deal_candidate_id
         *         -> deal_candidate.processing_run_id
         *
         * EVALUATE_DEAL:
         *     processing_job.offer_snapshot_id
         *         -> deal_candidate.offer_snapshot_id
         *         -> deal_candidate.processing_run_id
         *
         * UNION remove duplicidade quando o mesmo snapshot estiver
         * ligado a mais de um candidate da mesma run.
         */
        job_run_scope AS (
            SELECT
                job.id
                    AS job_id,
                job.status,
                job.processing_run_id
                    AS run_id
            FROM processing_job job
            CROSS JOIN alert_parameters parameters
            WHERE job.processing_run_id IS NOT NULL
              AND job.created_at <= parameters.evaluated_at

            UNION

            SELECT
                job.id
                    AS job_id,
                job.status,
                candidate.processing_run_id
                    AS run_id
            FROM processing_job job
            JOIN deal_candidate candidate
              ON candidate.id = job.deal_candidate_id
            CROSS JOIN alert_parameters parameters
            WHERE job.deal_candidate_id IS NOT NULL
              AND job.created_at <= parameters.evaluated_at

            UNION

            SELECT
                job.id
                    AS job_id,
                job.status,
                candidate.processing_run_id
                    AS run_id
            FROM processing_job job
            JOIN deal_candidate candidate
              ON candidate.offer_snapshot_id
                    = job.offer_snapshot_id
            CROSS JOIN alert_parameters parameters
            WHERE job.offer_snapshot_id IS NOT NULL
              AND job.created_at <= parameters.evaluated_at
        ),

        dead_job_counts AS (
            SELECT
                scope.run_id,
                COUNT(DISTINCT scope.job_id)::BIGINT
                    AS dead_job_count
            FROM job_run_scope scope
            WHERE scope.status = 'DEAD'
            GROUP BY
                scope.run_id
        ),

        dead_job_alerts AS (
            SELECT
                'DEAD_JOBS'::TEXT
                    AS alert_type,
                counts.run_id,
                NULL::TEXT
                    AS integration,
                counts.dead_job_count
                    AS observed_count,
                NULL::BIGINT
                    AS reference_count
            FROM dead_job_counts counts
        ),

        /*
         * =========================================================
         * CURRENT COLLECTION HEALTH
         * =========================================================
         *
         * Para ZERO_CANDIDATES e SUSPICIOUS_COLLECTION_DROP,
         * "estado atual" significa a ProcessingRun COMPLETED mais
         * recente no instante avaliado.
         *
         * requested_at + id fornece ordenação determinística.
         */
        latest_completed_run AS (
            SELECT
                run.id
                    AS run_id,
                run.requested_at,
                COUNT(candidate.id)::BIGINT
                    AS candidate_count
            FROM processing_run run
            LEFT JOIN deal_candidate candidate
              ON candidate.processing_run_id = run.id
            CROSS JOIN alert_parameters parameters
            WHERE run.status = 'COMPLETED'
              AND run.completed_at IS NOT NULL
              AND run.completed_at <= parameters.evaluated_at
            GROUP BY
                run.id,
                run.requested_at
            ORDER BY
                run.requested_at DESC,
                run.id DESC
            LIMIT 1
        ),

        /*
         * As N runs COMPLETED imediatamente anteriores formam a
         * referência histórica.
         *
         * A regra somente será avaliada se existirem exatamente N
         * runs anteriores. Com histórico insuficiente, não inventamos
         * baseline.
         */
        historical_run_counts AS (
            SELECT
                run.id
                    AS run_id,
                run.requested_at,
                COUNT(candidate.id)::BIGINT
                    AS candidate_count
            FROM processing_run run
            JOIN latest_completed_run current_run
              ON (
                    run.requested_at,
                    run.id
                 )
                 <
                 (
                    current_run.requested_at,
                    current_run.run_id
                 )
            LEFT JOIN deal_candidate candidate
              ON candidate.processing_run_id = run.id
            CROSS JOIN alert_parameters parameters
            WHERE run.status = 'COMPLETED'
              AND run.completed_at IS NOT NULL
              AND run.completed_at <= parameters.evaluated_at
            GROUP BY
                run.id,
                run.requested_at
            ORDER BY
                run.requested_at DESC,
                run.id DESC
            LIMIT (
                SELECT
                    lookback_runs
                FROM alert_parameters
            )
        ),

        historical_baseline AS (
            SELECT
                COUNT(*)::INTEGER
                    AS history_count,
                AVG(
                    candidate_count::NUMERIC
                )
                    AS average_candidates,
                CEIL(
                    AVG(
                        candidate_count::NUMERIC
                    )
                )::BIGINT
                    AS reference_count
            FROM historical_run_counts
        ),

        /*
         * Zero candidatos possui alerta próprio.
         */
        zero_candidate_alert AS (
            SELECT
                'ZERO_CANDIDATES'::TEXT
                    AS alert_type,
                current_run.run_id,
                NULL::TEXT
                    AS integration,
                current_run.candidate_count
                    AS observed_count,
                NULL::BIGINT
                    AS reference_count
            FROM latest_completed_run current_run
            WHERE current_run.candidate_count = 0
        ),

        /*
         * A queda suspeita:
         *
         * - somente é avaliada para candidate_count > 0;
         * - exige N runs anteriores;
         * - exige baseline médio mínimo;
         * - compara com a média exata, sem arredondá-la;
         * - reference_count é somente a representação inteira
         *   apresentada ao operador.
         *
         * O uso de <= torna inclusivo o limiar configurado:
         * dropFraction = 0.50 significa queda de 50% OU MAIS.
         */
        suspicious_drop_alert AS (
            SELECT
                'SUSPICIOUS_COLLECTION_DROP'::TEXT
                    AS alert_type,
                current_run.run_id,
                NULL::TEXT
                    AS integration,
                current_run.candidate_count
                    AS observed_count,
                baseline.reference_count
                    AS reference_count
            FROM latest_completed_run current_run
            CROSS JOIN historical_baseline baseline
            CROSS JOIN alert_parameters parameters
            WHERE current_run.candidate_count > 0
              AND baseline.history_count
                    = parameters.lookback_runs
              AND baseline.average_candidates
                    >= parameters.minimum_baseline_candidates
              AND current_run.candidate_count::NUMERIC
                    <= (
                        baseline.average_candidates
                        * (
                            1::NUMERIC
                            - parameters.drop_fraction
                        )
                    )
        )

        SELECT
            alert_type,
            run_id,
            integration,
            observed_count,
            reference_count
        FROM external_failure_alerts

        UNION ALL

        SELECT
            alert_type,
            run_id,
            integration,
            observed_count,
            reference_count
        FROM dead_job_alerts

        UNION ALL

        SELECT
            alert_type,
            run_id,
            integration,
            observed_count,
            reference_count
        FROM zero_candidate_alert

        UNION ALL

        SELECT
            alert_type,
            run_id,
            integration,
            observed_count,
            reference_count
        FROM suspicious_drop_alert

        ORDER BY
            alert_type,
            run_id NULLS LAST,
            integration NULLS LAST
        """;

    private final Connection connection;

    public JdbcOperationalAlertQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public List<OperationalAlert> findActiveAlerts(
        OperationalAlertPolicy policy,
        OffsetDateTime evaluatedAt
    ) {

        Objects.requireNonNull(
            policy,
            "policy must not be null"
        );

        Objects.requireNonNull(
            evaluatedAt,
            "evaluatedAt must not be null"
        );

        OffsetDateTime failureWindowStart =
            evaluatedAt.minus(
                policy.repeatedExternalFailureWindow()
            );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     SQL
                 )) {

            bindParameters(
                statement,
                policy,
                failureWindowStart,
                evaluatedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                return readAlerts(
                    resultSet
                );
            }

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                "Could not query operational alerts",
                exception
            );
        }
    }

    private void bindParameters(
        PreparedStatement statement,
        OperationalAlertPolicy policy,
        OffsetDateTime failureWindowStart,
        OffsetDateTime evaluatedAt
    ) throws SQLException {

        statement.setObject(
            1,
            failureWindowStart
        );

        statement.setObject(
            2,
            evaluatedAt
        );

        statement.setInt(
            3,
            policy.repeatedExternalFailureThreshold()
        );

        statement.setInt(
            4,
            policy.suspiciousCollectionLookbackRuns()
        );

        statement.setBigDecimal(
            5,
            policy.suspiciousCollectionDropFraction()
        );

        statement.setLong(
            6,
            policy.suspiciousCollectionMinimumBaselineCandidates()
        );
    }

    private List<OperationalAlert> readAlerts(
        ResultSet resultSet
    ) throws SQLException {

        List<OperationalAlert> alerts =
            new ArrayList<>();

        while (resultSet.next()) {

            alerts.add(
                readAlert(
                    resultSet
                )
            );
        }

        return List.copyOf(
            alerts
        );
    }

    private OperationalAlert readAlert(
        ResultSet resultSet
    ) throws SQLException {

        return new OperationalAlert(
            OperationalAlertType.valueOf(
                resultSet.getString(
                    "alert_type"
                )
            ),
            nullableLong(
                resultSet,
                "run_id"
            ),
            resultSet.getString(
                "integration"
            ),
            resultSet.getLong(
                "observed_count"
            ),
            nullableLong(
                resultSet,
                "reference_count"
            )
        );
    }

    private Long nullableLong(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        Object value =
            resultSet.getObject(
                column
            );

        if (value == null) {
            return null;
        }

        if (!(value instanceof Number number)) {

            throw new SQLException(
                "Expected numeric column "
                    + column
                    + " but received "
                    + value.getClass()
                    .getName()
            );
        }

        return number.longValue();
    }
}
