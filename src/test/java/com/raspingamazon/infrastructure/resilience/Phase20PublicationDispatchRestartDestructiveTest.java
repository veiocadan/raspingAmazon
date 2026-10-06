package com.raspingamazon.infrastructure.resilience;

import com.raspingamazon.application.publication.PublicationDispatchReconciliationResult;
import com.raspingamazon.application.publication.PublicationDispatchReconciliationService;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobQueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingRunPublicationReadinessQueryAdapter;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@PostgresIntegrationTest
class Phase20PublicationDispatchRestartDestructiveTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2000-01-01T01:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW.toInstant(),
            ZoneOffset.UTC
        );

    @Test
    void restartReconciliationShouldDrainMoreRunsThanSinglePageWithoutDuplicates()
        throws Exception {

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

                long firstRun =
                    insertCompletedRun(
                        connection,
                        "first"
                    );

                long secondRun =
                    insertCompletedRun(
                        connection,
                        "second"
                    );

                long thirdRun =
                    insertCompletedRun(
                        connection,
                        "third"
                    );

                long[] testRunIds =
                    {
                        firstRun,
                        secondRun,
                        thirdRun
                    };

                PublicationDispatchReconciliationResult result =
                    new PublicationDispatchReconciliationService(
                        limit ->
                            findTestCandidates(
                                connection,
                                testRunIds,
                                limit
                            ),
                        new JdbcProcessingRunPublicationReadinessQueryAdapter(
                            connection
                        ),
                        new JdbcProcessingJobQueueAdapter(
                            connection
                        ),
                        CLOCK,
                        3
                    ).reconcileUntilQuiescent(
                        1
                    );

                assertEquals(
                    3,
                    result.inspectedRunCount()
                );

                assertEquals(
                    3,
                    result.readyRunCount()
                );

                assertEquals(
                    3,
                    result.enqueuedJobCount()
                );

                assertEquals(
                    1L,
                    countDispatchJobs(
                        connection,
                        firstRun
                    )
                );

                assertEquals(
                    1L,
                    countDispatchJobs(
                        connection,
                        secondRun
                    )
                );

                assertEquals(
                    1L,
                    countDispatchJobs(
                        connection,
                        thirdRun
                    )
                );

                /*
                 * Uma segunda rodada já encontra quiescência.
                 * Nenhum job lógico duplicado é criado.
                 */
                PublicationDispatchReconciliationResult replay =
                    new PublicationDispatchReconciliationService(
                        limit ->
                            findTestCandidates(
                                connection,
                                testRunIds,
                                limit
                            ),
                        new JdbcProcessingRunPublicationReadinessQueryAdapter(
                            connection
                        ),
                        new JdbcProcessingJobQueueAdapter(
                            connection
                        ),
                        CLOCK,
                        3
                    ).reconcileUntilQuiescent(
                        1
                    );

                assertEquals(
                    0,
                    replay.inspectedRunCount()
                );

                assertEquals(
                    3L,
                    countAllDispatchJobs(
                        connection,
                        firstRun,
                        secondRun,
                        thirdRun
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private List<Long> findTestCandidates(
        Connection connection,
        long[] processingRunIds,
        int limit
    ) {

        String sql =
            """
            SELECT run.id
            FROM processing_run AS run
            WHERE run.id IN (?, ?, ?)
              AND NOT EXISTS (
                  SELECT 1
                  FROM processing_job AS job
                  WHERE job.job_type = 'PUBLICATION_DISPATCH'
                    AND job.processing_run_id = run.id
              )
            ORDER BY run.id
            LIMIT ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                processingRunIds[0]
            );

            statement.setLong(
                2,
                processingRunIds[1]
            );

            statement.setLong(
                3,
                processingRunIds[2]
            );

            statement.setInt(
                4,
                limit
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                List<Long> result =
                    new ArrayList<>();

                while (resultSet.next()) {

                    result.add(
                        resultSet.getLong(
                            "id"
                        )
                    );
                }

                return List.copyOf(
                    result
                );
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not read test reconciliation candidates",
                exception
            );
        }
    }

    private long insertCompletedRun(
        Connection connection,
        String suffix
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
            VALUES (
                ?,
                'https://example.invalid/phase20-restart',
                'COMPLETED',
                ?,
                ?,
                ?,
                ?,
                ?
            )
            RETURNING id
            """;

        OffsetDateTime completedAt =
            NOW.minusMinutes(
                10
            );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                "phase20-dispatch-"
                    + suffix
                    + ":"
                    + UUID.randomUUID()
            );

            statement.setObject(
                2,
                completedAt.minusMinutes(
                    2
                )
            );

            statement.setObject(
                3,
                completedAt.minusMinutes(
                    1
                )
            );

            statement.setObject(
                4,
                completedAt
            );

            statement.setObject(
                5,
                completedAt.minusMinutes(
                    2
                )
            );

            statement.setObject(
                6,
                completedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long countDispatchJobs(
        Connection connection,
        long processingRunId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
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

                resultSet.next();

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private long countAllDispatchJobs(
        Connection connection,
        long firstRun,
        long secondRun,
        long thirdRun
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_job
            WHERE job_type = 'PUBLICATION_DISPATCH'
              AND processing_run_id IN (?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                firstRun
            );

            statement.setLong(
                2,
                secondRun
            );

            statement.setLong(
                3,
                thirdRun
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getLong(
                    1
                );
            }
        }
    }
}
