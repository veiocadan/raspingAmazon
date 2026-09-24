package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão específica para NuPay genérico.
 *
 * <p>NuPay explicitamente observado sem "Limite Adicional" continua
 * sendo um método à vista reconhecido pelo filtro comercial.</p>
 */
class BestCashDiscountSelectorNuPayTest {

    @Test
    void shouldRecognizeGenericNuPayCashDiscount() {

        PaymentCondition nuPay =
            new PaymentCondition(
                PaymentConditionType.CASH,
                Money.of(
                    "90.00"
                ),
                Percentage.of(
                    "10"
                ),
                null,
                null,
                null,
                null,
                List.of(
                    PaymentMethod.NUPAY
                )
            );

        BestCashDiscountSelector selector =
            new BestCashDiscountSelector();

        var observation =
            selector.select(
                List.of(
                    nuPay
                )
            );

        assertTrue(
            observation.isPresent()
        );

        assertEquals(
            Percentage.of(
                "10"
            ),
            observation.get()
                .discountPercentage()
        );

        assertEquals(
            List.of(
                PaymentMethod.NUPAY
            ),
            observation.get()
                .paymentMethods()
        );

        assertEquals(
            "10|NUPAY",
            observation.get()
                .auditValue()
        );
    }
}
