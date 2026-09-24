package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Calcula o desconto derivado entre basisPrice e o preço efetivo
 * da oferta.
 *
 * <p>Fórmula:</p>
 *
 * <pre>
 * ((basisPrice - effectivePrice) / basisPrice) * 100
 * </pre>
 *
 * <p>A seleção do preço efetivo segue a ADR-0005:</p>
 *
 * <ol>
 *     <li>
 *         quando existir pelo menos uma condição CASH com preço
 *         explicitamente observado, utilizar o menor preço CASH;
 *     </li>
 *     <li>
 *         quando nenhuma condição CASH possuir preço numérico,
 *         utilizar currentPrice.
 *     </li>
 * </ol>
 *
 * <p>A utilização de currentPrice como fallback não o classifica
 * como preço Pix, NuPay ou qualquer outra modalidade.</p>
 *
 * <p>O cálculo não modifica PaymentCondition.discountPercentage.</p>
 */
public final class BasisDiscountCalculator {

    private static final int DISCOUNT_SCALE =
        4;

    /**
     * Calcula a observação derivada.
     *
     * @param snapshot snapshot comercial já normalizado
     * @return observação calculada ou Optional.empty() quando não
     *         houver dados consistentes suficientes
     */
    public Optional<BasisDiscountObservation> calculate(
        OfferSnapshot snapshot
    ) {

        Objects.requireNonNull(
            snapshot,
            "snapshot must not be null"
        );

        Money basisPrice =
            snapshot.basisPrice();

        if (basisPrice == null) {

            return Optional.empty();
        }

        BigDecimal basisAmount =
            basisPrice.amount();

        if (basisAmount.signum() <= 0) {

            return Optional.empty();
        }

        EffectivePriceSelection selection =
            selectEffectivePrice(
                snapshot
            );

        Money effectivePrice =
            selection.price();

        if (effectivePrice == null) {

            return Optional.empty();
        }

        BigDecimal effectiveAmount =
            effectivePrice.amount();

        /*
         * Money já impede valores negativos.
         *
         * A verificação continua explícita aqui porque faz parte da
         * semântica da ADR-0005 e protege o cálculo caso o contrato
         * monetário evolua futuramente.
         */
        if (effectiveAmount.signum() < 0) {

            return Optional.empty();
        }

        if (effectiveAmount.compareTo(
            basisAmount
        ) > 0) {

            /*
             * Não transformamos inconsistência em desconto negativo
             * nem fazemos fallback silencioso para outra fonte.
             */
            return Optional.empty();
        }

        BigDecimal discount =
            basisAmount
                .subtract(
                    effectiveAmount
                )
                .multiply(
                    BigDecimal.valueOf(
                        100
                    )
                )
                .divide(
                    basisAmount,
                    DISCOUNT_SCALE,
                    RoundingMode.HALF_UP
                );

        return Optional.of(
            new BasisDiscountObservation(
                basisPrice,
                effectivePrice,
                selection.source(),
                new Percentage(
                    discount
                )
            )
        );
    }

    /**
     * Seleciona deterministicamente o preço utilizado no cálculo.
     */
    private EffectivePriceSelection selectEffectivePrice(
        OfferSnapshot snapshot
    ) {

        Money lowestCashPrice =
            null;

        for (PaymentCondition condition
            : snapshot.paymentConditions()) {

            Objects.requireNonNull(
                condition,
                "paymentConditions must not contain null"
            );

            if (condition.type()
                != PaymentConditionType.CASH) {

                continue;
            }

            Money cashPrice =
                condition.price();

            if (cashPrice == null) {

                continue;
            }

            if (lowestCashPrice == null
                || cashPrice.amount()
                .compareTo(
                    lowestCashPrice.amount()
                ) < 0) {

                lowestCashPrice =
                    cashPrice;
            }
        }

        if (lowestCashPrice != null) {

            return new EffectivePriceSelection(
                lowestCashPrice,
                EffectivePriceSource.CASH_CONDITION
            );
        }

        return new EffectivePriceSelection(
            snapshot.currentPrice(),
            EffectivePriceSource.CURRENT_PRICE
        );
    }

    /**
     * Resultado interno da seleção do preço efetivo.
     */
    private record EffectivePriceSelection(
        Money price,
        EffectivePriceSource source
    ) {

        private EffectivePriceSelection {

            Objects.requireNonNull(
                price,
                "price must not be null"
            );

            Objects.requireNonNull(
                source,
                "source must not be null"
            );
        }
    }
}
