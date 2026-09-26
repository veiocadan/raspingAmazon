package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.orchestration.job.ProcessingJobPage;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobSearchCriteria;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcProcessingJobOperationalQueryAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2099-04-10T12:00:00-03:00"
        );

    @Test
    void shouldPageJobsInDeterministicOrder()
        throws Exception {

        inTransaction(
            connection -> {

                long runId =
                    insertRun(
                        connection,
                        "job-page-run"
                    );

                long firstId =
                    insertJob(
                        connection,
                        ProcessingJobType.COLLECT_DEALS,
                        ProcessingJobStatus.PENDING,
                        runId,
                        null,
                        null,
                        "job-page-1",
                        0,
                        5,
                        BASE_TIME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BASE_TIME,
                        null
                    );

                long secondId =
                    insertJob(
                        connection,
                        ProcessingJobType.COLLECT_DEALS,
                        ProcessingJobStatus.PENDING,
                        runId,
                        null,
                        null,
                        "job-page-2",
                        0,
                        5,
                        BASE_TIME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BASE_TIME,
                        null
                    );

                long olderId =
                    insertJob(
                        connection,
                        ProcessingJobType.COLLECT_DEALS,
                        ProcessingJobStatus.PENDING,
                        runId,
                        null,
                        null,
                        "job-page-3",
                        0,
                        5,
                        BASE_TIME.minusHours(
                            1
                        ),
                        null,
                        null,
                        null,
                        null,
                        null,
                        BASE_TIME.minusHours(
                            1
                        ),
                        null
                    );

                JdbcProcessingJobOperationalQueryAdapter adapter =
                    new JdbcProcessingJobOperationalQueryAdapter(
                        connection
                    );

                ProcessingJobPage firstPage =
                    adapter.search(
                        criteria(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            BASE_TIME.minusHours(
                                2
                            ),
                            BASE_TIME.plusHours(
                                1
                            ),
                            null,
                            2
                        )
                    );

                assertEquals(
                    2,
                    firstPage.items()
                        .size()
                );

                assertEquals(
                    secondId,
                    firstPage.items()
                        .get(0)
                        .jobId()
                );

                assertEquals(
                    firstId,
                    firstPage.items()
                        .get(1)
                        .jobId()
                );

                assertTrue(
                    firstPage.hasNextPage()
                );

                ProcessingJobPage secondPage =
                    adapter.search(
                        criteria(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            BASE_TIME.minusHours(
                                2
                            ),
                            BASE_TIME.plusHours(
                                1
                            ),
                            firstPage.nextCursor(),
                            2
                        )
                    );

                assertEquals(
                    1,
                    secondPage.items()
                        .size()
                );

                assertEquals(
                    olderId,
                    secondPage.items()
                        .getFirst()
                        .jobId()
                );

                assertFalse(
                    secondPage.hasNextPage()
                );
            }
        );
    }

    @Test
    void shouldFilterByTypeStatusAndFailureType()
        throws Exception {

        inTransaction(
            connection -> {

                long runId =
                    insertRun(
                        connection,
                        "job-filter-run"
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "B0JOB14001"
                    );

                long expectedId =
                    insertJob(
                        connection,
                        ProcessingJobType.ENRICH_DEAL,
                        ProcessingJobStatus.RETRY_WAIT,
                        null,
                        candidateId,
                        null,
                        "job-filter-expected",
                        1,
                        5,
                        BASE_TIME.plusMinutes(
                            10
                        ),
                        null,
                        null,
                        ProcessingFailureType.TRANSIENT,
                        "HTTP_503",
                        "Temporary upstream failure",
                        BASE_TIME,
                        null
                    );

                insertJob(
                    connection,
                    ProcessingJobType.ENRICH_DEAL,
                    ProcessingJobStatus.PENDING,
                    null,
                    candidateId,
                    null,
                    "job-filter-pending",
                    0,
                    5,
                    BASE_TIME,
                    null,
                    null,
                    null,
                    null,
                    null,
                    BASE_TIME.plusMinutes(
                        1
                    ),
                    null
                );

                insertJob(
                    connection,
                    ProcessingJobType.COLLECT_DEALS,
                    ProcessingJobStatus.RETRY_WAIT,
                    runId,
                    null,
                    null,
                    "job-filter-collect",
                    1,
                    5,
                    BASE_TIME,
                    null,
                    null,
                    ProcessingFailureType.TRANSIENT,
                    "HTTP_503",
                    "Temporary upstream failure",
                    BASE_TIME.plusMinutes(
                        2
                    ),
                    null
                );

                JdbcProcessingJobOperationalQueryAdapter adapter =
                    new JdbcProcessingJobOperationalQueryAdapter(
                        connection
                    );

                ProcessingJobPage page =
                    adapter.search(
                        criteria(
                            ProcessingJobType.ENRICH_DEAL,
                            ProcessingJobStatus.RETRY_WAIT,
                            ProcessingFailureType.TRANSIENT,
                            null,
                            null,
                            null,
                            BASE_TIME.minusMinutes(
                                1
                            ),
                            BASE_TIME.plusHours(
                                1
                            ),
                            null,
                            50
                        )
                    );

                assertEquals(
                    1,
                    page.items()
                        .size()
                );

                assertEquals(
                    expectedId,
                    page.items()
                        .getFirst()
                        .jobId()
                );

                assertEquals(
                    ProcessingFailureType.TRANSIENT,
                    page.items()
                        .getFirst()
                        .lastFailureType()
                );

                assertEquals(
                    "HTTP_503",
                    page.items()
                        .getFirst()
                        .lastErrorCode()
                );
            }
        );
    }

    @Test
    void shouldExposeRunningLease()
        throws Exception {

        inTransaction(
            connection -> {

                long runId =
                    insertRun(
                        connection,
                        "job-running-run"
                    );

                long expectedId =
                    insertJob(
                        connection,
                        ProcessingJobType.COLLECT_DEALS,
                        ProcessingJobStatus.RUNNING,
                        runId,
                        null,
                        null,
                        "job-running",
                        2,
                        5,
                        BASE_TIME,
                        BASE_TIME.plusMinutes(
                            1
                        ),
                        "worker-operational-1",
                        null,
                        null,
                        null,
                        BASE_TIME,
                        null
                    );

                JdbcProcessingJobOperationalQueryAdapter adapter =
                    new JdbcProcessingJobOperationalQueryAdapter(
                        connection
                    );

                ProcessingJobPage page =
                    adapter.search(
                        criteria(
                            null,
                            ProcessingJobStatus.RUNNING,
                            null,
                            runId,
                            null,
                            null,
                            BASE_TIME.minusMinutes(
                                1
                            ),
                            BASE_TIME.plusMinutes(
                                1
                            ),
                            null,
                            50
                        )
                    );

                assertEquals(
                    1,
                    page.items()
                        .size()
                );

                assertEquals(
                    expectedId,
                    page.items()
                        .getFirst()
                        .jobId()
                );

                assertEquals(
                    "worker-operational-1",
                    page.items()
                        .getFirst()
                        .lockedBy()
                );

                assertEquals(
                    3,
                    page.items()
                        .getFirst()
                        .remainingAttempts()
                );
            }
        );
    }

    @Test
    void shouldExposeDeadPermanentFailureWithoutInferringRetry()
        throws Exception {

        inTransaction(
            connection -> {

                long runId =
                    insertRun(
                        connection,
                        "job-dead-run"
                    );

                long expectedId =
                    insertJob(
                        connection,
                        ProcessingJobType.COLLECT_DEALS,
                        ProcessingJobStatus.DEAD,
                        runId,
                        null,
                        null,
                        "job-dead",
                        1,
                        5,
                        BASE_TIME,
                        null,
                        null,
                        ProcessingFailureType.PERMANENT,
                        "INVALID_CONTRACT",
                        "Permanent processing failure",
                        BASE_TIME,
                        BASE_TIME.plusMinutes(
                            2
                        )
                    );

                JdbcProcessingJobOperationalQueryAdapter adapter =
                    new JdbcProcessingJobOperationalQueryAdapter(
                        connection
                    );

                ProcessingJobPage page =
                    adapter.search(
                        criteria(
                            null,
                            ProcessingJobStatus.DEAD,
                            null,
                            null,
                            null,
                            null,
                            BASE_TIME.minusMinutes(
                                1
                            ),
                            BASE_TIME.plusMinutes(
                                1
                            ),
                            null,
                            50
                        )
                    );

                assertEquals(
                    expectedId,
                    page.items()
                        .getFirst()
                        .jobId()
                );

                assertTrue(
                    page.items()
                        .getFirst()
                        .terminal()
                );

                assertEquals(
                    4,
                    page.items()
                        .getFirst()
                        .remainingAttempts()
                );

                assertEquals(
                    ProcessingFailureType.PERMANENT,
                    page.items()
                        .getFirst()
                        .lastFailureType()
                );
            }
        );
    }

    @Test
    void shouldFilterByProcessingRunSubject()
        throws Exception {

        inTransaction(
            connection -> {

                long expectedRunId =
                    insertRun(
                        connection,
                        "job-subject-run-1"
                    );

                long otherRunId =
                    insertRun(
                        connection,
                        "job-subject-run-2"
                    );

                long expectedJobId =
                    insertJob(
                        connection,
                        ProcessingJobType.COLLECT_DEALS,
                        ProcessingJobStatus.PENDING,
                        expectedRunId,
                        null,
                        null,
                        "job-subject-1",
                        0,
                        5,
                        BASE_TIME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        BASE_TIME,
                        null
                    );

                insertJob(
                    connection,
                    ProcessingJobType.COLLECT_DEALS,
                    ProcessingJobStatus.PENDING,
                    otherRunId,
                    null,
                    null,
                    "job-subject-2",
                    0,
                    5,
                    BASE_TIME,
                    null,
                    null,
                    null,
                    null,
                    null,
                    BASE_TIME.plusMinutes(
                        1
                    ),
                    null
                );

                JdbcProcessingJobOperationalQueryAdapter adapter =
                    new JdbcProcessingJobOperationalQueryAdapter(
                        connection
                    );

                ProcessingJobPage page =
                    adapter.search(
                        criteria(
                            null,
                            null,
                            null,
                            expectedRunId,
                            null,
                            null,
                            BASE_TIME.minusMinutes(
                                1
                            ),
                            BASE_TIME.plusMinutes(
                                2
                            ),
                            null,
                            50
                        )
                    );

                assertEquals(
                    1,
                    page.items()
                        .size()
                );

                assertEquals(
                    expectedJobId,
                    page.items()
                        .getFirst()
                        .jobId()
                );
            }
        );
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcProcessingJobOperationalQueryAdapter adapter =
                    new JdbcProcessingJobOperationalQueryAdapter(
                        connection
                    );

                ProcessingJobPage page =
                    adapter.search(
                        criteria(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            OffsetDateTime.parse(
                                "2199-01-01T00:00:00Z"
                            ),
                            OffsetDateTime.parse(
                                "2199-01-02T00:00:00Z"
                            ),
                            null,
                            50
                        )
                    );

                assertTrue(
                    page.items()
                        .isEmpty()
                );

                assertNull(
                    page.nextCursor()
                );
            }
        );
    }

    @Test
    void shouldRejectInvalidAdapterInput()
        throws Exception {

        assertThrows(
            NullPointerException.class,
            () -> new JdbcProcessingJobOperationalQueryAdapter(
                null
            )
        );

        inTransaction(
            connection -> {

                JdbcProcessingJobOperationalQueryAdapter adapter =
                    new JdbcProcessingJobOperationalQueryAdapter(
                        connection
                    );

                assertThrows(
                    NullPointerException.class,
                    () -> adapter.search(
                        null
                    )
                );
            }
        );
    }

    private ProcessingJobSearchCriteria criteria(
        ProcessingJobType type,
        ProcessingJobStatus status,
        ProcessingFailureType failureType,
        Long processingRunId,
        Long dealCandidateId,
        Long offerSnapshotId,
        OffsetDateTime createdFrom,
        OffsetDateTime createdUntil,
        com.raspingamazon.application.operation.orchestration.job.ProcessingJobCursor after,
        int limit
    ) {

        return new ProcessingJobSearchCriteria(
            type,
            status,
            failureType,
            processingRunId,
            dealCandidateId,
            offerSnapshotId,
            createdFrom,
            createdUntil,
            after,
            limit
        );
    }

    private void inTransaction(
        TransactionTest transactionTest
    ) throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

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
                requested_at
            )
            VALUES (?, ?, 'PENDING', ?)
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

            statement.setObject(
                3,
                BASE_TIME.minusHours(
                    1
                )
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

    private long insertCandidate(
        Connection connection,
        long runId,
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
                runId
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
                "Produto operacional "
                    + asin
            );

            statement.setBigDecimal(
                5,
                new BigDecimal(
                    "99.90"
                )
            );

            statement.setObject(
                6,
                BASE_TIME
            );

            statement.setString(
                7,
                "TEST_OPERATIONAL_JOB"
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

    private long insertJob(
        Connection connection,
        ProcessingJobType type,
        ProcessingJobStatus status,
        Long processingRunId,
        Long dealCandidateId,
        Long offerSnapshotId,
        String idempotencyKey,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime availableAt,
        OffsetDateTime lockedAt,
        String lockedBy,
        ProcessingFailureType lastFailureType,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
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
                locked_at,
                locked_by,
                last_failure_type,
                last_error_code,
                last_error_message,
                created_at,
                updated_at,
                finished_at
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                type.name()
            );

            statement.setString(
                2,
                status.name()
            );

            setNullableLong(
                statement,
                3,
                processingRunId
            );

            setNullableLong(
                statement,
                4,
                dealCandidateId
            );

            setNullableLong(
                statement,
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
                maxAttempts
            );

            statement.setObject(
                9,
                availableAt
            );

            statement.setObject(
                10,
                lockedAt
            );

            statement.setString(
                11,
                lockedBy
            );

            statement.setString(
                12,
                lastFailureType == null
                    ? null
                    : lastFailureType.name()
            );

            statement.setString(
                13,
                lastErrorCode
            );

            statement.setString(
                14,
                lastErrorMessage
            );

            statement.setObject(
                15,
                createdAt
            );

            statement.setObject(
                16,
                finishedAt == null
                    ? createdAt
                    : finishedAt
            );

            statement.setObject(
                17,
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
    private interface TransactionTest {

        void execute(
            Connection connection
        ) throws Exception;
    }
}
