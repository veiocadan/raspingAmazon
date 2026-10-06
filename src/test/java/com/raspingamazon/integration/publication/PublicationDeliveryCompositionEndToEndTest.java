package com.raspingamazon.integration.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueStatus;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.worker.PublicationOutboxWorker;
import com.raspingamazon.application.publication.outbox.worker.PublicationOutboxWorkerRunResult;
import com.raspingamazon.infrastructure.composition.PublicationDeliveryComposition;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
import com.raspingamazon.infrastructure.config.WhatsAppChannelConfig;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxEnqueueAdapter;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpRequest;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpResponse;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class PublicationDeliveryCompositionEndToEndTest {

    private static final String SELECTION_VERSION =
        "PUBLICATION_SELECTION_V1";

    private static final String QUOTA_VERSION =
        "PUBLICATION_QUOTA_V1";

    private static final String WORKER_ID =
        "publication-delivery-fase19-e2e";

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            29
        );

    private static final OffsetDateTime ENQUEUED_AT =
        OffsetDateTime.parse(
            "2026-09-29T22:59:00Z"
        );

    private static final Instant WORKER_STARTED_AT =
        Instant.parse(
            "2026-09-29T23:00:00Z"
        );

    private static final Duration WORKER_CLOCK_STEP =
        Duration.ofSeconds(
            1
        );

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    private final TelegramChannelConfig telegramConfig =
        new TelegramChannelConfig(
            URI.create(
                "https://telegram.invalid"
            ),
            "123456:test-token",
            Duration.ofSeconds(
                10
            )
        );

    private final WhatsAppChannelConfig whatsAppConfig =
        new WhatsAppChannelConfig(
            URI.create(
                "https://graph.invalid"
            ),
            "v26.0",
            "123456789012345",
            "secret-access-token",
            "amazon_offer",
            "pt_BR",
            Duration.ofSeconds(
                10
            )
        );

    @Test
    void shouldDeliverTelegramThroughConcreteComposition()
        throws Exception {

        String destination =
            uniqueTelegramDestination();

        PublicationHttpResponse providerResponse =
            new PublicationHttpResponse(
                200,
                """
                {
                  "ok": true,
                  "result": {
                    "message_id": 9001
                  }
                }
                """
            );

        ScenarioResult result =
            executeScenario(
                "TELEGRAM",
                destination,
                providerResponse
            );

        assertEquals(
            PublicationOutboxStatus.SUCCEEDED,
            result.workerResult()
                .finalStatus()
        );

        assertEquals(
            "SUCCEEDED",
            result.outbox()
                .status()
        );

        assertEquals(
            "SUCCESS",
            result.attempt()
                .status()
        );

        assertEquals(
            "9001",
            result.attempt()
                .providerReference()
        );

        assertNull(
            result.attempt()
                .errorCode()
        );

        assertTrue(
            result.request()
                .uri()
                .getPath()
                .endsWith(
                    "/bot123456:test-token/sendMessage"
                )
        );

        JsonNode requestBody =
            objectMapper.readTree(
                result.request()
                    .body()
            );

        assertEquals(
            destination,
            requestBody.get(
                    "chat_id"
                )
                .asText()
        );

        assertEquals(
            result.content(),
            requestBody.get(
                    "text"
                )
                .asText()
        );
    }

    @Test
    void shouldDeliverWhatsAppThroughConcreteComposition()
        throws Exception {

        String destination =
            uniqueWhatsAppDestination();

        PublicationHttpResponse providerResponse =
            new PublicationHttpResponse(
                200,
                """
                {
                  "messaging_product": "whatsapp",
                  "contacts": [
                    {
                      "input": "5511999999999",
                      "wa_id": "5511999999999"
                    }
                  ],
                  "messages": [
                    {
                      "id": "wamid.fase19-e2e-success"
                    }
                  ]
                }
                """
            );

        ScenarioResult result =
            executeScenario(
                "WHATSAPP",
                destination,
                providerResponse
            );

        assertEquals(
            PublicationOutboxStatus.SUCCEEDED,
            result.workerResult()
                .finalStatus()
        );

        assertEquals(
            "SUCCEEDED",
            result.outbox()
                .status()
        );

        assertEquals(
            "SUCCESS",
            result.attempt()
                .status()
        );

        assertEquals(
            "wamid.fase19-e2e-success",
            result.attempt()
                .providerReference()
        );

        assertNull(
            result.attempt()
                .errorCode()
        );

        assertTrue(
            result.request()
                .uri()
                .getPath()
                .endsWith(
                    "/v26.0/123456789012345/messages"
                )
        );

        assertEquals(
            "Bearer secret-access-token",
            result.request()
                .headers()
                .get(
                    "Authorization"
                )
        );

        JsonNode requestBody =
            objectMapper.readTree(
                result.request()
                    .body()
            );

        assertEquals(
            destination,
            requestBody.get(
                    "to"
                )
                .asText()
        );

        assertEquals(
            "template",
            requestBody.get(
                    "type"
                )
                .asText()
        );

        assertEquals(
            result.content(),
            requestBody
                .path(
                    "template"
                )
                .path(
                    "components"
                )
                .get(
                    0
                )
                .path(
                    "parameters"
                )
                .get(
                    0
                )
                .path(
                    "text"
                )
                .asText()
        );
    }

    @Test
    void shouldPersistPermanentWhatsAppFailureThroughConcreteComposition()
        throws Exception {

        String destination =
            uniqueWhatsAppDestination();

        PublicationHttpResponse providerResponse =
            new PublicationHttpResponse(
                400,
                """
                {
                  "error": {
                    "message": "Message undeliverable",
                    "type": "OAuthException",
                    "code": 131026,
                    "is_transient": false
                  }
                }
                """
            );

        ScenarioResult result =
            executeScenario(
                "WHATSAPP",
                destination,
                providerResponse
            );

        assertEquals(
            PublicationOutboxStatus.FAILED_PERMANENT,
            result.workerResult()
                .finalStatus()
        );

        assertEquals(
            "FAILED_PERMANENT",
            result.outbox()
                .status()
        );

        assertEquals(
            "FAILED_PERMANENT",
            result.attempt()
                .status()
        );

        assertNull(
            result.attempt()
                .providerReference()
        );

        assertEquals(
            "WHATSAPP_API_REJECTED_131026",
            result.attempt()
                .errorCode()
        );
    }

    private ScenarioResult executeScenario(
        String channel,
        String destination,
        PublicationHttpResponse providerResponse
    ) throws Exception {

        ApplicationConfig applicationConfig =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     applicationConfig
                 )) {

            ReadyScenario scenario =
                null;

            boolean scenarioCommitted =
                false;

            connection.setAutoCommit(
                false
            );

            try {

                String asin =
                    uniqueAsin();

                scenario =
                    createReadyScenario(
                        connection,
                        channel,
                        destination,
                        asin
                    );

                JdbcPublicationOutboxEnqueueAdapter enqueueAdapter =
                    new JdbcPublicationOutboxEnqueueAdapter(
                        connection
                    );

                PublicationOutboxEnqueueResult enqueueResult =
                    enqueueAdapter.enqueue(
                        new PublicationOutboxEnqueueRequest(
                            scenario.publicationId(),
                            scenario.selectionRunId(),
                            ENQUEUED_AT,
                            ENQUEUED_AT
                        )
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
                 * ----------------------------------------------------
                 * FASE 20: COMMIT DA FIXTURE ANTES DO WORKER
                 * ----------------------------------------------------
                 *
                 * JdbcPublicationAttemptStartAdapter exige uma
                 * fronteira própria de commit antes do efeito externo.
                 * Portanto o worker não pode participar da transação
                 * externa utilizada apenas para montar a fixture.
                 */
                connection.commit();

                scenarioCommitted =
                    true;

                connection.setAutoCommit(
                    true
                );

                AtomicReference<PublicationHttpRequest> capturedRequest =
                    new AtomicReference<>();

                AtomicInteger transportCalls =
                    new AtomicInteger();

                PublicationHttpTransport transport =
                    request -> {

                        transportCalls.incrementAndGet();

                        capturedRequest.set(
                            request
                        );

                        return providerResponse;
                    };

                Clock workerClock =
                    new StepClock(
                        WORKER_STARTED_AT,
                        WORKER_CLOCK_STEP,
                        ZoneOffset.UTC
                    );

                PublicationOutboxWorker worker =
                    PublicationDeliveryComposition.create(
                        connection,
                        WORKER_ID,
                        telegramConfig,
                        whatsAppConfig,
                        transport,
                        objectMapper,
                        workerClock
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
                    1,
                    transportCalls.get()
                );

                PublicationHttpRequest request =
                    capturedRequest.get();

                assertNotNull(
                    request
                );

                PersistedOutbox outbox =
                    loadOutbox(
                        connection,
                        outboxId
                    );

                assertNull(
                    outbox.lockedAt()
                );

                assertNull(
                    outbox.lockedBy()
                );

                assertNotNull(
                    outbox.finishedAt()
                );

                /*
                 * O novo worker lê o relógio três vezes:
                 *
                 * T      = claim/lease
                 * T + 1s = STARTED durável
                 * T + 2s = conclusão depois do provider
                 */
                assertEquals(
                    WORKER_STARTED_AT
                        .plus(
                            WORKER_CLOCK_STEP.multipliedBy(
                                2L
                            )
                        ),
                    outbox.finishedAt()
                        .toInstant()
                );

                PersistedAttempt attempt =
                    loadSingleAttempt(
                        connection,
                        outboxId
                    );

                assertEquals(
                    scenario.publicationId(),
                    attempt.publicationId()
                );

                assertEquals(
                    outboxId,
                    attempt.outboxId()
                );

                assertEquals(
                    channel,
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
                    WORKER_STARTED_AT
                        .plus(
                            WORKER_CLOCK_STEP
                        ),
                    attempt.startedAt()
                        .toInstant()
                );

                assertEquals(
                    WORKER_STARTED_AT
                        .plus(
                            WORKER_CLOCK_STEP.multipliedBy(
                                2L
                            )
                        ),
                    attempt.finishedAt()
                        .toInstant()
                );

                /*
                 * created_at agora pertence ao STARTED, não à
                 * conclusão posterior da tentativa.
                 */
                assertEquals(
                    attempt.startedAt()
                        .toInstant(),
                    attempt.createdAt()
                        .toInstant()
                );

                /*
                 * Idempotência de reinício simples:
                 *
                 * a entrada já terminal não volta a ser selecionada.
                 */
                PublicationOutboxWorkerRunResult secondRun =
                    worker.runOnce();

                assertFalse(
                    secondRun.outboxClaimed()
                );

                assertNull(
                    secondRun.claimedOutboxId()
                );

                assertNull(
                    secondRun.finalStatus()
                );

                assertEquals(
                    1,
                    transportCalls.get()
                );

                assertEquals(
                    1L,
                    countAttempts(
                        connection,
                        outboxId
                    )
                );

                return new ScenarioResult(
                    workerResult,
                    request,
                    outbox,
                    attempt,
                    scenario.content()
                );

            } catch (Exception exception) {

                if (!scenarioCommitted) {
                    connection.rollback();
                }

                throw exception;

            } finally {

                if (scenarioCommitted
                    && scenario != null) {

                    cleanupCommittedScenario(
                        connection,
                        scenario
                    );
                }
            }
        }
    }

    private ReadyScenario createReadyScenario(
        Connection connection,
        String channel,
        String destination,
        String asin
    ) throws Exception {

        insertSelectionProfile(
            connection,
            channel,
            destination
        );

        insertQuotaProfile(
            connection,
            channel,
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
                channel,
                destination
            );

        insertSelectionDecision(
            connection,
            selectionRunId,
            evaluationId,
            asin
        );

        String content =
            "Oferta E2E FASE 19 "
                + asin;

        long publicationId =
            insertReadyPublication(
                connection,
                evaluationId,
                asin,
                content
            );

        return new ReadyScenario(
            channel,
            destination,
            productId,
            snapshotId,
            evaluationId,
            selectionRunId,
            publicationId,
            content
        );
    }

    private void insertSelectionProfile(
        Connection connection,
        String channel,
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
                channel
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
        String channel,
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
                channel
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
                "Publication delivery FASE 19 "
                    + asin
            );

            statement.setString(
                3,
                "https://example.invalid/"
                    + asin
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
                99.90,
                NULL,
                NULL,
                NULL,
                4.8,
                1000,
                'Amazon.com.br',
                'Amazon',
                'publication-delivery-fase19-e2e'
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
        String channel,
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
                channel
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
        String asin,
        String content
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
                content
            );

            statement.setString(
                3,
                "https://example.invalid/affiliate/"
                    + asin
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
                started_at,
                finished_at,
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
                            "started_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getObject(
                            "finished_at",
                            OffsetDateTime.class
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

    private void cleanupCommittedScenario(
        Connection connection,
        ReadyScenario scenario
    ) throws Exception {

        boolean originalAutoCommit =
            connection.getAutoCommit();

        if (originalAutoCommit) {
            connection.setAutoCommit(
                false
            );
        }

        try {

            deleteByLong(
                connection,
                """
                DELETE FROM publication_attempt
                WHERE publication_id = ?
                """,
                scenario.publicationId()
            );

            deleteByLong(
                connection,
                """
                DELETE FROM publication_outbox
                WHERE publication_id = ?
                """,
                scenario.publicationId()
            );

            deleteByLong(
                connection,
                """
                DELETE FROM publication
                WHERE id = ?
                """,
                scenario.publicationId()
            );

            deleteByLong(
                connection,
                """
                DELETE FROM publication_selection_decision
                WHERE selection_run_id = ?
                """,
                scenario.selectionRunId()
            );

            deleteByLong(
                connection,
                """
                DELETE FROM publication_selection_run
                WHERE id = ?
                """,
                scenario.selectionRunId()
            );

            deleteByLong(
                connection,
                """
                DELETE FROM deal_evaluation
                WHERE id = ?
                """,
                scenario.evaluationId()
            );

            deleteByLong(
                connection,
                """
                DELETE FROM offer_snapshot
                WHERE id = ?
                """,
                scenario.snapshotId()
            );

            deleteByLong(
                connection,
                """
                DELETE FROM product
                WHERE id = ?
                """,
                scenario.productId()
            );

            deleteProfile(
                connection,
                "publication_selection_profile",
                scenario.channel(),
                scenario.destination()
            );

            deleteProfile(
                connection,
                "publication_quota_profile",
                scenario.channel(),
                scenario.destination()
            );

            connection.commit();

        } catch (Exception exception) {

            connection.rollback();

            throw exception;

        } finally {

            if (originalAutoCommit
                && !connection.getAutoCommit()) {

                connection.setAutoCommit(
                    true
                );
            }
        }
    }

    private void deleteByLong(
        Connection connection,
        String sql,
        long id
    ) throws Exception {

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

    private void deleteProfile(
        Connection connection,
        String tableName,
        String channel,
        String destination
    ) throws Exception {

        String sql =
            "DELETE FROM "
                + tableName
                + " WHERE channel = ? AND destination = ?";

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                channel
            );

            statement.setString(
                2,
                destination
            );

            statement.executeUpdate();
        }
    }

    private String uniqueTelegramDestination() {

        long suffix =
            Math.floorMod(
                System.nanoTime(),
                100_000_000L
            );

        return "@fase19_telegram_"
            + suffix;
    }

    private String uniqueWhatsAppDestination() {

        long suffix =
            Math.floorMod(
                System.nanoTime(),
                100_000_000L
            );

        return "55119"
            + String.format(
            "%08d",
            suffix
        );
    }

    private String uniqueAsin() {

        long suffix =
            Math.floorMod(
                System.nanoTime(),
                10_000_000L
            );

        return "B0F"
            + String.format(
            "%07d",
            suffix
        );
    }

    private record ReadyScenario(
        String channel,
        String destination,
        long productId,
        long snapshotId,
        long evaluationId,
        long selectionRunId,
        long publicationId,
        String content
    ) {
    }

    private record ScenarioResult(
        PublicationOutboxWorkerRunResult workerResult,
        PublicationHttpRequest request,
        PersistedOutbox outbox,
        PersistedAttempt attempt,
        String content
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
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt
    ) {
    }

    /**
     * Clock determinístico que avança um passo a cada leitura.
     *
     * <p>PublicationOutboxWorker consulta o relógio três vezes numa
     * entrega efetiva:</p>
     *
     * <ol>
     *     <li>claim/lease;</li>
     *     <li>STARTED durável imediatamente antes do provider;</li>
     *     <li>conclusão depois do retorno do provider.</li>
     * </ol>
     */
    private static final class StepClock
        extends Clock {

        private Instant nextInstant;

        private final Duration step;

        private final ZoneId zone;

        private StepClock(
            Instant firstInstant,
            Duration step,
            ZoneId zone
        ) {

            this.nextInstant =
                firstInstant;

            this.step =
                step;

            this.zone =
                zone;
        }

        @Override
        public ZoneId getZone() {

            return zone;
        }

        @Override
        public Clock withZone(
            ZoneId newZone
        ) {

            return new StepClock(
                nextInstant,
                step,
                newZone
            );
        }

        @Override
        public Instant instant() {

            Instant current =
                nextInstant;

            nextInstant =
                nextInstant.plus(
                    step
                );

            return current;
        }
    }
}
