package com.raspingamazon.domain.commercial;

import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa a representação de condições comerciais no domínio.
 */
class PaymentConditionTest {

    @Test
    void shouldCreateCashPaymentCondition() {

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

        assertEquals(
                PaymentConditionType.CASH,
                condition.type()
        );

        assertEquals(
                new Money(new BigDecimal("161.40")),
                condition.price()
        );

        assertEquals(
                new Percentage(new BigDecimal("15.00")),
                condition.discountPercentage()
        );

        assertEquals(
                List.of(
                        PaymentMethod.PIX,
                        PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                ),
                condition.paymentMethods()
        );
    }

    @Test
    void shouldCreateCreditInstallmentCondition() {

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

        assertEquals(
                PaymentConditionType.CREDIT_INSTALLMENT,
                condition.type()
        );

        assertEquals(
                6,
                condition.installmentCount()
        );

        assertEquals(
                new Money(new BigDecimal("31.65")),
                condition.installmentAmount()
        );

        assertEquals(
                new Money(new BigDecimal("189.90")),
                condition.installmentTotal()
        );

        assertEquals(
                new Percentage(BigDecimal.ZERO),
                condition.interest()
        );

        assertEquals(
                List.of(PaymentMethod.CREDIT_CARD),
                condition.paymentMethods()
        );
    }

    @Test
    void shouldRejectNullType() {

        assertThrows(
                NullPointerException.class,
                () -> new PaymentCondition(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullPaymentMethods() {

        assertThrows(
                NullPointerException.class,
                () -> new PaymentCondition(
                        PaymentConditionType.CASH,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }
	
	    @Test
    void shouldRejectCashConditionWithInstallments() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCondition(
                        PaymentConditionType.CASH,
                        new Money(new BigDecimal("161.40")),
                        new Percentage(new BigDecimal("15.00")),
                        6,
                        null,
                        null,
                        null,
                        List.of(PaymentMethod.PIX)
                )
        );
    }

    @Test
    void shouldRejectCreditInstallmentWithoutInstallmentCount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCondition(
                        PaymentConditionType.CREDIT_INSTALLMENT,
                        null,
                        null,
                        null,
                        new Money(new BigDecimal("31.65")),
                        new Money(new BigDecimal("189.90")),
                        new Percentage(BigDecimal.ZERO),
                        List.of(PaymentMethod.CREDIT_CARD)
                )
        );
    }

    @Test
    void shouldRejectCreditInstallmentWithZeroInstallments() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCondition(
                        PaymentConditionType.CREDIT_INSTALLMENT,
                        null,
                        null,
                        0,
                        new Money(new BigDecimal("31.65")),
                        new Money(new BigDecimal("189.90")),
                        new Percentage(BigDecimal.ZERO),
                        List.of(PaymentMethod.CREDIT_CARD)
                )
        );
    }

    @Test
    void shouldRejectCreditInstallmentWithoutInstallmentAmount() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCondition(
                        PaymentConditionType.CREDIT_INSTALLMENT,
                        null,
                        null,
                        6,
                        null,
                        new Money(new BigDecimal("189.90")),
                        new Percentage(BigDecimal.ZERO),
                        List.of(PaymentMethod.CREDIT_CARD)
                )
        );
    }

    @Test
    void shouldRejectCreditInstallmentWithoutInstallmentTotal() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCondition(
                        PaymentConditionType.CREDIT_INSTALLMENT,
                        null,
                        null,
                        6,
                        new Money(new BigDecimal("31.65")),
                        null,
                        new Percentage(BigDecimal.ZERO),
                        List.of(PaymentMethod.CREDIT_CARD)
                )
        );
    }
}