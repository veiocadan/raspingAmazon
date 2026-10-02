package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.PublicationSelectionDispatchResult;
import com.raspingamazon.application.publication.PublicationSelectionDispatchService;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.selection.PublicationSelectionExecution;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;
import com.raspingamazon.domain.publication.selection.PublicationQuotaSnapshot;
import com.raspingamazon.domain.publication.selection.PublicationSelectionCandidate;
import com.raspingamazon.domain.publication.selection.PublicationSelectionDecision;
import com.raspingamazon.domain.publication.selection.PublicationSelectionDecisionStatus;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionRecency;
import com.raspingamazon.domain.publication.selection.PublicationSelectionResult;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class PublicationSelectionDispatchCompositionTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final Instant NOW =
        Instant.parse(
            "2026-09-30T12:10:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            30
        );

    private static final ZoneId ZONE =
        ZoneId.of(
            "America/Sao_Paulo"
        );

    @Test
    void shouldComposeDispatchWithPersistedCadenceProviderAndHistoryQuery()
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
                    uniqueDestination();

                insertCadenceProfile(
                    connection,
                    destination
                );

                Publication publication =
                    createPublication(
                        101L,
                        11L,
                        "B0CMP00001",
                        PublicationStatus.CREATED
                    );

                AtomicReference<PublicationOutboxEnqueueRequest>
                    capturedRequest =
                    new AtomicReference<>();

                PublicationSelectionDispatchService service =
                    PublicationSelectionDispatchComposition.create(
                        connection,
                        dealEvaluationId -> {

                            assertEquals(
                                11L,
                                dealEvaluationId
                            );

                            return publication;
                        },
                        publicationId -> {

                            assertEquals(
                                101L,
                                publicationId
                            );

                            if (publication.status()
                                == PublicationStatus.CREATED) {

                                publication.markReady();
                            }

                            return publication;
                        },
                        request -> {

                            capturedRequest.set(
                                request
                            );

                            return PublicationOutboxEnqueueResult
                                .enqueued(
                                    900L
                                );
                        },
                        CLOCK
                    );

                PublicationSelectionDispatchResult result =
                    service.dispatch(
                        selectionExecution(
                            destination
                        )
                    );

                PublicationOutboxEnqueueRequest request =
                    capturedRequest.get();

                assertEquals(
                    "CADENCE_COMPOSITION_V1",
                    request.cadenceProfileVersion()
                );

                assertTrue(
                    request.cadenceManaged()
                );

                /*
                 * NOW:
                 *
                 * 2026-09-30T12:10Z
                 * =
                 * 09:10 America/Sao_Paulo
                 *
                 * Grade:
                 *
                 * 08:00
                 * 10:00
                 * 12:00
                 * ...
                 *
                 * Logo o primeiro slot válido é 10:00.
                 */
                assertEquals(
                    QUOTA_DATE
                        .atTime(
                            10,
                            0
                        )
                        .atZone(
                            ZONE
                        )
                        .toInstant(),
                    request.availableAt()
                        .toInstant()
                );

                assertEquals(
                    NOW,
                    request.enqueuedAt()
                        .toInstant()
                );

                assertEquals(
                    1,
                    result.attemptedCount()
                );

                assertEquals(
                    1L,
                    result.reservedCount()
                );

                assertEquals(
                    0,
                    result.cadenceUnavailableCount()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private void insertCadenceProfile(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            INSERT INTO publication_cadence_profile (
                channel,
                destination,
                version,
                interval_seconds,
                window_start,
                window_end,
                cadence_zone,
                active
            )
            VALUES (
                ?,
                ?,
                'CADENCE_COMPOSITION_V1',
                7200,
                '08:00',
                '22:00',
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

    private PublicationSelectionExecution selectionExecution(
        String destination
    ) {

        PublicationSelectionCandidate candidate =
            new PublicationSelectionCandidate(
                11L,
                new Asin(
                    "B0CMP00001"
                ),
                new BigDecimal(
                    "91.2500"
                ),
                CHANNEL,
                destination,
                null
            );

        PublicationSelectionDecision decision =
            new PublicationSelectionDecision(
                candidate,
                PublicationSelectionDecisionStatus.SELECTED,
                PublicationSelectionRecency
                    .NEVER_SUCCESSFULLY_PUBLISHED,
                1
            );

        PublicationSelectionProfile selectionProfile =
            new PublicationSelectionProfile(
                "SELECTION_COMPOSITION_V1",
                Duration.ZERO,
                Duration.ZERO
            );

        PublicationQuotaSnapshot quotaSnapshot =
            new PublicationQuotaSnapshot(
                CHANNEL,
                destination,
                QUOTA_DATE,
                "QUOTA_COMPOSITION_V1",
                1,
                0L
            );

        PublicationSelectionResult selectionResult =
            new PublicationSelectionResult(
                NOW,
                selectionProfile,
                quotaSnapshot,
                List.of(
                    decision
                )
            );

        return new PublicationSelectionExecution(
            700L,
            selectionResult
        );
    }

    private Publication createPublication(
        long publicationId,
        long dealEvaluationId,
        String asin,
        PublicationStatus status
    ) {

        Product product =
            new Product(
                1001L,
                new Asin(
                    asin
                ),
                "Produto composition cadence",
                null,
                "https://www.amazon.com.br/dp/" + asin
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                2001L,
                product,
                OffsetDateTime.parse(
                    "2026-09-30T08:00:00-03:00"
                ),
                Money.of(
                    "99.90"
                ),
                Money.of(
                    "129.90"
                ),
                null,
                null,
                4.8,
                1000L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "publication-selection-dispatch-composition-test",
                List.of()
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                dealEvaluationId,
                snapshot,
                true,
                null,
                "COMPOSITION_TEST_ELIGIBILITY_V1",
                null,
                List.of(
                    EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    )
                ),
                null,
                null,
                List.of(),
                null,
                null,
                OffsetDateTime.parse(
                    "2026-09-30T08:30:00-03:00"
                )
            );

        return new Publication(
            publicationId,
            evaluation,
            "AMAZON_PUBLICATION_V2",
            "AMAZON_COMMERCIAL_PRESENTATION_V2",
            "AMAZON_AFFILIATE_LINK_V2",
            """
            🔹**Produto composition cadence**
            💰 De ~~R$ 129,90~~ por **R$ 99,90**!
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/%s?tag=test-20
            """
                .formatted(
                    asin
                )
                .strip(),
            "https://www.amazon.com.br/dp/"
                + asin
                + "?tag=test-20",
            status,
            OffsetDateTime.parse(
                "2026-09-30T08:45:00-03:00"
            )
        );
    }

    private String uniqueDestination() {

        return "@dispatch-composition-"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
