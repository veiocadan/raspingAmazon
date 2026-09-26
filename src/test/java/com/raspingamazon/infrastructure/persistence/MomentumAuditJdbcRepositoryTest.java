package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.history.SnapshotEvolution;
import com.raspingamazon.domain.momentum.MomentumAudit;
import com.raspingamazon.domain.momentum.MomentumEngine;
import com.raspingamazon.domain.momentum.MomentumResult;
import com.raspingamazon.domain.momentum.MomentumUnavailableReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class MomentumAuditJdbcRepositoryTest {

    @Test
    void shouldPersistAvailableMomentumAudit()
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

                Asin asin =
                    new Asin(
                        "B0MOM1101"
                    );

                Product product =
                    createProduct(
                        connection,
                        asin
                    );

                OffsetDateTime previousCollectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T10:00:00-03:00"
                    );

                OffsetDateTime currentCollectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T13:00:00-03:00"
                    );

                OfferSnapshotRepository snapshotRepository =
                    new OfferSnapshotRepository(
                        connection
                    );

                long previousSnapshotId =
                    snapshotRepository.insert(
                        snapshot(
                            product,
                            previousCollectedAt,
                            "100.00",
                            "62"
                        )
                    );

                long currentSnapshotId =
                    snapshotRepository.insert(
                        snapshot(
                            product,
                            currentCollectedAt,
                            "90.00",
                            "68"
                        )
                    );

                OfferSnapshot currentSnapshot =
                    persistedSnapshot(
                        currentSnapshotId,
                        product,
                        currentCollectedAt,
                        "90.00",
                        "68"
                    );

                SnapshotEvolution evolution =
                    new SnapshotEvolution(
                        asin,
                        previousSnapshotId,
                        currentSnapshotId,
                        previousCollectedAt,
                        currentCollectedAt,
                        new BigDecimal(
                            "6"
                        ),
                        new BigDecimal(
                            "-10.00"
                        ),
                        new BigDecimal(
                            "-10.0000"
                        ),
                        new BigDecimal(
                            "5"
                        )
                    );

                MomentumResult momentumResult =
                    new MomentumEngine()
                        .calculate(
                            evolution
                        );

                DealEvaluation evaluation =
                    persistEvaluation(
                        connection,
                        currentSnapshot,
                        momentumResult.value(),
                        momentumResult.version()
                    );

                MomentumAudit audit =
                    MomentumAudit.fromEvolution(
                        evaluation.id(),
                        evolution,
                        momentumResult
                    );

                MomentumAuditJdbcRepository repository =
                    new MomentumAuditJdbcRepository(
                        connection
                    );

                MomentumAudit persisted =
                    repository.save(
                        audit
                    );

                assertNotNull(
                    persisted.id()
                );

                assertTrue(
                    persisted.id() > 0
                );

                PersistedMomentumAudit row =
                    loadAudit(
                        connection,
                        persisted.id()
                    );

                assertEquals(
                    evaluation.id().longValue(),
                    row.dealEvaluationId()
                );

                assertEquals(
                    "MOMENTUM_V1",
                    row.calculationVersion()
                );

                assertEquals(
                    "AVAILABLE",
                    row.status()
                );

                assertNull(
                    row.unavailableReason()
                );

                assertEquals(
                    previousSnapshotId,
                    row.previousOfferSnapshotId()
                );

                assertEquals(
                    10_800L,
                    row.elapsedSeconds()
                );

                assertBigDecimalEquals(
                    "6",
                    row.soldPercentageDelta()
                );

                assertBigDecimalEquals(
                    "-10.00",
                    row.currentPriceDelta()
                );

                assertBigDecimalEquals(
                    "-10.0000",
                    row.currentPriceDeltaPercentage()
                );

                assertBigDecimalEquals(
                    "5",
                    row.cashDiscountDelta()
                );

                assertBigDecimalEquals(
                    "2.0000",
                    row.momentum()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldPersistUnavailableAuditWhenThereIsNoPreviousSnapshot()
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

                Asin asin =
                    new Asin(
                        "B0MOM1102"
                    );

                Product product =
                    createProduct(
                        connection,
                        asin
                    );

                OffsetDateTime collectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T10:00:00-03:00"
                    );

                OfferSnapshotRepository snapshotRepository =
                    new OfferSnapshotRepository(
                        connection
                    );

                long currentSnapshotId =
                    snapshotRepository.insert(
                        snapshot(
                            product,
                            collectedAt,
                            "100.00",
                            "62"
                        )
                    );

                OfferSnapshot currentSnapshot =
                    persistedSnapshot(
                        currentSnapshotId,
                        product,
                        collectedAt,
                        "100.00",
                        "62"
                    );

                DealEvaluation evaluation =
                    persistEvaluation(
                        connection,
                        currentSnapshot,
                        null,
                        null
                    );

                MomentumResult momentumResult =
                    MomentumResult.unavailable(
                        MomentumEngine.VERSION,
                        MomentumUnavailableReason
                            .NO_PREVIOUS_SNAPSHOT
                    );

                MomentumAudit audit =
                    MomentumAudit.withoutPreviousSnapshot(
                        evaluation.id(),
                        currentSnapshotId,
                        momentumResult
                    );

                MomentumAudit persisted =
                    new MomentumAuditJdbcRepository(
                        connection
                    ).save(
                        audit
                    );

                PersistedMomentumAudit row =
                    loadAudit(
                        connection,
                        persisted.id()
                    );

                assertEquals(
                    "MOMENTUM_V1",
                    row.calculationVersion()
                );

                assertEquals(
                    "UNAVAILABLE",
                    row.status()
                );

                assertEquals(
                    "NO_PREVIOUS_SNAPSHOT",
                    row.unavailableReason()
                );

                assertNull(
                    row.previousOfferSnapshotId()
                );

                assertNull(
                    row.elapsedSeconds()
                );

                assertNull(
                    row.soldPercentageDelta()
                );

                assertNull(
                    row.currentPriceDelta()
                );

                assertNull(
                    row.currentPriceDeltaPercentage()
                );

                assertNull(
                    row.cashDiscountDelta()
                );

                assertNull(
                    row.momentum()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldPersistUnavailableAuditWhenSoldPercentageIsMissing()
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

                Asin asin =
                    new Asin(
                        "B0MOM1103"
                    );

                Product product =
                    createProduct(
                        connection,
                        asin
                    );

                OffsetDateTime previousCollectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T10:00:00-03:00"
                    );

                OffsetDateTime currentCollectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T13:00:00-03:00"
                    );

                OfferSnapshotRepository snapshotRepository =
                    new OfferSnapshotRepository(
                        connection
                    );

                long previousSnapshotId =
                    snapshotRepository.insert(
                        snapshot(
                            product,
                            previousCollectedAt,
                            "100.00",
                            null
                        )
                    );

                long currentSnapshotId =
                    snapshotRepository.insert(
                        snapshot(
                            product,
                            currentCollectedAt,
                            "90.00",
                            "68"
                        )
                    );

                OfferSnapshot currentSnapshot =
                    persistedSnapshot(
                        currentSnapshotId,
                        product,
                        currentCollectedAt,
                        "90.00",
                        "68"
                    );

                SnapshotEvolution evolution =
                    new SnapshotEvolution(
                        asin,
                        previousSnapshotId,
                        currentSnapshotId,
                        previousCollectedAt,
                        currentCollectedAt,
                        null,
                        new BigDecimal(
                            "-10.00"
                        ),
                        new BigDecimal(
                            "-10.0000"
                        ),
                        null
                    );

                MomentumResult momentumResult =
                    new MomentumEngine()
                        .calculate(
                            evolution
                        );

                DealEvaluation evaluation =
                    persistEvaluation(
                        connection,
                        currentSnapshot,
                        null,
                        null
                    );

                MomentumAudit audit =
                    MomentumAudit.fromEvolution(
                        evaluation.id(),
                        evolution,
                        momentumResult
                    );

                MomentumAudit persisted =
                    new MomentumAuditJdbcRepository(
                        connection
                    ).save(
                        audit
                    );

                PersistedMomentumAudit row =
                    loadAudit(
                        connection,
                        persisted.id()
                    );

                assertEquals(
                    "UNAVAILABLE",
                    row.status()
                );

                assertEquals(
                    "SOLD_PERCENTAGE_UNAVAILABLE",
                    row.unavailableReason()
                );

                assertEquals(
                    previousSnapshotId,
                    row.previousOfferSnapshotId()
                );

                assertEquals(
                    10_800L,
                    row.elapsedSeconds()
                );

                assertNull(
                    row.soldPercentageDelta()
                );

                assertBigDecimalEquals(
                    "-10.00",
                    row.currentPriceDelta()
                );

                assertBigDecimalEquals(
                    "-10.0000",
                    row.currentPriceDeltaPercentage()
                );

                assertNull(
                    row.cashDiscountDelta()
                );

                assertNull(
                    row.momentum()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private Product createProduct(
        Connection connection,
        Asin asin
    ) throws Exception {

        ProductRepository repository =
            new ProductRepository(
                connection
            );

        long productId =
            repository.insert(
                asin.value(),
                "Produto de auditoria de momentum",
                null,
                "https://example.invalid/momentum-audit"
            );

        return new Product(
            productId,
            asin,
            "Produto de auditoria de momentum",
            null,
            "https://example.invalid/momentum-audit"
        );
    }

    private OfferSnapshot snapshot(
        Product product,
        OffsetDateTime collectedAt,
        String currentPrice,
        String soldPercentage
    ) {

        return new OfferSnapshot(
            null,
            product,
            collectedAt,
            Money.of(
                currentPrice
            ),
            null,
            null,
            soldPercentage == null
                ? null
                : Percentage.of(
                soldPercentage
            ),
            4.8,
            2000L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "momentum-audit-test",
            List.of()
        );
    }

    private OfferSnapshot persistedSnapshot(
        long id,
        Product product,
        OffsetDateTime collectedAt,
        String currentPrice,
        String soldPercentage
    ) {

        return new OfferSnapshot(
            id,
            product,
            collectedAt,
            Money.of(
                currentPrice
            ),
            null,
            null,
            soldPercentage == null
                ? null
                : Percentage.of(
                soldPercentage
            ),
            4.8,
            2000L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "momentum-audit-test",
            List.of()
        );
    }

    private DealEvaluation persistEvaluation(
        Connection connection,
        OfferSnapshot offerSnapshot,
        BigDecimal momentum,
        String momentumVersion
    ) {

        DealEvaluation evaluation =
            new DealEvaluation(
                null,
                offerSnapshot,
                true,
                null,
                "AMAZON_SELLER_DELIVERY_V1",
                null,
                passedRuleResults(),
                null,
                null,
                momentum,
                momentumVersion,
                OffsetDateTime.parse(
                    "2026-09-20T19:00:00-03:00"
                )
            );

        return new DealEvaluationJdbcRepository(
            connection
        ).save(
            evaluation
        );
    }

    private List<EvaluationRuleResult> passedRuleResults() {

        return List.of(
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
        );
    }

    private PersistedMomentumAudit loadAudit(
        Connection connection,
        long auditId
    ) throws Exception {

        String sql = """
            SELECT
                deal_evaluation_id,
                calculation_version,
                status,
                unavailable_reason,
                previous_offer_snapshot_id,
                elapsed_seconds,
                sold_percentage_delta,
                current_price_delta,
                current_price_delta_percentage,
                cash_discount_delta,
                momentum
            FROM deal_evaluation_momentum_audit
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                auditId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return new PersistedMomentumAudit(
                    resultSet.getLong(
                        "deal_evaluation_id"
                    ),
                    resultSet.getString(
                        "calculation_version"
                    ),
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getString(
                        "unavailable_reason"
                    ),
                    nullableLong(
                        resultSet,
                        "previous_offer_snapshot_id"
                    ),
                    nullableLong(
                        resultSet,
                        "elapsed_seconds"
                    ),
                    resultSet.getBigDecimal(
                        "sold_percentage_delta"
                    ),
                    resultSet.getBigDecimal(
                        "current_price_delta"
                    ),
                    resultSet.getBigDecimal(
                        "current_price_delta_percentage"
                    ),
                    resultSet.getBigDecimal(
                        "cash_discount_delta"
                    ),
                    resultSet.getBigDecimal(
                        "momentum"
                    )
                );
            }
        }
    }

    private Long nullableLong(
        ResultSet resultSet,
        String column
    ) throws Exception {

        long value =
            resultSet.getLong(
                column
            );

        return resultSet.wasNull()
            ? null
            : value;
    }

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {

        assertNotNull(
            actual
        );

        assertEquals(
            0,
            new BigDecimal(
                expected
            ).compareTo(
                actual
            )
        );
    }

    private record PersistedMomentumAudit(
        long dealEvaluationId,
        String calculationVersion,
        String status,
        String unavailableReason,
        Long previousOfferSnapshotId,
        Long elapsedSeconds,
        BigDecimal soldPercentageDelta,
        BigDecimal currentPriceDelta,
        BigDecimal currentPriceDeltaPercentage,
        BigDecimal cashDiscountDelta,
        BigDecimal momentum
    ) {
    }
}
