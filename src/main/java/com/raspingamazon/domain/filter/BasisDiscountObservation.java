package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * Representa o resultado auditável do cálculo de desconto entre
 * um preço-base observado e o preço efetivo selecionado pelo domínio.
 *
 * <p>O percentual desta classe é derivado pelo sistema. Ele não deve
 * ser confundido com PaymentCondition.discountPercentage, que
 * representa uma promoção explicitamente informada pela fonte.</p>
 *
 * @param basisPrice preço-base/riscado explicitamente observado
 * @param effectivePrice preço efetivamente utilizado no cálculo
 * @param effectivePriceSource origem do preço efetivo
 * @param discountPercentage percentual derivado contra basisPrice
 */
public record BasisDiscountObservation(
    Money basisPrice,
    Money effectivePrice,
    EffectivePriceSource effectivePriceSource,
    Percentage discountPercentage
) {

    public BasisDiscountObservation {

        Objects.requireNonNull(
            basisPrice,
            "basisPrice must not be null"
        );

        Objects.requireNonNull(
            effectivePrice,
            "effectivePrice must not be null"
        );

        Objects.requireNonNull(
            effectivePriceSource,
            "effectivePriceSource must not be null"
        );

        Objects.requireNonNull(
            discountPercentage,
            "discountPercentage must not be null"
        );

        if (basisPrice.amount()
            .signum() <= 0) {

            throw new IllegalArgumentException(
                "basisPrice must be greater than zero"
            );
        }

        if (effectivePrice.amount()
            .compareTo(
                basisPrice.amount()
            ) > 0) {

            throw new IllegalArgumentException(
                "effectivePrice must not exceed basisPrice"
            );
        }
    }

    /**
     * Produz o valor utilizado na auditoria de EvaluationRuleResult.
     *
     * <p>Exemplo:</p>
     *
     * <pre>
     * DISCOUNT=52.5368|BASIS=3599.00|EFFECTIVE=1708.20|SOURCE=CASH_CONDITION
     * </pre>
     */
    public String auditValue() {

        return "DISCOUNT="
            + discountPercentage
            .value()
            .setScale(
                4,
                RoundingMode.HALF_UP
            )
            .toPlainString()
            + "|BASIS="
            + basisPrice
            .amount()
            .toPlainString()
            + "|EFFECTIVE="
            + effectivePrice
            .amount()
            .toPlainString()
            + "|SOURCE="
            + effectivePriceSource.name();
    }
}
