package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.publication.ProcessingRunPublicationReadiness;
import com.raspingamazon.application.publication.port.ProcessingRunPublicationReadinessQueryPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * Projeção JDBC da prontidão de uma ProcessingRun para
 * seleção/publicação.
 *
 * <p>A consulta acompanha a linhagem persistente:</p>
 *
 * <pre>
 * ProcessingRun
 *      |
 *      v
 * DealCandidate
 *      |
 *      v
 * OfferSnapshot
 *      |
 *      v
 * DealEvaluation
 * </pre>
 *
 * <p>Os ProcessingJobs são utilizados somente quando o fato
 * esperado ainda não existe.</p>
 *
 * <p>Isso é deliberado: o fato persistido possui precedência sobre
 * o status técnico do job, pois é possível que:</p>
 *
 * <pre>
 * caso de uso faz COMMIT
 *      |
 *      v
 * processo cai
 *      |
 *      v
 * job ainda não foi marcado SUCCEEDED
 * </pre>
 *
 * <p>Nesse cenário não devemos transformar trabalho já concluído
 * em bloqueio artificial.</p>
 */
public final class
JdbcProcessingRunPublicationReadinessQueryAdapter
    implements ProcessingRunPublicationReadinessQueryPort {

    private final Connection connection;

    public JdbcProcessingRunPublicationReadinessQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Optional<ProcessingRunPublicationReadiness>
    findByProcessingRunId(
        long processingRunId
    ) {

        if (processingRunId <= 0L) {

            throw new IllegalArgumentException(
                "processingRunId must be positive"
            );
        }

        String sql =
            """
            WITH target_run AS (
                SELECT
                    id,
                    status
                FROM processing_run
                WHERE id = ?
            ),
            candidate_state AS (
                SELECT
                    candidate.id,
                    CASE

                        /*
                         * O fato final existe.
                         *
                         * O estado técnico de EVALUATE_DEAL deixa
                         * de ser relevante para a prontidão.
                         */
                        WHEN candidate.offer_snapshot_id IS NOT NULL
                         AND EXISTS (
                             SELECT 1
                             FROM deal_evaluation AS evaluation
                             WHERE evaluation.offer_snapshot_id =
                                 candidate.offer_snapshot_id
                         )
                        THEN 'COMPLETED'

                        /*
                         * Ainda não há snapshot, mas existe trabalho
                         * ativo capaz de produzi-lo.
                         */
                        WHEN candidate.offer_snapshot_id IS NULL
                         AND EXISTS (
                             SELECT 1
                             FROM processing_job AS enrichment_job
                             WHERE enrichment_job.job_type =
                                 'ENRICH_DEAL'
                               AND enrichment_job.deal_candidate_id =
                                   candidate.id
                               AND enrichment_job.status IN (
                                   'PENDING',
                                   'RUNNING',
                                   'RETRY_WAIT'
                               )
                         )
                        THEN 'IN_PROGRESS'

                        /*
                         * Já existe snapshot, ainda não existe
                         * DealEvaluation e existe EVALUATE_DEAL
                         * ativo capaz de produzi-la.
                         */
                        WHEN candidate.offer_snapshot_id IS NOT NULL
                         AND EXISTS (
                             SELECT 1
                             FROM processing_job AS evaluation_job
                             WHERE evaluation_job.job_type =
                                 'EVALUATE_DEAL'
                               AND evaluation_job.offer_snapshot_id =
                                   candidate.offer_snapshot_id
                               AND evaluation_job.status IN (
                                   'PENDING',
                                   'RUNNING',
                                   'RETRY_WAIT'
                               )
                         )
                        THEN 'IN_PROGRESS'

                        /*
                         * Nenhum fato final e nenhum trabalho ativo
                         * capaz de produzi-lo.
                         *
                         * Isso inclui:
                         *
                         * - DEAD;
                         * - SUCCEEDED sem fato esperado;
                         * - job estruturalmente ausente.
                         */
                        ELSE 'BLOCKED'
                    END AS logical_state
                FROM deal_candidate AS candidate
                INNER JOIN target_run AS run
                    ON run.id = candidate.processing_run_id
            )
            SELECT
                run.status AS processing_run_status,

                COUNT(
                    candidate_state.logical_state
                ) AS total_candidates,

                COUNT(*) FILTER (
                    WHERE candidate_state.logical_state =
                        'COMPLETED'
                ) AS completed_candidates,

                COUNT(*) FILTER (
                    WHERE candidate_state.logical_state =
                        'IN_PROGRESS'
                ) AS in_progress_candidates,

                COUNT(*) FILTER (
                    WHERE candidate_state.logical_state =
                        'BLOCKED'
                ) AS blocked_candidates

            FROM target_run AS run

            LEFT JOIN candidate_state
                ON TRUE

            GROUP BY
                run.status
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                processingRunId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    return Optional.empty();
                }

                ProcessingRunStatus runStatus =
                    ProcessingRunStatus.valueOf(
                        resultSet.getString(
                            "processing_run_status"
                        )
                    );

                long totalCandidates =
                    resultSet.getLong(
                        "total_candidates"
                    );

                long completedCandidates =
                    resultSet.getLong(
                        "completed_candidates"
                    );

                long inProgressCandidates =
                    resultSet.getLong(
                        "in_progress_candidates"
                    );

                long blockedCandidates =
                    resultSet.getLong(
                        "blocked_candidates"
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "ProcessingRun readiness query returned "
                            + "more than one row for id "
                            + processingRunId
                    );
                }

                return Optional.of(
                    ProcessingRunPublicationReadiness.from(
                        processingRunId,
                        runStatus,
                        totalCandidates,
                        completedCandidates,
                        inProgressCandidates,
                        blockedCandidates
                    )
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to query publication readiness "
                    + "for ProcessingRun "
                    + processingRunId,
                exception
            );
        }
    }
}
