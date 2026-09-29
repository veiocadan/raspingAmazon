package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxQueueAdapterTest {

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
    void shouldClaimAvailablePendingOutbox()
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
                        "claim"
                    );

                TestScope scope =
                    createScope(
                        connection,
                        destination
                    );

                long outboxId =
                    createOutbox(
                        connection,
                        scope,
                        "B0CLM18001",
                        1,
                        NOW.minusMinutes(
                            1
                        ),
                        "PENDING",
                        null,
                        null
                    );

                JdbcPublicationOutboxQueueAdapter adapter =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    );

                PublicationOutboxItem claimed =
                    adapter.claimNext(
                            "worker-a",
                            NOW
                        )
                        .orElseThrow();

                assertEquals(
                    outboxId,
                    claimed.id()
                );

                assertEquals(
                    PublicationOutboxStatus.PROCESSING,
                    claimed.status()
                );

                assertEquals(
                    NOW.toInstant(),
                    claimed.lockedAt()
                        .toInstant()
                );

                assertEquals(
                    "worker-a",
                    claimed.lockedBy()
                );

                assertEquals(
                    "TELEGRAM",
                    claimed.command()
                        .channel()
                );

                assertEquals(
                    destination,
                    claimed.command()
                        .destination()
                );

                assertEquals(
                    "Oferta B0CLM18001",
                    claimed.command()
                        .content()
                );

                assertEquals(
                    "PROCESSING",
                    loadStatus(
                        connection,
                        outboxId
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldNotClaimOutboxBeforeAvailableAt()
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
                        "future"
                    );

                TestScope scope =
                    createScope(
                        connection,
                        destination
                    );

                long outboxId =
                    createOutbox(
                        connection,
                        scope,
                        "B0CLM18002",
                        1,
                        NOW.plusHours(
                            1
                        ),
                        "PENDING",
                        null,
                        null
                    );

                Optional<PublicationOutboxItem> result =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    ).claimNext(
                        "worker-a",
                        NOW
                    );

                assertTrue(
                    result.isEmpty()
                );

                assertEquals(
                    "PENDING",
                    loadStatus(
                        connection,
                        outboxId
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldPreserveSelectionPositionOrderWithinRun()
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
                        "order"
                    );

                TestScope scope =
                    createScope(
                        connection,
                        destination
                    );

                /*
                 * Inserimos posição 2 antes da posição 1
                 * propositalmente.
                 *
                 * O id da linha não pode derrotar a ordem produzida
                 * pela seleção.
                 */
                createOutbox(
                    connection,
                    scope,
                    "B0CLM18003",
                    2,
                    NOW.minusMinutes(
                        1
                    ),
                    "PENDING",
                    null,
                    null
                );

                long firstPositionId =
                    createOutbox(
                        connection,
                        scope,
                        "B0CLM18004",
                        1,
                        NOW.minusMinutes(
                            1
                        ),
                        "PENDING",
                        null,
                        null
                    );

                PublicationOutboxItem claimed =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    ).claimNext(
                        "worker-a",
                        NOW
                    ).orElseThrow();

                assertEquals(
                    firstPositionId,
                    claimed.id()
                );

                assertEquals(
                    1,
                    claimed.selectionPosition()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRecoverExpiredProcessingLease()
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
                        "recover"
                    );

                TestScope scope =
                    createScope(
                        connection,
                        destination
                    );

                long outboxId =
                    createOutbox(
                        connection,
                        scope,
                        "B0CLM18005",
                        1,
                        NOW.minusHours(
                            2
                        ),
                        "PROCESSING",
                        NOW.minusHours(
                            2
                        ),
                        "dead-worker"
                    );

                JdbcPublicationOutboxQueueAdapter adapter =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    );

                int recovered =
                    adapter.recoverExpiredLeases(
                        NOW.minusHours(
                            1
                        ),
                        NOW
                    );

                assertEquals(
                    1,
                    recovered
                );

                assertEquals(
                    "PENDING",
                    loadStatus(
                        connection,
                        outboxId
                    )
                );

                assertLockCleared(
                    connection,
                    outboxId
                );

                PublicationOutboxItem reclaimed =
                    adapter.claimNext(
                            "replacement-worker",
                            NOW.plusSeconds(
                                1
                            )
                        )
                        .orElseThrow();

                assertEquals(
                    outboxId,
                    reclaimed.id()
                );

                assertEquals(
                    "replacement-worker",
                    reclaimed.lockedBy()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldNotRecoverRecentProcessingLease()
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
                        "recent"
                    );

                TestScope scope =
                    createScope(
                        connection,
                        destination
                    );

                long outboxId =
                    createOutbox(
                        connection,
                        scope,
                        "B0CLM18006",
                        1,
                        NOW.minusHours(
                            1
                        ),
                        "PROCESSING",
                        NOW.minusMinutes(
                            30
                        ),
                        "healthy-worker"
                    );

                int recovered =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    ).recoverExpiredLeases(
                        NOW.minusHours(
                            1
                        ),
                        NOW
                    );

                assertEquals(
                    0,
                    recovered
                );

                assertEquals(
                    "PROCESSING",
                    loadStatus(
                        connection,
                        outboxId
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private TestScope createScope(
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

        return new TestScope(
            destination,
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Selection run insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long createOutbox(
        Connection connection,
        TestScope scope,
        String asin,
        int position,
        OffsetDateTime availableAt,
        String status,
        OffsetDateTime lockedAt,
        String lockedBy
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

        insertDecision(
            connection,
            scope.selectionRunId(),
            evaluationId,
            asin,
            position
        );

        long publicationId =
            insertPublication(
                connection,
                evaluationId,
                asin
            );

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
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
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
                scope.selectionRunId()
            );

            statement.setInt(
                3,
                position
            );

            statement.setString(
                4,
                CHANNEL
            );

            statement.setString(
                5,
                scope.destination()
            );

            statement.setString(
                6,
                "Oferta " + asin
            );

            statement.setString(
                7,
                QUOTA_VERSION
            );

            statement.setObject(
                8,
                QUOTA_DATE
            );

            statement.setString(
                9,
                status
            );

            statement.setObject(
                10,
                availableAt
            );

            statement.setObject(
                11,
                lockedAt
            );

            statement.setString(
                12,
                lockedBy
            );

            statement.setObject(
                13,
                NOW.minusHours(
                    3
                )
            );

            statement.setObject(
                14,
                lockedAt != null
                    ? lockedAt
                    : NOW.minusHours(
                    3
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Outbox insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
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
                "Claim test " + asin
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
                'publication-outbox-claim-test'
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

    private void insertDecision(
        Connection connection,
        long selectionRunId,
        long evaluationId,
        String asin,
        int position
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

            statement.setInt(
                4,
                position
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

    private String loadStatus(
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

    private void assertLockCleared(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT
                locked_at,
                locked_by
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

                assertEquals(
                    null,
                    resultSet.getObject(
                        "locked_at"
                    )
                );

                assertEquals(
                    null,
                    resultSet.getString(
                        "locked_by"
                    )
                );
            }
        }
    }

    private String uniqueDestination(
        String suffix
    ) {

        return "@outbox-claim-"
            + suffix
            + "-"
            + System.nanoTime();
    }

    private record TestScope(
        String destination,
        long selectionRunId
    ) {
    }
}
