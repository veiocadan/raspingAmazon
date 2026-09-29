package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.selection.PublicationQuotaSnapshot;
import com.raspingamazon.domain.publication.selection.PublicationSelectionCandidate;
import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionResult;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationSelectionAuditRepositoryTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final Instant NOW =
        Instant.parse(
            "2026-09-27T20:00:00Z"
        );

    private static final ZoneId QUOTA_ZONE =
        ZoneId.of(
            "America/Sao_Paulo"
        );

    private static final PublicationSelectionProfile PROFILE =
        new PublicationSelectionProfile(
            PublicationSelectionPolicy.VERSION,
            Duration.ofDays(
                2
            ),
            Duration.ofDays(
                7
            )
        );

    @Test
    void shouldPersistSelectionRunAndAllDecisions()
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
                        "persist"
                    );

                insertProfiles(
                    connection,
                    destination
                );

                long firstEvaluationId =
                    insertEvaluation(
                        connection,
                        "B0AUD18001",
                        "90.0000",
                        NOW.minus(
                            Duration.ofHours(
                                3
                            )
                        )
                    );

                long secondEvaluationId =
                    insertEvaluation(
                        connection,
                        "B0AUD18002",
                        "80.0000",
                        NOW.minus(
                            Duration.ofHours(
                                2
                            )
                        )
                    );

                long thirdEvaluationId =
                    insertEvaluation(
                        connection,
                        "B0AUD18003",
                        "70.0000",
                        NOW.minus(
                            Duration.ofHours(
                                1
                            )
                        )
                    );

                PublicationSelectionCandidate first =
                    new PublicationSelectionCandidate(
                        firstEvaluationId,
                        new Asin(
                            "B0AUD18001"
                        ),
                        new BigDecimal(
                            "90.0000"
                        ),
                        CHANNEL,
                        destination,
                        null
                    );

                Asin secondAsin =
                    new Asin(
                        "B0AUD18002"
                    );

                PublicationSelectionCandidate second =
                    new PublicationSelectionCandidate(
                        secondEvaluationId,
                        secondAsin,
                        new BigDecimal(
                            "80.0000"
                        ),
                        CHANNEL,
                        destination,
                        new SuccessfulPublicationHistory(
                            secondAsin,
                            NOW.minus(
                                Duration.ofDays(
                                    20
                                )
                            ),
                            3L
                        )
                    );

                Asin thirdAsin =
                    new Asin(
                        "B0AUD18003"
                    );

                PublicationSelectionCandidate third =
                    new PublicationSelectionCandidate(
                        thirdEvaluationId,
                        thirdAsin,
                        new BigDecimal(
                            "70.0000"
                        ),
                        CHANNEL,
                        destination,
                        new SuccessfulPublicationHistory(
                            thirdAsin,
                            NOW.minus(
                                Duration.ofDays(
                                    1
                                )
                            ),
                            1L
                        )
                    );

                PublicationQuotaSnapshot quotaSnapshot =
                    quotaSnapshot(
                        destination,
                        2,
                        1L,
                        LocalDate.of(
                            2026,
                            9,
                            27
                        )
                    );

                PublicationSelectionResult result =
                    new PublicationSelectionPolicy()
                        .select(
                            List.of(
                                third,
                                second,
                                first
                            ),
                            PROFILE,
                            quotaSnapshot,
                            NOW
                        );

                long runId =
                    new JdbcPublicationSelectionAuditRepository(
                        connection
                    ).save(
                        result
                    );

                assertTrue(
                    runId > 0L
                );

                PersistedRun persistedRun =
                    loadRun(
                        connection,
                        runId
                    );

                assertEquals(
                    CHANNEL,
                    persistedRun.channel()
                );

                assertEquals(
                    destination,
                    persistedRun.destination()
                );

                assertEquals(
                    NOW,
                    persistedRun.decidedAt()
                );

                assertEquals(
                    PublicationSelectionPolicy.VERSION,
                    persistedRun.selectionProfileVersion()
                );

                assertEquals(
                    Duration.ofDays(
                        2
                    ).getSeconds(),
                    persistedRun.hardCooldownSeconds()
                );

                assertEquals(
                    Duration.ofDays(
                        7
                    ).getSeconds(),
                    persistedRun.preferredCooldownSeconds()
                );

                assertEquals(
                    "PUBLICATION_QUOTA_V1",
                    persistedRun.quotaProfileVersion()
                );

                assertEquals(
                    LocalDate.of(
                        2026,
                        9,
                        27
                    ),
                    persistedRun.quotaDate()
                );

                assertEquals(
                    2,
                    persistedRun.maxPublicationsPerDay()
                );

                assertEquals(
                    1L,
                    persistedRun.occupiedSlots()
                );

                Map<Long, PersistedDecision> decisions =
                    loadDecisions(
                        connection,
                        runId
                    );

                assertEquals(
                    3,
                    decisions.size()
                );

                PersistedDecision firstDecision =
                    decisions.get(
                        firstEvaluationId
                    );

                assertEquals(
                    "SELECTED",
                    firstDecision.status()
                );

                assertEquals(
                    "NEVER_SUCCESSFULLY_PUBLISHED",
                    firstDecision.recency()
                );

                assertEquals(
                    1,
                    firstDecision.priorityPosition()
                );

                assertNull(
                    firstDecision.lastSuccessfulPublicationAt()
                );

                assertEquals(
                    0L,
                    firstDecision.successfulPublicationCount()
                );

                PersistedDecision secondDecision =
                    decisions.get(
                        secondEvaluationId
                    );

                assertEquals(
                    "NOT_SELECTED_DUE_TO_QUOTA",
                    secondDecision.status()
                );

                assertEquals(
                    "OUTSIDE_PREFERRED_COOLDOWN",
                    secondDecision.recency()
                );

                assertEquals(
                    2,
                    secondDecision.priorityPosition()
                );

                assertEquals(
                    NOW.minus(
                        Duration.ofDays(
                            20
                        )
                    ),
                    secondDecision.lastSuccessfulPublicationAt()
                );

                assertEquals(
                    3L,
                    secondDecision.successfulPublicationCount()
                );

                PersistedDecision thirdDecision =
                    decisions.get(
                        thirdEvaluationId
                    );

                assertEquals(
                    "DEFERRED_DUE_TO_HARD_COOLDOWN",
                    thirdDecision.status()
                );

                assertEquals(
                    "INSIDE_HARD_COOLDOWN",
                    thirdDecision.recency()
                );

                assertNull(
                    thirdDecision.priorityPosition()
                );

                assertEquals(
                    NOW.minus(
                        Duration.ofDays(
                            1
                        )
                    ),
                    thirdDecision.lastSuccessfulPublicationAt()
                );

                assertEquals(
                    1L,
                    thirdDecision.successfulPublicationCount()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldPersistEmptySelectionRun()
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
                        "empty"
                    );

                insertProfiles(
                    connection,
                    destination
                );

                PublicationSelectionResult result =
                    new PublicationSelectionPolicy()
                        .select(
                            List.of(),
                            PROFILE,
                            quotaSnapshot(
                                destination,
                                2,
                                0L,
                                LocalDate.of(
                                    2026,
                                    9,
                                    27
                                )
                            ),
                            NOW
                        );

                long runId =
                    new JdbcPublicationSelectionAuditRepository(
                        connection
                    ).save(
                        result
                    );

                assertTrue(
                    runId > 0L
                );

                assertEquals(
                    0L,
                    countDecisions(
                        connection,
                        runId
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectCandidateWithAsinDifferentFromEvaluation()
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
                        "asin"
                    );

                insertProfiles(
                    connection,
                    destination
                );

                long evaluationId =
                    insertEvaluation(
                        connection,
                        "B0AUD18004",
                        "90.0000",
                        NOW
                    );

                PublicationSelectionCandidate candidate =
                    new PublicationSelectionCandidate(
                        evaluationId,
                        new Asin(
                            "B0WRONG001"
                        ),
                        new BigDecimal(
                            "90.0000"
                        ),
                        CHANNEL,
                        destination,
                        null
                    );

                PublicationSelectionResult result =
                    new PublicationSelectionPolicy()
                        .select(
                            List.of(
                                candidate
                            ),
                            PROFILE,
                            quotaSnapshot(
                                destination,
                                2,
                                0L,
                                LocalDate.of(
                                    2026,
                                    9,
                                    27
                                )
                            ),
                            NOW
                        );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        new JdbcPublicationSelectionAuditRepository(
                            connection
                        ).save(
                            result
                        )
                );

                assertEquals(
                    0L,
                    countRuns(
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
    void shouldRejectCandidateWithScoreDifferentFromEvaluation()
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
                        "score"
                    );

                insertProfiles(
                    connection,
                    destination
                );

                long evaluationId =
                    insertEvaluation(
                        connection,
                        "B0AUD18005",
                        "90.0000",
                        NOW
                    );

                PublicationSelectionCandidate candidate =
                    new PublicationSelectionCandidate(
                        evaluationId,
                        new Asin(
                            "B0AUD18005"
                        ),
                        new BigDecimal(
                            "80.0000"
                        ),
                        CHANNEL,
                        destination,
                        null
                    );

                PublicationSelectionResult result =
                    new PublicationSelectionPolicy()
                        .select(
                            List.of(
                                candidate
                            ),
                            PROFILE,
                            quotaSnapshot(
                                destination,
                                2,
                                0L,
                                LocalDate.of(
                                    2026,
                                    9,
                                    27
                                )
                            ),
                            NOW
                        );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        new JdbcPublicationSelectionAuditRepository(
                            connection
                        ).save(
                            result
                        )
                );

                assertEquals(
                    0L,
                    countRuns(
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
    void shouldRejectSelectionProfileValuesDifferentFromPersistedVersion()
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
                        "profile"
                    );

                insertProfiles(
                    connection,
                    destination
                );

                PublicationSelectionProfile differentProfile =
                    new PublicationSelectionProfile(
                        PublicationSelectionPolicy.VERSION,
                        Duration.ofDays(
                            3
                        ),
                        Duration.ofDays(
                            7
                        )
                    );

                PublicationSelectionResult result =
                    new PublicationSelectionPolicy()
                        .select(
                            List.of(),
                            differentProfile,
                            quotaSnapshot(
                                destination,
                                2,
                                0L,
                                LocalDate.of(
                                    2026,
                                    9,
                                    27
                                )
                            ),
                            NOW
                        );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        new JdbcPublicationSelectionAuditRepository(
                            connection
                        ).save(
                            result
                        )
                );

                assertEquals(
                    0L,
                    countRuns(
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
    void shouldRejectQuotaDateDifferentFromPersistedQuotaZone()
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
                        "date"
                    );

                insertProfiles(
                    connection,
                    destination
                );

                PublicationSelectionResult result =
                    new PublicationSelectionPolicy()
                        .select(
                            List.of(),
                            PROFILE,
                            quotaSnapshot(
                                destination,
                                2,
                                0L,
                                LocalDate.of(
                                    2026,
                                    9,
                                    26
                                )
                            ),
                            NOW
                        );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        new JdbcPublicationSelectionAuditRepository(
                            connection
                        ).save(
                            result
                        )
                );

                assertEquals(
                    0L,
                    countRuns(
                        connection,
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private String uniqueDestination(
        String suffix
    ) {

        return "@phase18-"
            + suffix
            + "-"
            + System.nanoTime();
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
            VALUES (?, ?, ?, ?, ?, true)
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
                PublicationSelectionPolicy.VERSION
            );

            statement.setLong(
                4,
                Duration.ofDays(
                    2
                ).getSeconds()
            );

            statement.setLong(
                5,
                Duration.ofDays(
                    7
                ).getSeconds()
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
            VALUES (?, ?, ?, ?, ?, true)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     quotaSql
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
                "PUBLICATION_QUOTA_V1"
            );

            statement.setInt(
                4,
                2
            );

            statement.setString(
                5,
                QUOTA_ZONE.getId()
            );

            statement.executeUpdate();
        }
    }

    private long insertEvaluation(
        Connection connection,
        String asin,
        String score,
        Instant collectedAt
    ) throws Exception {

        long productId =
            insertProduct(
                connection,
                asin
            );

        long snapshotId =
            insertSnapshot(
                connection,
                productId,
                collectedAt
            );

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
                ?,
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

            statement.setBigDecimal(
                2,
                new BigDecimal(
                    score
                )
            );

            statement.setObject(
                3,
                OffsetDateTime.ofInstant(
                    collectedAt.plusSeconds(
                        60L
                    ),
                    java.time.ZoneOffset.UTC
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
                "Publication selection audit test "
                    + asin
            );

            statement.setString(
                3,
                "https://example.invalid/"
                    + asin
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

    private long insertSnapshot(
        Connection connection,
        long productId,
        Instant collectedAt
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
                'publication-selection-audit-test'
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
                OffsetDateTime.ofInstant(
                    collectedAt,
                    java.time.ZoneOffset.UTC
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

    private PublicationQuotaSnapshot quotaSnapshot(
        String destination,
        int maximum,
        long occupied,
        LocalDate quotaDate
    ) {

        return new PublicationQuotaSnapshot(
            CHANNEL,
            destination,
            quotaDate,
            "PUBLICATION_QUOTA_V1",
            maximum,
            occupied
        );
    }

    private PersistedRun loadRun(
        Connection connection,
        long runId
    ) throws Exception {

        String sql =
            """
            SELECT
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
            FROM publication_selection_run
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                runId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return new PersistedRun(
                    resultSet.getString(
                        "channel"
                    ),
                    resultSet.getString(
                        "destination"
                    ),
                    resultSet.getObject(
                        "decided_at",
                        OffsetDateTime.class
                    ).toInstant(),
                    resultSet.getString(
                        "selection_profile_version"
                    ),
                    resultSet.getLong(
                        "hard_cooldown_seconds"
                    ),
                    resultSet.getLong(
                        "preferred_cooldown_seconds"
                    ),
                    resultSet.getString(
                        "quota_profile_version"
                    ),
                    resultSet.getObject(
                        "quota_date",
                        LocalDate.class
                    ),
                    resultSet.getInt(
                        "max_publications_per_day"
                    ),
                    resultSet.getLong(
                        "occupied_slots"
                    )
                );
            }
        }
    }

    private Map<Long, PersistedDecision> loadDecisions(
        Connection connection,
        long runId
    ) throws Exception {

        String sql =
            """
            SELECT
                deal_evaluation_id,
                asin,
                score,
                decision_status,
                recency,
                priority_position,
                last_successful_publication_at,
                successful_publication_count
            FROM publication_selection_decision
            WHERE selection_run_id = ?
            """;

        Map<Long, PersistedDecision> decisions =
            new HashMap<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                runId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    long evaluationId =
                        resultSet.getLong(
                            "deal_evaluation_id"
                        );

                    decisions.put(
                        evaluationId,
                        new PersistedDecision(
                            resultSet.getString(
                                "asin"
                            ),
                            resultSet.getBigDecimal(
                                "score"
                            ),
                            resultSet.getString(
                                "decision_status"
                            ),
                            resultSet.getString(
                                "recency"
                            ),
                            nullableInteger(
                                resultSet,
                                "priority_position"
                            ),
                            nullableInstant(
                                resultSet,
                                "last_successful_publication_at"
                            ),
                            resultSet.getLong(
                                "successful_publication_count"
                            )
                        )
                    );
                }
            }
        }

        return decisions;
    }

    private long countDecisions(
        Connection connection,
        long runId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS row_count
            FROM publication_selection_decision
            WHERE selection_run_id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                runId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "row_count"
                );
            }
        }
    }

    private long countRuns(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS row_count
            FROM publication_selection_run
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

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "row_count"
                );
            }
        }
    }

    private Integer nullableInteger(
        ResultSet resultSet,
        String column
    ) throws Exception {

        int value =
            resultSet.getInt(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private Instant nullableInstant(
        ResultSet resultSet,
        String column
    ) throws Exception {

        OffsetDateTime value =
            resultSet.getObject(
                column,
                OffsetDateTime.class
            );

        if (value == null) {
            return null;
        }

        return value.toInstant();
    }

    private record PersistedRun(
        String channel,
        String destination,
        Instant decidedAt,
        String selectionProfileVersion,
        long hardCooldownSeconds,
        long preferredCooldownSeconds,
        String quotaProfileVersion,
        LocalDate quotaDate,
        int maxPublicationsPerDay,
        long occupiedSlots
    ) {
    }

    private record PersistedDecision(
        String asin,
        BigDecimal score,
        String status,
        String recency,
        Integer priorityPosition,
        Instant lastSuccessfulPublicationAt,
        long successfulPublicationCount
    ) {

        private PersistedDecision {

            assertNotNull(
                asin
            );

            assertNotNull(
                score
            );
        }
    }
}
