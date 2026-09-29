package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxEnqueueConcurrencyTest {

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
    void shouldAllowOnlyOneConcurrentReservationForLastQuotaSlot()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String destination =
            uniqueDestination();

        TestData testData =
            null;

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        try {

            /*
             * A preparação é confirmada antes de iniciar a disputa.
             *
             * Os dois candidatos pertencem ao mesmo:
             *
             * channel
             * destination
             * quota profile
             * quota date
             *
             * e ambos foram SELECTED.
             *
             * A quota possui somente uma vaga.
             */
            testData =
                createCommittedTestData(
                    config,
                    destination
                );

            CountDownLatch workersReady =
                new CountDownLatch(
                    2
                );

            CountDownLatch startSignal =
                new CountDownLatch(
                    1
                );

            PublicationOutboxEnqueueRequest firstRequest =
                new PublicationOutboxEnqueueRequest(
                    testData.first()
                        .publicationId(),
                    testData.first()
                        .selectionRunId(),
                    NOW,
                    NOW
                );

            PublicationOutboxEnqueueRequest secondRequest =
                new PublicationOutboxEnqueueRequest(
                    testData.second()
                        .publicationId(),
                    testData.second()
                        .selectionRunId(),
                    NOW,
                    NOW
                );

            Future<PublicationOutboxEnqueueResult> firstFuture =
                executor.submit(
                    () ->
                        enqueueConcurrently(
                            config,
                            firstRequest,
                            workersReady,
                            startSignal
                        )
                );

            Future<PublicationOutboxEnqueueResult> secondFuture =
                executor.submit(
                    () ->
                        enqueueConcurrently(
                            config,
                            secondRequest,
                            workersReady,
                            startSignal
                        )
                );

            /*
             * Somente liberamos a corrida depois que os dois workers
             * abriram suas conexões independentes.
             */
            assertTrue(
                workersReady.await(
                    10,
                    TimeUnit.SECONDS
                ),
                "Both concurrent workers must become ready"
            );

            startSignal.countDown();

            PublicationOutboxEnqueueResult firstResult =
                firstFuture.get(
                    10,
                    TimeUnit.SECONDS
                );

            PublicationOutboxEnqueueResult secondResult =
                secondFuture.get(
                    10,
                    TimeUnit.SECONDS
                );

            List<PublicationOutboxEnqueueResult> results =
                List.of(
                    firstResult,
                    secondResult
                );

            long enqueuedCount =
                results.stream()
                    .filter(
                        result ->
                            result.status()
                                == PublicationOutboxEnqueueStatus.ENQUEUED
                    )
                    .count();

            long quotaExhaustedCount =
                results.stream()
                    .filter(
                        result ->
                            result.status()
                                == PublicationOutboxEnqueueStatus
                                .QUOTA_EXHAUSTED
                    )
                    .count();

            assertEquals(
                1L,
                enqueuedCount
            );

            assertEquals(
                1L,
                quotaExhaustedCount
            );

            /*
             * A prova final não depende somente dos objetos retornados.
             *
             * Verificamos também a fonte persistente de verdade.
             */
            try (Connection verificationConnection =
                     DatabaseConnection.open(
                         config
                     )) {

                assertEquals(
                    1L,
                    countOutbox(
                        verificationConnection,
                        destination
                    )
                );

                assertEquals(
                    1L,
                    new JdbcPublicationQuotaUsageQueryAdapter(
                        verificationConnection
                    ).occupiedSlots(
                        CHANNEL,
                        destination,
                        QUOTA_DATE
                    )
                );

                long reservedPublicationId =
                    loadReservedPublicationId(
                        verificationConnection,
                        destination
                    );

                assertTrue(
                    Set.of(
                        testData.first()
                            .publicationId(),
                        testData.second()
                            .publicationId()
                    ).contains(
                        reservedPublicationId
                    )
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

    private PublicationOutboxEnqueueResult enqueueConcurrently(
        ApplicationConfig config,
        PublicationOutboxEnqueueRequest request,
        CountDownLatch workersReady,
        CountDownLatch startSignal
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            /*
             * Fundamental para este teste:
             *
             * autoCommit=true significa que JdbcTransactionAdapter
             * será proprietário da transação.
             *
             * Portanto:
             *
             * FOR UPDATE
             *      ↓
             * COUNT / INSERT
             *      ↓
             * COMMIT
             *
             * pertencem à mesma unidade atômica e o lock é liberado
             * quando enqueue() termina.
             */
            connection.setAutoCommit(
                true
            );

            JdbcPublicationOutboxEnqueueAdapter adapter =
                new JdbcPublicationOutboxEnqueueAdapter(
                    connection
                );

            workersReady.countDown();

            if (!startSignal.await(
                10,
                TimeUnit.SECONDS
            )) {

                throw new IllegalStateException(
                    "Concurrent enqueue start signal was not released"
                );
            }

            return adapter.enqueue(
                request
            );
        }
    }

    private TestData createCommittedTestData(
        ApplicationConfig config,
        String destination
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                insertProfiles(
                    connection,
                    destination
                );

                Scenario first =
                    createScenario(
                        connection,
                        destination,
                        "B0CON18001"
                    );

                Scenario second =
                    createScenario(
                        connection,
                        destination,
                        "B0CON18002"
                    );

                connection.commit();

                return new TestData(
                    destination,
                    first,
                    second
                );

            } catch (Exception exception) {

                connection.rollback();

                throw exception;
            }
        }
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
            VALUES (
                ?,
                ?,
                ?,
                1,
                'America/Sao_Paulo',
                true
            )
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
                QUOTA_VERSION
            );

            statement.executeUpdate();
        }
    }

    private Scenario createScenario(
        Connection connection,
        String destination,
        String asin
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

        return new Scenario(
            productId,
            snapshotId,
            evaluationId,
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
                "Concurrent outbox test " + asin
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
                'publication-outbox-concurrency-test'
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
                NOW.minusMinutes(
                    30
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
                1,
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
                NOW.minusMinutes(
                    10
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Outbox count returned no row"
                    );
                }

                return resultSet.getLong(
                    "total"
                );
            }
        }
    }

    private long loadReservedPublicationId(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT publication_id
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Reserved Publication was not found"
                    );
                }

                long publicationId =
                    resultSet.getLong(
                        "publication_id"
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one Publication reserved "
                            + "the single quota slot"
                    );
                }

                return publicationId;
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

                deleteOutbox(
                    connection,
                    testData.destination()
                );

                deleteSelectionDecision(
                    connection,
                    testData.first()
                        .selectionRunId()
                );

                deleteSelectionDecision(
                    connection,
                    testData.second()
                        .selectionRunId()
                );

                deleteSelectionRun(
                    connection,
                    testData.first()
                        .selectionRunId()
                );

                deleteSelectionRun(
                    connection,
                    testData.second()
                        .selectionRunId()
                );

                deletePublication(
                    connection,
                    testData.first()
                        .publicationId()
                );

                deletePublication(
                    connection,
                    testData.second()
                        .publicationId()
                );

                deleteEvaluation(
                    connection,
                    testData.first()
                        .evaluationId()
                );

                deleteEvaluation(
                    connection,
                    testData.second()
                        .evaluationId()
                );

                deleteSnapshot(
                    connection,
                    testData.first()
                        .snapshotId()
                );

                deleteSnapshot(
                    connection,
                    testData.second()
                        .snapshotId()
                );

                deleteProduct(
                    connection,
                    testData.first()
                        .productId()
                );

                deleteProduct(
                    connection,
                    testData.second()
                        .productId()
                );

                deleteSelectionProfile(
                    connection,
                    testData.destination()
                );

                deleteQuotaProfile(
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
                    + "concurrency test data",
                exception
            );
        }
    }

    private void deleteOutbox(
        Connection connection,
        String destination
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM publication_outbox
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

    private void deleteSelectionRun(
        Connection connection,
        long selectionRunId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM publication_selection_run
                     WHERE id = ?
                     """
                 )) {

            statement.setLong(
                1,
                selectionRunId
            );

            statement.executeUpdate();
        }
    }

    private void deletePublication(
        Connection connection,
        long publicationId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM publication
                     WHERE id = ?
                     """
                 )) {

            statement.setLong(
                1,
                publicationId
            );

            statement.executeUpdate();
        }
    }

    private void deleteEvaluation(
        Connection connection,
        long evaluationId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM deal_evaluation
                     WHERE id = ?
                     """
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            statement.executeUpdate();
        }
    }

    private void deleteSnapshot(
        Connection connection,
        long snapshotId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM offer_snapshot
                     WHERE id = ?
                     """
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.executeUpdate();
        }
    }

    private void deleteProduct(
        Connection connection,
        long productId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM product
                     WHERE id = ?
                     """
                 )) {

            statement.setLong(
                1,
                productId
            );

            statement.executeUpdate();
        }
    }

    private void deleteSelectionProfile(
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
    }

    private void deleteQuotaProfile(
        Connection connection,
        String destination
    ) throws Exception {

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

        return "@outbox-concurrency-"
            + System.nanoTime();
    }

    private record Scenario(
        long productId,
        long snapshotId,
        long evaluationId,
        long publicationId,
        long selectionRunId
    ) {
    }

    private record TestData(
        String destination,
        Scenario first,
        Scenario second
    ) {
    }
}
