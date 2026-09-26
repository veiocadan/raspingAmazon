package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa a persistência de OfferSnapshot no PostgreSQL.
 *
 * <p>O objetivo deste teste é verificar a integração entre o objeto
 * de domínio OfferSnapshot e a tabela offer_snapshot criada pelas
 * migrations.</p>
 *
 * <p>As condições comerciais de pagamento são persistidas
 * separadamente por OfferPaymentConditionRepository.</p>
 */
@PostgresIntegrationTest
class OfferSnapshotRepositoryTest {

    @Test
    void shouldPersistOfferSnapshot() throws Exception {

        ApplicationConfig config = EnvironmentConfigProvider.load();

        String asinValue = "B000TEST03";

        long productId = 0;
        long snapshotId = 0;

        try (Connection connection = DatabaseConnection.open(config)) {

            ProductRepository productRepository =
                    new ProductRepository(connection);

            productId = productRepository.insert(
                    asinValue,
                    "Produto de teste do OfferSnapshot",
                    null,
                    "https://example.invalid/offer-snapshot"
            );

            Product product = new Product(
                    productId,
                    new Asin(asinValue),
                    "Produto de teste do OfferSnapshot",
                    null,
                    "https://example.invalid/offer-snapshot"
            );

            OffsetDateTime collectedAt = OffsetDateTime.parse(
                    "2026-09-15T18:00:00-03:00"
            );

            OfferSnapshot snapshot = new OfferSnapshot(
                    null,
                    product,
                    collectedAt,
                    new Money(new BigDecimal("161.40")),
                    new Money(new BigDecimal("299.00")),
                    new Money(new BigDecimal("199.90")),
                    new Percentage(new BigDecimal("30.00")),
                    4.7,
                    1234L,
                    "Amazon.com.br",
                    "Amazon",
                    SellerType.AMAZON,
                    DeliveryType.AMAZON,
                    "amazon",
                    List.of()
            );

            OfferSnapshotRepository repository =
                    new OfferSnapshotRepository(connection);

            snapshotId = repository.insert(snapshot);

            assertTrue(snapshotId > 0);

            String sql = """
                    SELECT
                        product_id,
                        collected_at,
                        current_price,
                        basis_price,
                        previous_price,
                        discount_percentage,
                        sold_percentage,
                        rating,
                        review_count,
                        seller_name,
                        delivery_provider,
                        source
                    FROM offer_snapshot
                    WHERE id = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setLong(1, snapshotId);

                try (ResultSet resultSet =
                             statement.executeQuery()) {

                    assertTrue(resultSet.next());

                    assertEquals(
                            productId,
                            resultSet.getLong("product_id")
                    );

                    assertEquals(
                            collectedAt.toInstant(),
                            resultSet.getObject(
                                    "collected_at",
                                    OffsetDateTime.class
                            ).toInstant()
                    );

                    assertEquals(
                            new BigDecimal("161.40"),
                            resultSet.getBigDecimal("current_price")
                    );

                    assertEquals(
                            new BigDecimal("299.00"),
                            resultSet.getBigDecimal("basis_price")
                    );

                    assertEquals(
                            new BigDecimal("199.90"),
                            resultSet.getBigDecimal("previous_price")
                    );

                    /*
                     * discount_percentage deixou de fazer parte
                     * do estado universal de OfferSnapshot.
                     *
                     * A informação comercial de desconto pertence
                     * à PaymentCondition.
                     *
                     * A coluna antiga continua existindo no schema
                     * por enquanto, mas não é mais preenchida pelo
                     * OfferSnapshotRepository.
                     */
                    assertNull(
                            resultSet.getBigDecimal("discount_percentage")
                    );

                    assertEquals(
                            new BigDecimal("30.00"),
                            resultSet.getBigDecimal("sold_percentage")
                    );

                    assertEquals(
                            4.7,
                            resultSet.getDouble("rating")
                    );

                    assertEquals(
                            1234L,
                            resultSet.getLong("review_count")
                    );

                    assertEquals(
                            "Amazon.com.br",
                            resultSet.getString("seller_name")
                    );

                    assertEquals(
                            "Amazon",
                            resultSet.getString("delivery_provider")
                    );

                    assertEquals(
                            "amazon",
                            resultSet.getString("source")
                    );
                }
            }

        } finally {

            try (Connection connection = DatabaseConnection.open(config)) {

                if (snapshotId > 0) {

                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         "DELETE FROM offer_snapshot WHERE id = ?"
                                 )) {

                        statement.setLong(1, snapshotId);
                        statement.executeUpdate();
                    }
                }

                if (productId > 0) {

                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         "DELETE FROM product WHERE id = ?"
                                 )) {

                        statement.setLong(1, productId);
                        statement.executeUpdate();
                    }
                }
            }
        }
    }
}
