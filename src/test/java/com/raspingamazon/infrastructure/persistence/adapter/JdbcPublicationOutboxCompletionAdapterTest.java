package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxCompletionAdapterTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String SELECTION_VERSION =
        "PUBLICATION_SELECTION_V1";

    private static final String QUOTA_VERSION =
        "PUBLICATION_QUOTA_V1";

    private static final String WORKER =
        "worker-completion";

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            27
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T20:00:00Z"
        );

    private static final OffsetDateTime COMPLETED_AT =
        NOW.plusMinutes(
            1
        );

    @Test
    void shouldPersistSuccessfulAttemptAndFinalizeOutbox()
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
                            "success"
                        ),
                        "B0CMP18001"
                    );

                JdbcPublicationOutboxCompletionAdapter adapter =
                    new JdbcPublicationOutboxCompletionAdapter(
                        connection
                    );

                PublicationOutboxItem completed =
                    adapter.complete(
                        scenario.outboxId(),
                        WORKER,
                        PublicationResult.success(
                            "provider-message-123"
                        ),
                        COMPLETED_AT
                    );

                assertEquals(
                    PublicationOutboxStatus.SUCCEEDED,
                    completed.status()
                );

                assertTrue(
                    completed.finished()
                );

                assertNull(
                    completed.lockedAt()
                );

                assertNull(
                    completed.lockedBy()
                );

                assertEquals(
                    COMPLETED_AT.toInstant(),
                    completed.finishedAt()
                        .toInstant()
                );

                PersistedAttempt attempt =
                    loadSingleAttempt(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    scenario.publicationId(),
                    attempt.publicationId()
                );

                assertEquals(
                    1,
                    attempt.attemptNumber()
                );

                assertEquals(
                    "SUCCESS",
                    attempt.status()
                );

                assertEquals(
                    CHANNEL,
                    attempt.channel()
                );

                assertEquals(
                    scenario.destination(),
                    attempt.target()
                );

                assertEquals(
                    "provider-message-123",
                    attempt.providerReference()
                );

                assertNull(
                    attempt.errorCode()
                );

                assertEquals(
                    COMPLETED_AT.toInstant(),
                    attempt.createdAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldPersistTransientFailure()
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
                            "transient"
                        ),
                        "B0CMP18002"
                    );

                PublicationOutboxItem completed =
                    new JdbcPublicationOutboxCompletionAdapter(
                        connection
                    ).complete(
                        scenario.outboxId(),
                        WORKER,
                        PublicationResult.failedTransient(
                            "PROVIDER_TEMPORARILY_UNAVAILABLE",
                            "provider-request-456"
                        ),
                        COMPLETED_AT
                    );

                assertEquals(
                    PublicationOutboxStatus.FAILED_TRANSIENT,
                    completed.status()
                );

                PersistedAttempt attempt =
                    loadSingleAttempt(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    "FAILED_TRANSIENT",
                    attempt.status()
                );

                assertEquals(
                    "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    attempt.errorCode()
                );

                assertEquals(
                    "provider-request-456",
                    attempt.providerReference()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldPersistPermanentFailure()
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
                            "permanent"
                        ),
                        "B0CMP18003"
                    );

                PublicationOutboxItem completed =
                    new JdbcPublicationOutboxCompletionAdapter(
                        connection
                    ).complete(
                        scenario.outboxId(),
                        WORKER,
                        PublicationResult.failedPermanent(
                            "INVALID_DESTINATION"
                        ),
                        COMPLETED_AT
                    );

                assertEquals(
                    PublicationOutboxStatus.FAILED_PERMANENT,
                    completed.status()
                );

                PersistedAttempt attempt =
                    loadSingleAttempt(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    "FAILED_PERMANENT",
                    attempt.status()
                );

                assertEquals(
                    "INVALID_DESTINATION",
                    attempt.errorCode()
                );

                assertNull(
                    attempt.providerReference()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectCompletionFromNonOwnerWorkerWithoutCreatingAttempt()
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
                            "wrong-worker"
                        ),
                        "B0CMP18004"
                    );

                JdbcPublicationOutboxCompletionAdapter adapter =
                    new JdbcPublicationOutboxCompletionAdapter(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        adapter.complete(
                            scenario.outboxId(),
                            "other-worker",
                            PublicationResult.success(
                                "provider-message"
                            ),
                            COMPLETED_AT
                        )
                );

                assertEquals(
                    0L,
                    countAttempts(
                        connection,
                        scenario.outboxId()
                    )
                );

                assertEquals(
                    "PROCESSING",
                    loadOutboxStatus(
                        connection,
                        scenario.outboxId()
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectSecondCompletionWithoutDuplicatingAttempt()
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
                            "duplicate"
                        ),
                        "B0CMP18005"
                    );

                JdbcPublicationOutboxCompletionAdapter adapter =
                    new JdbcPublicationOutboxCompletionAdapter(
                        connection
                    );

                adapter.complete(
                    scenario.outboxId(),
                    WORKER,
                    PublicationResult.success(
                        "provider-message-first"
                    ),
                    COMPLETED_AT
                );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        adapter.complete(
                            scenario.outboxId(),
                            WORKER,
                            PublicationResult.success(
                                "provider-message-second"
                            ),
                            COMPLETED_AT.plusSeconds(
                                1
                            )
                        )
                );

                assertEquals(
                    1L,
                    countAttempts(
                        connection,
                        scenario.outboxId()
                    )
                );

                assertEquals(
                    "SUCCEEDED",
                    loadOutboxStatus(
                        connection,
                        scenario.outboxId()
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldContinueAttemptNumberFromPersistedHistory()
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
                            "numbering"
                        ),
                        "B0CMP18006"
                    );

                insertPreviousAttempt(
                    connection,
                    scenario
                );

                new JdbcPublicationOutboxCompletionAdapter(
                    connection
                ).complete(
                    scenario.outboxId(),
                    WORKER,
                    PublicationResult.success(
                        "provider-message-second-attempt"
                    ),
                    COMPLETED_AT
                );

                assertEquals(
                    2,
                    loadMaximumAttemptNumber(
                        connection,
                        scenario.outboxId()
                    )
                );

                assertEquals(
                    2L,
                    countAttempts(
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
                "Completion test " + asin
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
                'publication-outbox-completion-test'
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
                NOW.minusHours(
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
                NOW
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
                NOW.minusHours(
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
                NOW.minusMinutes(
                    1
                )
            );

            statement.setObject(
                9,
                NOW
            );

            statement.setString(
                10,
                WORKER
            );

            statement.setObject(
                11,
                NOW.minusHours(
                    1
                )
            );

            statement.setObject(
                12,
                NOW
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

    private PersistedAttempt loadSingleAttempt(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT
                publication_id,
                channel,
                target,
                attempt_number,
                status,
                provider_reference,
                error_code,
                created_at
            FROM publication_attempt
            WHERE publication_outbox_id = ?
            ORDER BY attempt_number ASC
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
                        "PublicationAttempt was not found"
                    );
                }

                PersistedAttempt attempt =
                    new PersistedAttempt(
                        resultSet.getLong(
                            "publication_id"
                        ),
                        resultSet.getString(
                            "channel"
                        ),
                        resultSet.getString(
                            "target"
                        ),
                        resultSet.getInt(
                            "attempt_number"
                        ),
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
                            "created_at",
                            OffsetDateTime.class
                        )
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one PublicationAttempt was found"
                    );
                }

                return attempt;
            }
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

    private String loadOutboxStatus(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT status
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

                resultSet.next();

                return resultSet.getString(
                    "status"
                );
            }
        }
    }

    private void insertPreviousAttempt(
        Connection connection,
        Scenario scenario
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
                created_at
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                1,
                'FAILED_TRANSIENT',
                NULL,
                'PREVIOUS_TRANSIENT_FAILURE',
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

            statement.setObject(
                5,
                NOW.minusMinutes(
                    5
                )
            );

            statement.executeUpdate();
        }
    }

    private int loadMaximumAttemptNumber(
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

    private String uniqueDestination(
        String suffix
    ) {

        return "@outbox-completion-"
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
        long publicationId,
        String channel,
        String target,
        int attemptNumber,
        String status,
        String providerReference,
        String errorCode,
        OffsetDateTime createdAt
    ) {
    }
}
