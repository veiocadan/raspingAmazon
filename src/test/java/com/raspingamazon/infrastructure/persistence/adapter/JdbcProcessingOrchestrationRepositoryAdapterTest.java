package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.orchestration.ProcessingRun;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcProcessingOrchestrationRepositoryAdapterTest {

    private static final OffsetDateTime REQUESTED_AT =
        OffsetDateTime.parse(
            "2026-09-22T19:00:00-03:00"
        );

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-22T19:01:00-03:00"
        );

    @Test
    void shouldPersistProcessingRunIdempotently()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingRunRepositoryAdapter adapter =
                new JdbcProcessingRunRepositoryAdapter(
                    connection
                );

            ProcessingRun transientRun =
                createPendingRun();

            ProcessingRun first =
                adapter.save(
                    transientRun
                );

            ProcessingRun second =
                adapter.save(
                    transientRun
                );

            runId =
                first.id();

            assertEquals(
                first.id(),
                second.id()
            );

            assertEquals(
                ProcessingRunStatus.PENDING,
                first.status()
            );

            assertTrue(
                first.persisted()
            );

            assertSameInstant(
                REQUESTED_AT,
                first.requestedAt()
            );

        } finally {

            deleteRun(
                config,
                runId
            );
        }
    }

    @Test
    void shouldMoveProcessingRunThroughSuccessfulLifecycle()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingRunRepositoryAdapter adapter =
                new JdbcProcessingRunRepositoryAdapter(
                    connection
                );

            ProcessingRun persisted =
                adapter.save(
                    createPendingRun()
                );

            runId =
                persisted.id();

            OffsetDateTime startedAt =
                REQUESTED_AT.plusMinutes(
                    1
                );

            ProcessingRun running =
                adapter.markRunning(
                    runId,
                    startedAt
                );

            assertEquals(
                ProcessingRunStatus.RUNNING,
                running.status()
            );

            assertSameInstant(
                startedAt,
                running.startedAt()
            );

            assertNull(
                running.completedAt()
            );

            OffsetDateTime completedAt =
                startedAt.plusMinutes(
                    2
                );

            ProcessingRun completed =
                adapter.markCompleted(
                    runId,
                    completedAt
                );

            assertEquals(
                ProcessingRunStatus.COMPLETED,
                completed.status()
            );

            assertSameInstant(
                completedAt,
                completed.completedAt()
            );

            ProcessingRun loaded =
                adapter.findById(
                        runId
                    )
                    .orElseThrow();

            assertEquals(
                ProcessingRunStatus.COMPLETED,
                loaded.status()
            );

        } finally {

            deleteRun(
                config,
                runId
            );
        }
    }

    @Test
    void shouldAllowFailedProcessingRunToStartAgain()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingRunRepositoryAdapter adapter =
                new JdbcProcessingRunRepositoryAdapter(
                    connection
                );

            ProcessingRun persisted =
                adapter.save(
                    createPendingRun()
                );

            runId =
                persisted.id();

            OffsetDateTime firstStartedAt =
                REQUESTED_AT.plusMinutes(
                    1
                );

            adapter.markRunning(
                runId,
                firstStartedAt
            );

            OffsetDateTime failedAt =
                firstStartedAt.plusMinutes(
                    1
                );

            ProcessingRun failed =
                adapter.markFailed(
                    runId,
                    "COLLECTION_TIMEOUT",
                    "Controlled timeout",
                    failedAt
                );

            assertEquals(
                ProcessingRunStatus.FAILED,
                failed.status()
            );

            assertEquals(
                "COLLECTION_TIMEOUT",
                failed.lastErrorCode()
            );

            assertSameInstant(
                failedAt,
                failed.completedAt()
            );

            OffsetDateTime retryStartedAt =
                failedAt.plusMinutes(
                    5
                );

            ProcessingRun retried =
                adapter.markRunning(
                    runId,
                    retryStartedAt
                );

            assertEquals(
                ProcessingRunStatus.RUNNING,
                retried.status()
            );

            assertSameInstant(
                retryStartedAt,
                retried.startedAt()
            );

            assertNull(
                retried.completedAt()
            );

            assertNull(
                retried.lastErrorCode()
            );

            assertNull(
                retried.lastErrorMessage()
            );

        } finally {

            deleteRun(
                config,
                runId
            );
        }
    }

    @Test
    void shouldPersistDealCandidateIdempotently()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        long candidateId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingRunRepositoryAdapter runAdapter =
                new JdbcProcessingRunRepositoryAdapter(
                    connection
                );

            ProcessingRun run =
                runAdapter.save(
                    createPendingRun()
                );

            runId =
                run.id();

            JdbcDealCandidateRepositoryAdapter candidateAdapter =
                new JdbcDealCandidateRepositoryAdapter(
                    connection
                );

            DealCandidate transientCandidate =
                new DealCandidate(
                    null,
                    runId,
                    createParsedDeal()
                );

            DealCandidate first =
                candidateAdapter.save(
                    transientCandidate
                );

            DealCandidate second =
                candidateAdapter.save(
                    transientCandidate
                );

            candidateId =
                first.id();

            assertEquals(
                first.id(),
                second.id()
            );

            assertTrue(
                first.persisted()
            );

            assertEquals(
                runId,
                first.processingRunId()
            );

            assertEquals(
                "B087WLJH8Y",
                first.parsedDeal().asin()
            );

            assertSameInstant(
                COLLECTED_AT,
                first.parsedDeal()
                    .collectedAt()
            );

        } finally {

            deleteCandidateAndRun(
                config,
                candidateId,
                runId
            );
        }
    }

    @Test
    void shouldLoadPersistedDealCandidate()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        long candidateId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingRunRepositoryAdapter runAdapter =
                new JdbcProcessingRunRepositoryAdapter(
                    connection
                );

            ProcessingRun run =
                runAdapter.save(
                    createPendingRun()
                );

            runId =
                run.id();

            JdbcDealCandidateRepositoryAdapter candidateAdapter =
                new JdbcDealCandidateRepositoryAdapter(
                    connection
                );

            DealCandidate persisted =
                candidateAdapter.save(
                    new DealCandidate(
                        null,
                        runId,
                        createParsedDeal()
                    )
                );

            candidateId =
                persisted.id();

            DealCandidate loaded =
                candidateAdapter.findById(
                        candidateId
                    )
                    .orElseThrow();

            assertEquals(
                candidateId,
                loaded.id()
            );

            assertEquals(
                new BigDecimal(
                    "99.90"
                ),
                loaded.parsedDeal()
                    .currentPrice()
            );

            assertEquals(
                new BigDecimal(
                    "129.90"
                ),
                loaded.parsedDeal()
                    .basisPrice()
            );

            assertEquals(
                4.6,
                loaded.parsedDeal()
                    .rating()
            );

            assertEquals(
                58363L,
                loaded.parsedDeal()
                    .reviewCount()
            );

            assertSameInstant(
                COLLECTED_AT,
                loaded.parsedDeal()
                    .collectedAt()
            );

        } finally {

            deleteCandidateAndRun(
                config,
                candidateId,
                runId
            );
        }
    }

    @Test
    void shouldReturnEmptyWhenEntitiesDoNotExist()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingRunRepositoryAdapter runAdapter =
                new JdbcProcessingRunRepositoryAdapter(
                    connection
                );

            JdbcDealCandidateRepositoryAdapter candidateAdapter =
                new JdbcDealCandidateRepositoryAdapter(
                    connection
                );

            assertFalse(
                runAdapter.findById(
                    Long.MAX_VALUE
                ).isPresent()
            );

            assertFalse(
                candidateAdapter.findById(
                    Long.MAX_VALUE
                ).isPresent()
            );
        }
    }

    private ProcessingRun createPendingRun() {

        return new ProcessingRun(
            null,
            "test:"
                + UUID.randomUUID(),
            URI.create(
                "https://www.amazon.com.br/deals"
            ),
            ProcessingRunStatus.PENDING,
            REQUESTED_AT,
            null,
            null,
            null,
            null
        );
    }

    private ParsedDeal createParsedDeal() {

        return new ParsedDeal(
            "B087WLJH8Y",
            "https://www.amazon.com.br/dp/B087WLJH8Y",
            "Produto de teste",
            "https://example.com/image.jpg",
            new BigDecimal(
                "99.90"
            ),
            new BigDecimal(
                "129.90"
            ),
            new BigDecimal(
                "119.90"
            ),
            new BigDecimal(
                "42.00"
            ),
            4.6,
            58363L,
            COLLECTED_AT,
            "https://www.amazon.com.br/deals"
        );
    }

    private static void assertSameInstant(
        OffsetDateTime expected,
        OffsetDateTime actual
    ) {

        assertEquals(
            expected.toInstant(),
            actual.toInstant()
        );
    }

    private void deleteCandidateAndRun(
        ApplicationConfig config,
        long candidateId,
        long runId
    ) {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            if (candidateId > 0) {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM deal_candidate
                             WHERE id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        candidateId
                    );

                    statement.executeUpdate();
                }
            }

            deleteRunData(
                connection,
                runId
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean DealCandidate test data",
                exception
            );
        }
    }

    private void deleteRun(
        ApplicationConfig config,
        long runId
    ) {

        if (runId <= 0) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            deleteRunData(
                connection,
                runId
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean ProcessingRun test data",
                exception
            );
        }
    }

    private void deleteRunData(
        Connection connection,
        long runId
    ) throws Exception {

        if (runId <= 0) {
            return;
        }

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM processing_job
                     WHERE processing_run_id = ?
                        OR deal_candidate_id IN (
                            SELECT id
                            FROM deal_candidate
                            WHERE processing_run_id = ?
                        )
                     """
                 )) {

            statement.setLong(
                1,
                runId
            );

            statement.setLong(
                2,
                runId
            );

            statement.executeUpdate();
        }

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM deal_candidate
                     WHERE processing_run_id = ?
                     """
                 )) {

            statement.setLong(
                1,
                runId
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
                runId
            );

            statement.executeUpdate();
        }
    }
}
