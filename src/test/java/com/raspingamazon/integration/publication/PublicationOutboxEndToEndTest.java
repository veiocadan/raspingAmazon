package com.raspingamazon.integration.publication;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueStatus;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.worker.PublicationOutboxWorker;
import com.raspingamazon.application.publication.outbox.worker.PublicationOutboxWorkerRunResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxCompletionAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxEnqueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxQueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationQuotaUsageQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcSuccessfulPublicationHistoryQueryAdapter;
import com.raspingamazon.infrastructure.publication.channel.FakePublicationChannel;
import com.raspingamazon.infrastructure.publication.channel.MapPublicationChannelResolver;
import com.raspingamazon.application.publication.channel.PublicationResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class PublicationOutboxEndToEndTest {

    private static final String CHANNEL =
        "FAKE";

    private static final String SELECTION_VERSION =
        "PUBLICATION_SELECTION_V1";

    private static final String QUOTA_VERSION =
        "PUBLICATION_QUOTA_V1";

    private static final String WORKER_ID =
        "publication-e2e-worker";

    private static final String PROVIDER_REFERENCE =
        "fake-provider-e2e-001";

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            27
        );

    private static final OffsetDateTime ENQUEUED_AT =
        OffsetDateTime.parse(
            "2026-09-27T20:00:00Z"
        );

    private static final OffsetDateTime WORKER_TIME =
        OffsetDateTime.parse(
            "2026-09-27T20:01:00Z"
        );

    private static final Clock WORKER_CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-27T20:01:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldDeliverReadyPublicationThroughPersistedOutboxAndExposeSuccessHistory()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String destination =
            uniqueDestination();

        String asin =
            uniqueAsin();

        TestData testData =
            null;

        try {

            /*
             * O fixture persistido termina deliberadamente em:
             *
             * Publication.status = READY
             *
             * Esse é o ponto inicial exigido pelo critério de
             * conclusão da FASE 18.
             */
            testData =
                createCommittedReadyPublication(
                    config,
                    destination,
                    asin
                );

            FakePublicationChannel fakeChannel =
                new FakePublicationChannel(
                    PublicationResult.success(
                        PROVIDER_REFERENCE
                    )
                );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                connection.setAutoCommit(
                    true
                );

                /*
                 * ====================================================
                 * 1. READY -> OUTBOX
                 * ====================================================
                 */

                JdbcPublicationOutboxEnqueueAdapter enqueueAdapter =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    );

                PublicationOutboxEnqueueRequest enqueueRequest =
                    new PublicationOutboxEnqueueRequest(
                        testData.publicationId(),
                        testData.selectionRunId(),
                        ENQUEUED_AT,
                        ENQUEUED_AT
                    );

                PublicationOutboxEnqueueResult enqueueResult =
                    enqueueAdapter.enqueue(
                        enqueueRequest
                    );

                assertEquals(
                    PublicationOutboxEnqueueStatus.ENQUEUED,
                    enqueueResult.status()
                );

                long outboxId =
                    enqueueResult.outboxIdValue()
                        .orElseThrow();

                assertTrue(
                    outboxId > 0L
                );

                assertEquals(
                    "PENDING",
                    loadOutboxStatus(
                        connection,
                        outboxId
                    )
                );

                /*
                 * A linha criada é também a reserva persistente
                 * da quota.
                 */
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

                /*
                 * ====================================================
                 * 2. OUTBOX -> WORKER -> FAKE CHANNEL
                 * ====================================================
                 */

                PublicationOutboxWorker worker =
                    new PublicationOutboxWorker(
                        WORKER_ID,
                        new JdbcPublicationOutboxQueueAdapter(
                            connection
                        ),
                        new MapPublicationChannelResolver(
                            Map.of(
                                CHANNEL,
                                fakeChannel
                            )
                        ),
                        new JdbcPublicationOutboxCompletionAdapter(
                            connection
                        ),
                        WORKER_CLOCK
                    );

                PublicationOutboxWorkerRunResult workerResult =
                    worker.runOnce();

                assertTrue(
                    workerResult.outboxClaimed()
                );

                assertEquals(
                    outboxId,
                    workerResult.claimedOutboxId()
                );

                assertEquals(
                    PublicationOutboxStatus.SUCCEEDED,
                    workerResult.finalStatus()
                );

                /*
                 * ====================================================
                 * 3. PROVAR O COMANDO AUTOCONTIDO RECEBIDO PELO CANAL
                 * ====================================================
                 */

                assertEquals(
                    1,
                    fakeChannel.callCount()
                );

                assertTrue(
                    fakeChannel.lastCommand()
                        .isPresent()
                );

                assertEquals(
                    testData.publicationId(),
                    fakeChannel.lastCommand()
                        .orElseThrow()
                        .publicationId()
                );

                assertEquals(
                    CHANNEL,
                    fakeChannel.lastCommand()
                        .orElseThrow()
                        .channel()
                );

                assertEquals(
                    destination,
                    fakeChannel.lastCommand()
                        .orElseThrow()
                        .destination()
                );

                assertEquals(
                    "Oferta E2E " + asin,
                    fakeChannel.lastCommand()
                        .orElseThrow()
                        .content()
                );

                /*
                 * ====================================================
                 * 4. PROVAR RESULTADO TERMINAL DA OUTBOX
                 * ====================================================
                 */

                PersistedOutbox persistedOutbox =
                    loadOutbox(
                        connection,
                        outboxId
                    );

                assertEquals(
                    "SUCCEEDED",
                    persistedOutbox.status()
                );

                assertNull(
                    persistedOutbox.lockedAt()
                );

                assertNull(
                    persistedOutbox.lockedBy()
                );

                assertNotNull(
                    persistedOutbox.finishedAt()
                );

                assertEquals(
                    WORKER_TIME.toInstant(),
                    persistedOutbox.finishedAt()
                        .toInstant()
                );

                /*
                 * ====================================================
                 * 5. PROVAR PUBLICATION_ATTEMPT
                 * ====================================================
                 */

                PersistedAttempt attempt =
                    loadSingleAttempt(
                        connection,
                        outboxId
                    );

                assertEquals(
                    testData.publicationId(),
                    attempt.publicationId()
                );

                assertEquals(
                    outboxId,
                    attempt.outboxId()
                );

                assertEquals(
                    CHANNEL,
                    attempt.channel()
                );

                assertEquals(
                    destination,
                    attempt.target()
                );

                assertEquals(
                    1,
                    attempt.attemptNumber()
                );

                assertEquals(
                    "SUCCESS",
                    attempt.status()
                );

                assertEquals(
                    PROVIDER_REFERENCE,
                    attempt.providerReference()
                );

                assertNull(
                    attempt.errorCode()
                );

                assertEquals(
                    WORKER_TIME.toInstant(),
                    attempt.createdAt()
                        .toInstant()
                );

                /*
                 * ====================================================
                 * 6. PROVAR HISTÓRICO DE SUCESSO
                 * ====================================================
                 *
                 * Esta é a ligação com PublicationSelectionPolicy:
                 * a entrega bem-sucedida precisa reaparecer no
                 * histórico persistido usado pelo próximo ciclo.
                 */

                Asin domainAsin =
                    new Asin(
                        asin
                    );

                Map<Asin, SuccessfulPublicationHistory> historyByAsin =
                    new JdbcSuccessfulPublicationHistoryQueryAdapter(
                        connection
                    ).findSuccessfulByAsins(
                        Set.of(
                            domainAsin
                        ),
                        CHANNEL,
                        destination
                    );

                assertEquals(
                    1,
                    historyByAsin.size()
                );

                SuccessfulPublicationHistory history =
                    historyByAsin.get(
                        domainAsin
                    );

                assertNotNull(
                    history
                );

                assertEquals(
                    1L,
                    history.successfulPublicationCount()
                );

                assertEquals(
                    WORKER_TIME.toInstant(),
                    history.lastSuccessfulPublicationAt()
                );

                /*
                 * ====================================================
                 * 7. PROVAR IDEMPOTÊNCIA DE REINÍCIO SIMPLES
                 * ====================================================
                 *
                 * Uma nova rodada do worker não deve republicar a
                 * mesma entrada já SUCCEEDED.
                 */

                PublicationOutboxWorkerRunResult secondRun =
                    worker.runOnce();

                assertFalse(
                    secondRun.outboxClaimed()
                );

                assertEquals(
                    null,
                    secondRun.claimedOutboxId()
                );

                assertEquals(
                    null,
                    secondRun.finalStatus()
                );

                assertEquals(
                    1,
                    fakeChannel.callCount()
                );

                assertEquals(
                    1L,
                    countAttempts(
                        connection,
                        outboxId
                    )
                );

                /*
                 * A mesma publicação também continua representando
                 * uma única reserva de quota.
                 */
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
            }

        } finally {

            cleanup(
                config,
                testData
            );
        }
    }

    private TestData createCommittedReadyPublication(
        ApplicationConfig config,
        String destination,
        String asin
    ) throws Exception {

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
                    insertReadyPublication(
                        connection,
                        evaluationId,
                        asin
                    );

                connection.commit();

                return new TestData(
                    destination,
                    asin,
                    productId,
                    snapshotId,
                    evaluationId,
                    selectionRunId,
                    publicationId
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
                1,
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
                "Publication E2E " + asin
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
                'publication-outbox-e2e-test'
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
                ENQUEUED_AT.minusHours(
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
                95.0000,
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
                ENQUEUED_AT.minusHours(
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
                ENQUEUED_AT.minusMinutes(
                    5
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
                95.0000,
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

    private long insertReadyPublication(
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
                "Oferta E2E " + asin
            );

            statement.setString(
                3,
                "https://example.invalid/affiliate/" + asin
            );

            statement.setObject(
                4,
                ENQUEUED_AT.minusMinutes(
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

    private String loadOutboxStatus(
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationOutbox was not found"
                    );
                }

                return resultSet.getString(
                    "status"
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
                status,
                locked_at,
                locked_by,
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
                        "PublicationOutbox was not found"
                    );
                }

                return new PersistedOutbox(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getObject(
                        "locked_at",
                        OffsetDateTime.class
                    ),
                    resultSet.getString(
                        "locked_by"
                    ),
                    resultSet.getObject(
                        "finished_at",
                        OffsetDateTime.class
                    )
                );
            }
        }
    }

    private PersistedAttempt loadSingleAttempt(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT
                publication_id,
                publication_outbox_id,
                channel,
                target,
                attempt_number,
                status,
                provider_reference,
                error_code,
                created_at
            FROM publication_attempt
            WHERE publication_outbox_id = ?
            ORDER BY attempt_number ASC
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
                        "PublicationAttempt was not found"
                    );
                }

                PersistedAttempt attempt =
                    new PersistedAttempt(
                        resultSet.getLong(
                            "publication_id"
                        ),
                        resultSet.getLong(
                            "publication_outbox_id"
                        ),
                        resultSet.getString(
                            "channel"
                        ),
                        resultSet.getString(
                            "target"
                        ),
                        resultSet.getInt(
                            "attempt_number"
                        ),
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
                            "created_at",
                            OffsetDateTime.class
                        )
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one PublicationAttempt was found"
                    );
                }

                return attempt;
            }
        }
    }

    private long countAttempts(
        Connection connection,
        long outboxId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS total
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

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "PublicationAttempt count returned no row"
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

                deleteAttempts(
                    connection,
                    testData.publicationId()
                );

                deleteOutbox(
                    connection,
                    testData.publicationId()
                );

                deletePublication(
                    connection,
                    testData.publicationId()
                );

                deleteSelectionDecisions(
                    connection,
                    testData.selectionRunId()
                );

                deleteSelectionRun(
                    connection,
                    testData.selectionRunId()
                );

                deleteEvaluation(
                    connection,
                    testData.evaluationId()
                );

                deleteSnapshot(
                    connection,
                    testData.snapshotId()
                );

                deleteProduct(
                    connection,
                    testData.productId()
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
                "Could not clean publication outbox E2E test data",
                exception
            );
        }
    }

    private void deleteAttempts(
        Connection connection,
        long publicationId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM publication_attempt
                     WHERE publication_id = ?
                     """
                 )) {

            statement.setLong(
                1,
                publicationId
            );

            statement.executeUpdate();
        }
    }

    private void deleteOutbox(
        Connection connection,
        long publicationId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM publication_outbox
                     WHERE publication_id = ?
                     """
                 )) {

            statement.setLong(
                1,
                publicationId
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

    private void deleteSelectionDecisions(
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

        return "fake-e2e-destination-"
            + System.nanoTime();
    }

    private String uniqueAsin() {

        long suffix =
            Math.floorMod(
                System.nanoTime(),
                10_000_000L
            );

        return "B0E"
            + String.format(
            "%07d",
            suffix
        );
    }

    private record TestData(
        String destination,
        String asin,
        long productId,
        long snapshotId,
        long evaluationId,
        long selectionRunId,
        long publicationId
    ) {
    }

    private record PersistedOutbox(
        String status,
        OffsetDateTime lockedAt,
        String lockedBy,
        OffsetDateTime finishedAt
    ) {
    }

    private record PersistedAttempt(
        long publicationId,
        long outboxId,
        String channel,
        String target,
        int attemptNumber,
        String status,
        String providerReference,
        String errorCode,
        OffsetDateTime createdAt
    ) {
    }
}
