package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Percentage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Seleciona o maior desconto à vista explicitamente observado
 * entre as condições comerciais reconhecidas.
 *
 * São consideradas somente condições que:
 *
 * - sejam do tipo CASH;
 * - possuam desconto explicitamente informado;
 * - estejam associadas a Pix e/ou NuPay.
 *
 * Condições de cartão não participam desta seleção.
 *
 * O seletor não infere descontos pela diferença entre preços.
 *
 * Quando mais de um meio de pagamento possui o mesmo maior desconto,
 * todos os meios reconhecidos responsáveis por esse percentual são
 * preservados na observação.
 *
 * Isso garante resultado determinístico e mantém a informação
 * necessária para auditoria e decisões futuras de publicação.
 */
public final class BestCashDiscountSelector {

    /**
     * Localiza o maior desconto à vista reconhecido.
     *
     * @param paymentConditions condições comerciais observadas
     * @return melhor desconto ou Optional.empty() quando não existir
     *         evidência válida de desconto à vista
     */
    public Optional<CashDiscountObservation> select(
        List<PaymentCondition> paymentConditions
    ) {
        Objects.requireNonNull(
            paymentConditions,
            "paymentConditions must not be null"
        );

        Percentage bestDiscount = null;

        EnumSet<PaymentMethod> bestMethods =
            EnumSet.noneOf(
                PaymentMethod.class
            );

        for (PaymentCondition condition : paymentConditions) {

            Objects.requireNonNull(
                condition,
                "paymentConditions must not contain null"
            );

            if (condition.type()
                != PaymentConditionType.CASH) {

                continue;
            }

            Percentage discount =
                condition.discountPercentage();

            if (discount == null) {
                continue;
            }

            EnumSet<PaymentMethod> recognizedMethods =
                recognizedCashMethods(
                    condition
                );

            if (recognizedMethods.isEmpty()) {
                continue;
            }

            if (bestDiscount == null) {

                bestDiscount = discount;

                bestMethods.clear();
                bestMethods.addAll(
                    recognizedMethods
                );

                continue;
            }

            int comparison =
                discount.value().compareTo(
                    bestDiscount.value()
                );

            if (comparison > 0) {

                bestDiscount = discount;

                bestMethods.clear();
                bestMethods.addAll(
                    recognizedMethods
                );

                continue;
            }

            if (comparison == 0) {

                bestMethods.addAll(
                    recognizedMethods
                );
            }
        }

        if (bestDiscount == null) {
            return Optional.empty();
        }

        return Optional.of(
            new CashDiscountObservation(
                bestDiscount,
                new ArrayList<>(
                    bestMethods
                )
            )
        );
    }

    /**
     * Retém somente os meios de pagamento à vista reconhecidos
     * pela política vigente.
     */
    private EnumSet<PaymentMethod> recognizedCashMethods(
        PaymentCondition condition
    ) {
        EnumSet<PaymentMethod> recognized =
            EnumSet.noneOf(
                PaymentMethod.class
            );

        for (PaymentMethod paymentMethod
            : condition.paymentMethods()) {

            if (paymentMethod == PaymentMethod.PIX
                || paymentMethod
                == PaymentMethod.NUPAY_ADDITIONAL_LIMIT) {

                recognized.add(
                    paymentMethod
                );
            }
        }

        return recognized;
    }
}
