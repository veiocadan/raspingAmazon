package com.raspingamazon.application.publication.presentation;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.shared.Money;

import java.util.Objects;

/**
 * Representa os fatos comerciais selecionados para apresentação.
 *
 * <p>Esta estrutura ainda não é texto de publicação.</p>
 *
 * <p>Ela preserva separadamente:</p>
 *
 * <ul>
 *     <li>preço atual;</li>
 *     <li>preço-base/lista, quando observado;</li>
 *     <li>preço anterior histórico, quando explicitamente observado;</li>
 *     <li>condição principal à vista;</li>
 *     <li>condição secundária à vista, quando necessária;</li>
 *     <li>melhor condição de parcelamento selecionada;</li>
 *     <li>versão da política responsável pela decisão.</li>
 * </ul>
 */
public record CommercialPresentation(
    String policyVersion,
    Money currentPrice,
    Money basisPrice,
    Money previousPrice,
    PresentedCashCondition primaryCashCondition,
    PresentedCashCondition secondaryCashCondition,
    PaymentCondition installmentCondition
) {

    public CommercialPresentation {

        policyVersion =
            requireText(
                policyVersion,
                "policyVersion must not be blank"
            );

        Objects.requireNonNull(
            currentPrice,
            "currentPrice must not be null"
        );

        if (primaryCashCondition == null
            && secondaryCashCondition != null) {

            throw new IllegalArgumentException(
                "secondaryCashCondition requires primaryCashCondition"
            );
        }

        if (primaryCashCondition != null
            && secondaryCashCondition != null
            && primaryCashCondition.paymentMethod()
            == secondaryCashCondition.paymentMethod()) {

            throw new IllegalArgumentException(
                "Primary and secondary cash conditions must use different payment methods"
            );
        }

        if (installmentCondition != null
            && installmentCondition.type()
            != PaymentConditionType.CREDIT_INSTALLMENT) {

            throw new IllegalArgumentException(
                "installmentCondition must be CREDIT_INSTALLMENT"
            );
        }
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
