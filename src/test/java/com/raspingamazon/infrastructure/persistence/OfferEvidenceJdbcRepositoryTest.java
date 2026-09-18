package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
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
 * Teste de integração da persistência das evidências de enriquecimento.
 *
 * <p>O objetivo é garantir que seller e delivery cheguem ao PostgreSQL
 * sem perder:</p>
 *
 * <ul>
 *     <li>tipo da evidência;</li>
 *     <li>valor bruto;</li>
 *     <li>valor normalizado;</li>
 *     <li>adaptador de origem;</li>
 *     <li>componente específico da origem;</li>
 *     <li>timestamp do enriquecimento.</li>
 * </ul>
 */
class OfferEvidenceJdbcRepositoryTest {

    @Test
    void shouldPersistSellerAndDeliveryEvidence()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();

        /*
         * Garante que a migration V3 tenha sido aplicada antes
         * deste teste.
         *
         * Flyway é idempotente: migrations já aplicadas não são
         * executadas novamente.
         */
        DatabaseMigration.migrate(
                config
        );

        String asinValue =
                "B000EV0001";

        long productId = 0;
        long snapshotId = 0;

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            /*
             * Primeiro precisamos criar um Product, pois
             * offer_snapshot possui foreign key para product.
             */
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
                            "2026-09-18T18:00:00-03:00"
                    );

            /*
             * O snapshot continua mantendo os campos existentes
             * por compatibilidade com o modelo atual.
             *
             * A provenance detalhada será armazenada separadamente.
             */
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
                            null,
                            null,
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
                            "2026-09-18T18:01:30-03:00"
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

            /*
             * Agora lemos diretamente o banco.
             *
             * Não queremos testar apenas que "insert não lançou erro".
             * Precisamos provar que cada campo chegou corretamente.
             */
            String sql = """
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

            /*
             * Esperamos exatamente duas evidências.
             */
            assertEquals(
                    2,
                    persisted.size()
            );

            /*
             * ORDER BY evidence_type coloca DELIVERY antes de SELLER.
             */
            PersistedEvidence delivery =
                    persisted.get(
                            0
                    );

            assertEquals(
                    "DELIVERY",
                    delivery.evidenceType()
            );

            assertEquals(
                    "Amazon",
                    delivery.rawValue()
            );

            assertEquals(
                    "AMAZON",
                    delivery.normalizedValue()
            );

            assertEquals(
                    "AMAZON_PRODUCT_PAGE",
                    delivery.sourceAdapter()
            );

            assertEquals(
                    "fulfillerInfoFeature",
                    delivery.sourceComponent()
            );

            assertEquals(
                    enrichedAt.toInstant(),
                    delivery.observedAt().toInstant()
            );

            PersistedEvidence seller =
                    persisted.get(
                            1
                    );

            assertEquals(
                    "SELLER",
                    seller.evidenceType()
            );

            assertEquals(
                    "Amazon.com.br",
                    seller.rawValue()
            );

            assertEquals(
                    "AMAZON",
                    seller.normalizedValue()
            );

            assertEquals(
                    "AMAZON_PRODUCT_PAGE",
                    seller.sourceAdapter()
            );

            assertEquals(
                    "merchantInfoFeature",
                    seller.sourceComponent()
            );

            assertEquals(
                    enrichedAt.toInstant(),
                    seller.observedAt().toInstant()
            );

        } finally {

            /*
             * Como offer_evidence possui ON DELETE CASCADE,
             * apagar o snapshot também elimina suas evidências.
             */
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
    }

    /**
     * Estrutura auxiliar utilizada somente pelo teste para representar
     * uma linha lida diretamente de offer_evidence.
     */
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