package com.raspingamazon.domain.history;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.filter.BestCashDiscountSelector;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotEvolutionCalculatorTest {

    private static final Asin ASIN =
        new Asin(
            "B0HIST1101"
        );

    private final SnapshotEvolutionCalculator calculator =
        new SnapshotEvolutionCalculator(
            new BestCashDiscountSelector()
        );

    @Test
    void shouldCalculateCompleteEvolution() {

        HistoricalOfferObservation previous =
            observation(
                1L,
                ASIN,
                "2026-09-20T10:00:00-03:00",
                "100.00",
                "62",
                cashCondition(
                    "10"
                )
            );

        HistoricalOfferObservation current =
            observation(
                2L,
                ASIN,
                "2026-09-20T13:00:00-03:00",
                "90.00",
                "68",
                cashCondition(
                    "20"
                )
            );

        SnapshotEvolution evolution =
            calculator.calculate(
                previous,
                current
            );

        assertEquals(
            ASIN,
            evolution.asin()
        );

        assertEquals(
            1L,
            evolution.previousSnapshotId()
        );

        assertEquals(
            2L,
            evolution.currentSnapshotId()
        );

        assertEquals(
            10_800L,
            evolution.elapsedSeconds()
        );

        assertBigDecimalEquals(
            "6",
            evolution.soldPercentageDelta()
        );

        assertBigDecimalEquals(
            "-10.00",
            evolution.currentPriceDelta()
        );

        assertBigDecimalEquals(
            "-10.0000",
            evolution.currentPriceDeltaPercentage()
        );

        assertBigDecimalEquals(
            "10",
            evolution.cashDiscountDelta()
        );

        assertTrue(
            evolution.hasSoldPercentageDelta()
        );

        assertTrue(
            evolution.hasCurrentPriceDeltaPercentage()
        );

        assertTrue(
            evolution.hasCashDiscountDelta()
        );
    }

    @Test
    void shouldPreserveNegativeEvolutionSignals() {

        HistoricalOfferObservation previous =
            observation(
                10L,
                ASIN,
                "2026-09-20T10:00:00-03:00",
                "100.00",
                "83",
                cashCondition(
                    "20"
                )
            );

        HistoricalOfferObservation current =
            observation(
                11L,
                ASIN,
                "2026-09-20T14:00:00-03:00",
                "120.00",
                "75",
                cashCondition(
                    "10"
                )
            );

        SnapshotEvolution evolution =
            calculator.calculate(
                previous,
                current
            );

        assertBigDecimalEquals(
            "-8",
            evolution.soldPercentageDelta()
        );

        assertBigDecimalEquals(
            "20.00",
            evolution.currentPriceDelta()
        );

        assertBigDecimalEquals(
            "20.0000",
            evolution.currentPriceDeltaPercentage()
        );

        assertBigDecimalEquals(
            "-10",
            evolution.cashDiscountDelta()
        );
    }

    @Test
    void shouldReturnNullSoldDeltaWhenPercentageIsUnavailable() {

        HistoricalOfferObservation previous =
            observation(
                20L,
                ASIN,
                "2026-09-20T10:00:00-03:00",
                "100.00",
                null,
                cashCondition(
                    "10"
                )
            );

        HistoricalOfferObservation current =
            observation(
                21L,
                ASIN,
                "2026-09-20T11:00:00-03:00",
                "90.00",
                "70",
                cashCondition(
                    "15"
                )
            );

        SnapshotEvolution evolution =
            calculator.calculate(
                previous,
                current
            );

        assertNull(
            evolution.soldPercentageDelta()
        );

        assertTrue(
            !evolution.hasSoldPercentageDelta()
        );
    }

    @Test
    void shouldReturnNullCashDiscountDeltaWhenDiscountIsUnavailable() {

        HistoricalOfferObservation previous =
            observation(
                30L,
                ASIN,
                "2026-09-20T10:00:00-03:00",
                "100.00",
                "50",
                null
            );

        HistoricalOfferObservation current =
            observation(
                31L,
                ASIN,
                "2026-09-20T11:00:00-03:00",
                "90.00",
                "55",
                cashCondition(
                    "15"
                )
            );

        SnapshotEvolution evolution =
            calculator.calculate(
                previous,
                current
            );

        assertNull(
            evolution.cashDiscountDelta()
        );

        assertTrue(
            !evolution.hasCashDiscountDelta()
        );
    }

    @Test
    void shouldReturnNullPricePercentageDeltaWhenPreviousPriceIsZero() {

        HistoricalOfferObservation previous =
            observation(
                40L,
                ASIN,
                "2026-09-20T10:00:00-03:00",
                "0",
                "50",
                cashCondition(
                    "10"
                )
            );

        HistoricalOfferObservation current =
            observation(
                41L,
                ASIN,
                "2026-09-20T11:00:00-03:00",
                "50.00",
                "55",
                cashCondition(
                    "10"
                )
            );

        SnapshotEvolution evolution =
            calculator.calculate(
                previous,
                current
            );

        assertBigDecimalEquals(
            "50.00",
            evolution.currentPriceDelta()
        );

        assertNull(
            evolution.currentPriceDeltaPercentage()
        );

        assertTrue(
            !evolution.hasCurrentPriceDeltaPercentage()
        );
    }

    @Test
    void shouldRejectObservationsFromDifferentAsins() {

        HistoricalOfferObservation previous =
            observation(
                50L,
                new Asin(
                    "B0HIST1101"
                ),
                "2026-09-20T10:00:00-03:00",
                "100.00",
                "50",
                null
            );

        HistoricalOfferObservation current =
            observation(
                51L,
                new Asin(
                    "B0HIST1102"
                ),
                "2026-09-20T11:00:00-03:00",
                "90.00",
                "60",
                null
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                calculator.calculate(
                    previous,
                    current
                )
        );
    }

    @Test
    void shouldRejectNonIncreasingCollectionTime() {

        HistoricalOfferObservation previous =
            observation(
                60L,
                ASIN,
                "2026-09-20T10:00:00-03:00",
                "100.00",
                "50",
                null
            );

        HistoricalOfferObservation current =
            observation(
                61L,
                ASIN,
                "2026-09-20T10:00:00-03:00",
                "90.00",
                "60",
                null
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                calculator.calculate(
                    previous,
                    current
                )
        );
    }

    private HistoricalOfferObservation observation(
        long snapshotId,
        Asin asin,
        String collectedAt,
        String currentPrice,
        String soldPercentage,
        PaymentCondition paymentCondition
    ) {

        List<PaymentCondition> paymentConditions =
            paymentCondition == null
                ? List.of()
                : List.of(
                paymentCondition
            );

        Percentage sold =
            soldPercentage == null
                ? null
                : Percentage.of(
                soldPercentage
            );

        return new HistoricalOfferObservation(
            snapshotId,
            asin,
            OffsetDateTime.parse(
                collectedAt
            ),
            Money.of(
                currentPrice
            ),
            sold,
            "history-test",
            paymentConditions
        );
    }

    private PaymentCondition cashCondition(
        String discount
    ) {

        return new PaymentCondition(
            PaymentConditionType.CASH,
            null,
            Percentage.of(
                discount
            ),
            null,
            null,
            null,
            null,
            List.of(
                PaymentMethod.PIX
            )
        );
    }

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {

        assertEquals(
            0,
            new BigDecimal(
                expected
            ).compareTo(
                actual
            )
        );
    }
}
