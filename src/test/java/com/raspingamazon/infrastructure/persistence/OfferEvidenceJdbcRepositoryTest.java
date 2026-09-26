package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.RatingEvidence;
import com.raspingamazon.application.enrichment.contract.ReviewCountEvidence;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integração da persistência das evidências de enrichment.
 *
 * <p>O teste valida os quatro conceitos atualmente persistidos:
 * SELLER, DELIVERY, RATING e REVIEW_COUNT.</p>
 */
@PostgresIntegrationTest
class OfferEvidenceJdbcRepositoryTest {

    @Test
    void shouldPersistSellerDeliveryRatingAndReviewCountEvidence()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();


        String asinValue =
            "B000EV0001";

        long productId =
            0;

        long snapshotId =
            0;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            ProductRepository productRepository =
                new ProductRepository(
                    connection
                );

            productId =
                productRepository.insert(
                    asinValue,
                    "Produto de teste de evidência",
                    null,
                    "https://example.invalid/evidence"
                );

            Product product =
                new Product(
                    productId,
                    new Asin(
                        asinValue
                    ),
                    "Produto de teste de evidência",
                    null,
                    "https://example.invalid/evidence"
                );

            OffsetDateTime collectedAt =
                OffsetDateTime.parse(
                    "2026-09-24T18:00:00-03:00"
                );

            OfferSnapshot snapshot =
                new OfferSnapshot(
                    null,
                    product,
                    collectedAt,
                    new Money(
                        new BigDecimal(
                            "199.90"
                        )
                    ),
                    null,
                    null,
                    null,
                    4.8d,
                    618L,
                    "Amazon.com.br",
                    "Amazon",
                    SellerType.AMAZON,
                    DeliveryType.AMAZON,
                    "AMAZON_DEALS",
                    List.of()
                );

            OfferSnapshotRepository snapshotRepository =
                new OfferSnapshotRepository(
                    connection
                );

            snapshotId =
                snapshotRepository.insert(
                    snapshot
                );

            OffsetDateTime enrichedAt =
                OffsetDateTime.parse(
                    "2026-09-24T18:00:05-03:00"
                );

            ProductEnrichmentResult enrichmentResult =
                new ProductEnrichmentResult(
                    asinValue,
                    new SellerEvidence(
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature"
                    ),
                    new DeliveryEvidence(
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature"
                    ),
                    new RatingEvidence(
                        "4,8 de 5 estrelas",
                        4.8d,
                        "averageCustomerReviews_feature_div/acrPopover@title"
                    ),
                    new ReviewCountEvidence(
                        "618 Análises",
                        618L,
                        "averageCustomerReviews_feature_div/acrCustomerReviewText@aria-label"
                    ),
                    List.of(),
                    "AMAZON_PRODUCT_PAGE",
                    "https://www.amazon.com.br/dp/"
                        + asinValue,
                    enrichedAt
                );

            OfferEvidenceJdbcRepository evidenceRepository =
                new OfferEvidenceJdbcRepository(
                    connection
                );

            evidenceRepository.insert(
                snapshotId,
                enrichmentResult
            );

            List<PersistedEvidence> persisted =
                loadEvidence(
                    connection,
                    snapshotId
                );

            assertEquals(
                4,
                persisted.size()
            );

            PersistedEvidence delivery =
                persisted.get(
                    0
                );

            assertEvidence(
                delivery,
                "DELIVERY",
                "Amazon",
                "AMAZON",
                "fulfillerInfoFeature",
                enrichedAt
            );

            PersistedEvidence rating =
                persisted.get(
                    1
                );

            assertEvidence(
                rating,
                "RATING",
                "4,8 de 5 estrelas",
                "4.8",
                "averageCustomerReviews_feature_div/acrPopover@title",
                enrichedAt
            );

            PersistedEvidence reviewCount =
                persisted.get(
                    2
                );

            assertEvidence(
                reviewCount,
                "REVIEW_COUNT",
                "618 Análises",
                "618",
                "averageCustomerReviews_feature_div/acrCustomerReviewText@aria-label",
                enrichedAt
            );

            PersistedEvidence seller =
                persisted.get(
                    3
                );

            assertEvidence(
                seller,
                "SELLER",
                "Amazon.com.br",
                "AMAZON",
                "merchantInfoFeature",
                enrichedAt
            );

        } finally {

            cleanup(
                config,
                snapshotId,
                productId
            );
        }
    }

    private List<PersistedEvidence> loadEvidence(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql =
            """
            SELECT
                evidence_type,
                raw_value,
                normalized_value,
                source_adapter,
                source_component,
                observed_at
            FROM offer_evidence
            WHERE offer_snapshot_id = ?
            ORDER BY evidence_type
            """;

        List<PersistedEvidence> persisted =
            new ArrayList<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    persisted.add(
                        new PersistedEvidence(
                            resultSet.getString(
                                "evidence_type"
                            ),
                            resultSet.getString(
                                "raw_value"
                            ),
                            resultSet.getString(
                                "normalized_value"
                            ),
                            resultSet.getString(
                                "source_adapter"
                            ),
                            resultSet.getString(
                                "source_component"
                            ),
                            resultSet.getObject(
                                "observed_at",
                                OffsetDateTime.class
                            )
                        )
                    );
                }
            }
        }

        return List.copyOf(
            persisted
        );
    }

    private void assertEvidence(
        PersistedEvidence evidence,
        String evidenceType,
        String rawValue,
        String normalizedValue,
        String sourceComponent,
        OffsetDateTime enrichedAt
    ) {

        assertEquals(
            evidenceType,
            evidence.evidenceType()
        );

        assertEquals(
            rawValue,
            evidence.rawValue()
        );

        assertEquals(
            normalizedValue,
            evidence.normalizedValue()
        );

        assertEquals(
            "AMAZON_PRODUCT_PAGE",
            evidence.sourceAdapter()
        );

        assertEquals(
            sourceComponent,
            evidence.sourceComponent()
        );

        assertEquals(
            enrichedAt.toInstant(),
            evidence.observedAt().toInstant()
        );
    }

    private void cleanup(
        ApplicationConfig config,
        long snapshotId,
        long productId
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            if (snapshotId > 0) {

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

                    assertTrue(
                        statement.executeUpdate() >= 0
                    );
                }
            }

            if (productId > 0) {

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

                    assertTrue(
                        statement.executeUpdate() >= 0
                    );
                }
            }
        }
    }

    private record PersistedEvidence(
        String evidenceType,
        String rawValue,
        String normalizedValue,
        String sourceAdapter,
        String sourceComponent,
        OffsetDateTime observedAt
    ) {
    }
}
