package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSearchCriteria;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcProcessingRunOperationalQueryAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2099-03-10T12:00:00-03:00"
        );

    @Test
    void shouldPageRunsInDeterministicOrder()
        throws Exception {

        inTransaction(
            connection -> {

                long firstId =
                    insertRun(
                        connection,
                        "operational-run-1",
                        ProcessingRunStatus.COMPLETED,
                        BASE_TIME,
                        null,
                        null
                    );

                long secondId =
                    insertRun(
                        connection,
                        "operational-run-2",
                        ProcessingRunStatus.COMPLETED,
                        BASE_TIME,
                        null,
                        null
                    );

                long olderId =
                    insertRun(
                        connection,
                        "operational-run-3",
                        ProcessingRunStatus.COMPLETED,
                        BASE_TIME.minusHours(
                            1
                        ),
                        null,
                        null
                    );

                JdbcProcessingRunOperationalQueryAdapter adapter =
                    new JdbcProcessingRunOperationalQueryAdapter(
                        connection
                    );

                ProcessingRunPage firstPage =
                    adapter.search(
                        new ProcessingRunSearchCriteria(
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
                        .runId()
                );

                assertEquals(
                    firstId,
                    firstPage.items()
                        .get(1)
                        .runId()
                );

                assertTrue(
                    firstPage.hasNextPage()
                );

                ProcessingRunPage secondPage =
                    adapter.search(
                        new ProcessingRunSearchCriteria(
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
                        .runId()
                );

                assertFalse(
                    secondPage.hasNextPage()
                );
            }
        );
    }

    @Test
    void shouldFilterFailedRunsAndExposeFailure()
        throws Exception {

        inTransaction(
            connection -> {

                long expectedId =
                    insertRun(
                        connection,
                        "operational-failed-run",
                        ProcessingRunStatus.FAILED,
                        BASE_TIME.plusDays(
                            1
                        ),
                        "HTTP_500",
                        "Temporary upstream failure"
                    );

                insertRun(
                    connection,
                    "operational-completed-run",
                    ProcessingRunStatus.COMPLETED,
                    BASE_TIME.plusDays(
                        1
                    ).plusMinutes(
                        1
                    ),
                    null,
                    null
                );

                JdbcProcessingRunOperationalQueryAdapter adapter =
                    new JdbcProcessingRunOperationalQueryAdapter(
                        connection
                    );

                ProcessingRunPage page =
                    adapter.search(
                        new ProcessingRunSearchCriteria(
                            ProcessingRunStatus.FAILED,
                            BASE_TIME,
                            BASE_TIME.plusDays(
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
                    expectedId,
                    page.items()
                        .getFirst()
                        .runId()
                );

                assertTrue(
                    page.items()
                        .getFirst()
                        .failed()
                );

                assertEquals(
                    "HTTP_500",
                    page.items()
                        .getFirst()
                        .lastErrorCode()
                );

                assertEquals(
                    "Temporary upstream failure",
                    page.items()
                        .getFirst()
                        .lastErrorMessage()
                );
            }
        );
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcProcessingRunOperationalQueryAdapter adapter =
                    new JdbcProcessingRunOperationalQueryAdapter(
                        connection
                    );

                ProcessingRunPage page =
                    adapter.search(
                        new ProcessingRunSearchCriteria(
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
            () -> new JdbcProcessingRunOperationalQueryAdapter(
                null
            )
        );

        inTransaction(
            connection -> {

                JdbcProcessingRunOperationalQueryAdapter adapter =
                    new JdbcProcessingRunOperationalQueryAdapter(
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
        String runKey,
        ProcessingRunStatus status,
        OffsetDateTime requestedAt,
        String errorCode,
        String errorMessage
    ) throws Exception {

        OffsetDateTime startedAt =
            status == ProcessingRunStatus.PENDING
                ? null
                : requestedAt.plusMinutes(
                1
            );

        OffsetDateTime completedAt =
            status == ProcessingRunStatus.COMPLETED
                || status == ProcessingRunStatus.FAILED
                ? requestedAt.plusMinutes(
                2
            )
                : null;

        String sql =
            """
            INSERT INTO processing_run (
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
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                status.name()
            );

            statement.setObject(
                4,
                requestedAt
            );

            statement.setObject(
                5,
                startedAt
            );

            statement.setObject(
                6,
                completedAt
            );

            statement.setString(
                7,
                errorCode
            );

            statement.setString(
                8,
                errorMessage
            );

            statement.setObject(
                9,
                requestedAt
            );

            statement.setObject(
                10,
                completedAt == null
                    ? requestedAt
                    : completedAt
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

    @FunctionalInterface
    private interface TransactionTest {

        void execute(
            Connection connection
        ) throws Exception;
    }
}
