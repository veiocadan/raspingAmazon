package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxUnknownRecoveryTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String SELECTION_VERSION =
        "PUBLICATION_SELECTION_V1";

    private static final String QUOTA_VERSION =
        "PUBLICATION_QUOTA_V1";

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            10,
            3
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-03T23:50:00Z"
        );

    private static final OffsetDateTime RECOVERY_LIMIT =
        NOW.minusHours(
            1
        );

    @Test
    void expiredProcessingWithStartedAttemptShouldBecomeDeliveryUnknown()
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
                    createProcessingScenario(
                        connection,
                        NOW.minusHours(
                            2
                        )
                    );

                long attemptId =
                    insertStartedAttempt(
                        connection,
                        scenario,
                        NOW.minusHours(
                            2
                        ).plusSeconds(
                            10
                        )
                    );

                JdbcPublicationOutboxQueueAdapter adapter =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    );

                int recovered =
                    adapter.recoverExpiredLeases(
                        RECOVERY_LIMIT,
                        NOW
                    );

                assertEquals(
                    1,
                    recovered
                );

                PersistedOutbox outbox =
                    loadOutbox(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    "DELIVERY_UNKNOWN",
                    outbox.status()
                );

                assertNull(
                    outbox.lockedAt()
                );

                assertNull(
                    outbox.lockedBy()
                );

                assertEquals(
                    NOW.toInstant(),
                    outbox.updatedAt()
                        .toInstant()
                );

                assertEquals(
                    NOW.toInstant(),
                    outbox.finishedAt()
                        .toInstant()
                );

                PersistedAttempt attempt =
                    loadAttempt(
                        connection,
                        attemptId
                    );

                assertEquals(
                    "DELIVERY_UNKNOWN",
                    attempt.status()
                );

                assertEquals(
                    JdbcPublicationOutboxQueueAdapter
                        .UNKNOWN_AFTER_LEASE_EXPIRY_ERROR,
                    attempt.errorCode()
                );

                assertEquals(
                    NOW.toInstant(),
                    attempt.finishedAt()
                        .toInstant()
                );

                /*
                 * DELIVERY_UNKNOWN não participa do claim normal.
                 * Uma recuperação de lease nunca pode transformar
                 * ambiguidade externa em reenvio automático.
                 */
                Optional<PublicationOutboxItem> reclaimed =
                    adapter.claimNext(
                        "replacement-worker",
                        NOW.plusSeconds(
                            1
                        )
                    );

                assertTrue(
                    reclaimed.isEmpty()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void recentProcessingWithStartedAttemptShouldRemainUntouched()
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

                OffsetDateTime recentLock =
                    NOW.minusMinutes(
                        30
                    );

                Scenario scenario =
                    createProcessingScenario(
                        connection,
                        recentLock
                    );

                long attemptId =
                    insertStartedAttempt(
                        connection,
                        scenario,
                        recentLock.plusSeconds(
                            10
                        )
                    );

                int recovered =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    ).recoverExpiredLeases(
                        RECOVERY_LIMIT,
                        NOW
                    );

                assertEquals(
                    0,
                    recovered
                );

                PersistedOutbox outbox =
                    loadOutbox(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    "PROCESSING",
                    outbox.status()
                );

                assertEquals(
                    recentLock.toInstant(),
                    outbox.lockedAt()
                        .toInstant()
                );

                assertEquals(
                    "dead-worker",
                    outbox.lockedBy()
                );

                assertNull(
                    outbox.finishedAt()
                );

                PersistedAttempt attempt =
                    loadAttempt(
                        connection,
                        attemptId
                    );

                assertEquals(
                    "STARTED",
                    attempt.status()
                );

                assertNull(
                    attempt.errorCode()
                );

                assertNull(
                    attempt.finishedAt()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private Scenario createProcessingScenario(
        Connection connection,
        OffsetDateTime lockedAt
    ) throws Exception {

        String token =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                );

        String destination =
            "@phase20_recovery_"
                + token;

        String asin =
            "B0R"
                + token.substring(
                    0,
                    7
                ).toUpperCase();

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
                asin,
                lockedAt
            );

        return new Scenario(
            outboxId,
            publicationId,
            destination
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
            VALUES (?, ?, ?, 10, 'UTC', true)
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
                "Phase 20 recovery " + asin
            );

            statement.setString(
                3,
                "https://example.invalid/" + asin
            );

            return readReturnedId(
                statement,
                "Product"
            );
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
                100,
                'Amazon.com.br',
                'Amazon',
                'phase-20-unknown-recovery-test'
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
                NOW.minusHours(
                    5
                )
            );

            return readReturnedId(
                statement,
                "OfferSnapshot"
            );
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
                NOW.minusHours(
                    4
                )
            );

            return readReturnedId(
                statement,
                "DealEvaluation"
            );
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
                NOW.minusHours(
                    3
                )
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

            return readReturnedId(
                statement,
                "PublicationSelectionRun"
            );
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
                NOW.minusHours(
                    2
                )
            );

            return readReturnedId(
                statement,
                "Publication"
            );
        }
    }

    private long insertProcessingOutbox(
        Connection connection,
        long publicationId,
        long selectionRunId,
        String destination,
        String asin,
        OffsetDateTime lockedAt
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
                'dead-worker',
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
                lockedAt.minusMinutes(
                    1
                )
            );

            statement.setObject(
                9,
                lockedAt
            );

            statement.setObject(
                10,
                lockedAt.minusHours(
                    1
                )
            );

            statement.setObject(
                11,
                lockedAt
            );

            return readReturnedId(
                statement,
                "PublicationOutbox"
            );
        }
    }

    private long insertStartedAttempt(
        Connection connection,
        Scenario scenario,
        OffsetDateTime startedAt
    ) throws Exception {

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
                1,
                'STARTED',
                NULL,
                NULL,
                ?,
                NULL,
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

            statement.setObject(
                5,
                startedAt
            );

            statement.setObject(
                6,
                startedAt
            );

            return readReturnedId(
                statement,
                "PublicationAttempt"
            );
        }
    }

    private PersistedOutbox loadOutbox(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT
                status,
                locked_at,
                locked_by,
                updated_at,
                finished_at
            FROM publication_outbox
            WHERE id = ?
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationOutbox not found: "
                            + outboxId
                    );
                }

                return new PersistedOutbox(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getObject(
                        "locked_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getString(
                        "locked_by"
                    ),
                    resultSet.getObject(
                        "updated_at",
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

    private PersistedAttempt loadAttempt(
        Connection connection,
        long attemptId
    ) throws Exception {

        String sql =
            """
            SELECT
                status,
                error_code,
                finished_at
            FROM publication_attempt
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                attemptId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationAttempt not found: "
                            + attemptId
                    );
                }

                return new PersistedAttempt(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getString(
                        "error_code"
                    ),
                    resultSet.getObject(
                        "finished_at",
                        OffsetDateTime.class
                    )
                );
            }
        }
    }

    private long readReturnedId(
        PreparedStatement statement,
        String entityName
    ) throws Exception {

        try (ResultSet resultSet =
                 statement.executeQuery()) {

            if (!resultSet.next()) {

                throw new IllegalStateException(
                    entityName
                        + " insert returned no id"
                );
            }

            return resultSet.getLong(
                "id"
            );
        }
    }

    private record Scenario(
        long outboxId,
        long publicationId,
        String destination
    ) {
    }

    private record PersistedOutbox(
        String status,
        OffsetDateTime lockedAt,
        String lockedBy,
        OffsetDateTime updatedAt,
        OffsetDateTime finishedAt
    ) {
    }

    private record PersistedAttempt(
        String status,
        String errorCode,
        OffsetDateTime finishedAt
    ) {
    }
}
