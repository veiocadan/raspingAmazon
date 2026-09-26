package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.orchestration.run.ProcessingRunDetail;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunJobMetrics;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPipelineMetrics;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSummary;
import com.raspingamazon.application.operation.orchestration.run.port.ProcessingRunOperationalDetailQueryPort;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Consulta JDBC do detalhe operacional de uma ProcessingRun.
 *
 * <p>Este adapter é estritamente somente leitura.</p>
 *
 * <p>A consulta percorre a linhagem persistida:</p>
 *
 * <pre>
 * ProcessingRun
 *     -> DealCandidate
 *         -> OfferSnapshot
 *             -> DealEvaluation
 *                 -> Publication
 * </pre>
 *
 * <p>ProcessingJobs são associados à run por seu sujeito persistido:</p>
 *
 * <pre>
 * COLLECT_DEALS
 *     -> processing_run_id
 *
 * ENRICH_DEAL
 *     -> deal_candidate_id
 *
 * EVALUATE_DEAL
 *     -> offer_snapshot_id
 * </pre>
 *
 * <p>As métricas são agregadas em escopos independentes para evitar
 * multiplicação de contagens causada por joins entre relações
 * one-to-many, especialmente DealEvaluation -> Publication.</p>
 *
 * <p>O adapter não recalcula elegibilidade, score, momentum ou
 * qualquer regra de publicação.</p>
 */
