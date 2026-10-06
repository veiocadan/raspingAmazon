package com.raspingamazon.infrastructure.resilience;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fixture PostgreSQL específica dos testes destrutivos da FASE 20.
 *
 * <p>Ela cria uma publication_outbox já em PROCESSING e uma
 * publication_attempt em STARTED, representando a fotografia
 * persistida deixada por um processo que morreu depois do start
 * barrier e antes da conclusão da chamada externa.</p>
 */
final class Phase20PublicationCrashFixture {

    static final String CHANNEL =
        "TELEGRAM";

    static final String SELECTION_VERSION =
        "PUBLICATION_SELECTION_V1";

    static final String QUOTA_VERSION =
        "PUBLICATION_QUOTA_V1";

    private Phase20PublicationCrashFixture() {
    }

    static Scenario create(
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
            "@phase20_destructive_"
                + token;

        String asin =
            "B0H"
                + token.substring(
                    0,
                    7
                ).toUpperCase();

        LocalDate quotaDate =
            lockedAt.toLocalDate();

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
                productId,
                lockedAt
            );

        long evaluationId =
            insertEvaluation(
                connection,
                snapshotId,
                lockedAt
            );

        long selectionRunId =
            insertSelectionRun(
                connection,
                destination,
                quotaDate,
                lockedAt
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
                asin,
                lockedAt
            );

        long outboxId =
            insertProcessingOutbox(
                connection,
                publicationId,
                selectionRunId,
                destination,
                asin,
                quotaDate,
                lockedAt
            );

        long attemptId =
            insertStartedAttempt(
                connection,
                publicationId,
                outboxId,
                destination,
                lockedAt.plusSeconds(
                    5
                )
            );

        return new Scenario(
            outboxId,
            publicationId,
            attemptId,
            destination
        );
    }

    private static void insertSelectionProfile(
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

    private static void insertQuotaProfile(
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

    private static long insertProduct(
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
                "Phase 20 destructive "
                    + asin
            );

            statement.setString(
                3,
                "https://example.invalid/"
                    + asin
            );

            return returnedId(
                statement
            );
        }
    }

    private static long insertSnapshot(
        Connection connection,
        long productId,
        OffsetDateTime observedAt
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
                'phase-20-destructive-test'
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
                observedAt.minusHours(
                    4
                )
            );

            return returnedId(
                statement
            );
        }
    }

    private static long insertEvaluation(
        Connection connection,
        long snapshotId,
        OffsetDateTime observedAt
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
                observedAt.minusHours(
                    3
                )
            );

            return returnedId(
                statement
            );
        }
    }

    private static long insertSelectionRun(
        Connection connection,
        String destination,
        LocalDate quotaDate,
        OffsetDateTime observedAt
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
                observedAt.minusHours(
                    2
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
                quotaDate
            );

            return returnedId(
                statement
            );
        }
    }

    private static void insertSelectionDecision(
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

    private static long insertPublication(
        Connection connection,
        long evaluationId,
        String asin,
        OffsetDateTime observedAt
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
                observedAt.minusHours(
                    1
                )
            );

            return returnedId(
                statement
            );
        }
    }

    private static long insertProcessingOutbox(
        Connection connection,
        long publicationId,
        long selectionRunId,
        String destination,
        String asin,
        LocalDate quotaDate,
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
                'simulated-dead-publication-worker',
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
                "Oferta "
                    + asin
            );

            statement.setString(
                6,
                QUOTA_VERSION
            );

            statement.setObject(
                7,
                quotaDate
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

            return returnedId(
                statement
            );
        }
    }

    private static long insertStartedAttempt(
        Connection connection,
        long publicationId,
        long outboxId,
        String destination,
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
                startedAt
            );

            statement.setObject(
                6,
                startedAt
            );

            return returnedId(
                statement
            );
        }
    }

    private static long returnedId(
        PreparedStatement statement
    ) throws Exception {

        try (ResultSet resultSet =
                 statement.executeQuery()) {

            if (!resultSet.next()) {

                throw new IllegalStateException(
                    "INSERT returned no id"
                );
            }

            return resultSet.getLong(
                "id"
            );
        }
    }

    record Scenario(
        long outboxId,
        long publicationId,
        long attemptId,
        String destination
    ) {
    }
}
