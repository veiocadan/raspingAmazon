package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.deal.OfferSnapshot;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OfferPaymentConditionRepositoryTest {

    @Test
    void shouldPersistCashPaymentCondition() throws Exception {

        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String database = System.getenv("DB_NAME");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        String asinValue = "B000TEST04";

        long productId = 0;
        long snapshotId = 0;
        long conditionId = 0;

        try (Connection connection = DatabaseConnection.open(
                host,
                port,
                database,
                username,
                password
        )) {

            ProductRepository productRepository =
                    new ProductRepository(connection);

            productId = productRepository.insert(
                    asinValue,
                    "Produto de teste da condição comercial",
                    null,
                    "https://example.invalid/payment-condition"
            );

            Product product = new Product(
                    productId,
                    new Asin(asinValue),
                    "Produto de teste da condição comercial",
                    null,
                    "https://example.invalid/payment-condition"
            );

            OfferSnapshot snapshot = new OfferSnapshot(
                    null,
                    product,
                    OffsetDateTime.parse(
                            "2026-09-15T18:00:00-03:00"
                    ),
                    new Money(new BigDecimal("161.40")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Amazon.com.br",
                    "Amazon",
                    SellerType.AMAZON,
                    DeliveryType.AMAZON,
                    "amazon"
            );

            OfferSnapshotRepository snapshotRepository =
                    new OfferSnapshotRepository(connection);

            snapshotId = snapshotRepository.insert(snapshot);

            PaymentCondition condition = new PaymentCondition(
                    PaymentConditionType.CASH,
                    new Money(new BigDecimal("161.40")),
                    new Percentage(new BigDecimal("15.00")),
                    null,
                    null,
                    null,
                    null,
                    List.of(
                            PaymentMethod.PIX,
                            PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                    )
            );

            OfferPaymentConditionRepository repository =
                    new OfferPaymentConditionRepository(connection);

            conditionId = repository.insert(
                    snapshotId,
                    condition
            );

            assertTrue(conditionId > 0);

            String conditionSql = """
                    SELECT
                        offer_snapshot_id,
                        condition_type,
                        price,
                        discount_percentage,
                        installment_count,
                        installment_amount,
                        installment_total,
                        interest
                    FROM offer_payment_condition
                    WHERE id = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(conditionSql)) {

                statement.setLong(1, conditionId);

                try (ResultSet resultSet = statement.executeQuery()) {

                    assertTrue(resultSet.next());

                    assertEquals(
                            snapshotId,
                            resultSet.getLong("offer_snapshot_id")
                    );

                    assertEquals(
                            "CASH",
                            resultSet.getString("condition_type")
                    );

                    assertEquals(
                            new BigDecimal("161.40"),
                            resultSet.getBigDecimal("price")
                    );

                    assertEquals(
                            new BigDecimal("15.00"),
                            resultSet.getBigDecimal(
                                    "discount_percentage"
                            )
                    );

                    assertEquals(
                            null,
                            resultSet.getObject("installment_count")
                    );

                    assertEquals(
                            null,
                            resultSet.getBigDecimal(
                                    "installment_amount"
                            )
                    );

                    assertEquals(
                            null,
                            resultSet.getBigDecimal(
                                    "installment_total"
                            )
                    );

                    assertEquals(
                            null,
                            resultSet.getBigDecimal("interest")
                    );
                }
            }

            String methodSql = """
                    SELECT payment_method
                    FROM offer_payment_condition_method
                    WHERE payment_condition_id = ?
                    ORDER BY payment_method
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(methodSql)) {

                statement.setLong(1, conditionId);

                try (ResultSet resultSet = statement.executeQuery()) {

                    assertTrue(resultSet.next());

                    assertEquals(
                            "NUPAY_ADDITIONAL_LIMIT",
                            resultSet.getString("payment_method")
                    );

                    assertTrue(resultSet.next());

                    assertEquals(
                            "PIX",
                            resultSet.getString("payment_method")
                    );
                }
            }

        } finally {

            try (Connection connection = DatabaseConnection.open(
                    host,
                    port,
                    database,
                    username,
                    password
            )) {

                if (conditionId > 0) {

                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         "DELETE FROM offer_payment_condition_method " +
                                         "WHERE payment_condition_id = ?"
                                 )) {

                        statement.setLong(1, conditionId);
                        statement.executeUpdate();
                    }

                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         "DELETE FROM offer_payment_condition " +
                                         "WHERE id = ?"
                                 )) {

                        statement.setLong(1, conditionId);
                        statement.executeUpdate();
                    }
                }

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

    @Test
    void shouldPersistCreditInstallmentCondition() throws Exception {

        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String database = System.getenv("DB_NAME");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        String asinValue = "B000TEST05";

        long productId = 0;
        long snapshotId = 0;
        long conditionId = 0;

        try (Connection connection = DatabaseConnection.open(
                host,
                port,
                database,
                username,
                password
        )) {

            ProductRepository productRepository =
                    new ProductRepository(connection);

            productId = productRepository.insert(
                    asinValue,
                    "Produto de teste parcelado",
                    "https://example.com/image5.jpg",
                    "https://example.com/product5"
            );

            Product product = new Product(
                    productId,
                    new Asin(asinValue),
                    "Produto de teste parcelado",
                    "https://example.com/image5.jpg",
                    "https://example.com/product5"
            );

            OfferSnapshot snapshot = new OfferSnapshot(
                    null,
                    product,
                    OffsetDateTime.parse(
                            "2026-09-15T18:00:00-03:00"
                    ),
                    new Money(new BigDecimal("161.40")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Amazon.com.br",
                    "Amazon",
                    SellerType.AMAZON,
                    DeliveryType.AMAZON,
                    "amazon"
            );

            OfferSnapshotRepository snapshotRepository =
                    new OfferSnapshotRepository(connection);

            snapshotId = snapshotRepository.insert(snapshot);

            PaymentCondition condition = new PaymentCondition(
                    PaymentConditionType.CREDIT_INSTALLMENT,
                    null,
                    null,
                    6,
                    new Money(new BigDecimal("31.65")),
                    new Money(new BigDecimal("189.90")),
                    new Percentage(BigDecimal.ZERO),
                    List.of(PaymentMethod.CREDIT_CARD)
            );

            OfferPaymentConditionRepository repository =
                    new OfferPaymentConditionRepository(connection);

            conditionId = repository.insert(
                    snapshotId,
                    condition
            );

            assertTrue(conditionId > 0);

            String conditionSql = """
                    SELECT
                        offer_snapshot_id,
                        condition_type,
                        price,
                        discount_percentage,
                        installment_count,
                        installment_amount,
                        installment_total,
                        interest
                    FROM offer_payment_condition
                    WHERE id = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(conditionSql)) {

                statement.setLong(1, conditionId);

                try (ResultSet resultSet = statement.executeQuery()) {

                    assertTrue(resultSet.next());

                    assertEquals(
                            snapshotId,
                            resultSet.getLong("offer_snapshot_id")
                    );

                    assertEquals(
                            "CREDIT_INSTALLMENT",
                            resultSet.getString("condition_type")
                    );

                    assertEquals(
                            null,
                            resultSet.getBigDecimal("price")
                    );

                    assertEquals(
                            null,
                            resultSet.getBigDecimal(
                                    "discount_percentage"
                            )
                    );

                    assertEquals(
                            6,
                            resultSet.getInt("installment_count")
                    );

                    assertEquals(
                            new BigDecimal("31.65"),
                            resultSet.getBigDecimal(
                                    "installment_amount"
                            )
                    );

                    assertEquals(
                            new BigDecimal("189.90"),
                            resultSet.getBigDecimal(
                                    "installment_total"
                            )
                    );

                    assertEquals(
                            BigDecimal.ZERO,
                            resultSet.getBigDecimal("interest")
                    );
                }
            }

            String methodSql = """
                    SELECT payment_method
                    FROM offer_payment_condition_method
                    WHERE payment_condition_id = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(methodSql)) {

                statement.setLong(1, conditionId);

                try (ResultSet resultSet = statement.executeQuery()) {

                    assertTrue(resultSet.next());

                    assertEquals(
                            "CREDIT_CARD",
                            resultSet.getString("payment_method")
                    );

                    assertFalse(resultSet.next());
                }
            }

        } finally {

            try (Connection connection = DatabaseConnection.open(
                    host,
                    port,
                    database,
                    username,
                    password
            )) {

                if (conditionId > 0) {

                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         "DELETE FROM offer_payment_condition_method " +
                                         "WHERE payment_condition_id = ?"
                                 )) {

                        statement.setLong(1, conditionId);
                        statement.executeUpdate();
                    }

                    try (PreparedStatement statement =
                                 connection.prepareStatement(
                                         "DELETE FROM offer_payment_condition " +
                                         "WHERE id = ?"
                                 )) {

                        statement.setLong(1, conditionId);
                        statement.executeUpdate();
                    }
                }

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