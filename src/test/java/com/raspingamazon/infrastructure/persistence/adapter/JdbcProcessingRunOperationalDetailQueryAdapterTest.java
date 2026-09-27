package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.operation.orchestration.run.ProcessingRunDetail;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunIntegrationMetrics;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunJobMetrics;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPipelineMetrics;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcProcessingRunOperationalDetailQueryAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2099-07-10T12:00:00-03:00"
        );

    private static final String SOURCE =
        "TEST_PROCESSING_RUN_DETAIL";

    @Test
    void shouldAggregateRunPipelineAndJobsWithoutMultiplyingCounts()
        throws Exception {

        inTransaction(
            connection -> {

                long runId =
                    insertRun(
                        connection,
                        "observability-detail-main-"
                            + UUID.randomUUID()
                    );

                long firstProductId =
                    insertProduct(
                        connection,
                        "B0RUN16001",
                        "Produto observabilidade 1"
                    );

                long firstSnapshotId =
                    insertSnapshot(
                        connection,
                        firstProductId,
                        BASE_TIME.minusMinutes(
                            10
                        )
                    );

                long firstCandidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0RUN16001",
                        firstSnapshotId,
                        BASE_TIME.minusMinutes(
                            10
                        )
                    );

                long secondProductId =
                    insertProduct(
                        connection,
                        "B0RUN16002",
                        "Produto observabilidade 2"
                    );

                long secondSnapshotId =
                    insertSnapshot(
                        connection,
                        secondProductId,
                        BASE_TIME.minusMinutes(
                            9
                        )
                    );

                long secondCandidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0RUN16002",
                        secondSnapshotId,
                        BASE_TIME.minusMinutes(
                            9
                        )
                    );

                long thirdCandidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0RUN16003",
                        null,
                        BASE_TIME.minusMinutes(
                            8
                        )
                    );

                long firstEvaluationId =
                    insertEvaluation(
                        connection,
                        firstSnapshotId,
                        true,
                        null,
                        new BigDecimal(
                            "84.0000"
                        ),
                        BASE_TIME.minusMinutes(
                            5
                        )
                    );

                long secondEvaluationId =
                    insertEvaluation(
                        connection,
                        secondSnapshotId,
                        false,
                        "RATING_BELOW_MINIMUM",
                        null,
                        BASE_TIME.minusMinutes(
                            4
                        )
                    );

                /*
                 * Duas publicações para a primeira avaliação são
                 * deliberadas.
                 *
                 * Elas provam que evaluations continua igual a 2,
                 * enquanto publicationsGenerated é contado
                 * independentemente.
                 */
                insertPublication(
                    connection,
                    firstEvaluationId,
                    "RUN_DETAIL_TEMPLATE_V1"
                );

                insertPublication(
                    connection,
                    firstEvaluationId,
                    "RUN_DETAIL_TEMPLATE_V2"
                );

                insertPublication(
                    connection,
                    secondEvaluationId,
                    "RUN_DETAIL_REJECTED_AUDIT"
                );

                /*
                 * Jobs pertencentes à run através dos três tipos
                 * diferentes de sujeito persistido.
                 */
                insertJob(
                    connection,
                    "COLLECT_DEALS",
                    "SUCCEEDED",
                    runId,
                    null,
                    null,
                    1,
                    "run-detail-collect-"
                        + runId
                );

                insertJob(
                    connection,
                    "ENRICH_DEAL",
                    "SUCCEEDED",
                    null,
                    firstCandidateId,
                    null,
                    1,
                    "run-detail-enrich-"
                        + firstCandidateId
                );

                insertJob(
                    connection,
                    "ENRICH_DEAL",
                    "RETRY_WAIT",
                    null,
                    secondCandidateId,
                    null,
                    2,
                    "run-detail-enrich-"
                        + secondCandidateId
                );

                insertJob(
                    connection,
                    "ENRICH_DEAL",
                    "PENDING",
                    null,
                    thirdCandidateId,
                    null,
                    0,
                    "run-detail-enrich-"
                        + thirdCandidateId
                );

                insertJob(
                    connection,
                    "EVALUATE_DEAL",
                    "SUCCEEDED",
                    null,
                    null,
                    firstSnapshotId,
                    1,
                    "run-detail-evaluate-"
                        + firstSnapshotId
                );

                insertJob(
                    connection,
                    "EVALUATE_DEAL",
                    "DEAD",
                    null,
                    null,
                    secondSnapshotId,
                    3,
                    "run-detail-evaluate-"
                        + secondSnapshotId
                );

                /*
                 * Contexto de outra ProcessingRun.
                 *
                 * Esses dados não podem contaminar as métricas da
                 * run principal.
                 */
                insertForeignRunContext(
                    connection
                );

                JdbcProcessingRunOperationalDetailQueryAdapter adapter =
                    new JdbcProcessingRunOperationalDetailQueryAdapter(
                        connection
                    );

                ProcessingRunDetail detail =
                    adapter.findById(
                            runId
                        )
                        .orElseThrow();

                assertEquals(
                    runId,
                    detail.summary()
                        .runId()
                );

                assertEquals(
                    ProcessingRunStatus.COMPLETED,
                    detail.summary()
                        .status()
                );

                ProcessingRunPipelineMetrics pipeline =
                    detail.pipeline();

                assertEquals(
                    3L,
                    pipeline.collectedCandidates()
                );

                assertEquals(
                    2L,
                    pipeline.enrichedCandidates()
                );

                assertEquals(
                    1L,
                    pipeline.pendingEnrichmentCandidates()
                );

                assertEquals(
                    2L,
                    pipeline.evaluations()
                );

                assertEquals(
                    1L,
                    pipeline.eligibleEvaluations()
                );

                assertEquals(
                    1L,
                    pipeline.rejectedEvaluations()
                );

                assertEquals(
                    3L,
                    pipeline.publicationsGenerated()
                );

                ProcessingRunJobMetrics jobs =
                    detail.jobs();

                assertEquals(
                    6L,
                    jobs.totalJobs()
                );

                assertEquals(
                    1L,
                    jobs.pendingJobs()
                );

                assertEquals(
                    0L,
                    jobs.runningJobs()
                );

                assertEquals(
                    1L,
                    jobs.retryWaitJobs()
                );

                assertEquals(
                    3L,
                    jobs.succeededJobs()
                );

                assertEquals(
                    1L,
                    jobs.deadJobs()
                );

                assertEquals(
                    8L,
                    jobs.totalAttempts()
                );

                assertEquals(
                    3L,
                    jobs.retryAttempts()
                );

                assertTrue(
                    jobs.hasActiveJobs()
                );

                assertTrue(
                    jobs.hasDeadJobs()
                );
            }
        );
    }

    @Test
    void shouldAggregateIntegrationMetricsOnlyForRequestedRun()
        throws Exception {

        inTransaction(
            connection -> {

                long runId =
                    insertRun(
                        connection,
                        "observability-integration-main-"
                            + UUID.randomUUID()
                    );

                long foreignRunId =
                    insertRun(
                        connection,
                        "observability-integration-foreign-"
                            + UUID.randomUUID()
                    );

                insertIntegrationObservation(
                    connection,
                    runId,
                    "amazon-deals-http",
                    "SUCCESS",
                    120L,
                    null
                );

                insertIntegrationObservation(
                    connection,
                    runId,
                    "amazon-deals-http",
                    "FAILURE",
                    180L,
                    "EXTERNAL"
                );

                insertIntegrationObservation(
                    connection,
                    runId,
                    "amazon-deals-http",
                    "FAILURE",
                    240L,
                    "INTERNAL"
                );

                insertIntegrationObservation(
                    connection,
                    runId,
                    "amazon-product-page",
                    "SUCCESS",
                    60L,
                    null
                );

                insertIntegrationObservation(
                    connection,
                    runId,
                    "amazon-product-page",
                    "SUCCESS",
                    90L,
                    null
                );

                insertIntegrationObservation(
                    connection,
                    runId,
                    "amazon-product-page",
                    "FAILURE",
                    150L,
                    "EXTERNAL"
                );

                /*
                 * Esta observação pertence a outra run e não pode
                 * contaminar nenhuma métrica da execução consultada.
                 */
                insertIntegrationObservation(
                    connection,
                    foreignRunId,
                    "amazon-deals-http",
                    "FAILURE",
                    999L,
                    "EXTERNAL"
                );

                JdbcProcessingRunOperationalDetailQueryAdapter adapter =
                    new JdbcProcessingRunOperationalDetailQueryAdapter(
                        connection
                    );

                ProcessingRunDetail detail =
                    adapter.findById(
                            runId
                        )
                        .orElseThrow();

                List<ProcessingRunIntegrationMetrics> integrations =
                    detail.integrations();

                assertEquals(
                    2,
                    integrations.size()
                );

                ProcessingRunIntegrationMetrics collection =
                    integrations.getFirst();

                assertEquals(
                    "amazon-deals-http",
                    collection.integration()
                );

                assertEquals(
                    3L,
                    collection.observations()
                );

                assertEquals(
                    1L,
                    collection.successes()
                );

                assertEquals(
                    2L,
                    collection.failures()
                );

                assertEquals(
                    1L,
                    collection.externalFailures()
                );

                assertEquals(
                    1L,
                    collection.internalFailures()
                );

                assertEquals(
                    0,
                    new BigDecimal(
                        "180"
                    ).compareTo(
                        collection.averageDurationMs()
                    )
                );

                assertEquals(
                    240L,
                    collection.maximumDurationMs()
                );

                ProcessingRunIntegrationMetrics enrichment =
                    integrations.get(
                        1
                    );

                assertEquals(
                    "amazon-product-page",
                    enrichment.integration()
                );

                assertEquals(
                    3L,
                    enrichment.observations()
                );

                assertEquals(
                    2L,
                    enrichment.successes()
                );

                assertEquals(
                    1L,
                    enrichment.failures()
                );

                assertEquals(
                    1L,
                    enrichment.externalFailures()
                );

                assertEquals(
                    0L,
                    enrichment.internalFailures()
                );

                assertEquals(
                    0,
                    new BigDecimal(
                        "100"
                    ).compareTo(
                        enrichment.averageDurationMs()
                    )
                );

                assertEquals(
                    150L,
                    enrichment.maximumDurationMs()
                );
            }
        );
    }

    @Test
    void shouldReturnZeroMetricsForRunWithoutPipelineActivity()
        throws Exception {

        inTransaction(
            connection -> {

                long runId =
                    insertRun(
                        connection,
                        "observability-empty-run-"
                            + UUID.randomUUID()
                    );

                JdbcProcessingRunOperationalDetailQueryAdapter adapter =
                    new JdbcProcessingRunOperationalDetailQueryAdapter(
                        connection
                    );

                ProcessingRunDetail detail =
                    adapter.findById(
                            runId
                        )
                        .orElseThrow();

                ProcessingRunPipelineMetrics pipeline =
                    detail.pipeline();

                assertEquals(
                    0L,
                    pipeline.collectedCandidates()
                );

                assertEquals(
                    0L,
                    pipeline.enrichedCandidates()
                );

                assertEquals(
                    0L,
                    pipeline.evaluations()
                );

                assertEquals(
                    0L,
                    pipeline.eligibleEvaluations()
                );

                assertEquals(
                    0L,
                    pipeline.rejectedEvaluations()
                );

                assertEquals(
                    0L,
                    pipeline.publicationsGenerated()
                );

                ProcessingRunJobMetrics jobs =
                    detail.jobs();

                assertEquals(
                    0L,
                    jobs.totalJobs()
                );

                assertEquals(
                    0L,
                    jobs.totalAttempts()
                );

                assertEquals(
                    0L,
                    jobs.retryAttempts()
                );

                assertTrue(
                    detail.integrations()
                        .isEmpty()
                );
            }
        );
    }

    @Test
    void shouldReturnEmptyWhenRunDoesNotExist()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcProcessingRunOperationalDetailQueryAdapter adapter =
                    new JdbcProcessingRunOperationalDetailQueryAdapter(
                        connection
                    );

                Optional<ProcessingRunDetail> result =
                    adapter.findById(
                        Long.MAX_VALUE
                    );

                assertTrue(
                    result.isEmpty()
                );
            }
        );
    }

    @Test
    void shouldRejectInvalidAdapterInput()
        throws Exception {

        assertThrows(
            NullPointerException.class,
            () ->
                new JdbcProcessingRunOperationalDetailQueryAdapter(
                    null
                )
        );

        inTransaction(
            connection -> {

                JdbcProcessingRunOperationalDetailQueryAdapter adapter =
                    new JdbcProcessingRunOperationalDetailQueryAdapter(
                        connection
                    );

                assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.findById(
                        0L
                    )
                );

                assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.findById(
                        -1L
                    )
                );
            }
        );
    }

    private void insertIntegrationObservation(
        Connection connection,
        long runId,
        String integration,
        String outcome,
        long durationMs,
        String failureOrigin
    ) throws Exception {

        String sql =
            """
            INSERT INTO integration_observation (
                observed_at,
                integration,
                operation,
                outcome,
                duration_ms,
                processing_run_id,
                failure_origin,
                failure_type,
                error_code
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        boolean failure =
            "FAILURE".equals(
                outcome
            );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                BASE_TIME.minusMinutes(
                    1
                )
            );

            statement.setString(
                2,
                integration
            );

            statement.setString(
                3,
                "GET"
            );

            statement.setString(
                4,
                outcome
            );

            statement.setLong(
                5,
                durationMs
            );

            statement.setLong(
                6,
                runId
            );

            statement.setString(
                7,
                failure
                    ? failureOrigin
                    : null
            );

            statement.setString(
                8,
                failure
                    ? "TRANSIENT"
                    : null
            );

            statement.setString(
                9,
                failure
                    ? "RUN_DETAIL_INTEGRATION_FAILURE"
                    : null
            );

            statement.executeUpdate();
        }
    }

    private void insertForeignRunContext(
        Connection connection
    ) throws Exception {

        long foreignRunId =
            insertRun(
                connection,
                "observability-detail-foreign-"
                    + UUID.randomUUID()
            );

        long productId =
            insertProduct(
                connection,
                "B0RUN16004",
                "Produto de outra run"
            );

        long snapshotId =
            insertSnapshot(
                connection,
                productId,
                BASE_TIME.minusHours(
                    1
                )
            );

        long candidateId =
            insertCandidate(
                connection,
                foreignRunId,
                "B0RUN16004",
                snapshotId,
                BASE_TIME.minusHours(
                    1
                )
            );

        long evaluationId =
            insertEvaluation(
                connection,
                snapshotId,
                true,
                null,
                new BigDecimal(
                    "90.0000"
                ),
                BASE_TIME.minusMinutes(
                    40
                )
            );

        insertPublication(
            connection,
            evaluationId,
            "FOREIGN_TEMPLATE"
        );

        insertJob(
            connection,
            "COLLECT_DEALS",
            "SUCCEEDED",
            foreignRunId,
            null,
            null,
            1,
            "foreign-collect-"
                + foreignRunId
        );

        insertJob(
            connection,
            "ENRICH_DEAL",
            "SUCCEEDED",
            null,
            candidateId,
            null,
            1,
            "foreign-enrich-"
                + candidateId
        );

        insertJob(
            connection,
            "EVALUATE_DEAL",
            "SUCCEEDED",
            null,
            null,
            snapshotId,
            1,
            "foreign-evaluate-"
                + snapshotId
        );
    }

    private void inTransaction(
        TransactionTest transactionTest
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

                transactionTest.execute(
                    connection
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private long insertRun(
        Connection connection,
        String runKey
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at,
                started_at,
                completed_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                runKey
            );

            statement.setString(
                2,
                "https://www.amazon.com.br/deals"
            );

            statement.setString(
                3,
                ProcessingRunStatus.COMPLETED.name()
            );

            statement.setObject(
                4,
                BASE_TIME.minusMinutes(
                    15
                )
            );

            statement.setObject(
                5,
                BASE_TIME.minusMinutes(
                    14
                )
            );

            statement.setObject(
                6,
                BASE_TIME
            );

            statement.setObject(
                7,
                BASE_TIME.minusMinutes(
                    15
                )
            );

            statement.setObject(
                8,
                BASE_TIME
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "ProcessingRun insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertProduct(
        Connection connection,
        String asin,
        String title
    ) throws Exception {

        String sql =
            """
            INSERT INTO product (
                asin,
                title,
                image_url,
                product_url
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
                asin
            );

            statement.setString(
                2,
                title
            );

            statement.setString(
                3,
                "https://example.invalid/"
                    + asin
                    + ".jpg"
            );

            statement.setString(
                4,
                "https://www.amazon.com.br/dp/"
                    + asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Product insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertSnapshot(
        Connection connection,
        long productId,
        OffsetDateTime collectedAt
    ) throws Exception {

        String sql =
            """
            INSERT INTO offer_snapshot (
                product_id,
                collected_at,
                current_price,
                previous_price,
                sold_percentage,
                rating,
                review_count,
                seller_name,
                delivery_provider,
                source
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                productId
            );

            statement.setObject(
                2,
                collectedAt
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
                    "40.00"
                )
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "4.70"
                )
            );

            statement.setLong(
                7,
                1200L
            );

            statement.setString(
                8,
                "Amazon.com.br"
            );

            statement.setString(
                9,
                "Amazon"
            );

            statement.setString(
                10,
                SOURCE
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "OfferSnapshot insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertCandidate(
        Connection connection,
        long processingRunId,
        String asin,
        Long offerSnapshotId,
        OffsetDateTime collectedAt
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_candidate (
                processing_run_id,
                asin,
                product_url,
                title,
                image_url,
                current_price,
                collected_at,
                source,
                offer_snapshot_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                "https://www.amazon.com.br/dp/"
                    + asin
            );

            statement.setString(
                4,
                "Produto "
                    + asin
            );

            statement.setString(
                5,
                "https://example.invalid/"
                    + asin
                    + ".jpg"
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "99.90"
                )
            );

            statement.setObject(
                7,
                collectedAt
            );

            statement.setString(
                8,
                SOURCE
            );

            statement.setObject(
                9,
                offerSnapshotId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "DealCandidate insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertEvaluation(
        Connection connection,
        long snapshotId,
        boolean eligible,
        String rejectionReason,
        BigDecimal score,
        OffsetDateTime evaluatedAt
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation (
                offer_snapshot_id,
                eligible,
                rejection_reason,
                eligibility_policy_version,
                filter_profile_version,
                score,
                score_version,
                momentum,
                momentum_version,
                evaluated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setBoolean(
                2,
                eligible
            );

            statement.setString(
                3,
                rejectionReason
            );

            statement.setString(
                4,
                "TEST_RUN_DETAIL_ELIGIBILITY"
            );

            statement.setString(
                5,
                "TEST_RUN_DETAIL_FILTER"
            );

            statement.setBigDecimal(
                6,
                score
            );

            statement.setString(
                7,
                eligible
                    ? "TEST_RUN_DETAIL_SCORE"
                    : null
            );

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
                evaluatedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "DealEvaluation insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertPublication(
        Connection connection,
        long evaluationId,
        String templateVersion
    ) throws Exception {

        String sql =
            """
            INSERT INTO publication (
                deal_evaluation_id,
                template_version,
                commercial_presentation_version,
                affiliate_link_version,
                generated_text,
                affiliate_url,
                status,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            statement.setString(
                2,
                templateVersion
            );

            statement.setString(
                3,
                "RUN_DETAIL_COMMERCIAL_V1"
            );

            statement.setString(
                4,
                "RUN_DETAIL_AFFILIATE_V1"
            );

            statement.setString(
                5,
                "Texto gerado para observabilidade"
            );

            statement.setString(
                6,
                "https://example.invalid/affiliate/"
                    + evaluationId
                    + "/"
                    + templateVersion
            );

            statement.setString(
                7,
                "CREATED"
            );

            statement.setObject(
                8,
                BASE_TIME
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertJob(
        Connection connection,
        String jobType,
        String status,
        Long processingRunId,
        Long dealCandidateId,
        Long offerSnapshotId,
        int attemptCount,
        String idempotencyKey
    ) throws Exception {

        String lastFailureType =
            switch (status) {

                case "RETRY_WAIT" ->
                    "TRANSIENT";

                case "DEAD" ->
                    "PERMANENT";

                default ->
                    null;
            };

        String errorCode =
            lastFailureType == null
                ? null
                : "TEST_FAILURE";

        String errorMessage =
            lastFailureType == null
                ? null
                : "Controlled failure for run detail test";

        OffsetDateTime finishedAt =
            "SUCCEEDED".equals(
                status
            )
                || "DEAD".equals(
                status
            )
                ? BASE_TIME
                : null;

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
                created_at,
                updated_at,
                finished_at
            )
            VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                jobType
            );

            statement.setString(
                2,
                status
            );

            statement.setObject(
                3,
                processingRunId
            );

            statement.setObject(
                4,
                dealCandidateId
            );

            statement.setObject(
                5,
                offerSnapshotId
            );

            statement.setString(
                6,
                idempotencyKey
            );

            statement.setInt(
                7,
                attemptCount
            );

            statement.setInt(
                8,
                5
            );

            statement.setObject(
                9,
                BASE_TIME
            );

            statement.setString(
                10,
                lastFailureType
            );

            statement.setString(
                11,
                errorCode
            );

            statement.setString(
                12,
                errorMessage
            );

            statement.setObject(
                13,
                BASE_TIME.minusMinutes(
                    3
                )
            );

            statement.setObject(
                14,
                BASE_TIME
            );

            statement.setObject(
                15,
                finishedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "ProcessingJob insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    @FunctionalInterface
    private interface TransactionTest {

        void execute(
            Connection connection
        ) throws Exception;
    }
}
