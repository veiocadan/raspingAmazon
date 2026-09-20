package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Percentage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BestCashDiscountSelectorTest {

    private final BestCashDiscountSelector selector =
        new BestCashDiscountSelector();

    @Test
    void shouldSelectPixDiscount() {

        Optional<CashDiscountObservation> result =
            selector.select(
                List.of(
                    cashCondition(
                        "10",
                        PaymentMethod.PIX
                    )
                )
            );

        assertTrue(
            result.isPresent()
        );

        assertEquals(
            Percentage.of("10"),
            result.get()
                .discountPercentage()
        );

        assertEquals(
            List.of(
                PaymentMethod.PIX
            ),
            result.get()
                .paymentMethods()
        );
    }

    @Test
    void shouldSelectHigherNuPayDiscount() {

        Optional<CashDiscountObservation> result =
            selector.select(
                List.of(
                    cashCondition(
                        "8",
                        PaymentMethod.PIX
                    ),
                    cashCondition(
                        "12",
                        PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                    )
                )
            );

        assertTrue(
            result.isPresent()
        );

        assertEquals(
            Percentage.of("12"),
            result.get()
                .discountPercentage()
        );

        assertEquals(
            List.of(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT
            ),
            result.get()
                .paymentMethods()
        );
    }

    @Test
    void shouldPreservePixAndNuPayWhenBothHaveBestDiscount() {

        Optional<CashDiscountObservation> result =
            selector.select(
                List.of(
                    cashCondition(
                        "15",
                        PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                    ),
                    cashCondition(
                        "15",
                        PaymentMethod.PIX
                    )
                )
            );

        assertTrue(
            result.isPresent()
        );

        assertEquals(
            List.of(
                PaymentMethod.PIX,
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT
            ),
            result.get()
                .paymentMethods()
        );

        assertEquals(
            "15|PIX,NUPAY_ADDITIONAL_LIMIT",
            result.get()
                .auditValue()
        );
    }

    @Test
    void shouldPreserveBothMethodsWhenSameConditionContainsBoth() {

        PaymentCondition condition =
            new PaymentCondition(
                PaymentConditionType.CASH,
                null,
                Percentage.of("15"),
                null,
                null,
                null,
                null,
                List.of(
                    PaymentMethod.PIX,
                    PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                )
            );

        Optional<CashDiscountObservation> result =
            selector.select(
                List.of(condition)
            );

        assertTrue(
            result.isPresent()
        );

        assertEquals(
            "15|PIX,NUPAY_ADDITIONAL_LIMIT",
            result.get()
                .auditValue()
        );
    }

    @Test
    void shouldIgnoreCreditInstallmentCondition() {

        PaymentCondition creditCondition =
            new PaymentCondition(
                PaymentConditionType.CREDIT_INSTALLMENT,
                null,
                Percentage.of("30"),
                10,
                com.raspingamazon.domain.shared.Money.of("100.00"),
                com.raspingamazon.domain.shared.Money.of("1000.00"),
                Percentage.of("0"),
                List.of(
                    PaymentMethod.CREDIT_CARD
                )
            );

        Optional<CashDiscountObservation> result =
            selector.select(
                List.of(
                    creditCondition,
                    cashCondition(
                        "8",
                        PaymentMethod.PIX
                    )
                )
            );

        assertTrue(
            result.isPresent()
        );

        assertEquals(
            Percentage.of("8"),
            result.get()
                .discountPercentage()
        );
    }

    @Test
    void shouldIgnoreCashConditionWithoutRecognizedMethod() {

        PaymentCondition condition =
            new PaymentCondition(
                PaymentConditionType.CASH,
                null,
                Percentage.of("25"),
                null,
                null,
                null,
                null,
                List.of()
            );

        Optional<CashDiscountObservation> result =
            selector.select(
                List.of(condition)
            );

        assertTrue(
            result.isEmpty()
        );
    }

    @Test
    void shouldIgnoreCashConditionWithoutExplicitDiscount() {

        PaymentCondition condition =
            new PaymentCondition(
                PaymentConditionType.CASH,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                    PaymentMethod.PIX
                )
            );

        Optional<CashDiscountObservation> result =
            selector.select(
                List.of(condition)
            );

        assertTrue(
            result.isEmpty()
        );
    }

    @Test
    void shouldReturnEmptyWhenThereAreNoPaymentConditions() {

        Optional<CashDiscountObservation> result =
            selector.select(
                List.of()
            );

        assertTrue(
            result.isEmpty()
        );
    }

    private PaymentCondition cashCondition(
        String discount,
        PaymentMethod paymentMethod
    ) {
        return new PaymentCondition(
            PaymentConditionType.CASH,
            null,
            Percentage.of(discount),
            null,
            null,
            null,
            null,
            List.of(paymentMethod)
        );
    }
}
