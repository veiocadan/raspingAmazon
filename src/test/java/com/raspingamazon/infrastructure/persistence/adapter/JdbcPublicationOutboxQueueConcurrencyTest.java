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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxQueueConcurrencyTest {

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
    void shouldAllowOnlyOneWorkerToClaimSinglePendingOutbox()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData testData =
            null;

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        try {

            testData =
                createCommittedTestData(
                    config
                );

            CountDownLatch workersReady =
                new CountDownLatch(
                    2
                );

            CountDownLatch startSignal =
                new CountDownLatch(
                    1
                );

            Future<Optional<PublicationOutboxItem>> firstFuture =
                executor.submit(
                    () ->
                        claimConcurrently(
                            config,
                            "worker-a",
                            workersReady,
                            startSignal
                        )
                );

            Future<Optional<PublicationOutboxItem>> secondFuture =
                executor.submit(
                    () ->
                        claimConcurrently(
                            config,
                            "worker-b",
                            workersReady,
                            startSignal
                        )
                );

            /*
             * Os dois workers precisam estar com conexões independentes
             * prontas antes de iniciarmos a disputa.
             */
            assertTrue(
                workersReady.await(
                    10,
                    TimeUnit.SECONDS
                ),
                "Both outbox workers must become ready"
            );

            startSignal.countDown();

            Optional<PublicationOutboxItem> firstResult =
                firstFuture.get(
                    10,
                    TimeUnit.SECONDS
                );

            Optional<PublicationOutboxItem> secondResult =
                secondFuture.get(
                    10,
                    TimeUnit.SECONDS
                );

            List<Optional<PublicationOutboxItem>> results =
                List.of(
                    firstResult,
                    secondResult
                );

            long claimedCount =
                results.stream()
                    .filter(
                        Optional::isPresent
                    )
                    .count();

            long emptyCount =
                results.stream()
                    .filter(
                        Optional::isEmpty
                    )
                    .count();

            assertEquals(
                1L,
                claimedCount
            );

            assertEquals(
                1L,
                emptyCount
            );

            PublicationOutboxItem claimed =
                results.stream()
                    .flatMap(
                        Optional::stream
                    )
                    .findFirst()
                    .orElseThrow();

            assertEquals(
                testData.outboxId(),
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

            assertTrue(
                Set.of(
                    "worker-a",
                    "worker-b"
                ).contains(
                    claimed.lockedBy()
                )
            );

            /*
             * Verificamos também o estado persistente.
             *
             * Não basta os objetos Java estarem corretos:
             * PostgreSQL é a fonte de verdade da fila.
             */
            try (Connection verificationConnection =
                     DatabaseConnection.open(
                         config
                     )) {

                PersistedClaim persisted =
                    loadPersistedClaim(
                        verificationConnection,
                        testData.outboxId()
                    );

                assertEquals(
                    "PROCESSING",
                    persisted.status()
                );

                assertEquals(
                    claimed.lockedBy(),
                    persisted.lockedBy()
                );

                assertEquals(
                    NOW.toInstant(),
                    persisted.lockedAt()
                        .toInstant()
                );

                assertEquals(
                    1L,
                    countProcessingOutbox(
                        verificationConnection,
                        testData.destination()
                    )
                );

                /*
                 * Depois do primeiro claim confirmado, não existe
                 * mais trabalho PENDING para um terceiro worker.
                 */
                Optional<PublicationOutboxItem> thirdClaim =
                    new JdbcPublicationOutboxQueueAdapter(
                        verificationConnection
                    ).claimNext(
                        "worker-c",
                        NOW.plusSeconds(
                            1
                        )
                    );

                assertTrue(
                    thirdClaim.isEmpty()
                );
            }

        } finally {

            executor.shutdownNow();

            cleanup(
                config,
                testData
            );
        }
    }

    private Optional<PublicationOutboxItem> claimConcurrently(
        ApplicationConfig config,
        String workerId,
        CountDownLatch workersReady,
        CountDownLatch startSignal
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            /*
             * claimNext é uma única instrução SQL atômica.
             *
             * autoCommit=true garante que o UPDATE realizado pelo
             * claim seja confirmado quando a instrução termina.
             */
            connection.setAutoCommit(
                true
            );

            JdbcPublicationOutboxQueueAdapter adapter =
                new JdbcPublicationOutboxQueueAdapter(
                    connection
                );

            workersReady.countDown();

            if (!startSignal.await(
                10,
                TimeUnit.SECONDS
            )) {

                throw new IllegalStateException(
                    "Concurrent claim start signal was not released"
                );
            }

            return adapter.claimNext(
                workerId,
                NOW
            );
        }
    }

    private TestData createCommittedTestData(
        ApplicationConfig config
    ) throws Exception {

        String destination =
            uniqueDestination();

        String asin =
            uniqueAsin();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

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
                    insertPendingOutbox(
                        connection,
                        publicationId,
                        selectionRunId,
                        destination,
                        asin
                    );

                connection.commit();

                return new TestData(
                    destination,
                    productId,
                    snapshotId,
                    evaluationId,
                    selectionRunId,
                    publicationId,
                    outboxId
                );

            } catch (Exception exception) {

                connection.rollback();

                throw exception;
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
            VALUES (
                ?,
                ?,
                ?,
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
                "Concurrent claim test " + asin
            );

            statement.setString(
                3,
                "https://example.invalid/" + asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Product insert returned no id"
                    );
                }

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
                'publication-outbox-claim-concurrency-test'
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "OfferSnapshot insert returned no id"
                    );
                }

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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "DealEvaluation insert returned no id"
                    );
                }

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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationSelectionRun insert returned no id"
                    );
                }

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
                "Oferta concorrente " + asin
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertPendingOutbox(
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
                'PENDING',
                ?,
                NULL,
                NULL,
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
                "Oferta concorrente " + asin
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
                NOW.minusHours(
                    1
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationOutbox insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private PersistedClaim loadPersistedClaim(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT
                status,
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationOutbox was not found"
                    );
                }

                return new PersistedClaim(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getObject(
                        "locked_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getString(
                        "locked_by"
                    )
                );
            }
        }
    }

    private long countProcessingOutbox(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
            FROM publication_outbox
            WHERE channel = ?
              AND destination = ?
              AND status = 'PROCESSING'
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationOutbox count returned no row"
                    );
                }

                return resultSet.getLong(
                    "total"
                );
            }
        }
    }

    private void cleanup(
        ApplicationConfig config,
        TestData testData
    ) {

        if (testData == null) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                deleteById(
                    connection,
                    "publication_outbox",
                    testData.outboxId()
                );

                deleteById(
                    connection,
                    "publication",
                    testData.publicationId()
                );

                deleteSelectionDecision(
                    connection,
                    testData.selectionRunId()
                );

                deleteById(
                    connection,
                    "publication_selection_run",
                    testData.selectionRunId()
                );

                deleteById(
                    connection,
                    "deal_evaluation",
                    testData.evaluationId()
                );

                deleteById(
                    connection,
                    "offer_snapshot",
                    testData.snapshotId()
                );

                deleteById(
                    connection,
                    "product",
                    testData.productId()
                );

                deleteProfiles(
                    connection,
                    testData.destination()
                );

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();

                throw exception;
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean publication outbox "
                    + "claim concurrency test data",
                exception
            );
        }
    }

    private void deleteSelectionDecision(
        Connection connection,
        long selectionRunId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM publication_selection_decision
                     WHERE selection_run_id = ?
                     """
                 )) {

            statement.setLong(
                1,
                selectionRunId
            );

            statement.executeUpdate();
        }
    }

    private void deleteById(
        Connection connection,
        String table,
        long id
    ) throws Exception {

        /*
         * table recebe exclusivamente constantes definidas
         * neste próprio teste.
         */
        String sql =
            "DELETE FROM "
                + table
                + " WHERE id = ?";

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                id
            );

            statement.executeUpdate();
        }
    }

    private void deleteProfiles(
        Connection connection,
        String destination
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM publication_selection_profile
                     WHERE channel = ?
                       AND destination = ?
                     """
                 )) {

            statement.setString(
                1,
                CHANNEL
            );

            statement.setString(
                2,
                destination
            );

            statement.executeUpdate();
        }

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM publication_quota_profile
                     WHERE channel = ?
                       AND destination = ?
                     """
                 )) {

            statement.setString(
                1,
                CHANNEL
            );

            statement.setString(
                2,
                destination
            );

            statement.executeUpdate();
        }
    }

    private String uniqueDestination() {

        return "@outbox-claim-concurrency-"
            + System.nanoTime();
    }

    private String uniqueAsin() {

        long suffix =
            Math.floorMod(
                System.nanoTime(),
                10_000_000L
            );

        return "B0C"
            + String.format(
            "%07d",
            suffix
        );
    }

    private record TestData(
        String destination,
        long productId,
        long snapshotId,
        long evaluationId,
        long selectionRunId,
        long publicationId,
        long outboxId
    ) {
    }

    private record PersistedClaim(
        String status,
        OffsetDateTime lockedAt,
        String lockedBy
    ) {
    }
}
