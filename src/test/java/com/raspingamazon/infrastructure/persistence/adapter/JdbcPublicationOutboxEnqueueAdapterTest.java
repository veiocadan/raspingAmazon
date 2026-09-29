package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueStatus;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxEnqueueAdapterTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String SELECTION_VERSION =
        "PUBLICATION_SELECTION_V1";

    private static final String QUOTA_VERSION =
        "PUBLICATION_QUOTA_V1";

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

    @Test
    void shouldEnqueueSelectedReadyPublicationAndReserveQuota()
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

                String destination =
                    uniqueDestination(
                        "enqueue"
                    );

                insertProfiles(
                    connection,
                    destination,
                    QUOTA_VERSION,
                    2,
                    true
                );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18001",
                        "READY",
                        "SELECTED",
                        1,
                        QUOTA_VERSION,
                        2,
                        QUOTA_DATE
                    );

                JdbcPublicationOutboxEnqueueAdapter adapter =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    );

                PublicationOutboxEnqueueResult result =
                    adapter.enqueue(
                        request(
                            scenario
                        )
                    );

                assertEquals(
                    PublicationOutboxEnqueueStatus.ENQUEUED,
                    result.status()
                );

                long outboxId =
                    result.outboxIdValue()
                        .orElseThrow();

                assertTrue(
                    outboxId > 0L
                );

                PersistedOutbox persisted =
                    loadOutbox(
                        connection,
                        outboxId
                    );

                assertEquals(
                    scenario.publicationId(),
                    persisted.publicationId()
                );

                assertEquals(
                    scenario.selectionRunId(),
                    persisted.selectionRunId()
                );

                assertEquals(
                    1,
                    persisted.selectionPosition()
                );

                assertEquals(
                    CHANNEL,
                    persisted.channel()
                );

                assertEquals(
                    destination,
                    persisted.destination()
                );

                assertEquals(
                    "Oferta B0OBX18001",
                    persisted.content()
                );

                assertEquals(
                    QUOTA_VERSION,
                    persisted.quotaProfileVersion()
                );

                assertEquals(
                    QUOTA_DATE,
                    persisted.quotaDate()
                );

                assertEquals(
                    "PENDING",
                    persisted.status()
                );

                JdbcPublicationQuotaUsageQueryAdapter quotaUsage =
                    new JdbcPublicationQuotaUsageQueryAdapter(
                        connection
                    );

                assertEquals(
                    1L,
                    quotaUsage.occupiedSlots(
                        CHANNEL,
                        destination,
                        QUOTA_DATE
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReturnExistingOutboxWithoutConsumingSecondQuotaSlot()
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

                String destination =
                    uniqueDestination(
                        "idempotent"
                    );

                insertProfiles(
                    connection,
                    destination,
                    QUOTA_VERSION,
                    2,
                    true
                );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18002",
                        "READY",
                        "SELECTED",
                        1,
                        QUOTA_VERSION,
                        2,
                        QUOTA_DATE
                    );

                JdbcPublicationOutboxEnqueueAdapter adapter =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    );

                PublicationOutboxEnqueueResult first =
                    adapter.enqueue(
                        request(
                            scenario
                        )
                    );

                PublicationOutboxEnqueueResult second =
                    adapter.enqueue(
                        request(
                            scenario
                        )
                    );

                assertEquals(
                    PublicationOutboxEnqueueStatus.ENQUEUED,
                    first.status()
                );

                assertEquals(
                    PublicationOutboxEnqueueStatus.ALREADY_ENQUEUED,
                    second.status()
                );

                assertEquals(
                    first.outboxIdValue()
                        .orElseThrow(),
                    second.outboxIdValue()
                        .orElseThrow()
                );

                assertEquals(
                    1L,
                    countOutbox(
                        connection,
                        destination
                    )
                );

                assertEquals(
                    1L,
                    new JdbcPublicationQuotaUsageQueryAdapter(
                        connection
                    ).occupiedSlots(
                        CHANNEL,
                        destination,
                        QUOTA_DATE
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReturnQuotaExhaustedWithoutCreatingWaitingOutbox()
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

                String destination =
                    uniqueDestination(
                        "quota"
                    );

                insertProfiles(
                    connection,
                    destination,
                    QUOTA_VERSION,
                    1,
                    true
                );

                Scenario first =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18003",
                        "READY",
                        "SELECTED",
                        1,
                        QUOTA_VERSION,
                        1,
                        QUOTA_DATE
                    );

                Scenario second =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18004",
                        "READY",
                        "SELECTED",
                        1,
                        QUOTA_VERSION,
                        1,
                        QUOTA_DATE
                    );

                JdbcPublicationOutboxEnqueueAdapter adapter =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    );

                PublicationOutboxEnqueueResult firstResult =
                    adapter.enqueue(
                        request(
                            first
                        )
                    );

                PublicationOutboxEnqueueResult secondResult =
                    adapter.enqueue(
                        request(
                            second
                        )
                    );

                assertTrue(
                    firstResult.enqueued()
                );

                assertTrue(
                    secondResult.quotaExhausted()
                );

                assertTrue(
                    secondResult.outboxIdValue()
                        .isEmpty()
                );

                /*
                 * Esta é a propriedade discutida:
                 *
                 * a "oitava" oferta não fica esperando na outbox.
                 */
                assertEquals(
                    1L,
                    countOutbox(
                        connection,
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectPublicationThatIsNotReady()
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

                String destination =
                    uniqueDestination(
                        "not-ready"
                    );

                insertProfiles(
                    connection,
                    destination,
                    QUOTA_VERSION,
                    2,
                    true
                );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18005",
                        "CREATED",
                        "SELECTED",
                        1,
                        QUOTA_VERSION,
                        2,
                        QUOTA_DATE
                    );

                JdbcPublicationOutboxEnqueueAdapter adapter =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        adapter.enqueue(
                            request(
                                scenario
                            )
                        )
                );

                assertEquals(
                    0L,
                    countOutbox(
                        connection,
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectPublicationThatWasNotSelected()
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

                String destination =
                    uniqueDestination(
                        "not-selected"
                    );

                insertProfiles(
                    connection,
                    destination,
                    QUOTA_VERSION,
                    2,
                    true
                );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18006",
                        "READY",
                        "NOT_SELECTED_DUE_TO_QUOTA",
                        1,
                        QUOTA_VERSION,
                        2,
                        QUOTA_DATE
                    );

                JdbcPublicationOutboxEnqueueAdapter adapter =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        adapter.enqueue(
                            request(
                                scenario
                            )
                        )
                );

                assertEquals(
                    0L,
                    countOutbox(
                        connection,
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReturnStaleSelectionWhenOperationalDayChanged()
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

                String destination =
                    uniqueDestination(
                        "stale-date"
                    );

                insertProfiles(
                    connection,
                    destination,
                    QUOTA_VERSION,
                    2,
                    true
                );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18007",
                        "READY",
                        "SELECTED",
                        1,
                        QUOTA_VERSION,
                        2,
                        QUOTA_DATE
                    );

                OffsetDateTime nextOperationalDay =
                    OffsetDateTime.parse(
                        "2026-09-28T03:30:00Z"
                    );

                PublicationOutboxEnqueueResult result =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    ).enqueue(
                        new PublicationOutboxEnqueueRequest(
                            scenario.publicationId(),
                            scenario.selectionRunId(),
                            nextOperationalDay,
                            nextOperationalDay
                        )
                    );

                assertTrue(
                    result.staleSelection()
                );

                assertEquals(
                    0L,
                    countOutbox(
                        connection,
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReturnStaleSelectionWhenActiveQuotaProfileChanged()
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

                String destination =
                    uniqueDestination(
                        "stale-profile"
                    );

                insertProfiles(
                    connection,
                    destination,
                    QUOTA_VERSION,
                    2,
                    true
                );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18008",
                        "READY",
                        "SELECTED",
                        1,
                        QUOTA_VERSION,
                        2,
                        QUOTA_DATE
                    );

                deactivateQuotaProfile(
                    connection,
                    destination,
                    QUOTA_VERSION
                );

                insertQuotaProfile(
                    connection,
                    destination,
                    "PUBLICATION_QUOTA_V2",
                    3,
                    true
                );

                PublicationOutboxEnqueueResult result =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    ).enqueue(
                        request(
                            scenario
                        )
                    );

                assertTrue(
                    result.staleSelection()
                );

                assertEquals(
                    0L,
                    countOutbox(
                        connection,
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldKeepQuotaOccupiedAfterPermanentFailure()
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

                String destination =
                    uniqueDestination(
                        "failed"
                    );

                insertProfiles(
                    connection,
                    destination,
                    QUOTA_VERSION,
                    2,
                    true
                );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination,
                        "B0OBX18009",
                        "READY",
                        "SELECTED",
                        1,
                        QUOTA_VERSION,
                        2,
                        QUOTA_DATE
                    );

                PublicationOutboxEnqueueResult result =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    ).enqueue(
                        request(
                            scenario
                        )
                    );

                long outboxId =
                    result.outboxIdValue()
                        .orElseThrow();

                markOutboxPermanentFailure(
                    connection,
                    outboxId
                );

                assertEquals(
                    1L,
                    new JdbcPublicationQuotaUsageQueryAdapter(
                        connection
                    ).occupiedSlots(
                        CHANNEL,
                        destination,
                        QUOTA_DATE
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private PublicationOutboxEnqueueRequest request(
        Scenario scenario
    ) {

        return new PublicationOutboxEnqueueRequest(
            scenario.publicationId(),
            scenario.selectionRunId(),
            NOW,
            NOW
        );
    }

    private String uniqueDestination(
        String suffix
    ) {

        return "@outbox-"
            + suffix
            + "-"
            + System.nanoTime();
    }

    private void insertProfiles(
        Connection connection,
        String destination,
        String quotaVersion,
        int maximum,
        boolean active
    ) throws Exception {

        String selectionSql =
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
                     selectionSql
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

        insertQuotaProfile(
            connection,
            destination,
            quotaVersion,
            maximum,
            active
        );
    }

    private void insertQuotaProfile(
        Connection connection,
        String destination,
        String version,
        int maximum,
        boolean active
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
            VALUES (?, ?, ?, ?, 'America/Sao_Paulo', ?)
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
                version
            );

            statement.setInt(
                4,
                maximum
            );

            statement.setBoolean(
                5,
                active
            );

            statement.executeUpdate();
        }
    }

    private void deactivateQuotaProfile(
        Connection connection,
        String destination,
        String version
    ) throws Exception {

        String sql =
            """
            UPDATE publication_quota_profile
            SET active = false
            WHERE channel = ?
              AND destination = ?
              AND version = ?
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
                version
            );

            statement.executeUpdate();
        }
    }

    private Scenario createScenario(
        Connection connection,
        String destination,
        String asin,
        String publicationStatus,
        String decisionStatus,
        int priorityPosition,
        String quotaVersion,
        int maximum,
        LocalDate quotaDate
    ) throws Exception {

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
                destination,
                quotaVersion,
                maximum,
                quotaDate
            );

        insertDecision(
            connection,
            selectionRunId,
            evaluationId,
            asin,
            decisionStatus,
            priorityPosition
        );

        long publicationId =
            insertPublication(
                connection,
                evaluationId,
                asin,
                publicationStatus
            );

        return new Scenario(
            publicationId,
            selectionRunId
        );
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
                "Outbox test " + asin
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
                'publication-outbox-test'
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
                    1
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
                NOW.minusMinutes(
                    30
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
        String destination,
        String quotaVersion,
        int maximum,
        LocalDate quotaDate
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
                ?,
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
                quotaVersion
            );

            statement.setObject(
                6,
                quotaDate
            );

            statement.setInt(
                7,
                maximum
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

    private void insertDecision(
        Connection connection,
        long selectionRunId,
        long evaluationId,
        String asin,
        String decisionStatus,
        int priorityPosition
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
                ?,
                'NEVER_SUCCESSFULLY_PUBLISHED',
                ?,
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

            statement.setString(
                4,
                decisionStatus
            );

            statement.setInt(
                5,
                priorityPosition
            );

            statement.executeUpdate();
        }
    }

    private long insertPublication(
        Connection connection,
        long evaluationId,
        String asin,
        String publicationStatus
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

            statement.setString(
                4,
                publicationStatus
            );

            statement.setObject(
                5,
                NOW.minusMinutes(
                    10
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

    private long countOutbox(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
            FROM publication_outbox
            WHERE channel = ?
              AND destination = ?
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

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getLong(
                    "total"
                );
            }
        }
    }

    private PersistedOutbox loadOutbox(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT
                publication_id,
                selection_run_id,
                selection_position,
                channel,
                destination,
                content,
                quota_profile_version,
                quota_date,
                status
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

                return new PersistedOutbox(
                    resultSet.getLong(
                        "publication_id"
                    ),
                    resultSet.getLong(
                        "selection_run_id"
                    ),
                    resultSet.getInt(
                        "selection_position"
                    ),
                    resultSet.getString(
                        "channel"
                    ),
                    resultSet.getString(
                        "destination"
                    ),
                    resultSet.getString(
                        "content"
                    ),
                    resultSet.getString(
                        "quota_profile_version"
                    ),
                    resultSet.getObject(
                        "quota_date",
                        LocalDate.class
                    ),
                    resultSet.getString(
                        "status"
                    )
                );
            }
        }
    }

    private void markOutboxPermanentFailure(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'FAILED_PERMANENT',
                finished_at = ?,
                updated_at = ?
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                NOW.plusMinutes(
                    1
                )
            );

            statement.setObject(
                2,
                NOW.plusMinutes(
                    1
                )
            );

            statement.setLong(
                3,
                outboxId
            );

            statement.executeUpdate();
        }
    }

    private record Scenario(
        long publicationId,
        long selectionRunId
    ) {
    }

    private record PersistedOutbox(
        long publicationId,
        long selectionRunId,
        int selectionPosition,
        String channel,
        String destination,
        String content,
        String quotaProfileVersion,
        LocalDate quotaDate,
        String status
    ) {
    }
}
