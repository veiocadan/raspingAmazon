package com.raspingamazon.application.momentum;

import com.raspingamazon.application.history.OfferHistoryQueryPort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.filter.BestCashDiscountSelector;
import com.raspingamazon.domain.history.HistoricalOfferObservation;
import com.raspingamazon.domain.history.SnapshotEvolutionCalculator;
import com.raspingamazon.domain.momentum.MomentumAudit;
import com.raspingamazon.domain.momentum.MomentumEngine;
import com.raspingamazon.domain.momentum.MomentumUnavailableReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentumCalculationServiceTest {

    private static final Asin ASIN =
        new Asin(
            "B0MOM1104"
        );

    @Test
    void shouldCalculateAvailableMomentumUsingPreviousObservation() {

        HistoricalOfferObservation previous =
            historicalObservation(
                10L,
                "2026-09-20T10:00:00-03:00",
                "100.00",
                "62"
            );

        MomentumCalculationService service =
            serviceWithPrevious(
                previous
            );

        OfferSnapshot current =
            currentSnapshot(
                11L,
                "2026-09-20T13:00:00-03:00",
                "90.00",
                "68"
            );

        MomentumCalculation calculation =
            service.calculate(
                current
            );

        assertTrue(
            calculation.result()
                .isAvailable()
        );

        assertEquals(
            "MOMENTUM_V1",
            calculation.result()
                .version()
        );

        assertEquals(
            0,
            calculation.result()
                .value()
                .compareTo(
                    new java.math.BigDecimal(
                        "2.0000"
                    )
                )
        );

        assertEquals(
            0,
            calculation.dealEvaluationMomentum()
                .compareTo(
                    new java.math.BigDecimal(
                        "2.0000"
                    )
                )
        );

        assertEquals(
            "MOMENTUM_V1",
            calculation
                .dealEvaluationMomentumVersion()
        );

        assertEquals(
            10L,
            calculation.evolution()
                .previousSnapshotId()
        );

        assertEquals(
            11L,
            calculation.evolution()
                .currentSnapshotId()
        );
    }

    @Test
    void shouldReturnUnavailableWhenThereIsNoPreviousObservation() {

        MomentumCalculationService service =
            serviceWithPrevious(
                null
            );

        OfferSnapshot current =
            currentSnapshot(
                20L,
                "2026-09-20T13:00:00-03:00",
                "90.00",
                "68"
            );

        MomentumCalculation calculation =
            service.calculate(
                current
            );

        assertFalse(
            calculation.result()
                .isAvailable()
        );

        assertEquals(
            MomentumUnavailableReason.NO_PREVIOUS_SNAPSHOT,
            calculation.result()
                .unavailableReason()
        );

        assertNull(
            calculation.evolution()
        );

        assertNull(
            calculation.dealEvaluationMomentum()
        );

        assertNull(
            calculation.dealEvaluationMomentumVersion()
        );

        MomentumAudit audit =
            calculation.toAudit(
                100L
            );

        assertEquals(
            100L,
            audit.dealEvaluationId()
        );

        assertEquals(
            20L,
            audit.currentOfferSnapshotId()
        );

        assertEquals(
            MomentumUnavailableReason.NO_PREVIOUS_SNAPSHOT,
            audit.unavailableReason()
        );
    }

    @Test
    void shouldReturnUnavailableWhenPreviousSoldPercentageIsMissing() {

        HistoricalOfferObservation previous =
            historicalObservation(
                30L,
                "2026-09-20T10:00:00-03:00",
                "100.00",
                null
            );

        MomentumCalculationService service =
            serviceWithPrevious(
                previous
            );

        OfferSnapshot current =
            currentSnapshot(
                31L,
                "2026-09-20T13:00:00-03:00",
                "90.00",
                "68"
            );

        MomentumCalculation calculation =
            service.calculate(
                current
            );

        assertFalse(
            calculation.result()
                .isAvailable()
        );

        assertEquals(
            MomentumUnavailableReason
                .SOLD_PERCENTAGE_UNAVAILABLE,
            calculation.result()
                .unavailableReason()
        );

        assertTrue(
            calculation.evolution() != null
        );

        assertNull(
            calculation
                .dealEvaluationMomentum()
        );

        assertNull(
            calculation
                .dealEvaluationMomentumVersion()
        );

        assertEquals(
            10_800L,
            calculation.evolution()
                .elapsedSeconds()
        );
    }

    @Test
    void shouldBuildAvailableAuditFromCalculation() {

        HistoricalOfferObservation previous =
            historicalObservation(
                40L,
                "2026-09-20T10:00:00-03:00",
                "100.00",
                "62"
            );

        MomentumCalculationService service =
            serviceWithPrevious(
                previous
            );

        OfferSnapshot current =
            currentSnapshot(
                41L,
                "2026-09-20T13:00:00-03:00",
                "90.00",
                "68"
            );

        MomentumCalculation calculation =
            service.calculate(
                current
            );

        MomentumAudit audit =
            calculation.toAudit(
                200L
            );

        assertEquals(
            200L,
            audit.dealEvaluationId()
        );

        assertEquals(
            41L,
            audit.currentOfferSnapshotId()
        );

        assertEquals(
            40L,
            audit.previousOfferSnapshotId()
        );

        assertEquals(
            10_800L,
            audit.elapsedSeconds()
        );

        assertTrue(
            audit.isAvailable()
        );

        assertEquals(
            0,
            audit.momentum()
                .compareTo(
                    new java.math.BigDecimal(
                        "2.0000"
                    )
                )
        );
    }

    private MomentumCalculationService serviceWithPrevious(
        HistoricalOfferObservation previous
    ) {

        OfferHistoryQueryPort historyQueryPort =
            new StubOfferHistoryQueryPort(
                previous
            );

        return new MomentumCalculationService(
            historyQueryPort,
            new SnapshotEvolutionCalculator(
                new BestCashDiscountSelector()
            ),
            new MomentumEngine()
        );
    }

    private HistoricalOfferObservation historicalObservation(
        long snapshotId,
        String collectedAt,
        String currentPrice,
        String soldPercentage
    ) {

        return new HistoricalOfferObservation(
            snapshotId,
            ASIN,
            OffsetDateTime.parse(
                collectedAt
            ),
            Money.of(
                currentPrice
            ),
            soldPercentage == null
                ? null
                : Percentage.of(
                soldPercentage
            ),
            "momentum-calculation-test",
            List.of()
        );
    }

    private OfferSnapshot currentSnapshot(
        long snapshotId,
        String collectedAt,
        String currentPrice,
        String soldPercentage
    ) {

        Product product =
            new Product(
                1L,
                ASIN,
                "Produto Momentum",
                null,
                "https://example.invalid/momentum"
            );

        return new OfferSnapshot(
            snapshotId,
            product,
            OffsetDateTime.parse(
                collectedAt
            ),
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
            "momentum-calculation-test",
            List.of()
        );
    }

    /**
     * Stub propositalmente pequeno.
     *
     * <p>Este teste verifica a orquestração de aplicação, não JDBC.</p>
     */
    private static final class StubOfferHistoryQueryPort
        implements OfferHistoryQueryPort {

        private final HistoricalOfferObservation
            previous;

        private StubOfferHistoryQueryPort(
            HistoricalOfferObservation previous
        ) {

            this.previous =
                previous;
        }

        @Override
        public List<HistoricalOfferObservation> findHistoryByAsin(
            Asin asin
        ) {

            return previous == null
                ? List.of()
                : List.of(
                previous
            );
        }

        @Override
        public Optional<HistoricalOfferObservation> findFirstByAsin(
            Asin asin
        ) {

            return Optional.ofNullable(
                previous
            );
        }

        @Override
        public Optional<HistoricalOfferObservation> findLatestByAsin(
            Asin asin
        ) {

            return Optional.ofNullable(
                previous
            );
        }

        @Override
        public Optional<HistoricalOfferObservation> findPreviousByAsin(
            Asin asin,
            OffsetDateTime collectedAt
        ) {

            return Optional.ofNullable(
                previous
            );
        }

        @Override
        public long countByAsin(
            Asin asin
        ) {

            return previous == null
                ? 0L
                : 1L;
        }
    }
}
