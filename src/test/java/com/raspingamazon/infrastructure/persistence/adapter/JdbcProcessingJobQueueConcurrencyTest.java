package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcProcessingJobQueueConcurrencyTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-23T00:20:00Z"
        );

    @Test
    void shouldAllowTwoWorkersToClaimDifferentJobs()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData first =
            null;

        TestData second =
            null;

        try (
            Connection firstConnection =
                DatabaseConnection.open(
                    config
                );

            Connection secondConnection =
                DatabaseConnection.open(
                    config
                )
        ) {

            long firstRunId =
                insertProcessingRun(
                    firstConnection,
                    "concurrency-1"
                );

            long firstJobId =
                insertPendingJob(
                    firstConnection,
                    firstRunId,
                    "concurrency-job-1"
                );

            first =
                new TestData(
                    firstRunId,
                    firstJobId
                );

            long secondRunId =
                insertProcessingRun(
                    firstConnection,
                    "concurrency-2"
                );

            long secondJobId =
                insertPendingJob(
                    firstConnection,
                    secondRunId,
                    "concurrency-job-2"
                );

            second =
                new TestData(
                    secondRunId,
                    secondJobId
                );

            JdbcProcessingJobQueueAdapter firstQueue =
                new JdbcProcessingJobQueueAdapter(
                    firstConnection
                );

            JdbcProcessingJobQueueAdapter secondQueue =
                new JdbcProcessingJobQueueAdapter(
                    secondConnection
                );

            Optional<ProcessingJob> firstClaim =
                firstQueue.claimNext(
                    "worker-a",
                    NOW
                );

            Optional<ProcessingJob> secondClaim =
                secondQueue.claimNext(
                    "worker-b",
                    NOW
                );

            assertTrue(
                firstClaim.isPresent()
            );

            assertTrue(
                secondClaim.isPresent()
            );

            assertNotEquals(
                firstClaim.get().id(),
                secondClaim.get().id()
            );

        } finally {

            deleteTestData(
                config,
                second
            );

            deleteTestData(
                config,
                first
            );
        }
    }

    private long insertProcessingRun(
        Connection connection,
        String suffix
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
                "integration-"
                    + suffix
                    + "-"
                    + System.nanoTime()
            );

            statement.setString(
                2,
                "https://www.amazon.com.br/deals"
            );

            statement.setObject(
                3,
                NOW.minusHours(
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

    private long insertPendingJob(
        Connection connection,
        long processingRunId,
        String keyPrefix
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
                available_at
            )
            VALUES (
                'COLLECT_DEALS',
                'PENDING',
                ?,
                NULL,
                NULL,
                ?,
                0,
                5,
                ?
            )
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
                keyPrefix
                    + "-"
                    + System.nanoTime()
            );

            statement.setObject(
                3,
                NOW.minusMinutes(
                    1
                )
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

    private void deleteTestData(
        ApplicationConfig config,
        TestData data
    ) {

        if (data == null) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_job
                         WHERE id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.jobId()
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_run
                         WHERE id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.processingRunId()
                );

                statement.executeUpdate();
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean concurrency test data",
                exception
            );
        }
    }

    private record TestData(
        long processingRunId,
        long jobId
    ) {
    }
}
