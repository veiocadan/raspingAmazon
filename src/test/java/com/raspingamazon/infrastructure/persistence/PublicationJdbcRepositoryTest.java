package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

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
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PostgresIntegrationTest
class PublicationJdbcRepositoryTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T19:30:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-23T19:31:00-03:00"
        );

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-23T19:32:00-03:00"
        );

    @Test
    void shouldPersistPublicationWithGenerationAudit()
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

                DealEvaluation evaluation =
                    createPersistedEvaluation(
                        connection
                    );

                Publication publication =
                    newPublication(
                        evaluation,
                        "Texto original"
                    );

                PublicationJdbcRepository repository =
                    new PublicationJdbcRepository(
                        connection
                    );

                Publication persisted =
                    repository.save(
                        publication
                    );

                assertNotNull(
                    persisted.id()
                );

                assertEquals(
                    evaluation.id(),
                    persisted.dealEvaluation()
                        .id()
                );

                assertEquals(
                    "AMAZON_PUBLICATION_V1",
                    persisted.templateVersion()
                );

                assertEquals(
                    "AMAZON_COMMERCIAL_PRESENTATION_V1",
                    persisted.commercialPresentationVersion()
                );

                assertEquals(
                    "AMAZON_AFFILIATE_LINK_V1",
                    persisted.affiliateLinkVersion()
                );

                assertEquals(
                    "Texto original",
                    persisted.generatedText()
                );

                assertEquals(
                    "https://www.amazon.com.br/dp/B0PUB13005?tag=test-20",
                    persisted.affiliateUrl()
                );

                assertEquals(
                    PublicationStatus.CREATED,
                    persisted.status()
                );

                assertEquals(
                    CREATED_AT.toInstant(),
                    persisted.createdAt()
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

    @Test
    void shouldReturnExistingPublicationWhenGenerationIdentityIsRepeated()
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

                DealEvaluation evaluation =
                    createPersistedEvaluation(
                        connection
                    );

                PublicationJdbcRepository repository =
                    new PublicationJdbcRepository(
                        connection
                    );

                Publication first =
                    repository.save(
                        newPublication(
                            evaluation,
                            "Texto da primeira geração"
                        )
                    );

                Publication second =
                    repository.save(
                        newPublication(
                            evaluation,
                            "Texto que não deve substituir o histórico"
                        )
                    );

                assertEquals(
                    first.id(),
                    second.id()
                );

                assertEquals(
                    "Texto da primeira geração",
                    second.generatedText()
                );

                assertEquals(
                    first.affiliateUrl(),
                    second.affiliateUrl()
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

    @Test
    void shouldRejectAlreadyPersistedPublication()
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

                DealEvaluation evaluation =
                    createPersistedEvaluation(
                        connection
                    );

                Publication publication =
                    new Publication(
                        999L,
                        evaluation,
                        "AMAZON_PUBLICATION_V1",
                        "AMAZON_COMMERCIAL_PRESENTATION_V1",
                        "AMAZON_AFFILIATE_LINK_V1",
                        "Texto",
                        "https://www.amazon.com.br/dp/B0PUB13005?tag=test-20",
                        PublicationStatus.CREATED,
                        CREATED_AT
                    );

                PublicationJdbcRepository repository =
                    new PublicationJdbcRepository(
                        connection
                    );

                assertThrows(
                    IllegalArgumentException.class,
                    () -> repository.save(
                        publication
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
                "B0PUB13005",
                "Produto de publicação JDBC",
                null,
                "https://www.amazon.com.br/dp/B0PUB13005"
            );

        Product product =
            new Product(
                productId,
                new Asin(
                    "B0PUB13005"
                ),
                "Produto de publicação JDBC",
                null,
                "https://www.amazon.com.br/dp/B0PUB13005"
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
                    2000L,
                    "Amazon.com.br",
                    "Amazon",
                    SellerType.AMAZON,
                    DeliveryType.AMAZON,
                    "publication-jdbc-test",
                    List.of()
                )
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
                2000L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "publication-jdbc-test",
                List.of()
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                null,
                persistedSnapshot,
                true,
                null,
                "AMAZON_SELLER_DELIVERY_V1",
                null,
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

    private Publication newPublication(
        DealEvaluation evaluation,
        String generatedText
    ) {

        return new Publication(
            null,
            evaluation,
            "AMAZON_PUBLICATION_V1",
            "AMAZON_COMMERCIAL_PRESENTATION_V1",
            "AMAZON_AFFILIATE_LINK_V1",
            generatedText,
            "https://www.amazon.com.br/dp/B0PUB13005?tag=test-20",
            PublicationStatus.CREATED,
            CREATED_AT
        );
    }

    private long countPublications(
        Connection connection,
        long dealEvaluationId
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
                dealEvaluationId
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
