package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasisDiscountCalculatorTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T21:00:00-03:00"
        );

    private final BasisDiscountCalculator calculator =
        new BasisDiscountCalculator();

    @Test
    void shouldUseLowestExplicitCashPriceAgainstBasisPrice() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "1898.00"
                ),
                Money.of(
                    "3599.00"
                ),
                List.of(
                    cashCondition(
                        "1750.00"
                    ),
                    cashCondition(
                        "1708.20"
                    )
                )
            );

        BasisDiscountObservation observation =
            calculator.calculate(
                    snapshot
                )
                .orElseThrow();

        assertEquals(
            Money.of(
                "3599.00"
            ),
            observation.basisPrice()
        );

        assertEquals(
            Money.of(
                "1708.20"
            ),
            observation.effectivePrice()
        );

        assertEquals(
            EffectivePriceSource.CASH_CONDITION,
            observation.effectivePriceSource()
        );

        assertEquals(
            Percentage.of(
                "52.5368"
            ),
            observation.discountPercentage()
        );

        assertEquals(
            "DISCOUNT=52.5368|BASIS=3599.00|EFFECTIVE=1708.20|SOURCE=CASH_CONDITION",
            observation.auditValue()
        );
    }

    @Test
    void shouldUseCurrentPriceWhenNoCashPriceExists() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "3298.99"
                ),
                Money.of(
                    "5499.00"
                ),
                List.of()
            );

        BasisDiscountObservation observation =
            calculator.calculate(
                    snapshot
                )
                .orElseThrow();

        assertEquals(
            Money.of(
                "3298.99"
            ),
            observation.effectivePrice()
        );

        assertEquals(
            EffectivePriceSource.CURRENT_PRICE,
            observation.effectivePriceSource()
        );

        assertEquals(
            Percentage.of(
                "40.0075"
            ),
            observation.discountPercentage()
        );

        assertEquals(
            "DISCOUNT=40.0075|BASIS=5499.00|EFFECTIVE=3298.99|SOURCE=CURRENT_PRICE",
            observation.auditValue()
        );
    }

    @Test
    void shouldUseCurrentPriceWhenCashConditionHasNoPrice() {

        PaymentCondition cashWithoutPrice =
            new PaymentCondition(
                PaymentConditionType.CASH,
                null,
                Percentage.of(
                    "10"
                ),
                null,
                null,
                null,
                null,
                List.of(
                    PaymentMethod.PIX
                )
            );

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "80.00"
                ),
                Money.of(
                    "100.00"
                ),
                List.of(
                    cashWithoutPrice
                )
            );

        BasisDiscountObservation observation =
            calculator.calculate(
                    snapshot
                )
                .orElseThrow();

        assertEquals(
            EffectivePriceSource.CURRENT_PRICE,
            observation.effectivePriceSource()
        );

        assertEquals(
            Money.of(
                "80.00"
            ),
            observation.effectivePrice()
        );

        assertEquals(
            Percentage.of(
                "20.0000"
            ),
            observation.discountPercentage()
        );
    }

    @Test
    void shouldReturnEmptyWhenBasisPriceIsMissing() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "80.00"
                ),
                null,
                List.of()
            );

        Optional<BasisDiscountObservation> observation =
            calculator.calculate(
                snapshot
            );

        assertTrue(
            observation.isEmpty()
        );
    }

    @Test
    void shouldReturnEmptyWhenBasisPriceIsZero() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "0.00"
                ),
                Money.of(
                    "0.00"
                ),
                List.of()
            );

        Optional<BasisDiscountObservation> observation =
            calculator.calculate(
                snapshot
            );

        assertTrue(
            observation.isEmpty()
        );
    }

    @Test
    void shouldReturnEmptyWhenEffectivePriceExceedsBasisPrice() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "110.00"
                ),
                Money.of(
                    "100.00"
                ),
                List.of()
            );

        Optional<BasisDiscountObservation> observation =
            calculator.calculate(
                snapshot
            );

        assertTrue(
            observation.isEmpty()
        );
    }

    @Test
    void shouldReturnZeroWhenEffectivePriceEqualsBasisPrice() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "100.00"
                ),
                Money.of(
                    "100.00"
                ),
                List.of()
            );

        BasisDiscountObservation observation =
            calculator.calculate(
                    snapshot
                )
                .orElseThrow();

        assertEquals(
            Percentage.of(
                "0.0000"
            ),
            observation.discountPercentage()
        );
    }

    private PaymentCondition cashCondition(
        String price
    ) {

        return new PaymentCondition(
            PaymentConditionType.CASH,
            Money.of(
                price
            ),
            null,
            null,
            null,
            null,
            null,
            List.of(
                PaymentMethod.PIX
            )
        );
    }

    private OfferSnapshot snapshot(
        Money currentPrice,
        Money basisPrice,
        List<PaymentCondition> paymentConditions
    ) {

        Product product =
            new Product(
                1L,
                new Asin(
                    "B0BASIS001"
                ),
                "Produto de teste",
                null,
                "https://www.amazon.com.br/dp/B0BASIS001"
            );

        return new OfferSnapshot(
            10L,
            product,
            COLLECTED_AT,
            currentPrice,
            basisPrice,
            null,
            null,
            4.8,
            500L,
            "Amazon.com.br",
            "Amazon.com.br",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "TEST",
            paymentConditions
        );
    }
}
