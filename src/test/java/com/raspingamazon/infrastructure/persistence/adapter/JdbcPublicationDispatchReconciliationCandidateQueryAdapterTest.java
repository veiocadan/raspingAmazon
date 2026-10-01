package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationDispatchReconciliationCandidateQueryAdapterTest {

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-01T19:30:00-03:00"
        );

    @Test
    void shouldReturnOnlyActionableRunsWithoutPublicationDispatch()
        throws Exception {

        withConnection(
            connection -> {

                /*
                 * READY:
                 *
                 * run COMPLETED sem candidatos.
                 */
                long readyRunId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED,
                        "ready"
                    );

                /*
                 * BLOCKED:
                 *
                 * existe candidato, mas não há snapshot nem
                 * ENRICH_DEAL ativo.
                 */
                long blockedRunId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED,
                        "blocked"
                    );

                insertCandidate(
                    connection,
                    blockedRunId,
                    asin(
                        "BLK"
                    )
                );

                /*
                 * IN_PROGRESS:
                 *
                 * candidato ainda possui ENRICH_DEAL ativo.
                 *
                 * Essa run NÃO deve entrar no lote.
                 */
                long inProgressRunId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED,
                        "in-progress"
                    );

                long inProgressCandidateId =
                    insertCandidate(
                        connection,
                        inProgressRunId,
                        asin(
                            "PRO"
                        )
                    );

                insertEnrichmentJob(
                    connection,
                    inProgressCandidateId
                );

                /*
                 * FAILED:
                 *
                 * é acionável e será classificada como BLOCKED pelo
                 * readiness autoritativo.
                 */
                long failedRunId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.FAILED,
                        "failed"
                    );

                /*
                 * Já possui PUBLICATION_DISPATCH.
                 *
                 * Mesmo sendo COMPLETED e sem candidatos, ela já foi
                 * reconciliada e não pode reaparecer.
                 */
                long alreadyDispatchedRunId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED,
                        "already-dispatched"
                    );

                JdbcProcessingJobQueueAdapter queue =
                    new JdbcProcessingJobQueueAdapter(
                        connection
                    );

                queue.enqueue(
                    ProcessingJobSubmission.publicationDispatch(
                        alreadyDispatchedRunId,
                        "publication-dispatch:"
                            + alreadyDispatchedRunId,
                        5,
                        NOW
                    )
                );

                JdbcPublicationDispatchReconciliationCandidateQueryAdapter
                    adapter =
                    new JdbcPublicationDispatchReconciliationCandidateQueryAdapter(
                        connection
                    );

                List<Long> candidates =
                    adapter.findCandidates(
                        20
                    );

                assertEquals(
                    List.of(
                        readyRunId,
                        blockedRunId,
                        failedRunId
                    ),
                    candidates
                );

                assertTrue(
                    !candidates.contains(
                        inProgressRunId
                    )
                );

                assertTrue(
                    !candidates.contains(
                        alreadyDispatchedRunId
                    )
                );
            }
        );
    }

    @Test
    void activeEvaluationShouldKeepCompletedRunOutOfCandidateBatch()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED,
                        "active-evaluation"
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        asin(
                            "EVA"
                        )
                    );

                long snapshotId =
                    insertSnapshotAndLinkCandidate(
                        connection,
                        candidateId,
                        asin(
                            "EVA"
                        )
                    );

                insertEvaluationJob(
                    connection,
                    snapshotId
                );

                JdbcPublicationDispatchReconciliationCandidateQueryAdapter
                    adapter =
                    new JdbcPublicationDispatchReconciliationCandidateQueryAdapter(
                        connection
                    );

                assertEquals(
                    List.of(),
                    adapter.findCandidates(
                        10
                    )
                );
            }
        );
    }

    @Test
    void persistedEvaluationShouldMakeCompletedRunActionable()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED,
                        "evaluation-complete"
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        asin(
                            "CMP"
                        )
                    );

                long snapshotId =
                    insertSnapshotAndLinkCandidate(
                        connection,
                        candidateId,
                        asin(
                            "CMP"
                        )
                    );

                /*
                 * O job técnico pode continuar ativo ou atrasado;
                 * o fato persistido final tem precedência.
                 */
                insertEvaluationJob(
                    connection,
                    snapshotId
                );

                insertDealEvaluation(
                    connection,
                    snapshotId
                );

                JdbcPublicationDispatchReconciliationCandidateQueryAdapter
                    adapter =
                    new JdbcPublicationDispatchReconciliationCandidateQueryAdapter(
                        connection
                    );

                assertEquals(
                    List.of(
                        runId
                    ),
                    adapter.findCandidates(
                        10
                    )
                );
            }
        );
    }

    @Test
    void shouldRespectLimitWithDeterministicOrdering()
        throws Exception {

        withConnection(
            connection -> {

                long first =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED,
                        "limit-1"
                    );

                long second =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED,
                        "limit-2"
                    );

                insertProcessingRun(
                    connection,
                    ProcessingRunStatus.COMPLETED,
                    "limit-3"
                );

                JdbcPublicationDispatchReconciliationCandidateQueryAdapter
                    adapter =
                    new JdbcPublicationDispatchReconciliationCandidateQueryAdapter(
                        connection
                    );

                assertEquals(
                    List.of(
                        first,
                        second
                    ),
                    adapter.findCandidates(
                        2
                    )
                );
            }
        );
    }

    @Test
    void shouldRejectInvalidLimit()
        throws Exception {

        withConnection(
            connection -> {

                JdbcPublicationDispatchReconciliationCandidateQueryAdapter
                    adapter =
                    new JdbcPublicationDispatchReconciliationCandidateQueryAdapter(
                        connection
                    );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        adapter.findCandidates(
                            0
                        )
                );
            }
        );
    }

    private long insertProcessingRun(
        Connection connection,
        ProcessingRunStatus status,
        String suffix
    ) throws Exception {

        String unique =
            suffix
                + "-"
                + SEQUENCE.incrementAndGet()
                + "-"
                + Long.toUnsignedString(
                System.nanoTime()
            );

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at
            )
            VALUES (?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                "dispatch-reconciliation-" + unique
            );

            statement.setString(
                2,
                "https://example.test/reconciliation/"
                    + unique
            );

            statement.setString(
                3,
                status.name()
            );

            statement.setObject(
                4,
                NOW
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertCandidate(
        Connection connection,
        long processingRunId,
        String asin
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_candidate (
                processing_run_id,
                asin,
                product_url,
                title,
                current_price,
                collected_at,
                source
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                processingRunId
            );

            statement.setString(
                2,
                asin
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/" + asin
            );

            statement.setString(
                4,
                "Produto reconciliation " + asin
            );

            statement.setBigDecimal(
                5,
                new BigDecimal(
                    "99.90"
                )
            );

            statement.setObject(
                6,
                NOW
            );

            statement.setString(
                7,
                "publication-dispatch-reconciliation-test"
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void insertEnrichmentJob(
        Connection connection,
        long candidateId
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_job (
                job_type,
                status,
                deal_candidate_id,
                idempotency_key,
                attempt_count,
                max_attempts,
                available_at
            )
            VALUES (
                'ENRICH_DEAL',
                'PENDING',
                ?,
                ?,
                0,
                5,
                ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                candidateId
            );

            statement.setString(
                2,
                "reconciliation-enrich:" + candidateId
            );

            statement.setObject(
                3,
                NOW
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private void insertEvaluationJob(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_job (
                job_type,
                status,
                offer_snapshot_id,
                idempotency_key,
                attempt_count,
                max_attempts,
                available_at
            )
            VALUES (
                'EVALUATE_DEAL',
                'PENDING',
                ?,
                ?,
                0,
                5,
                ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setString(
                2,
                "reconciliation-evaluate:" + snapshotId
            );

            statement.setObject(
                3,
                NOW
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private long insertSnapshotAndLinkCandidate(
        Connection connection,
        long candidateId,
        String asin
    ) throws Exception {

        long productId =
            insertProduct(
                connection,
                asin
            );

        String snapshotSql =
            """
            INSERT INTO offer_snapshot (
                product_id,
                collected_at,
                current_price,
                previous_price,
                discount_percentage,
                sold_percentage,
                rating,
                review_count,
                seller_name,
                delivery_provider,
                source
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        long snapshotId;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     snapshotSql
                 )) {

            statement.setLong(
                1,
                productId
            );

            statement.setObject(
                2,
                NOW
            );

            statement.setBigDecimal(
                3,
                new BigDecimal(
                    "99.90"
                )
            );

            statement.setBigDecimal(
                4,
                new BigDecimal(
                    "129.90"
                )
            );

            statement.setBigDecimal(
                5,
                new BigDecimal(
                    "23.09"
                )
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "15.00"
                )
            );

            statement.setBigDecimal(
                7,
                new BigDecimal(
                    "4.80"
                )
            );

            statement.setInt(
                8,
                1000
            );

            statement.setString(
                9,
                "Amazon.com.br"
            );

            statement.setString(
                10,
                "Amazon"
            );

            statement.setString(
                11,
                "publication-dispatch-reconciliation-test"
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                snapshotId =
                    resultSet.getLong(
                        "id"
                    );
            }
        }

        String linkSql =
            """
            UPDATE deal_candidate
            SET offer_snapshot_id = ?
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     linkSql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setLong(
                2,
                candidateId
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }

        return snapshotId;
    }

    private long insertProduct(
        Connection connection,
        String asin
    ) throws Exception {

        String sql =
            """
            INSERT INTO product (
                asin,
                title,
                image_url,
                product_url
            )
            VALUES (?, ?, NULL, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                asin
            );

            statement.setString(
                2,
                "Produto reconciliation " + asin
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/" + asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void insertDealEvaluation(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation (
                offer_snapshot_id,
                eligible,
                rejection_reason,
                eligibility_policy_version,
                score,
                score_version,
                evaluated_at
            )
            VALUES (?, true, NULL, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setString(
                2,
                "RECONCILIATION_ELIGIBILITY_V1"
            );

            statement.setBigDecimal(
                3,
                new BigDecimal(
                    "90.0000"
                )
            );

            statement.setString(
                4,
                "RECONCILIATION_SCORE_V1"
            );

            statement.setObject(
                5,
                NOW
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private String asin(
        String marker
    ) {

        int sequence =
            SEQUENCE.incrementAndGet();

        /*
         * ASIN de exatamente 10 caracteres.
         */
        return "B"
            + marker
            + String.format(
            "%06d",
            sequence
        );
    }

    private void withConnection(
        SqlTestAction action
    ) throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                action.execute(
                    connection
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @FunctionalInterface
    private interface SqlTestAction {

        void execute(
            Connection connection
        ) throws Exception;
    }
}
