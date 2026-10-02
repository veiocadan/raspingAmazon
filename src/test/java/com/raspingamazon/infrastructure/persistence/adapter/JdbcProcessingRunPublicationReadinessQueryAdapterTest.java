package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.publication.ProcessingRunPublicationReadiness;
import com.raspingamazon.application.publication.ProcessingRunPublicationReadinessStatus;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcProcessingRunPublicationReadinessQueryAdapterTest {

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-30T22:30:00-03:00"
        );

    @Test
    void pendingRunShouldBeInProgress()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.PENDING
                    );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertEquals(
                    ProcessingRunPublicationReadinessStatus
                        .IN_PROGRESS,
                    readiness.status()
                );

                assertEquals(
                    0L,
                    readiness.totalCandidates()
                );
            }
        );
    }

    @Test
    void completedRunWithoutCandidatesShouldBeReady()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED
                    );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertTrue(
                    readiness.ready()
                );

                assertEquals(
                    0L,
                    readiness.totalCandidates()
                );
            }
        );
    }

    @Test
    void failedRunShouldBeBlocked()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.FAILED
                    );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertTrue(
                    readiness.blocked()
                );
            }
        );
    }

    @Test
    void activeEnrichmentWithoutSnapshotShouldBeInProgress()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0RDY00001"
                    );

                insertEnrichmentJob(
                    connection,
                    candidateId,
                    "PENDING"
                );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertEquals(
                    1L,
                    readiness.totalCandidates()
                );

                assertEquals(
                    0L,
                    readiness.completedCandidates()
                );

                assertEquals(
                    1L,
                    readiness.inProgressCandidates()
                );

                assertEquals(
                    0L,
                    readiness.blockedCandidates()
                );

                assertTrue(
                    readiness.inProgress()
                );
            }
        );
    }

    @Test
    void deadEnrichmentWithoutSnapshotShouldBlockRun()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0RDY00002"
                    );

                insertEnrichmentJob(
                    connection,
                    candidateId,
                    "DEAD"
                );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertEquals(
                    1L,
                    readiness.blockedCandidates()
                );

                assertTrue(
                    readiness.blocked()
                );
            }
        );
    }

    @Test
    void activeEvaluationWithoutEvaluationFactShouldBeInProgress()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0RDY00003"
                    );

                long snapshotId =
                    insertSnapshotAndLinkCandidate(
                        connection,
                        candidateId,
                        "B0RDY00003"
                    );

                insertEvaluationJob(
                    connection,
                    snapshotId,
                    "RETRY_WAIT"
                );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertEquals(
                    1L,
                    readiness.inProgressCandidates()
                );

                assertTrue(
                    readiness.inProgress()
                );
            }
        );
    }

    @Test
    void deadEvaluationWithoutEvaluationFactShouldBlockRun()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0RDY00004"
                    );

                long snapshotId =
                    insertSnapshotAndLinkCandidate(
                        connection,
                        candidateId,
                        "B0RDY00004"
                    );

                insertEvaluationJob(
                    connection,
                    snapshotId,
                    "DEAD"
                );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertEquals(
                    1L,
                    readiness.blockedCandidates()
                );

                assertTrue(
                    readiness.blocked()
                );
            }
        );
    }

    @Test
    void persistedEvaluationShouldWinOverTechnicalJobStatus()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0RDY00005"
                    );

                long snapshotId =
                    insertSnapshotAndLinkCandidate(
                        connection,
                        candidateId,
                        "B0RDY00005"
                    );

                /*
                 * Simula:
                 *
                 * DealEvaluation COMMIT
                 *      ↓
                 * queda da JVM antes de markSucceeded
                 *
                 * O estado técnico pode inclusive terminar DEAD
                 * depois de falhas posteriores de infraestrutura.
                 *
                 * O fato final já existe e é a autoridade.
                 */
                insertEvaluationJob(
                    connection,
                    snapshotId,
                    "DEAD"
                );

                insertDealEvaluation(
                    connection,
                    snapshotId
                );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertEquals(
                    1L,
                    readiness.completedCandidates()
                );

                assertEquals(
                    0L,
                    readiness.inProgressCandidates()
                );

                assertEquals(
                    0L,
                    readiness.blockedCandidates()
                );

                assertTrue(
                    readiness.ready()
                );
            }
        );
    }

    @Test
    void missingExpectedJobShouldBeBlocked()
        throws Exception {

        withConnection(
            connection -> {

                long runId =
                    insertProcessingRun(
                        connection,
                        ProcessingRunStatus.COMPLETED
                    );

                insertCandidate(
                    connection,
                    runId,
                    "B0RDY00006"
                );

                ProcessingRunPublicationReadiness readiness =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            runId
                        )
                        .orElseThrow();

                assertTrue(
                    readiness.blocked()
                );

                assertEquals(
                    1L,
                    readiness.blockedCandidates()
                );
            }
        );
    }

    @Test
    void unknownRunShouldReturnEmpty()
        throws Exception {

        withConnection(
            connection -> {

                Optional<ProcessingRunPublicationReadiness> result =
                    adapter(
                        connection
                    )
                        .findByProcessingRunId(
                            Long.MAX_VALUE
                        );

                assertTrue(
                    result.isEmpty()
                );
            }
        );
    }

    @Test
    void shouldRejectInvalidProcessingRunId()
        throws Exception {

        withConnection(
            connection ->
                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        adapter(
                            connection
                        )
                            .findByProcessingRunId(
                                0L
                            )
                )
        );
    }

    private JdbcProcessingRunPublicationReadinessQueryAdapter
    adapter(
        Connection connection
    ) {

        return new JdbcProcessingRunPublicationReadinessQueryAdapter(
            connection
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

    private long insertProcessingRun(
        Connection connection,
        ProcessingRunStatus status
    ) throws Exception {

        String unique =
            SEQUENCE.incrementAndGet()
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
                "readiness-" + unique
            );

            statement.setString(
                2,
                "https://example.test/readiness/" + unique
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
                "Produto readiness " + asin
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
                "readiness-test"
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
        long candidateId,
        String status
    ) throws Exception {

        insertJob(
            connection,
            "ENRICH_DEAL",
            status,
            candidateId,
            null,
            "readiness-enrich:" + candidateId
        );
    }

    private void insertEvaluationJob(
        Connection connection,
        long snapshotId,
        String status
    ) throws Exception {

        insertJob(
            connection,
            "EVALUATE_DEAL",
            status,
            null,
            snapshotId,
            "readiness-evaluate:" + snapshotId
        );
    }

    private void insertJob(
        Connection connection,
        String type,
        String status,
        Long candidateId,
        Long snapshotId,
        String idempotencyKey
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_job (
                job_type,
                status,
                processing_run_id,
                deal_candidate_id,
                offer_snapshot_id,
                idempotency_key,
                attempt_count,
                max_attempts,
                available_at,
                last_failure_type,
                last_error_code,
                last_error_message,
                finished_at
            )
            VALUES (?, ?, NULL, ?, ?, ?, ?, 5, ?, ?, ?, ?, ?)
            """;

        boolean dead =
            "DEAD".equals(
                status
            );

        boolean retryWait =
            "RETRY_WAIT".equals(
                status
            );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                type
            );

            statement.setString(
                2,
                status
            );

            setNullableLong(
                statement,
                3,
                candidateId
            );

            setNullableLong(
                statement,
                4,
                snapshotId
            );

            statement.setString(
                5,
                idempotencyKey
            );

            statement.setInt(
                6,
                dead || retryWait
                    ? 1
                    : 0
            );

            statement.setObject(
                7,
                NOW
            );

            if (dead) {

                statement.setString(
                    8,
                    "PERMANENT"
                );

                statement.setString(
                    9,
                    "READINESS_TEST_DEAD"
                );

                statement.setString(
                    10,
                    "terminal test job"
                );

                statement.setObject(
                    11,
                    NOW
                );

            } else if (retryWait) {

                statement.setString(
                    8,
                    "TRANSIENT"
                );

                statement.setString(
                    9,
                    "READINESS_TEST_RETRY"
                );

                statement.setString(
                    10,
                    "retry test job"
                );

                statement.setObject(
                    11,
                    null
                );

            } else {

                statement.setObject(
                    8,
                    null
                );

                statement.setObject(
                    9,
                    null
                );

                statement.setObject(
                    10,
                    null
                );

                statement.setObject(
                    11,
                    null
                );
            }

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
                "readiness-test"
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
                "Produto readiness " + asin
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
                "ELIGIBILITY_READINESS_TEST_V1"
            );

            statement.setBigDecimal(
                3,
                new BigDecimal(
                    "90.0000"
                )
            );

            statement.setString(
                4,
                "SCORE_READINESS_TEST_V1"
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

    private void setNullableLong(
        PreparedStatement statement,
        int index,
        Long value
    ) throws Exception {

        if (value == null) {

            statement.setObject(
                index,
                null
            );

            return;
        }

        statement.setLong(
            index,
            value
        );
    }

    @FunctionalInterface
    private interface SqlTestAction {

        void execute(
            Connection connection
        ) throws Exception;
    }
}
