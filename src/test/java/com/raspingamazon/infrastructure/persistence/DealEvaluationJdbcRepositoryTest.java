package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.RejectionReason;
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
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealEvaluationJdbcRepositoryTest {

    @Test
    void shouldPersistEligibleDealEvaluation() throws Exception {
        ApplicationConfig config = EnvironmentConfigProvider.load();

        long productId = 0;
        long offerSnapshotId = 0;
        long evaluationId = 0;

        try (Connection connection = DatabaseConnection.open(config)) {

            Product product = createProduct(connection);
            productId = product.id();

            offerSnapshotId = createOfferSnapshot(
                    connection,
                    product.id()
            );

            OfferSnapshot offerSnapshot = createOfferSnapshotDomain(
                    product,
                    offerSnapshotId
            );

            OffsetDateTime evaluatedAt = OffsetDateTime.now();

            DealEvaluation evaluation = new DealEvaluation(
                    null,
                    offerSnapshot,
                    true,
                    null,
                    "AMAZON_SELLER_DELIVERY_V1",
                    null,
                    null,
                    evaluatedAt
            );

            DealEvaluationJdbcRepository repository =
                    new DealEvaluationJdbcRepository(connection);

            DealEvaluation persisted = repository.save(evaluation);
            evaluationId = persisted.id();

            assertNotNull(persisted);
            assertNotNull(persisted.id());
            assertTrue(persisted.id() > 0);

            assertEquals(
                    offerSnapshotId,
                    persisted.offerSnapshot().id()
            );

            assertTrue(
                    persisted.eligible()
            );

            assertNull(
                    persisted.rejectionReason()
            );

            assertEquals(
                    "AMAZON_SELLER_DELIVERY_V1",
                    persisted.filterVersion()
            );

            assertNull(
                    persisted.score()
            );

            assertNull(
                    persisted.momentum()
            );

            assertEquals(
                    evaluatedAt.toInstant(),
                    persisted.evaluatedAt().toInstant()
            );

            assertDatabaseRow(
                    connection,
                    persisted.id(),
                    offerSnapshotId,
                    true,
                    null,
                    "AMAZON_SELLER_DELIVERY_V1",
                    null,
                    null,
                    evaluatedAt
            );
        } finally {
            cleanup(
                    config,
                    evaluationId,
                    offerSnapshotId,
                    productId
            );
        }
    }

    @Test
    void shouldPersistRejectedDealEvaluation() throws Exception {
        ApplicationConfig config = EnvironmentConfigProvider.load();

        long productId = 0;
        long offerSnapshotId = 0;
        long evaluationId = 0;

        try (Connection connection = DatabaseConnection.open(config)) {

            Product product = createProduct(connection);
            productId = product.id();

            offerSnapshotId = createOfferSnapshot(
                    connection,
                    product.id()
            );

            OfferSnapshot offerSnapshot = createOfferSnapshotDomain(
                    product,
                    offerSnapshotId
            );

            OffsetDateTime evaluatedAt = OffsetDateTime.now();

            DealEvaluation evaluation = new DealEvaluation(
                    null,
                    offerSnapshot,
                    false,
                    RejectionReason.SELLER_THIRD_PARTY,
                    "AMAZON_SELLER_DELIVERY_V1",
                    null,
                    null,
                    evaluatedAt
            );

            DealEvaluationJdbcRepository repository =
                    new DealEvaluationJdbcRepository(connection);

            DealEvaluation persisted = repository.save(evaluation);
            evaluationId = persisted.id();

            assertNotNull(persisted);
            assertNotNull(persisted.id());
            assertTrue(persisted.id() > 0);

            assertEquals(
                    offerSnapshotId,
                    persisted.offerSnapshot().id()
            );

            assertFalse(
                    persisted.eligible()
            );

            assertEquals(
                    RejectionReason.SELLER_THIRD_PARTY,
                    persisted.rejectionReason()
            );

            assertEquals(
                    "AMAZON_SELLER_DELIVERY_V1",
                    persisted.filterVersion()
            );

            assertNull(
                    persisted.score()
            );

            assertNull(
                    persisted.momentum()
            );

            assertEquals(
                    evaluatedAt.toInstant(),
                    persisted.evaluatedAt().toInstant()
            );

            assertDatabaseRow(
                    connection,
                    persisted.id(),
                    offerSnapshotId,
                    false,
                    RejectionReason.SELLER_THIRD_PARTY.name(),
                    "AMAZON_SELLER_DELIVERY_V1",
                    null,
                    null,
                    evaluatedAt
            );
        } finally {
            cleanup(
                    config,
                    evaluationId,
                    offerSnapshotId,
                    productId
            );
        }
    }

    private Product createProduct(
            Connection connection
    ) throws SQLException {
        ProductRepository repository =
                new ProductRepository(connection);

        String asin =
                "B000TEST86";

        long productId =
                repository.insert(
                        asin,
                        "Produto de teste",
                        null,
                        "https://example.invalid/produto"
                );

        return new Product(
                productId,
                new Asin(asin),
                "Produto de teste",
                null,
                "https://example.invalid/produto"
        );
    }

    private long createOfferSnapshot(
            Connection connection,
            long productId
    ) throws SQLException {
        String sql = """
                INSERT INTO offer_snapshot (
                    product_id,
                    collected_at,
                    current_price,
                    basis_price,
                    previous_price,
                    sold_percentage,
                    rating,
                    review_count,
                    seller_name,
                    delivery_provider,
                    source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    productId
            );

            statement.setObject(
                    2,
                    OffsetDateTime.now()
            );

            statement.setBigDecimal(
                    3,
                    new BigDecimal("99.90")
            );

            statement.setBigDecimal(
                    4,
                    new BigDecimal("129.90")
            );

            statement.setBigDecimal(
                    5,
                    new BigDecimal("119.90")
            );

            statement.setObject(
                    6,
                    null
            );

            statement.setObject(
                    7,
                    null
            );

            statement.setObject(
                    8,
                    null
            );

            statement.setString(
                    9,
                    "Vendedor teste"
            );

            statement.setString(
                    10,
                    "Amazon"
            );

            statement.setString(
                    11,
                    "TEST"
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                            "Failed to obtain generated offer_snapshot id"
                    );
                }

                return resultSet.getLong("id");
            }
        }
    }

    private OfferSnapshot createOfferSnapshotDomain(
            Product product,
            long offerSnapshotId
    ) {
        return new OfferSnapshot(
                offerSnapshotId,
                product,
                OffsetDateTime.now(),
                new Money(
                        new BigDecimal("99.90")
                ),
                new Money(
                        new BigDecimal("129.90")
                ),
                new Money(
                        new BigDecimal("119.90")
                ),
                null,
                null,
                null,
                "Vendedor teste",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST",
                List.of()
        );
    }

    private void assertDatabaseRow(
            Connection connection,
            long evaluationId,
            long offerSnapshotId,
            boolean eligible,
            String rejectionReason,
            String filterVersion,
            BigDecimal score,
            BigDecimal momentum,
            OffsetDateTime evaluatedAt
    ) throws SQLException {
        String sql = """
                SELECT
                    offer_snapshot_id,
                    eligible,
                    rejection_reason,
                    filter_version,
                    score,
                    momentum,
                    evaluated_at
                FROM deal_evaluation
                WHERE id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    evaluationId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next(),
                        "Persisted deal_evaluation row was not found"
                );

                assertEquals(
                        offerSnapshotId,
                        resultSet.getLong(
                                "offer_snapshot_id"
                        )
                );

                assertEquals(
                        eligible,
                        resultSet.getBoolean(
                                "eligible"
                        )
                );

                assertEquals(
                        rejectionReason,
                        resultSet.getString(
                                "rejection_reason"
                        )
                );

                assertEquals(
                        filterVersion,
                        resultSet.getString(
                                "filter_version"
                        )
                );

                assertEquals(
                        score,
                        resultSet.getBigDecimal(
                                "score"
                        )
                );

                assertEquals(
                        momentum,
                        resultSet.getBigDecimal(
                                "momentum"
                        )
                );

                OffsetDateTime databaseEvaluatedAt =
                        resultSet.getObject(
                                "evaluated_at",
                                OffsetDateTime.class
                        );

                Duration difference =
                        Duration.between(
                                evaluatedAt.toInstant(),
                                databaseEvaluatedAt.toInstant()
                        ).abs();

                assertTrue(
                        difference.compareTo(
                                Duration.ofNanos(1_000)
                        ) <= 0,
                        "Database timestamp differs from application timestamp by more than 1 microsecond"
                );
            }
        }
    }

    private void cleanup(
            ApplicationConfig config,
            long evaluationId,
            long offerSnapshotId,
            long productId
    ) {
        if (
                evaluationId == 0
                        && offerSnapshotId == 0
                        && productId == 0
        ) {
            return;
        }

        try (Connection connection =
                     DatabaseConnection.open(config)) {

            if (evaluationId > 0) {
                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     "DELETE FROM deal_evaluation WHERE id = ?"
                             )) {

                    statement.setLong(
                            1,
                            evaluationId
                    );

                    statement.executeUpdate();
                }
            }

            if (offerSnapshotId > 0) {
                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     "DELETE FROM offer_snapshot WHERE id = ?"
                             )) {

                    statement.setLong(
                            1,
                            offerSnapshotId
                    );

                    statement.executeUpdate();
                }
            }

            if (productId > 0) {
                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     "DELETE FROM product WHERE id = ?"
                             )) {

                    statement.setLong(
                            1,
                            productId
                    );

                    statement.executeUpdate();
                }
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to clean up persistence test data",
                    exception
            );
        }
    }
}