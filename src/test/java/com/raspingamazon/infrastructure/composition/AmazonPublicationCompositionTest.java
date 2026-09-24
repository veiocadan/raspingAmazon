package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.PublicationGenerator;
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
import com.raspingamazon.infrastructure.config.AmazonAffiliateConfig;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.DealEvaluationJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferSnapshotRepository;
import com.raspingamazon.infrastructure.persistence.ProductRepository;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmazonPublicationCompositionTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T20:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-23T20:05:00-03:00"
        );

    private static final Instant GENERATED_AT =
        Instant.parse(
            "2026-09-23T23:10:00Z"
        );

    private static final Clock FIXED_CLOCK =
        Clock.fixed(
            GENERATED_AT,
            ZoneOffset.UTC
        );

    @Test
    void shouldGeneratePersistAndReusePublicationThroughRealComposition()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                DealEvaluation evaluation =
                    createPersistedEvaluation(
                        connection
                    );

                PublicationGenerator generator =
                    AmazonPublicationComposition.create(
                        connection,
                        new AmazonAffiliateConfig(
                            "test-20"
                        ),
                        FIXED_CLOCK
                    );

                Publication first =
                    generator.generate(
                        evaluation.id()
                    );

                assertNotNull(
                    first.id()
                );

                assertEquals(
                    evaluation.id(),
                    first.dealEvaluation()
                        .id()
                );

                assertEquals(
                    "AMAZON_PUBLICATION_V1",
                    first.templateVersion()
                );

                assertEquals(
                    "AMAZON_COMMERCIAL_PRESENTATION_V1",
                    first.commercialPresentationVersion()
                );

                assertEquals(
                    "AMAZON_AFFILIATE_LINK_V2",
                    first.affiliateLinkVersion()
                );

                assertEquals(
                    PublicationStatus.CREATED,
                    first.status()
                );

                assertEquals(
                    GENERATED_AT,
                    first.createdAt()
                        .toInstant()
                );

                assertEquals(
                    "https://www.amazon.com.br/dp/B0PUB13007?tag=test-20",
                    first.affiliateUrl()
                );

                assertEquals(
                    """
                    Produto vertical de publicação
                    Preço atual: R$ 99,90
                    Link patrocinado: https://www.amazon.com.br/dp/B0PUB13007?tag=test-20""",
                    first.generatedText()
                );

                /*
                 * Segunda execução da mesma identidade versionada:
                 * deve recuperar o mesmo fato histórico persistido.
                 */
                Publication second =
                    generator.generate(
                        evaluation.id()
                    );

                assertEquals(
                    first.id(),
                    second.id()
                );

                assertEquals(
                    first.generatedText(),
                    second.generatedText()
                );

                assertEquals(
                    first.createdAt()
                        .toInstant(),
                    second.createdAt()
                        .toInstant()
                );

                assertEquals(
                    1L,
                    countPublications(
                        connection,
                        evaluation.id()
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private DealEvaluation createPersistedEvaluation(
        Connection connection
    ) throws Exception {

        ProductRepository productRepository =
            new ProductRepository(
                connection
            );

        long productId =
            productRepository.insert(
                "B0PUB13007",
                "Produto vertical de publicação",
                null,
                "https://www.amazon.com.br/dp/B0PUB13007"
            );

        Product product =
            new Product(
                productId,
                new Asin(
                    "B0PUB13007"
                ),
                "Produto vertical de publicação",
                null,
                "https://www.amazon.com.br/dp/B0PUB13007"
            );

        OfferSnapshotRepository snapshotRepository =
            new OfferSnapshotRepository(
                connection
            );

        long snapshotId =
            snapshotRepository.insert(
                new OfferSnapshot(
                    null,
                    product,
                    COLLECTED_AT,
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
                    "publication-composition-test",
                    List.of()
                )
            );

        insertEvidence(
            connection,
            snapshotId,
            "SELLER",
            "Amazon.com.br",
            "AMAZON"
        );

        insertEvidence(
            connection,
            snapshotId,
            "DELIVERY",
            "Amazon.com.br",
            "AMAZON"
        );

        OfferSnapshot persistedSnapshot =
            new OfferSnapshot(
                snapshotId,
                product,
                COLLECTED_AT,
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
                "publication-composition-test",
                List.of()
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                null,
                persistedSnapshot,
                true,
                null,
                "AMAZON_ELIGIBILITY_TEST",
                "COMMERCIAL_FILTER_TEST",
                List.of(
                    EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    ),
                    EvaluationRuleResult.passed(
                        "DELIVERY_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    )
                ),
                null,
                null,
                null,
                null,
                EVALUATED_AT
            );

        return new DealEvaluationJdbcRepository(
            connection
        ).save(
            evaluation
        );
    }

    private void insertEvidence(
        Connection connection,
        long snapshotId,
        String evidenceType,
        String rawValue,
        String normalizedValue
    ) throws Exception {

        String sql = """
                INSERT INTO offer_evidence (
                    offer_snapshot_id,
                    evidence_type,
                    raw_value,
                    normalized_value,
                    source_adapter,
                    source_component,
                    observed_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setString(
                2,
                evidenceType
            );

            statement.setString(
                3,
                rawValue
            );

            statement.setString(
                4,
                normalizedValue
            );

            statement.setString(
                5,
                "publication-composition-test"
            );

            statement.setString(
                6,
                "fixture"
            );

            statement.setObject(
                7,
                COLLECTED_AT
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private long countPublications(
        Connection connection,
        long evaluationId
    ) throws Exception {

        String sql = """
                SELECT COUNT(*) AS total
                FROM publication
                WHERE deal_evaluation_id = ?
                """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getLong(
                    "total"
                );
            }
        }
    }
}
