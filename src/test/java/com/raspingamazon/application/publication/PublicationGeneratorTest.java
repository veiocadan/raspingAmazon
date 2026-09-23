package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.affiliate.AmazonAffiliateLinkGeneratorV1;
import com.raspingamazon.application.publication.port.PublicationDataQueryPort;
import com.raspingamazon.application.publication.presentation.AmazonCommercialPresentationV1;
import com.raspingamazon.application.publication.template.AmazonPublicationV1;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationGeneratorTest {

    private static final long EVALUATION_ID =
        30L;

    private static final Instant GENERATED_AT =
        Instant.parse(
            "2026-09-23T23:00:00Z"
        );

    private static final Clock FIXED_CLOCK =
        Clock.fixed(
            GENERATED_AT,
            ZoneOffset.UTC
        );

    @Test
    void shouldGenerateAndPersistPublicationFromPersistedEvaluation() {

        PublicationData publicationData =
            createPublicationData();

        StubPublicationDataQueryPort queryPort =
            new StubPublicationDataQueryPort(
                publicationData
            );

        CapturingPublicationRepository repository =
            new CapturingPublicationRepository();

        PublicationGenerator generator =
            new PublicationGenerator(
                queryPort,
                new AmazonCommercialPresentationV1(),
                new AmazonAffiliateLinkGeneratorV1(
                    "test-20"
                ),
                new AmazonPublicationV1(),
                repository,
                FIXED_CLOCK
            );

        Publication result =
            generator.generate(
                EVALUATION_ID
            );

        assertEquals(
            EVALUATION_ID,
            queryPort.requestedEvaluationId
        );

        assertNotNull(
            repository.received
        );

        assertEquals(
            999L,
            result.id()
        );

        assertEquals(
            EVALUATION_ID,
            result.dealEvaluation()
                .id()
        );

        assertEquals(
            "AMAZON_PUBLICATION_V1",
            result.templateVersion()
        );

        assertEquals(
            "AMAZON_COMMERCIAL_PRESENTATION_V1",
            result.commercialPresentationVersion()
        );

        assertEquals(
            "AMAZON_AFFILIATE_LINK_V1",
            result.affiliateLinkVersion()
        );

        assertEquals(
            "https://www.amazon.com.br/dp/B0PUB13006?tag=test-20",
            result.affiliateUrl()
        );

        assertEquals(
            PublicationStatus.CREATED,
            result.status()
        );

        assertEquals(
            GENERATED_AT,
            result.createdAt()
                .toInstant()
        );

        assertEquals(
            """
            Produto do generator
            Preço atual: R$ 99,90
            Link patrocinado: https://www.amazon.com.br/dp/B0PUB13006?tag=test-20""",
            result.generatedText()
        );
    }

    @Test
    void shouldDelegateFinalIdentityDecisionToRepository() {

        PublicationData publicationData =
            createPublicationData();

        Publication existing =
            new Publication(
                777L,
                publicationData.dealEvaluation(),
                "AMAZON_PUBLICATION_V1",
                "AMAZON_COMMERCIAL_PRESENTATION_V1",
                "AMAZON_AFFILIATE_LINK_V1",
                "Conteúdo histórico já persistido",
                "https://www.amazon.com.br/dp/B0PUB13006?tag=test-20",
                PublicationStatus.CREATED,
                OffsetDateTime.parse(
                    "2026-09-23T20:00:00Z"
                )
            );

        PublicationRepository repository =
            publication -> existing;

        PublicationGenerator generator =
            new PublicationGenerator(
                new StubPublicationDataQueryPort(
                    publicationData
                ),
                new AmazonCommercialPresentationV1(),
                new AmazonAffiliateLinkGeneratorV1(
                    "test-20"
                ),
                new AmazonPublicationV1(),
                repository,
                FIXED_CLOCK
            );

        Publication result =
            generator.generate(
                EVALUATION_ID
            );

        assertEquals(
            777L,
            result.id()
        );

        assertEquals(
            "Conteúdo histórico já persistido",
            result.generatedText()
        );
    }

    @Test
    void shouldRejectNonPositiveEvaluationId() {

        PublicationGenerator generator =
            generatorWithMissingData();

        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generate(
                0L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generate(
                -1L
            )
        );
    }

    @Test
    void shouldRejectUnknownEvaluation() {

        PublicationGenerator generator =
            generatorWithMissingData();

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(
                    9999L
                )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "9999"
                )
        );
    }

    private PublicationGenerator generatorWithMissingData() {

        PublicationDataQueryPort queryPort =
            dealEvaluationId ->
                Optional.empty();

        PublicationRepository repository =
            publication -> publication;

        return new PublicationGenerator(
            queryPort,
            new AmazonCommercialPresentationV1(),
            new AmazonAffiliateLinkGeneratorV1(
                "test-20"
            ),
            new AmazonPublicationV1(),
            repository,
            FIXED_CLOCK
        );
    }

    private PublicationData createPublicationData() {

        Product product =
            new Product(
                10L,
                new Asin(
                    "B0PUB13006"
                ),
                "Produto do generator",
                null,
                "https://www.amazon.com.br/dp/B0PUB13006"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                20L,
                product,
                OffsetDateTime.parse(
                    "2026-09-23T19:50:00-03:00"
                ),
                Money.of(
                    "99.90"
                ),
                null,
                null,
                null,
                4.8,
                1500L,
                "Amazon.com.br",
                "Amazon.com.br",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "publication-generator-test",
                List.of()
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                EVALUATION_ID,
                snapshot,
                true,
                null,
                "AMAZON_ELIGIBILITY_TEST",
                "COMMERCIAL_FILTER_TEST",
                List.of(
                    EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    )
                ),
                null,
                null,
                null,
                null,
                OffsetDateTime.parse(
                    "2026-09-23T19:55:00-03:00"
                )
            );

        return new PublicationData(
            evaluation
        );
    }

    private static final class StubPublicationDataQueryPort
        implements PublicationDataQueryPort {

        private final PublicationData publicationData;

        private long requestedEvaluationId;

        private StubPublicationDataQueryPort(
            PublicationData publicationData
        ) {

            this.publicationData =
                publicationData;
        }

        @Override
        public Optional<PublicationData> findByDealEvaluationId(
            long dealEvaluationId
        ) {

            requestedEvaluationId =
                dealEvaluationId;

            return Optional.of(
                publicationData
            );
        }
    }

    private static final class CapturingPublicationRepository
        implements PublicationRepository {

        private Publication received;

        @Override
        public Publication save(
            Publication publication
        ) {

            received =
                publication;

            return new Publication(
                999L,
                publication.dealEvaluation(),
                publication.templateVersion(),
                publication.commercialPresentationVersion(),
                publication.affiliateLinkVersion(),
                publication.generatedText(),
                publication.affiliateUrl(),
                publication.status(),
                publication.createdAt()
            );
        }
    }
}
