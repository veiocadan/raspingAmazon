package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationDispatchProcessingJobTest {

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-01T19:00:00-03:00"
        );

    @Test
    void shouldPersistPublicationDispatchJobForProcessingRun()
        throws Exception {

        withConnection(
            connection -> {

                long processingRunId =
                    insertProcessingRun(
                        connection
                    );

                JdbcProcessingJobQueueAdapter queue =
                    new JdbcProcessingJobQueueAdapter(
                        connection
                    );

                ProcessingJob persisted =
                    queue.enqueue(
                        ProcessingJobSubmission.publicationDispatch(
                            processingRunId,
                            idempotencyKey(
                                processingRunId
                            ),
                            7,
                            NOW
                        )
                    );

                assertNotNull(
                    persisted.id()
                );

                assertEquals(
                    ProcessingJobType.PUBLICATION_DISPATCH,
                    persisted.type()
                );

                assertEquals(
                    ProcessingJobStatus.PENDING,
                    persisted.status()
                );

                assertEquals(
                    processingRunId,
                    persisted.processingRunId()
                );

                assertEquals(
                    null,
                    persisted.dealCandidateId()
                );

                assertEquals(
                    null,
                    persisted.offerSnapshotId()
                );

                assertEquals(
                    7,
                    persisted.maxAttempts()
                );
            }
        );
    }

    @Test
    void repeatedEnqueueShouldReuseSamePublicationDispatchJob()
        throws Exception {

        withConnection(
            connection -> {

                long processingRunId =
                    insertProcessingRun(
                        connection
                    );

                JdbcProcessingJobQueueAdapter queue =
                    new JdbcProcessingJobQueueAdapter(
                        connection
                    );

                ProcessingJobSubmission submission =
                    ProcessingJobSubmission.publicationDispatch(
                        processingRunId,
                        idempotencyKey(
                            processingRunId
                        ),
                        7,
                        NOW
                    );

                ProcessingJob first =
                    queue.enqueue(
                        submission
                    );

                ProcessingJob second =
                    queue.enqueue(
                        submission
                    );

                assertNotNull(
                    first.id()
                );

                assertEquals(
                    first.id(),
                    second.id()
                );

                assertEquals(
                    1L,
                    countPublicationDispatchJobs(
                        connection,
                        processingRunId
                    )
                );
            }
        );
    }

    private long insertProcessingRun(
        Connection connection
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
            VALUES (?, ?, 'COMPLETED', ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                "publication-dispatch-test-" + unique
            );

            statement.setString(
                2,
                "https://example.test/publication-dispatch/"
                    + unique
            );

            statement.setObject(
                3,
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

    private long countPublicationDispatchJobs(
        Connection connection,
        long processingRunId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
            FROM processing_job
            WHERE job_type = 'PUBLICATION_DISPATCH'
              AND processing_run_id = ?
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

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "total"
                );
            }
        }
    }

    private String idempotencyKey(
        long processingRunId
    ) {

        return "publication-dispatch:"
            + processingRunId;
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
