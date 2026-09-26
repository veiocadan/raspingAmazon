package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@PostgresIntegrationTest
class JdbcDealCandidateIdempotencyTest {

    private static final String ASIN =
        "B0IDEMP001";

    private static final String SOURCE =
        "AMAZON_DEALS";

    private static final OffsetDateTime FIRST_COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T00:30:00Z"
        );

    private static final OffsetDateTime SECOND_COLLECTED_AT =
        FIRST_COLLECTED_AT.plusMinutes(
            5
        );

    @Test
    void shouldReturnSameCandidateWhenTimestampChangesWithinSameRun()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Long runId =
            null;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertProcessingRun(
                    connection
                );

            JdbcDealCandidateRepositoryAdapter repository =
                new JdbcDealCandidateRepositoryAdapter(
                    connection
                );

            DealCandidate first =
                repository.save(
                    candidate(
                        runId,
                        FIRST_COLLECTED_AT,
                        new BigDecimal(
                            "99.90"
                        )
                    )
                );

            DealCandidate second =
                repository.save(
                    candidate(
                        runId,
                        SECOND_COLLECTED_AT,
                        new BigDecimal(
                            "89.90"
                        )
                    )
                );

            assertEquals(
                first.id(),
                second.id()
            );

            /*
             * A primeira observação persistida continua sendo
             * a fonte de verdade da ProcessingRun.
             */
            assertEquals(
                FIRST_COLLECTED_AT,
                second.parsedDeal()
                    .collectedAt()
            );

            assertEquals(
                new BigDecimal(
                    "99.90"
                ),
                second.parsedDeal()
                    .currentPrice()
            );

            assertEquals(
                1,
                countCandidates(
                    connection,
                    runId
                )
            );

        } finally {

            deleteProcessingRun(
                config,
                runId
            );
        }
    }

    private DealCandidate candidate(
        long processingRunId,
        OffsetDateTime collectedAt,
        BigDecimal currentPrice
    ) {

        ParsedDeal deal =
            new ParsedDeal(
                ASIN,
                "https://www.amazon.com.br/dp/"
                    + ASIN,
                "Produto idempotente",
                "https://example.invalid/product.jpg",
                currentPrice,
                new BigDecimal(
                    "129.90"
                ),
                new BigDecimal(
                    "119.90"
                ),
                new BigDecimal(
                    "35.00"
                ),
                4.5,
                1000L,
                collectedAt,
                SOURCE
            );

        return new DealCandidate(
            null,
            processingRunId,
            deal
        );
    }

    private long insertProcessingRun(
        Connection connection
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at
            )
            VALUES (?, ?, 'RUNNING', ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                "candidate-idempotency-"
                    + System.nanoTime()
            );

            statement.setString(
                2,
                "https://www.amazon.com.br/deals"
            );

            statement.setObject(
                3,
                FIRST_COLLECTED_AT
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

    private int countCandidates(
        Connection connection,
        long processingRunId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
            FROM deal_candidate
            WHERE processing_run_id = ?
              AND asin = ?
              AND source = ?
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
                ASIN
            );

            statement.setString(
                3,
                SOURCE
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getInt(
                    "total"
                );
            }
        }
    }

    private void deleteProcessingRun(
        ApplicationConfig config,
        Long processingRunId
    ) {

        if (processingRunId == null) {
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
                         WHERE deal_candidate_id IN (
                             SELECT id
                             FROM deal_candidate
                             WHERE processing_run_id = ?
                         )
                         """
                     )) {

                statement.setLong(
                    1,
                    processingRunId
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
                    processingRunId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_job
                         WHERE processing_run_id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    processingRunId
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
                    processingRunId
                );

                statement.executeUpdate();
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean DealCandidate idempotency test data",
                exception
            );
        }
    }
}
