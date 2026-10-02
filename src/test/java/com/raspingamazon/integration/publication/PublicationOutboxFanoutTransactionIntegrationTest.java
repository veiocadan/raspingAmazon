package com.raspingamazon.integration.publication;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.outbox.PublicationOutboxDerivedTarget;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxFanoutEnqueueService;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxDerivedEnqueuePort;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxDerivedEnqueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxEnqueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcTransactionAdapter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class PublicationOutboxFanoutTransactionIntegrationTest {

    private static final String PRIMARY_CHANNEL =
        "TELEGRAM";

    private static final String DERIVED_CHANNEL =
        "WHATSAPP_MANUAL";

    private static final String SELECTION_VERSION =
        "D3B_SELECTION_V1";

    private static final String QUOTA_VERSION =
        "D3B_QUOTA_V1";

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            30
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-30T20:30:00Z"
        );

    private static final OffsetDateTime AVAILABLE_AT =
        NOW.plusMinutes(
            5
        );

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    @Test
    void shouldPersistPrimaryAndManualWhatsAppDerivedDeliveryTogether()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            /*
             * Mantemos uma transação externa somente para que toda
             * a fixture possa ser descartada ao final do teste.
             *
             * O JdbcTransactionAdapter usado pelo serviço trabalhará
             * com savepoint dentro desta transação real.
             */
            connection.setAutoCommit(
                false
            );

            try {

                Scenario scenario =
                    createScenario(
                        connection
                    );

                String manualDestination =
                    uniqueDestination(
                        "manual"
                    );

                PublicationOutboxFanoutEnqueueService service =
                    createRealService(
                        connection,
                        manualDestination
                    );

                PublicationOutboxEnqueueResult result =
                    service.enqueue(
                        request(
                            scenario
                        )
                    );

                assertTrue(
                    result.enqueued()
                );

                long primaryOutboxId =
                    result.outboxIdValue()
                        .orElseThrow();

                PersistedOutbox primary =
                    loadOutbox(
                        connection,
                        primaryOutboxId
                    );

                assertEquals(
                    scenario.publicationId(),
                    primary.publicationId()
                );

                assertEquals(
                    scenario.selectionRunId(),
                    primary.selectionRunId()
                );

                assertEquals(
                    1,
                    primary.selectionPosition()
                );

                assertEquals(
                    PRIMARY_CHANNEL,
                    primary.channel()
                );

                assertEquals(
                    scenario.primaryDestination(),
                    primary.destination()
                );

                assertEquals(
                    scenario.content(),
                    primary.content()
                );

                assertEquals(
                    QUOTA_VERSION,
                    primary.quotaProfileVersion()
                );

                assertEquals(
                    QUOTA_DATE,
                    primary.quotaDate()
                );

                long derivedOutboxId =
                    findOutboxId(
                        connection,
                        scenario.publicationId(),
                        DERIVED_CHANNEL,
                        manualDestination
                    );

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
                    primary.selectionPosition(),
                    derived.selectionPosition()
                );

                assertEquals(
                    DERIVED_CHANNEL,
                    derived.channel()
                );

                assertEquals(
                    manualDestination,
                    derived.destination()
                );

                /*
                 * A outbox derivada recebe exatamente o mesmo
                 * conteúdo canônico persistido para a principal.
                 *
                 * A transformação para WhatsApp só ocorrerá no
                 * adapter de entrega.
                 */
                assertEquals(
                    primary.content(),
                    derived.content()
                );

                assertNull(
                    derived.quotaProfileVersion()
                );

                assertNull(
                    derived.quotaDate()
                );

                assertEquals(
                    primary.availableAt(),
                    derived.availableAt()
                );

                assertEquals(
                    "PENDING",
                    primary.status()
                );

                assertEquals(
                    "PENDING",
                    derived.status()
                );

                assertEquals(
                    2L,
                    countPublicationOutboxes(
                        connection,
                        scenario.publicationId()
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
    void shouldRemainIdempotentAcrossEntireFanout()
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
                        connection
                    );

                String manualDestination =
                    uniqueDestination(
                        "idempotent"
                    );

                PublicationOutboxFanoutEnqueueService service =
                    createRealService(
                        connection,
                        manualDestination
                    );

                PublicationOutboxEnqueueResult first =
                    service.enqueue(
                        request(
                            scenario
                        )
                    );

                PublicationOutboxEnqueueResult second =
                    service.enqueue(
                        request(
                            scenario
                        )
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

                assertEquals(
                    1L,
                    countDelivery(
                        connection,
                        scenario.publicationId(),
                        PRIMARY_CHANNEL,
                        scenario.primaryDestination()
                    )
                );

                assertEquals(
                    1L,
                    countDelivery(
                        connection,
                        scenario.publicationId(),
                        DERIVED_CHANNEL,
                        manualDestination
                    )
                );

                assertEquals(
                    2L,
                    countPublicationOutboxes(
                        connection,
                        scenario.publicationId()
                    )
                );

                /*
                 * A repetição do fan-out também não reserva
                 * uma segunda vaga de quota.
                 */
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
    void shouldRollbackPrimaryAndDerivedWhenFanoutFails()
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
                        connection
                    );

                String manualDestination =
                    uniqueDestination(
                        "rollback"
                    );

                JdbcPublicationOutboxDerivedEnqueueAdapter
                    realDerivedAdapter =
                    new JdbcPublicationOutboxDerivedEnqueueAdapter(
                        connection
                    );

                /*
                 * O wrapper primeiro grava a entrega derivada real
                 * e só depois lança uma falha proposital.
                 *
                 * Portanto, antes do rollback lógico, as duas escritas
                 * realmente chegaram a acontecer dentro da unidade
                 * transacional.
                 */
                PublicationOutboxDerivedEnqueuePort failingDerived =
                    request -> {

                        realDerivedAdapter.enqueue(
                            request
                        );

                        throw new IllegalStateException(
                            "forced fanout failure"
                        );
                    };

                PublicationOutboxFanoutEnqueueService service =
                    new PublicationOutboxFanoutEnqueueService(
                        new JdbcPublicationOutboxEnqueueAdapter(
                            connection
                        ),
                        failingDerived,
                        new JdbcTransactionAdapter(
                            connection
                        ),
                        List.of(
                            new PublicationOutboxDerivedTarget(
                                DERIVED_CHANNEL,
                                manualDestination
                            )
                        )
                    );

                IllegalStateException failure =
                    assertThrows(
                        IllegalStateException.class,
                        () ->
                            service.enqueue(
                                request(
                                    scenario
                                )
                            )
                    );

                assertEquals(
                    "forced fanout failure",
                    failure.getMessage()
                );

                /*
                 * A TransactionPort externa deve ter voltado para
                 * seu savepoint, removendo tanto a principal quanto
                 * a derivada.
                 */
                assertEquals(
                    0L,
                    countDelivery(
                        connection,
                        scenario.publicationId(),
                        PRIMARY_CHANNEL,
                        scenario.primaryDestination()
                    )
                );

                assertEquals(
                    0L,
                    countDelivery(
                        connection,
                        scenario.publicationId(),
                        DERIVED_CHANNEL,
                        manualDestination
                    )
                );

                assertEquals(
                    0L,
                    countPublicationOutboxes(
                        connection,
                        scenario.publicationId()
                    )
                );

                assertEquals(
                    0L,
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

    private PublicationOutboxFanoutEnqueueService createRealService(
        Connection connection,
        String manualDestination
    ) {

        return new PublicationOutboxFanoutEnqueueService(
            new JdbcPublicationOutboxEnqueueAdapter(
                connection
            ),
            new JdbcPublicationOutboxDerivedEnqueueAdapter(
                connection
            ),
            new JdbcTransactionAdapter(
                connection
            ),
            List.of(
                new PublicationOutboxDerivedTarget(
                    DERIVED_CHANNEL,
                    manualDestination
                )
            )
        );
    }

    private PublicationOutboxEnqueueRequest request(
        Scenario scenario
    ) {

        return new PublicationOutboxEnqueueRequest(
            scenario.publicationId(),
            scenario.selectionRunId(),
            AVAILABLE_AT,
            NOW
        );
    }

    private Scenario createScenario(
        Connection connection
    ) throws Exception {

        int sequence =
            SEQUENCE.incrementAndGet();

        String asin =
            "B0D3B"
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
            🔹**Produto D3B**
            💰 De ~~R$ 129,90~~ por **R$ 99,90**!
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/%s
            """
                .formatted(
                    asin
                )
                .strip();

        insertProfiles(
            connection,
            primaryDestination
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
                primaryDestination
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
                content,
                asin
            );

        return new Scenario(
            publicationId,
            selectionRunId,
            primaryDestination,
            content
        );
    }

    private void insertProfiles(
        Connection connection,
        String destination
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

        String quotaSql =
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
                     quotaSql
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
                "Produto D3B " + asin
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
                'publication-fanout-transaction-test'
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

                PersistedOutbox outbox =
                    new PersistedOutbox(
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

                assertTrue(
                    !resultSet.next()
                );

                return outbox;
            }
        }
    }

    private long findOutboxId(
        Connection connection,
        long publicationId,
        String channel,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT id
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

                assertTrue(
                    resultSet.next()
                );

                long id =
                    resultSet.getLong(
                        "id"
                    );

                assertTrue(
                    !resultSet.next()
                );

                return id;
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

        return "@d3b_"
            + prefix
            + "_"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }

    private record Scenario(
        long publicationId,
        long selectionRunId,
        String primaryDestination,
        String content
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