public final class JdbcProcessingRunOperationalDetailQueryAdapter
    implements ProcessingRunOperationalDetailQueryPort {

    private static final String SQL =
        """
        WITH target_run AS (
            SELECT
                id,
                run_key,
                source_uri,
                status,
                requested_at,
                started_at,
                completed_at,
                last_error_code,
                last_error_message,
                created_at,
                updated_at
            FROM processing_run
            WHERE id = ?
        ),
        candidate_scope AS (
            SELECT
                dc.id,
                dc.offer_snapshot_id
            FROM deal_candidate dc
            INNER JOIN target_run tr
                ON tr.id = dc.processing_run_id
        ),
        snapshot_scope AS (
            SELECT DISTINCT
                cs.offer_snapshot_id
            FROM candidate_scope cs
            WHERE cs.offer_snapshot_id IS NOT NULL
        ),
        evaluation_scope AS (
            SELECT
                de.id,
                de.eligible
            FROM deal_evaluation de
            INNER JOIN snapshot_scope ss
                ON ss.offer_snapshot_id = de.offer_snapshot_id
        ),
        candidate_metrics AS (
            SELECT
                COUNT(*) AS collected_candidates,
                COUNT(*) FILTER (
                    WHERE offer_snapshot_id IS NOT NULL
                ) AS enriched_candidates
            FROM candidate_scope
        ),
        evaluation_metrics AS (
            SELECT
                COUNT(*) AS evaluations,
                COUNT(*) FILTER (
                    WHERE eligible
                ) AS eligible_evaluations,
                COUNT(*) FILTER (
                    WHERE NOT eligible
                ) AS rejected_evaluations
            FROM evaluation_scope
        ),
        publication_metrics AS (
            SELECT
                COUNT(*) AS publications_generated
            FROM publication pub
            INNER JOIN evaluation_scope es
                ON es.id = pub.deal_evaluation_id
        ),
        job_scope AS (
            SELECT
                pj.id,
                pj.status,
                pj.attempt_count
            FROM processing_job pj
            INNER JOIN target_run tr
                ON tr.id = pj.processing_run_id

            UNION ALL

            SELECT
                pj.id,
                pj.status,
                pj.attempt_count
            FROM processing_job pj
            INNER JOIN candidate_scope cs
                ON cs.id = pj.deal_candidate_id

            UNION ALL

            SELECT
                pj.id,
                pj.status,
                pj.attempt_count
            FROM processing_job pj
            INNER JOIN snapshot_scope ss
                ON ss.offer_snapshot_id = pj.offer_snapshot_id
        ),
        job_metrics AS (
            SELECT
                COUNT(*) AS total_jobs,
                COUNT(*) FILTER (
                    WHERE status = 'PENDING'
                ) AS pending_jobs,
                COUNT(*) FILTER (
                    WHERE status = 'RUNNING'
                ) AS running_jobs,
                COUNT(*) FILTER (
                    WHERE status = 'RETRY_WAIT'
                ) AS retry_wait_jobs,
                COUNT(*) FILTER (
                    WHERE status = 'SUCCEEDED'
                ) AS succeeded_jobs,
                COUNT(*) FILTER (
                    WHERE status = 'DEAD'
                ) AS dead_jobs,
                COALESCE(
                    SUM(attempt_count),
                    0
                ) AS total_attempts,
                COALESCE(
                    SUM(
                        GREATEST(
                            attempt_count - 1,
                            0
                        )
                    ),
                    0
                ) AS retry_attempts
            FROM job_scope
        )
        SELECT
            tr.id AS run_id,
            tr.run_key,
            tr.source_uri,
            tr.status AS run_status,
            tr.requested_at,
            tr.started_at,
            tr.completed_at,
            tr.last_error_code,
            tr.last_error_message,
            tr.created_at,
            tr.updated_at,

            cm.collected_candidates,
            cm.enriched_candidates,

            em.evaluations,
            em.eligible_evaluations,
            em.rejected_evaluations,

            pm.publications_generated,

            jm.total_jobs,
            jm.pending_jobs,
            jm.running_jobs,
            jm.retry_wait_jobs,
            jm.succeeded_jobs,
            jm.dead_jobs,
            jm.total_attempts,
            jm.retry_attempts

        FROM target_run tr
        CROSS JOIN candidate_metrics cm
        CROSS JOIN evaluation_metrics em
        CROSS JOIN publication_metrics pm
        CROSS JOIN job_metrics jm
        """;

    private final Connection connection;

    public JdbcProcessingRunOperationalDetailQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Optional<ProcessingRunDetail> findById(
        long runId
    ) {

        if (runId <= 0L) {

            throw new IllegalArgumentException(
                "runId must be positive"
            );
        }

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     SQL
                 )) {

            statement.setLong(
                1,
                runId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                ProcessingRunDetail detail =
                    readDetail(
                        resultSet
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one operational detail row "
                            + "found for ProcessingRun "
                            + runId
                    );
                }

                return Optional.of(
                    detail
                );
            }

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                "Could not load operational detail "
                    + "for ProcessingRun "
                    + runId,
                exception
            );
        }
    }

    private ProcessingRunDetail readDetail(
        ResultSet resultSet
    ) throws SQLException {

        ProcessingRunSummary summary =
            new ProcessingRunSummary(
                resultSet.getLong(
                    "run_id"
                ),
                resultSet.getString(
                    "run_key"
                ),
                resultSet.getString(
                    "source_uri"
                ),
                ProcessingRunStatus.valueOf(
                    resultSet.getString(
                        "run_status"
                    )
                ),
                resultSet.getObject(
                    "requested_at",
                    OffsetDateTime.class
                ),
                resultSet.getObject(
                    "started_at",
                    OffsetDateTime.class
                ),
                resultSet.getObject(
                    "completed_at",
                    OffsetDateTime.class
                ),
                resultSet.getString(
                    "last_error_code"
                ),
                resultSet.getString(
                    "last_error_message"
                ),
                resultSet.getObject(
                    "created_at",
                    OffsetDateTime.class
                ),
                resultSet.getObject(
                    "updated_at",
                    OffsetDateTime.class
                )
            );

        ProcessingRunPipelineMetrics pipeline =
            new ProcessingRunPipelineMetrics(
                resultSet.getLong(
                    "collected_candidates"
                ),
                resultSet.getLong(
                    "enriched_candidates"
                ),
                resultSet.getLong(
                    "evaluations"
                ),
                resultSet.getLong(
                    "eligible_evaluations"
                ),
                resultSet.getLong(
                    "rejected_evaluations"
                ),
                resultSet.getLong(
                    "publications_generated"
                )
            );

        ProcessingRunJobMetrics jobs =
            new ProcessingRunJobMetrics(
                resultSet.getLong(
                    "total_jobs"
                ),
                resultSet.getLong(
                    "pending_jobs"
                ),
                resultSet.getLong(
                    "running_jobs"
                ),
                resultSet.getLong(
                    "retry_wait_jobs"
                ),
                resultSet.getLong(
                    "succeeded_jobs"
                ),
                resultSet.getLong(
                    "dead_jobs"
                ),
                resultSet.getLong(
                    "total_attempts"
                ),
                resultSet.getLong(
                    "retry_attempts"
                )
            );

        return new ProcessingRunDetail(
            summary,
            pipeline,
            jobs
        );
    }
}
