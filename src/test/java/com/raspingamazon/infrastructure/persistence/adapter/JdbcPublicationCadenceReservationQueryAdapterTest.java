package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationCadenceReservationQueryAdapterTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            30
        );

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    @Test
    void shouldReturnGreatestAvailableAtFromQuotaBearingReservations()
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
                        "greatest"
                    );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination
                    );

                /*
                 * Reserva primária 1.
                 */
                insertOutbox(
                    connection,
                    createPublication(
                        connection,
                        "B0CAD00001",
                        "Oferta 1"
                    ),
                    scenario.selectionRunId(),
                    1,
                    CHANNEL,
                    destination,
                    "QUOTA_CADENCE_TEST_V1",
                    QUOTA_DATE,
                    OffsetDateTime.parse(
                        "2026-09-30T11:00:00-03:00"
                    )
                );

                /*
                 * Reserva primária 2.
                 *
                 * Deve ser devolvida porque possui o maior
                 * available_at.
                 */
                OffsetDateTime expected =
                    OffsetDateTime.parse(
                        "2026-09-30T17:00:00-03:00"
                    );

                insertOutbox(
                    connection,
                    createPublication(
                        connection,
                        "B0CAD00002",
                        "Oferta 2"
                    ),
                    scenario.selectionRunId(),
                    2,
                    CHANNEL,
                    destination,
                    "QUOTA_CADENCE_TEST_V1",
                    QUOTA_DATE,
                    expected
                );

                /*
                 * Outra reserva primária com ID criado depois,
                 * porém available_at menor.
                 *
                 * Prova que a consulta usa MAX(available_at)
                 * em vez de "último id".
                 */
                insertOutbox(
                    connection,
                    createPublication(
                        connection,
                        "B0CAD00003",
                        "Oferta 3"
                    ),
                    scenario.selectionRunId(),
                    3,
                    CHANNEL,
                    destination,
                    "QUOTA_CADENCE_TEST_V1",
                    QUOTA_DATE,
                    OffsetDateTime.parse(
                        "2026-09-30T15:00:00-03:00"
                    )
                );

                JdbcPublicationCadenceReservationQueryAdapter adapter =
                    new JdbcPublicationCadenceReservationQueryAdapter(
                        connection
                    );

                Optional<OffsetDateTime> result =
                    adapter.findLastReservedAvailableAt(
                        CHANNEL,
                        destination,
                        QUOTA_DATE
                    );

                assertTrue(
                    result.isPresent()
                );

                assertEquals(
                    expected.toInstant(),
                    result.orElseThrow()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldIgnoreRowsThatDoNotReserveQuota()
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
                        "derived"
                    );

                Scenario scenario =
                    createScenario(
                        connection,
                        destination
                    );

                OffsetDateTime expected =
                    OffsetDateTime.parse(
                        "2026-09-30T14:00:00-03:00"
                    );

                /*
                 * Reserva primária real.
                 */
                insertOutbox(
                    connection,
                    createPublication(
                        connection,
                        "B0CAD00004",
                        "Oferta primária"
                    ),
                    scenario.selectionRunId(),
                    1,
                    CHANNEL,
                    destination,
                    "QUOTA_CADENCE_TEST_V1",
                    QUOTA_DATE,
                    expected
                );

                /*
                 * Linha sem quota com horário muito mais distante.
                 *
                 * Ela simula a semântica das entregas derivadas
                 * introduzidas na FASE 19:
                 *
                 * quota_profile_version = NULL
                 * quota_date = NULL
                 *
                 * Mesmo tendo available_at maior, não pode afetar
                 * a cadência primária.
                 */
                insertNonQuotaOutbox(
                    connection,
                    createPublication(
                        connection,
                        "B0CAD00005",
                        "Oferta derivada"
                    ),
                    scenario.selectionRunId(),
                    2,
                    CHANNEL,
                    destination,
                    OffsetDateTime.parse(
                        "2026-10-01T00:00:00-03:00"
                    )
                );

                JdbcPublicationCadenceReservationQueryAdapter adapter =
                    new JdbcPublicationCadenceReservationQueryAdapter(
                        connection
                    );

                Optional<OffsetDateTime> result =
                    adapter.findLastReservedAvailableAt(
                        CHANNEL,
                        destination,
                        QUOTA_DATE
                    );

                assertEquals(
                    expected.toInstant(),
                    result.orElseThrow()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRespectChannelDestinationAndQuotaDateScope()
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
                        "scope"
                    );

                String anotherDestination =
                    uniqueDestination(
                        "other"
                    );

                Scenario requestedScenario =
                    createScenario(
                        connection,
                        destination
                    );

                Scenario anotherScenario =
                    createScenario(
                        connection,
                        anotherDestination
                    );

                OffsetDateTime expected =
                    OffsetDateTime.parse(
                        "2026-09-30T16:00:00-03:00"
                    );

                insertOutbox(
                    connection,
                    createPublication(
                        connection,
                        "B0CAD00006",
                        "Oferta correta"
                    ),
                    requestedScenario.selectionRunId(),
                    1,
                    CHANNEL,
                    destination,
                    "QUOTA_CADENCE_TEST_V1",
                    QUOTA_DATE,
                    expected
                );

                /*
                 * Mesmo dia, outro destino.
                 */
                insertOutbox(
                    connection,
                    createPublication(
                        connection,
                        "B0CAD00007",
                        "Oferta outro destino"
                    ),
                    anotherScenario.selectionRunId(),
                    1,
                    CHANNEL,
                    anotherDestination,
                    "QUOTA_CADENCE_TEST_V1",
                    QUOTA_DATE,
                    OffsetDateTime.parse(
                        "2026-09-30T21:00:00-03:00"
                    )
                );

                JdbcPublicationCadenceReservationQueryAdapter adapter =
                    new JdbcPublicationCadenceReservationQueryAdapter(
                        connection
                    );

                assertEquals(
                    expected.toInstant(),
                    adapter.findLastReservedAvailableAt(
                            CHANNEL,
                            destination,
                            QUOTA_DATE
                        )
                        .orElseThrow()
                        .toInstant()
                );

                assertTrue(
                    adapter.findLastReservedAvailableAt(
                            CHANNEL,
                            destination,
                            QUOTA_DATE.plusDays(
                                1L
                            )
                        )
                        .isEmpty()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReturnEmptyWhenNoQuotaReservationExists()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcPublicationCadenceReservationQueryAdapter adapter =
                new JdbcPublicationCadenceReservationQueryAdapter(
                    connection
                );

            Optional<OffsetDateTime> result =
                adapter.findLastReservedAvailableAt(
                    CHANNEL,
                    uniqueDestination(
                        "empty"
                    ),
                    QUOTA_DATE
                );

            assertTrue(
                result.isEmpty()
            );
        }
    }

    @Test
    void shouldRejectInvalidScopeBeforeQuery()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcPublicationCadenceReservationQueryAdapter adapter =
                new JdbcPublicationCadenceReservationQueryAdapter(
                    connection
                );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    adapter.findLastReservedAvailableAt(
                        "   ",
                        "@destination",
                        QUOTA_DATE
                    )
            );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    adapter.findLastReservedAvailableAt(
                        CHANNEL,
                        "   ",
                        QUOTA_DATE
                    )
            );

            assertThrows(
                NullPointerException.class,
                () ->
                    adapter.findLastReservedAvailableAt(
                        CHANNEL,
                        "@destination",
                        null
                    )
            );
        }
    }

    private Scenario createScenario(
        Connection connection,
        String destination
    ) throws Exception {

        insertSelectionProfile(
            connection,
            destination
        );

        insertQuotaProfile(
            connection,
            destination
        );

        long selectionRunId =
            insertSelectionRun(
                connection,
                destination
            );

        return new Scenario(
            selectionRunId
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
            VALUES (
                ?,
                ?,
                'SELECTION_CADENCE_TEST_V1',
                0,
                0,
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

            assertEquals(
                1,
                statement.executeUpdate()
            );
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
                'QUOTA_CADENCE_TEST_V1',
                20,
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

            assertEquals(
                1,
                statement.executeUpdate()
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
                'SELECTION_CADENCE_TEST_V1',
                0,
                0,
                'QUOTA_CADENCE_TEST_V1',
                ?,
                20,
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
                OffsetDateTime.parse(
                    "2026-09-30T09:00:00-03:00"
                )
            );

            statement.setObject(
                4,
                QUOTA_DATE
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long createPublication(
        Connection connection,
        String asin,
        String text
    ) throws Exception {

        long productId =
            insertProduct(
                connection,
                asin
            );

        long snapshotId =
            insertOfferSnapshot(
                connection,
                productId
            );

        long evaluationId =
            insertEvaluation(
                connection,
                snapshotId
            );

        return insertPublication(
            connection,
            evaluationId,
            text
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
            VALUES (
                ?,
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

            statement.setString(
                1,
                asin
            );

            statement.setString(
                2,
                "Produto " + asin
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/" + asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertOfferSnapshot(
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
                50.00,
                4.8,
                1000,
                'Amazon.com.br',
                'Amazon',
                'cadence-reservation-query-test'
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
                OffsetDateTime.parse(
                    "2026-09-30T08:00:00-03:00"
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

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
                'CADENCE_TEST_ELIGIBILITY_V1',
                90.0000,
                'CADENCE_TEST_SCORE_V1',
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
                OffsetDateTime.parse(
                    "2026-09-30T08:30:00-03:00"
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertPublication(
        Connection connection,
        long evaluationId,
        String text
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
                'CADENCE_TEST_TEMPLATE_V1',
                'CADENCE_TEST_COMMERCIAL_V1',
                'CADENCE_TEST_AFFILIATE_V1',
                ?,
                'https://www.amazon.com.br/?tag=test-20',
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
                text
            );

            statement.setObject(
                3,
                OffsetDateTime.parse(
                    "2026-09-30T08:45:00-03:00"
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void insertOutbox(
        Connection connection,
        long publicationId,
        long selectionRunId,
        int selectionPosition,
        String channel,
        String destination,
        String quotaProfileVersion,
        LocalDate quotaDate,
        OffsetDateTime availableAt
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
                ?,
                ?,
                ?,
                'cadence test content',
                ?,
                ?,
                'PENDING',
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
                selectionRunId
            );

            statement.setInt(
                3,
                selectionPosition
            );

            statement.setString(
                4,
                channel
            );

            statement.setString(
                5,
                destination
            );

            statement.setString(
                6,
                quotaProfileVersion
            );

            statement.setObject(
                7,
                quotaDate
            );

            statement.setObject(
                8,
                availableAt
            );

            statement.setObject(
                9,
                OffsetDateTime.parse(
                    "2026-09-30T09:00:00-03:00"
                )
            );

            statement.setObject(
                10,
                OffsetDateTime.parse(
                    "2026-09-30T09:00:00-03:00"
                )
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private void insertNonQuotaOutbox(
        Connection connection,
        long publicationId,
        long selectionRunId,
        int selectionPosition,
        String channel,
        String destination,
        OffsetDateTime availableAt
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
                ?,
                ?,
                ?,
                'non quota cadence test content',
                NULL,
                NULL,
                'PENDING',
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
                selectionRunId
            );

            statement.setInt(
                3,
                selectionPosition
            );

            statement.setString(
                4,
                channel
            );

            statement.setString(
                5,
                destination
            );

            statement.setObject(
                6,
                availableAt
            );

            statement.setObject(
                7,
                OffsetDateTime.parse(
                    "2026-09-30T09:00:00-03:00"
                )
            );

            statement.setObject(
                8,
                OffsetDateTime.parse(
                    "2026-09-30T09:00:00-03:00"
                )
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private String uniqueDestination(
        String suffix
    ) {

        return "@cadence-reservation-"
            + suffix
            + "-"
            + SEQUENCE.incrementAndGet()
            + "-"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }

    private record Scenario(
        long selectionRunId
    ) {
    }
}
