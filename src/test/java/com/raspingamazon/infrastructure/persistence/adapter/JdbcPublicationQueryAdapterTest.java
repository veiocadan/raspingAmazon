package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.application.publication.port.PublicationDataQueryPort;
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
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.DealEvaluationJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferSnapshotRepository;
import com.raspingamazon.infrastructure.persistence.ProductRepository;
import com.raspingamazon.infrastructure.persistence.PublicationJdbcRepository;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationQueryAdapterTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-27T17:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-27T17:05:00-03:00"
        );

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-27T17:10:00-03:00"
        );

    @Test
    void shouldLoadPersistedPublicationById()
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

                Publication persisted =
                    new PublicationJdbcRepository(
                        connection
                    ).save(
                        newPublication(
                            evaluation
                        )
                    );

                PublicationDataQueryPort dataQueryPort =
                    dealEvaluationId -> {

                        assertEquals(
                            evaluation.id()
                                .longValue(),
                            dealEvaluationId
                        );

                        return Optional.of(
                            new PublicationData(
                                evaluation
                            )
                        );
                    };

                JdbcPublicationQueryAdapter adapter =
                    new JdbcPublicationQueryAdapter(
                        connection,
                        dataQueryPort
                    );

                Publication loaded =
                    adapter.findById(
                            persisted.id()
                        )
                        .orElseThrow();

                assertEquals(
                    persisted.id(),
                    loaded.id()
                );

                assertEquals(
                    evaluation.id(),
                    loaded.dealEvaluation()
                        .id()
                );

                assertEquals(
                    "AMAZON_PUBLICATION_V1",
                    loaded.templateVersion()
                );

                assertEquals(
                    "AMAZON_COMMERCIAL_PRESENTATION_V1",
                    loaded.commercialPresentationVersion()
                );

                assertEquals(
                    "AMAZON_AFFILIATE_LINK_V1",
                    loaded.affiliateLinkVersion()
                );

                assertEquals(
                    "Oferta para consulta",
                    loaded.generatedText()
                );

                assertEquals(
                    "https://www.amazon.com.br/dp/B0QRY18001?tag=test-20",
                    loaded.affiliateUrl()
                );

                assertEquals(
                    PublicationStatus.CREATED,
                    loaded.status()
                );

                assertEquals(
                    CREATED_AT.toInstant(),
                    loaded.createdAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReadCurrentPersistedStatus()
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

                Publication persisted =
                    repository.save(
                        newPublication(
                            evaluation
                        )
                    );

                persisted.markReady();

                repository.updateStatus(
                    persisted,
                    PublicationStatus.CREATED
                );

                PublicationDataQueryPort dataQueryPort =
                    dealEvaluationId ->
                        Optional.of(
                            new PublicationData(
                                evaluation
                            )
                        );

                Publication loaded =
                    new JdbcPublicationQueryAdapter(
                        connection,
                        dataQueryPort
                    ).findById(
                        persisted.id()
                    ).orElseThrow();

                assertEquals(
                    PublicationStatus.READY,
                    loaded.status()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReturnEmptyWithoutLoadingEvaluationWhenPublicationDoesNotExist()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            PublicationDataQueryPort dataQueryPort =
                dealEvaluationId -> {
                    throw new AssertionError(
                        "PublicationDataQueryPort must not be called"
                    );
                };

            JdbcPublicationQueryAdapter adapter =
                new JdbcPublicationQueryAdapter(
                    connection,
                    dataQueryPort
                );

            assertTrue(
                adapter.findById(
                        Long.MAX_VALUE
                    )
                    .isEmpty()
            );
        }
    }

    @Test
    void shouldRejectNonPositivePublicationId()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            PublicationDataQueryPort dataQueryPort =
                dealEvaluationId -> {
                    throw new AssertionError(
                        "PublicationDataQueryPort must not be called"
                    );
                };

            JdbcPublicationQueryAdapter adapter =
                new JdbcPublicationQueryAdapter(
                    connection,
                    dataQueryPort
                );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    adapter.findById(
                        0L
                    )
            );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    adapter.findById(
                        -1L
                    )
            );
        }
    }

    @Test
    void shouldFailWhenReferencedEvaluationCannotBeReconstructed()
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

                Publication persisted =
                    new PublicationJdbcRepository(
                        connection
                    ).save(
                        newPublication(
                            evaluation
                        )
                    );

                JdbcPublicationQueryAdapter adapter =
                    new JdbcPublicationQueryAdapter(
                        connection,
                        dealEvaluationId ->
                            Optional.empty()
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        adapter.findById(
                            persisted.id()
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
                "B0QRY18001",
                "Produto de consulta de publicação",
                null,
                "https://www.amazon.com.br/dp/B0QRY18001"
            );

        Product product =
            new Product(
                productId,
                new Asin(
                    "B0QRY18001"
                ),
                "Produto de consulta de publicação",
                null,
                "https://www.amazon.com.br/dp/B0QRY18001"
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
                    "publication-query-test",
                    List.of()
                )
            );

        OfferSnapshot snapshot =
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
                "publication-query-test",
                List.of()
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                null,
                snapshot,
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
        DealEvaluation evaluation
    ) {

        return new Publication(
            null,
            evaluation,
            "AMAZON_PUBLICATION_V1",
            "AMAZON_COMMERCIAL_PRESENTATION_V1",
            "AMAZON_AFFILIATE_LINK_V1",
            "Oferta para consulta",
            "https://www.amazon.com.br/dp/B0QRY18001?tag=test-20",
            PublicationStatus.CREATED,
            CREATED_AT
        );
    }
}
