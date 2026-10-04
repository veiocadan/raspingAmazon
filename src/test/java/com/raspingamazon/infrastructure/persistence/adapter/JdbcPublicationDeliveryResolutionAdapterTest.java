package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionDecision;
import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionRequest;
import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionResult;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationDeliveryResolutionAdapterTest {

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
            "2026-10-04T02:00:00Z"
        );

    private static final OffsetDateTime UNKNOWN_FINISHED_AT =
        NOW.plusMinutes(
            1
        );

    private static final OffsetDateTime RESOLVED_AT =
        NOW.plusMinutes(
            10
        );

    @Test
    void shouldConfirmDeliveredWithoutChangingOriginalAttempt()
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
                    createUnknownScenario(
                        connection,
                        uniqueDestination(
                            "delivered"
                        ),
                        uniqueAsin(
                            "DLV"
                        )
                    );

                JdbcPublicationDeliveryResolutionAdapter adapter =
                    new JdbcPublicationDeliveryResolutionAdapter(
                        connection
                    );

                PublicationDeliveryResolutionResult result =
                    adapter.resolve(
                        request(
                            scenario.outboxId(),
                            PublicationDeliveryResolutionDecision
                                .CONFIRMED_DELIVERED,
                            "provider-message-123"
                        ),
                        RESOLVED_AT
                    );

                assertTrue(
                    result.newlyApplied()
                );

                assertEquals(
                    PublicationOutboxStatus.SUCCEEDED,
                    result.resultingStatus()
                );

                PersistedOutbox outbox =
                    readOutbox(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    "SUCCEEDED",
                    outbox.status()
                );

                /*
                 * A confirmação posterior não muda o instante em que
                 * a chamada externa original terminou.
                 */
                assertSameInstant(
                    UNKNOWN_FINISHED_AT,
                    outbox.finishedAt()
                );

                PersistedAttempt attempt =
                    readSingleAttempt(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    "DELIVERY_UNKNOWN",
                    attempt.status()
                );

                assertEquals(
                    "PUBLICATION_DELIVERY_OUTCOME_UNKNOWN_TEST",
                    attempt.errorCode()
                );

                assertNull(
                    attempt.providerReference()
                );

                ResolutionEvent event =
                    readResolutionEvent(
                        connection,
                        result.resolutionEventId()
                    );

                assertEquals(
                    "CONFIRMED_DELIVERED",
                    event.decision()
                );

                assertEquals(
                    "SUCCEEDED",
                    event.resultingStatus()
                );

                assertEquals(
                    "provider-message-123",
                    event.providerReference()
                );

                assertSameInstant(
                    UNKNOWN_FINISHED_AT,
                    event.previousFinishedAt()
                );

                assertSameInstant(
                    RESOLVED_AT,
                    event.resolvedAt()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRequeueOnlyWhenNonDeliveryWasConfirmed()
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
                    createUnknownScenario(
                        connection,
                        uniqueDestination(
                            "not-delivered"
                        ),
                        uniqueAsin(
                            "NDL"
                        )
                    );

                JdbcPublicationDeliveryResolutionAdapter adapter =
                    new JdbcPublicationDeliveryResolutionAdapter(
                        connection
                    );

                PublicationDeliveryResolutionResult result =
                    adapter.resolve(
                        request(
                            scenario.outboxId(),
                            PublicationDeliveryResolutionDecision
                                .CONFIRMED_NOT_DELIVERED,
                            null
                        ),
                        RESOLVED_AT
                    );

                assertEquals(
                    PublicationOutboxStatus.PENDING,
                    result.resultingStatus()
                );

                PersistedOutbox pending =
                    readOutbox(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    "PENDING",
                    pending.status()
                );

                assertSameInstant(
                    RESOLVED_AT,
                    pending.availableAt()
                );

                assertNull(
                    pending.finishedAt()
                );

                /*
                 * Voltar a PENDING torna a MESMA outbox elegível
                 * para o predicado de claim.
                 *
                 * Consultamos pelo id para que o teste não dependa
                 * de outras outboxes eventualmente existentes no
                 * banco de integração.
                 */
                assertTrue(
                    isClaimable(
                        connection,
                        scenario.outboxId(),
                        RESOLVED_AT.plusSeconds(
                            1
                        )
                    )
                );

                /*
                 * A resolução não cria uma nova tentativa externa.
                 * A tentativa DELIVERY_UNKNOWN continua sendo a única.
                 */
                assertEquals(
                    1L,
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

    @Test
    void shouldKeepUnknownWithoutMakingOutboxClaimable()
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
                    createUnknownScenario(
                        connection,
                        uniqueDestination(
                            "remains-unknown"
                        ),
                        uniqueAsin(
                            "UNK"
                        )
                    );

                PublicationDeliveryResolutionResult result =
                    new JdbcPublicationDeliveryResolutionAdapter(
                        connection
                    ).resolve(
                        request(
                            scenario.outboxId(),
                            PublicationDeliveryResolutionDecision
                                .REMAINS_UNKNOWN,
                            null
                        ),
                        RESOLVED_AT
                    );

                assertEquals(
                    PublicationOutboxStatus.DELIVERY_UNKNOWN,
                    result.resultingStatus()
                );

                PersistedOutbox outbox =
                    readOutbox(
                        connection,
                        scenario.outboxId()
                    );

                assertEquals(
                    "DELIVERY_UNKNOWN",
                    outbox.status()
                );

                assertSameInstant(
                    UNKNOWN_FINISHED_AT,
                    outbox.finishedAt()
                );

                assertFalse(
                    isClaimable(
                        connection,
                        scenario.outboxId(),
                        RESOLVED_AT.plusHours(
                            1
                        )
                    )
                );

                assertEquals(
                    1L,
                    countResolutionEventsForOutbox(
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
    void shouldReplaySameRequestAfterOutboxHasAdvanced()
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
                    createUnknownScenario(
                        connection,
                        uniqueDestination(
                            "replay"
                        ),
                        uniqueAsin(
                            "RPL"
                        )
                    );

                String requestKey =
                    "resolution:"
                        + UUID.randomUUID();

                PublicationDeliveryResolutionRequest request =
                    request(
                        scenario.outboxId(),
                        requestKey,
                        PublicationDeliveryResolutionDecision
                            .CONFIRMED_NOT_DELIVERED,
                        null
                    );

                JdbcPublicationDeliveryResolutionAdapter adapter =
                    new JdbcPublicationDeliveryResolutionAdapter(
                        connection
                    );

                PublicationDeliveryResolutionResult first =
                    adapter.resolve(
                        request,
                        RESOLVED_AT
                    );

                markProcessing(
                    connection,
                    scenario.outboxId(),
                    "worker-after-resolution",
                    RESOLVED_AT.plusSeconds(
                        1
                    )
                );

                PublicationDeliveryResolutionResult replay =
                    adapter.resolve(
                        request,
                        RESOLVED_AT.plusMinutes(
                            1
                        )
                    );

                assertFalse(
                    replay.newlyApplied()
                );

                assertEquals(
                    first.resolutionEventId(),
                    replay.resolutionEventId()
                );

                /*
                 * O resultado idempotente representa a decisão
                 * original, não o estado atual da outbox.
                 */
                assertEquals(
                    PublicationOutboxStatus.PENDING,
                    replay.resultingStatus()
                );

                assertEquals(
                    "PROCESSING",
                    readOutbox(
                        connection,
                        scenario.outboxId()
                    ).status()
                );

                assertEquals(
                    1L,
                    countResolutionEventsForOutbox(
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
    void shouldRejectNewResolutionForNonUnknownOutbox()
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
                    createUnknownScenario(
                        connection,
                        uniqueDestination(
                            "non-unknown"
                        ),
                        uniqueAsin(
                            "NUN"
                        )
                    );

                JdbcPublicationDeliveryResolutionAdapter adapter =
                    new JdbcPublicationDeliveryResolutionAdapter(
                        connection
                    );

                adapter.resolve(
                    request(
                        scenario.outboxId(),
                        PublicationDeliveryResolutionDecision
                            .CONFIRMED_DELIVERED,
                        "provider-confirmed"
                    ),
                    RESOLVED_AT
                );

                IllegalStateException exception =
                    assertThrows(
                        IllegalStateException.class,
                        () ->
                            adapter.resolve(
                                request(
                                    scenario.outboxId(),
                                    PublicationDeliveryResolutionDecision
                                        .REMAINS_UNKNOWN,
                                    null
                                ),
                                RESOLVED_AT.plusMinutes(
                                    1
                                )
                            )
                    );

                assertTrue(
                    exception.getMessage()
                        .contains(
                            "status is SUCCEEDED"
                        )
                );

                assertEquals(
                    1L,
                    countResolutionEventsForOutbox(
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
    void shouldRejectRequestKeyCollisionAcrossOutboxes()
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

                Scenario first =
                    createUnknownScenario(
                        connection,
                        uniqueDestination(
                            "collision-first"
                        ),
                        uniqueAsin(
                            "CL1"
                        )
                    );

                Scenario second =
                    createUnknownScenario(
                        connection,
                        uniqueDestination(
                            "collision-second"
                        ),
                        uniqueAsin(
                            "CL2"
                        )
                    );

                String requestKey =
                    "resolution:"
                        + UUID.randomUUID();

                JdbcPublicationDeliveryResolutionAdapter adapter =
                    new JdbcPublicationDeliveryResolutionAdapter(
                        connection
                    );

                adapter.resolve(
                    request(
                        first.outboxId(),
                        requestKey,
                        PublicationDeliveryResolutionDecision
                            .REMAINS_UNKNOWN,
                        null
                    ),
                    RESOLVED_AT
                );

                IllegalStateException exception =
                    assertThrows(
                        IllegalStateException.class,
                        () ->
                            adapter.resolve(
                                request(
                                    second.outboxId(),
                                    requestKey,
                                    PublicationDeliveryResolutionDecision
                                        .REMAINS_UNKNOWN,
                                    null
                                ),
                                RESOLVED_AT.plusMinutes(
                                    1
                                )
                            )
                    );

                assertTrue(
                    exception.getMessage()
                        .contains(
                            "requestKey collision"
                        )
                );

                assertEquals(
                    "DELIVERY_UNKNOWN",
                    readOutbox(
                        connection,
                        second.outboxId()
                    ).status()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private Scenario createUnknownScenario(
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
            insertUnknownOutbox(
                connection,
                publicationId,
                selectionRunId,
                destination,
                asin
            );

        insertUnknownAttempt(
            connection,
            publicationId,
            outboxId,
            destination
        );

        return new Scenario(
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
                "Delivery resolution test "
                    + asin
            );

            statement.setString(
                3,
                "https://example.invalid/"
                    + asin
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
                'publication-delivery-resolution-test'
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
                "Oferta "
                    + asin
            );

            statement.setString(
                3,
                "https://example.invalid/affiliate/"
                    + asin
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

    private long insertUnknownOutbox(
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
                'DELIVERY_UNKNOWN',
                ?,
                NULL,
                NULL,
                ?,
                ?,
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
                "Oferta "
                    + asin
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
                NOW.minusHours(
                    1
                )
            );

            statement.setObject(
                10,
                UNKNOWN_FINISHED_AT
            );

            statement.setObject(
                11,
                UNKNOWN_FINISHED_AT
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

    private void insertUnknownAttempt(
        Connection connection,
        long publicationId,
        long outboxId,
        String destination
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
                'DELIVERY_UNKNOWN',
                NULL,
                'PUBLICATION_DELIVERY_OUTCOME_UNKNOWN_TEST',
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
                publicationId
            );

            statement.setLong(
                2,
                outboxId
            );

            statement.setString(
                3,
                CHANNEL
            );

            statement.setString(
                4,
                destination
            );

            statement.setObject(
                5,
                NOW
            );

            statement.setObject(
                6,
                UNKNOWN_FINISHED_AT
            );

            statement.setObject(
                7,
                NOW
            );

            statement.executeUpdate();
        }
    }

    private PublicationDeliveryResolutionRequest request(
        long outboxId,
        PublicationDeliveryResolutionDecision decision,
        String providerReference
    ) {

        return request(
            outboxId,
            "resolution:"
                + UUID.randomUUID(),
            decision,
            providerReference
        );
    }

    private PublicationDeliveryResolutionRequest request(
        long outboxId,
        String requestKey,
        PublicationDeliveryResolutionDecision decision,
        String providerReference
    ) {

        return new PublicationDeliveryResolutionRequest(
            outboxId,
            requestKey,
            decision,
            "operator-test",
            "controlled evidence for delivery resolution",
            providerReference
        );
    }

    private PersistedOutbox readOutbox(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT
                status,
                available_at,
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
                        "Publication outbox was not found"
                    );
                }

                return new PersistedOutbox(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getObject(
                        "available_at",
                        OffsetDateTime.class
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

    private PersistedAttempt readSingleAttempt(
        Connection connection,
        long outboxId
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
            ORDER BY attempt_number
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
                        "Publication attempt was not found"
                    );
                }

                PersistedAttempt attempt =
                    new PersistedAttempt(
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

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one publication attempt was found"
                    );
                }

                return attempt;
            }
        }
    }

    private ResolutionEvent readResolutionEvent(
        Connection connection,
        long eventId
    ) throws Exception {

        String sql =
            """
            SELECT
                decision,
                resulting_status,
                provider_reference,
                resolved_at,
                previous_status,
                previous_finished_at
            FROM publication_delivery_resolution_event
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                eventId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Resolution event was not found"
                    );
                }

                return new ResolutionEvent(
                    resultSet.getString(
                        "decision"
                    ),
                    resultSet.getString(
                        "resulting_status"
                    ),
                    resultSet.getString(
                        "provider_reference"
                    ),
                    resultSet.getObject(
                        "resolved_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getString(
                        "previous_status"
                    ),
                    resultSet.getObject(
                        "previous_finished_at",
                        OffsetDateTime.class
                    )
                );
            }
        }
    }

    private boolean isClaimable(
        Connection connection,
        long outboxId,
        OffsetDateTime claimedAt
    ) throws Exception {

        String sql =
            """
            SELECT EXISTS (
                SELECT 1
                FROM publication_outbox
                WHERE id = ?
                  AND status = 'PENDING'
                  AND available_at <= ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
            );

            statement.setObject(
                2,
                claimedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getBoolean(
                    1
                );
            }
        }
    }

    private void markProcessing(
        Connection connection,
        long outboxId,
        String workerId,
        OffsetDateTime claimedAt
    ) throws Exception {

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'PROCESSING',
                locked_at = ?,
                locked_by = ?,
                updated_at = ?,
                finished_at = NULL
            WHERE id = ?
              AND status = 'PENDING'
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                claimedAt
            );

            statement.setString(
                2,
                workerId
            );

            statement.setObject(
                3,
                claimedAt
            );

            statement.setLong(
                4,
                outboxId
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private long countAttempts(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
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
                    1
                );
            }
        }
    }

    private long countResolutionEventsForOutbox(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM publication_delivery_resolution_event
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
                    1
                );
            }
        }
    }

    private String uniqueDestination(
        String prefix
    ) {

        return "delivery-resolution-"
            + prefix
            + "-"
            + UUID.randomUUID();
    }

    private String uniqueAsin(
        String prefix
    ) {

        String suffix =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                )
                .substring(
                    0,
                    7
                )
                .toUpperCase();

        String normalizedPrefix =
            prefix.toUpperCase();

        String candidate =
            normalizedPrefix
                + suffix;

        return candidate.substring(
            0,
            Math.min(
                10,
                candidate.length()
            )
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

    private record Scenario(
        long publicationId,
        long outboxId
    ) {
    }

    private record PersistedOutbox(
        String status,
        OffsetDateTime availableAt,
        OffsetDateTime updatedAt,
        OffsetDateTime finishedAt
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

    private record ResolutionEvent(
        String decision,
        String resultingStatus,
        String providerReference,
        OffsetDateTime resolvedAt,
        String previousStatus,
        OffsetDateTime previousFinishedAt
    ) {
    }
}
