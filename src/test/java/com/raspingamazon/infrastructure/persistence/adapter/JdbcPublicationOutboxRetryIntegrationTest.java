package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.retry.BoundedExponentialPublicationOutboxRetryPolicy;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxRetryIntegrationTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String SELECTION_VERSION =
        "PUBLICATION_SELECTION_V1";

    private static final String QUOTA_VERSION =
        "PUBLICATION_QUOTA_V1";

    private static final String WORKER =
        "worker-outbox-retry";

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            10,
            1
        );

    private static final OffsetDateTime STARTED_AT =
        OffsetDateTime.parse(
            "2026-10-01T22:00:00Z"
        );

    private static final OffsetDateTime COMPLETED_AT =
        STARTED_AT.plusMinutes(
            1
        );

    @Test
    void transientFailureShouldPersistAttemptAndRequeueSameOutbox()
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

                Scenario scenario =
                    createScenario(
                        connection,
                        uniqueDestination(
                            "requeue"
                        ),
                        "B0RTY19001"
                    );

                JdbcPublicationOutboxCompletionAdapter adapter =
                    new JdbcPublicationOutboxCompletionAdapter(
                        connection,
                        new BoundedExponentialPublicationOutboxRetryPolicy(
                            3,
                            Duration.ofSeconds(
                                30
                            ),
                            Duration.ofMinutes(
                                5
                            )
                        )
                    );

                PublicationOutboxItem result =
                    adapter.complete(
                        scenario.outboxId(),
                        WORKER,
                        PublicationResult.failedTransient(
                            "TELEGRAM_RATE_LIMITED",
                            "telegram-request-1"
                        ),
                        COMPLETED_AT
                    );

                assertEquals(
                    scenario.outboxId(),
                    result.id()
                );

                assertEquals(
                    PublicationOutboxStatus.PENDING,
                    result.status()
                );

                assertEquals(
                    COMPLETED_AT
                        .plusSeconds(
                            30
                        )
                        .toInstant(),
                    result.availableAt()
                        .toInstant()
                );

                assertNull(
                    result.lockedAt()
                );

                assertNull(
                    result.lockedBy()
                );

                assertNull(
                    result.finishedAt()
                );

                assertEquals(
                    1L,
                    countAttempts(
                        connection,
                        scenario.outboxId()
                    )
                );

                PersistedAttempt attempt =
                    loadAttempt(
                        connection,
                        scenario.outboxId(),
                        1
                    );

                assertEquals(
                    "FAILED_TRANSIENT",
                    attempt.status()
                );

                assertEquals(
                    "TELEGRAM_RATE_LIMITED",
                    attempt.errorCode()
                );

                assertEquals(
                    "telegram-request-1",
                    attempt.providerReference()
                );

                assertEquals(
                    STARTED_AT.toInstant(),
                    attempt.startedAt()
                        .toInstant()
                );

                assertEquals(
                    COMPLETED_AT.toInstant(),
                    attempt.finishedAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void lastAllowedTransientFailureShouldBecomeTerminal()
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

                Scenario scenario =
                    createScenario(
                        connection,
                        uniqueDestination(
                            "terminal"
                        ),
                        "B0RTY19002"
                    );

                insertPreviousTransientAttempt(
                    connection,
                    scenario,
                    1
                );

                JdbcPublicationOutboxCompletionAdapter adapter =
                    new JdbcPublicationOutboxCompletionAdapter(
                        connection,
                        new BoundedExponentialPublicationOutboxRetryPolicy(
                            2,
                            Duration.ofSeconds(
                                30
                            ),
                            Duration.ofMinutes(
                                5
                            )
                        )
                    );

                PublicationOutboxItem result =
                    adapter.complete(
                        scenario.outboxId(),
                        WORKER,
                        PublicationResult.failedTransient(
                            "PROVIDER_TEMPORARILY_UNAVAILABLE",
                            "provider-request-2"
                        ),
                        COMPLETED_AT
                    );

                assertEquals(
                    PublicationOutboxStatus.FAILED_TRANSIENT,
                    result.status()
                );

                assertTrue(
                    result.finished()
                );

                assertEquals(
                    COMPLETED_AT.toInstant(),
                    result.finishedAt()
                        .toInstant()
                );

                assertNull(
                    result.lockedAt()
                );

                assertNull(
                    result.lockedBy()
                );

                assertEquals(
                    2L,
                    countAttempts(
                        connection,
                        scenario.outboxId()
                    )
                );

                assertEquals(
                    2,
                    maximumAttemptNumber(
                        connection,
                        scenario.outboxId()
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private Scenario createScenario(
        Connection connection,
        String destination,
        String asin
    ) throws Exception {

        insertSelectionProfile(
            connection,
            destination
        );

        insertQuotaProfile(
            connection,
            destination
        );

        long productId =
            insertProduct(
                connection,
                asin
            );

        long snapshotId =
            insertSnapshot(
                connection,
                productId
            );

        long evaluationId =
            insertEvaluation(
                connection,
                snapshotId
            );

        long selectionRunId =
            insertSelectionRun(
                connection,
                destination
            );

        insertSelectionDecision(
            connection,
            selectionRunId,
            evaluationId,
            asin
        );

        long publicationId =
            insertPublication(
                connection,
                evaluationId,
                asin
            );

        long outboxId =
            insertProcessingOutbox(
                connection,
                publicationId,
                selectionRunId,
                destination,
                asin
            );

        return new Scenario(
            destination,
            publicationId,
            outboxId
        );
    }

    private void insertSelectionProfile(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            INSERT INTO publication_selection_profile (
                channel,
                destination,
                version,
                hard_cooldown_seconds,
                preferred_cooldown_seconds,
                active
            )
            VALUES (?, ?, ?, 0, 0, true)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
            );

            statement.setString(
                2,
                destination
            );

            statement.setString(
                3,
                SELECTION_VERSION
            );

            statement.executeUpdate();
        }
    }

    private void insertQuotaProfile(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            INSERT INTO publication_quota_profile (
                channel,
                destination,
                version,
                max_publications_per_day,
                quota_zone,
                active
            )
            VALUES (
                ?,
                ?,
                ?,
                10,
                'America/Sao_Paulo',
                true
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
            );

            statement.setString(
                2,
                destination
            );

            statement.setString(
                3,
                QUOTA_VERSION
            );

            statement.executeUpdate();
        }
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
                "Retry test " + asin
            );

            statement.setString(
                3,
                "https://example.invalid/" + asin
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

    private long insertSnapshot(
        Connection connection,
        long productId
    ) throws Exception {

        String sql =
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
            VALUES (
                ?,
                ?,
                100.00,
                NULL,
                NULL,
                NULL,
                4.8,
                1000,
                'Amazon.com.br',
                'Amazon',
                'publication-outbox-retry-test'
            )
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
                STARTED_AT.minusHours(
                    4
                )
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

    private long insertEvaluation(
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
            VALUES (
                ?,
                true,
                NULL,
                'TEST_ELIGIBILITY_V1',
                90.0000,
                'SCORE_V1',
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
                snapshotId
            );

            statement.setObject(
                2,
                STARTED_AT.minusHours(
                    3
                )
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

    private long insertSelectionRun(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            INSERT INTO publication_selection_run (
                channel,
                destination,
                decided_at,
                selection_profile_version,
                hard_cooldown_seconds,
                preferred_cooldown_seconds,
                quota_profile_version,
                quota_date,
                max_publications_per_day,
                occupied_slots
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                0,
                0,
                ?,
                ?,
                10,
                0
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
            );

            statement.setString(
                2,
                destination
            );

            statement.setObject(
                3,
                STARTED_AT
            );

            statement.setString(
                4,
                SELECTION_VERSION
            );

            statement.setString(
                5,
                QUOTA_VERSION
            );

            statement.setObject(
                6,
                QUOTA_DATE
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

    private void insertSelectionDecision(
        Connection connection,
        long selectionRunId,
        long evaluationId,
        String asin
    ) throws Exception {

        String sql =
            """
            INSERT INTO publication_selection_decision (
                selection_run_id,
                deal_evaluation_id,
                asin,
                score,
                decision_status,
                recency,
                priority_position,
                last_successful_publication_at,
                successful_publication_count
            )
            VALUES (
                ?,
                ?,
                ?,
                90.0000,
                'SELECTED',
                'NEVER_SUCCESSFULLY_PUBLISHED',
                1,
                NULL,
                0
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                selectionRunId
            );

            statement.setLong(
                2,
                evaluationId
            );

            statement.setString(
                3,
                asin
            );

            statement.executeUpdate();
        }
    }

    private long insertPublication(
        Connection connection,
        long evaluationId,
        String asin
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
            VALUES (
                ?,
                'AMAZON_PUBLICATION_V1',
                'AMAZON_COMMERCIAL_PRESENTATION_V1',
                'AMAZON_AFFILIATE_LINK_V1',
                ?,
                ?,
                'READY',
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
                evaluationId
            );

            statement.setString(
                2,
                "Oferta " + asin
            );

            statement.setString(
                3,
                "https://example.invalid/affiliate/" + asin
            );

            statement.setObject(
                4,
                STARTED_AT.minusHours(
                    2
                )
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

    private long insertProcessingOutbox(
        Connection connection,
        long publicationId,
        long selectionRunId,
        String destination,
        String asin
    ) throws Exception {

        String sql =
            """
            INSERT INTO publication_outbox (
                publication_id,
                selection_run_id,
                selection_position,
                channel,
                destination,
                content,
                quota_profile_version,
                quota_date,
                status,
                available_at,
                locked_at,
                locked_by,
                created_at,
                updated_at,
                finished_at
            )
            VALUES (
                ?,
                ?,
                1,
                ?,
                ?,
                ?,
                ?,
                ?,
                'PROCESSING',
                ?,
                ?,
                ?,
                ?,
                ?,
                NULL
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                publicationId
            );

            statement.setLong(
                2,
                selectionRunId
            );

            statement.setString(
                3,
                CHANNEL
            );

            statement.setString(
                4,
                destination
            );

            statement.setString(
                5,
                "Oferta " + asin
            );

            statement.setString(
                6,
                QUOTA_VERSION
            );

            statement.setObject(
                7,
                QUOTA_DATE
            );

            statement.setObject(
                8,
                STARTED_AT.minusMinutes(
                    1
                )
            );

            statement.setObject(
                9,
                STARTED_AT
            );

            statement.setString(
                10,
                WORKER
            );

            statement.setObject(
                11,
                STARTED_AT.minusHours(
                    1
                )
            );

            statement.setObject(
                12,
                STARTED_AT
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

    private void insertPreviousTransientAttempt(
        Connection connection,
        Scenario scenario,
        int attemptNumber
    ) throws Exception {

        OffsetDateTime previousFinishedAt =
            STARTED_AT.minusMinutes(
                1
            );

        OffsetDateTime previousStartedAt =
            previousFinishedAt.minusMinutes(
                1
            );

        String sql =
            """
            INSERT INTO publication_attempt (
                publication_id,
                publication_outbox_id,
                channel,
                target,
                attempt_number,
                status,
                provider_reference,
                error_code,
                started_at,
                finished_at,
                created_at
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                'FAILED_TRANSIENT',
                'previous-provider-reference',
                'PREVIOUS_TRANSIENT_FAILURE',
                ?,
                ?,
                ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                scenario.publicationId()
            );

            statement.setLong(
                2,
                scenario.outboxId()
            );

            statement.setString(
                3,
                CHANNEL
            );

            statement.setString(
                4,
                scenario.destination()
            );

            statement.setInt(
                5,
                attemptNumber
            );

            statement.setObject(
                6,
                previousStartedAt
            );

            statement.setObject(
                7,
                previousFinishedAt
            );

            statement.setObject(
                8,
                previousFinishedAt
            );

            statement.executeUpdate();
        }
    }

    private long countAttempts(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
            FROM publication_attempt
            WHERE publication_outbox_id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getLong(
                    "total"
                );
            }
        }
    }

    private int maximumAttemptNumber(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT MAX(attempt_number) AS maximum_attempt_number
            FROM publication_attempt
            WHERE publication_outbox_id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getInt(
                    "maximum_attempt_number"
                );
            }
        }
    }

    private PersistedAttempt loadAttempt(
        Connection connection,
        long outboxId,
        int attemptNumber
    ) throws Exception {

        String sql =
            """
            SELECT
                status,
                provider_reference,
                error_code,
                started_at,
                finished_at
            FROM publication_attempt
            WHERE publication_outbox_id = ?
              AND attempt_number = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
            );

            statement.setInt(
                2,
                attemptNumber
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationAttempt was not found"
                    );
                }

                return new PersistedAttempt(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getString(
                        "provider_reference"
                    ),
                    resultSet.getString(
                        "error_code"
                    ),
                    resultSet.getObject(
                        "started_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getObject(
                        "finished_at",
                        OffsetDateTime.class
                    )
                );
            }
        }
    }

    private String uniqueDestination(
        String suffix
    ) {

        return "@outbox-retry-"
            + suffix
            + "-"
            + System.nanoTime();
    }

    private record Scenario(
        String destination,
        long publicationId,
        long outboxId
    ) {
    }

    private record PersistedAttempt(
        String status,
        String providerReference,
        String errorCode,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
    ) {
    }
}
