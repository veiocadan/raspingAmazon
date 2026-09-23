package com.raspingamazon.application.publication.presentation;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;

import java.util.Objects;

/**
 * Representa uma condição à vista selecionada especificamente
 * para apresentação em publicação.
 *
 * <p>O PaymentMethod é mantido explicitamente porque uma mesma
 * PaymentCondition pode carregar mais de um método de pagamento.</p>
 */
public record PresentedCashCondition(
    PaymentMethod paymentMethod,
    PaymentCondition condition
) {

    public PresentedCashCondition {

        Objects.requireNonNull(
            paymentMethod,
            "paymentMethod must not be null"
        );

        Objects.requireNonNull(
            condition,
            "condition must not be null"
        );

        if (paymentMethod != PaymentMethod.PIX
            && paymentMethod != PaymentMethod.NUPAY_ADDITIONAL_LIMIT) {

            throw new IllegalArgumentException(
                "Presented cash condition must use PIX or NUPAY_ADDITIONAL_LIMIT"
            );
        }

        if (condition.type() != PaymentConditionType.CASH) {
            throw new IllegalArgumentException(
                "Presented cash condition must reference a CASH condition"
            );
        }

        if (!condition.paymentMethods().contains(
            paymentMethod
        )) {

            throw new IllegalArgumentException(
                "Payment condition does not contain the presented payment method"
            );
        }
    }
}
