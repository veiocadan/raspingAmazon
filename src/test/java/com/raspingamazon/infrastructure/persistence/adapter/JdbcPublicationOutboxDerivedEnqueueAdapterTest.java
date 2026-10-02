package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.outbox.PublicationOutboxDerivedEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueStatus;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxDerivedEnqueueAdapterTest {

    private static final String PRIMARY_CHANNEL =
        "TELEGRAM";

    private static final String DERIVED_CHANNEL =
        "WHATSAPP_MANUAL";

    private static final String SELECTION_VERSION =
        "D2B_SELECTION_V1";

    private static final String QUOTA_VERSION =
        "D2B_QUOTA_V1";

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            30
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-30T20:00:00Z"
        );

    private static final OffsetDateTime AVAILABLE_AT =
        NOW.plusMinutes(
            5
        );

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    @Test
    void shouldCreateDerivedDeliveryWithoutReservingSecondQuota()
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
                    createPrimaryScenario(
                        connection
                    );

                String derivedDestination =
                    uniqueDestination(
                        "manual"
                    );

                PublicationOutboxEnqueueResult result =
                    new JdbcPublicationOutboxDerivedEnqueueAdapter(
                        connection
                    ).enqueue(
                        request(
                            scenario.sourceOutboxId(),
                            derivedDestination
                        )
                    );

                assertEquals(
                    PublicationOutboxEnqueueStatus.ENQUEUED,
                    result.status()
                );

                long derivedOutboxId =
                    result.outboxIdValue()
                        .orElseThrow();

                PersistedOutbox derived =
                    loadOutbox(
                        connection,
                        derivedOutboxId
                    );

                assertEquals(
                    scenario.publicationId(),
                    derived.publicationId()
                );

                assertEquals(
                    scenario.selectionRunId(),
                    derived.selectionRunId()
                );

                assertEquals(
                    1,
                    derived.selectionPosition()
                );

                assertEquals(
                    DERIVED_CHANNEL,
                    derived.channel()
                );

                assertEquals(
                    derivedDestination,
                    derived.destination()
                );

                assertEquals(
                    scenario.content(),
                    derived.content()
                );

                assertNull(
                    derived.quotaProfileVersion()
                );

                assertNull(
                    derived.quotaDate()
                );

                assertEquals(
                    "PENDING",
                    derived.status()
                );

                assertEquals(
                    scenario.availableAt(),
                    derived.availableAt()
                );

                /*
                 * Duas entregas físicas existem para a mesma
                 * posição selecionada.
                 */
                assertEquals(
                    2L,
                    countPublicationOutboxes(
                        connection,
                        scenario.publicationId()
                    )
                );

                /*
                 * Mas apenas uma delas representa reserva de quota.
                 */
                assertEquals(
                    1L,
                    countQuotaReservations(
                        connection,
                        scenario.selectionRunId(),
                        1
                    )
                );

                /*
                 * A quota operacional original do Telegram continua
                 * ocupando exatamente uma vaga.
                 */
                assertEquals(
                    1L,
                    new JdbcPublicationQuotaUsageQueryAdapter(
                        connection
                    ).occupiedSlots(
                        PRIMARY_CHANNEL,
                        scenario.primaryDestination(),
                        QUOTA_DATE
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldBeIdempotentForSameDerivedDeliveryIdentity()
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
                    createPrimaryScenario(
                        connection
                    );

                String derivedDestination =
                    uniqueDestination(
                        "idempotent"
                    );

                JdbcPublicationOutboxDerivedEnqueueAdapter adapter =
                    new JdbcPublicationOutboxDerivedEnqueueAdapter(
                        connection
                    );

                PublicationOutboxDerivedEnqueueRequest request =
                    request(
                        scenario.sourceOutboxId(),
                        derivedDestination
                    );

                PublicationOutboxEnqueueResult first =
                    adapter.enqueue(
                        request
                    );

                PublicationOutboxEnqueueResult second =
                    adapter.enqueue(
                        request
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
                    countDelivery(
                        connection,
                        scenario.publicationId(),
                        DERIVED_CHANNEL,
                        derivedDestination
                    )
                );

                assertEquals(
                    1L,
                    countQuotaReservations(
                        connection,
                        scenario.selectionRunId(),
                        1
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldKeepIdempotencyAfterPrimaryWasAlreadyCompleted()
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
                    createPrimaryScenario(
                        connection
                    );

                String derivedDestination =
                    uniqueDestination(
                        "after-success"
                    );

                JdbcPublicationOutboxDerivedEnqueueAdapter adapter =
                    new JdbcPublicationOutboxDerivedEnqueueAdapter(
                        connection
                    );

                PublicationOutboxDerivedEnqueueRequest request =
                    request(
                        scenario.sourceOutboxId(),
                        derivedDestination
                    );

                PublicationOutboxEnqueueResult first =
                    adapter.enqueue(
                        request
                    );

                markOutboxSucceeded(
                    connection,
                    scenario.sourceOutboxId()
                );

                PublicationOutboxEnqueueResult second =
                    adapter.enqueue(
                        request
                    );

                assertTrue(
                    first.enqueued()
                );

                assertTrue(
                    second.alreadyEnqueued()
                );

                assertEquals(
                    first.outboxIdValue()
                        .orElseThrow(),
                    second.outboxIdValue()
                        .orElseThrow()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectCreatingNewDerivedDeliveryAfterPrimaryStartedProcessing()
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
                    createPrimaryScenario(
                        connection
                    );

                markOutboxSucceeded(
                    connection,
                    scenario.sourceOutboxId()
                );

                JdbcPublicationOutboxDerivedEnqueueAdapter adapter =
                    new JdbcPublicationOutboxDerivedEnqueueAdapter(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        adapter.enqueue(
                            request(
                                scenario.sourceOutboxId(),
                                uniqueDestination(
                                    "late"
                                )
                            )
                        )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectDerivedDeliveryAsSourceForAnotherDerivedDelivery()
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
                    createPrimaryScenario(
                        connection
                    );

                JdbcPublicationOutboxDerivedEnqueueAdapter adapter =
                    new JdbcPublicationOutboxDerivedEnqueueAdapter(
                        connection
                    );

                PublicationOutboxEnqueueResult firstDerived =
                    adapter.enqueue(
                        request(
                            scenario.sourceOutboxId(),
                            uniqueDestination(
                                "first-derived"
                            )
                        )
                    );

                long derivedOutboxId =
                    firstDerived.outboxIdValue()
                        .orElseThrow();

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        adapter.enqueue(
                            new PublicationOutboxDerivedEnqueueRequest(
                                derivedOutboxId,
                                "ANOTHER_DERIVED_CHANNEL",
                                uniqueDestination(
                                    "second-derived"
                                ),
                                NOW
                            )
                        )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectSameDeliveryIdentityAsSource()
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
                    createPrimaryScenario(
                        connection
                    );

                JdbcPublicationOutboxDerivedEnqueueAdapter adapter =
                    new JdbcPublicationOutboxDerivedEnqueueAdapter(
                        connection
                    );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        adapter.enqueue(
                            new PublicationOutboxDerivedEnqueueRequest(
                                scenario.sourceOutboxId(),
                                PRIMARY_CHANNEL,
                                scenario.primaryDestination(),
                                NOW
                            )
                        )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectMissingSourceOutbox()
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

                JdbcPublicationOutboxDerivedEnqueueAdapter adapter =
                    new JdbcPublicationOutboxDerivedEnqueueAdapter(
                        connection
                    );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        adapter.enqueue(
                            new PublicationOutboxDerivedEnqueueRequest(
                                Long.MAX_VALUE,
                                DERIVED_CHANNEL,
                                uniqueDestination(
                                    "missing"
                                ),
                                NOW
                            )
                        )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private PublicationOutboxDerivedEnqueueRequest request(
        long sourceOutboxId,
        String derivedDestination
    ) {

        return new PublicationOutboxDerivedEnqueueRequest(
            sourceOutboxId,
            DERIVED_CHANNEL,
            derivedDestination,
            NOW
        );
    }

    private Scenario createPrimaryScenario(
        Connection connection
    ) throws Exception {

        int sequence =
            SEQUENCE.incrementAndGet();

        String asin =
            "B0D2B"
                + String.format(
                "%05d",
                sequence
            );

        String primaryDestination =
            uniqueDestination(
                "primary"
            );

        String content =
            """
            🔹**Produto D2B**
            💰 De ~~R$ 129,90~~ por **R$ 99,90**!
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/%s
            """
                .formatted(
                    asin
                )
                .strip();

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

        long publicationId =
            insertPublication(
                connection,
                evaluationId,
                content,
                asin
            );

        insertSelectionProfile(
            connection,
            primaryDestination
        );

        insertQuotaProfile(
            connection,
            primaryDestination
        );

        long selectionRunId =
            insertSelectionRun(
                connection,
                primaryDestination
            );

        long sourceOutboxId =
            insertPrimaryOutbox(
                connection,
                publicationId,
                selectionRunId,
                primaryDestination,
                content
            );

        return new Scenario(
            sourceOutboxId,
            publicationId,
            selectionRunId,
            primaryDestination,
            content,
            AVAILABLE_AT
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
                "Produto D2B " + asin
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/" + asin
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
                99.90,
                129.90,
                23.10,
                NULL,
                4.8,
                1000,
                'Amazon.com.br',
                'Amazon',
                'publication-derived-outbox-test'
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

    private long insertPublication(
        Connection connection,
        long evaluationId,
        String content,
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
                'AMAZON_PUBLICATION_V2',
                'AMAZON_COMMERCIAL_PRESENTATION_V2',
                'AMAZON_AFFILIATE_LINK_V2',
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
                content
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/"
                    + asin
                    + "?tag=test-20"
            );

            statement.setObject(
                4,
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
                PRIMARY_CHANNEL
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
            VALUES (?, ?, ?, 10, 'America/Sao_Paulo', true)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                PRIMARY_CHANNEL
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
                PRIMARY_CHANNEL
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

    private long insertPrimaryOutbox(
        Connection connection,
        long publicationId,
        long selectionRunId,
        String destination,
        String content
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
                created_at,
                updated_at
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
                'PENDING',
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
                PRIMARY_CHANNEL
            );

            statement.setString(
                4,
                destination
            );

            statement.setString(
                5,
                content
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
                AVAILABLE_AT
            );

            statement.setObject(
                9,
                NOW
            );

            statement.setObject(
                10,
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

    private void markOutboxSucceeded(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'SUCCEEDED',
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
                    10
                )
            );

            statement.setObject(
                2,
                NOW.plusMinutes(
                    10
                )
            );

            statement.setLong(
                3,
                outboxId
            );

            assertEquals(
                1,
                statement.executeUpdate()
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
                publication_id,
                selection_run_id,
                selection_position,
                channel,
                destination,
                content,
                quota_profile_version,
                quota_date,
                status,
                available_at
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

                assertTrue(
                    resultSet.next()
                );

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
                    ),
                    resultSet.getObject(
                        "available_at",
                        OffsetDateTime.class
                    )
                );
            }
        }
    }

    private long countPublicationOutboxes(
        Connection connection,
        long publicationId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
            FROM publication_outbox
            WHERE publication_id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                publicationId
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

    private long countDelivery(
        Connection connection,
        long publicationId,
        String channel,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
            FROM publication_outbox
            WHERE publication_id = ?
              AND channel = ?
              AND destination = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                publicationId
            );

            statement.setString(
                2,
                channel
            );

            statement.setString(
                3,
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

    private long countQuotaReservations(
        Connection connection,
        long selectionRunId,
        int selectionPosition
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
            FROM publication_outbox
            WHERE selection_run_id = ?
              AND selection_position = ?
              AND quota_profile_version IS NOT NULL
              AND quota_date IS NOT NULL
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                selectionRunId
            );

            statement.setInt(
                2,
                selectionPosition
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

    private String uniqueDestination(
        String prefix
    ) {

        return "@"
            + prefix
            + "_"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }

    private record Scenario(
        long sourceOutboxId,
        long publicationId,
        long selectionRunId,
        String primaryDestination,
        String content,
        OffsetDateTime availableAt
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
        String status,
        OffsetDateTime availableAt
    ) {
    }
}
