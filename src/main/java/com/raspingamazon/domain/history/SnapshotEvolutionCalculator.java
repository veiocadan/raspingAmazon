package com.raspingamazon.domain.history;

import com.raspingamazon.domain.filter.BestCashDiscountSelector;
import com.raspingamazon.domain.filter.CashDiscountObservation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Calcula a evolução entre duas observações históricas do mesmo ASIN.
 *
 * <p>Este componente pertence ao domínio. Ele recebe fatos históricos
 * já reconstruídos e produz somente diferenças temporais.</p>
 *
 * <p>Ele não:</p>
 *
 * <ul>
 *     <li>consulta banco de dados;</li>
 *     <li>calcula momentum;</li>
 *     <li>decide elegibilidade;</li>
 *     <li>calcula score;</li>
 *     <li>publica ofertas.</li>
 * </ul>
 *
 * <p>A interpretação do desconto à vista reutiliza
 * BestCashDiscountSelector. Dessa forma, histórico e avaliação atual
 * compartilham a mesma semântica comercial.</p>
 */
public final class SnapshotEvolutionCalculator {

    private static final int PRICE_PERCENTAGE_SCALE = 4;

    private final BestCashDiscountSelector
        bestCashDiscountSelector;

    public SnapshotEvolutionCalculator(
        BestCashDiscountSelector bestCashDiscountSelector
    ) {

        this.bestCashDiscountSelector =
            Objects.requireNonNull(
                bestCashDiscountSelector,
                "bestCashDiscountSelector must not be null"
            );
    }

    /**
     * Compara duas observações históricas.
     *
     * <p>A primeira observação precisa ser estritamente anterior
     * à segunda.</p>
     *
     * @param previous observação imediatamente anterior
     * @param current observação atual
     * @return evolução calculada
     */
    public SnapshotEvolution calculate(
        HistoricalOfferObservation previous,
        HistoricalOfferObservation current
    ) {

        Objects.requireNonNull(
            previous,
            "previous observation must not be null"
        );

        Objects.requireNonNull(
            current,
            "current observation must not be null"
        );

        validateComparableObservations(
            previous,
            current
        );

        BigDecimal soldPercentageDelta =
            calculateSoldPercentageDelta(
                previous,
                current
            );

        BigDecimal currentPriceDelta =
            calculateCurrentPriceDelta(
                previous,
                current
            );

        BigDecimal currentPriceDeltaPercentage =
            calculateCurrentPriceDeltaPercentage(
                previous,
                current,
                currentPriceDelta
            );

        BigDecimal cashDiscountDelta =
            calculateCashDiscountDelta(
                previous,
                current
            );

        return new SnapshotEvolution(
            current.asin(),
            previous.snapshotId(),
            current.snapshotId(),
            previous.collectedAt(),
            current.collectedAt(),
            soldPercentageDelta,
            currentPriceDelta,
            currentPriceDeltaPercentage,
            cashDiscountDelta
        );
    }

    private void validateComparableObservations(
        HistoricalOfferObservation previous,
        HistoricalOfferObservation current
    ) {

        if (!previous.asin().equals(
            current.asin()
        )) {

            throw new IllegalArgumentException(
                "Historical observations must belong to the same ASIN"
            );
        }

        if (!current.collectedAt().isAfter(
            previous.collectedAt()
        )) {

            throw new IllegalArgumentException(
                "Current observation must be collected after previous observation"
            );
        }

        if (previous.snapshotId()
            == current.snapshotId()) {

            throw new IllegalArgumentException(
                "Historical observations must reference different snapshots"
            );
        }
    }

    /**
     * Calcula a diferença em pontos percentuais.
     *
     * <p>Exemplo:</p>
     *
     * <pre>
     * previous = 62%
     * current  = 68%
     * delta    = +6 p.p.
     * </pre>
     *
     * <p>Quando qualquer uma das observações não possui percentual
     * vendido, não há dado suficiente para produzir o delta.</p>
     */
    private BigDecimal calculateSoldPercentageDelta(
        HistoricalOfferObservation previous,
        HistoricalOfferObservation current
    ) {

        if (previous.soldPercentage() == null
            || current.soldPercentage() == null) {

            return null;
        }

        return current.soldPercentage()
            .value()
            .subtract(
                previous.soldPercentage()
                    .value()
            );
    }

    /**
     * Calcula a variação absoluta do preço atual.
     *
     * <p>Valor negativo significa queda de preço.</p>
     *
     * <p>Valor positivo significa aumento de preço.</p>
     */
    private BigDecimal calculateCurrentPriceDelta(
        HistoricalOfferObservation previous,
        HistoricalOfferObservation current
    ) {

        return current.currentPrice()
            .amount()
            .subtract(
                previous.currentPrice()
                    .amount()
            );
    }

    /**
     * Calcula a variação percentual do preço em relação ao preço
     * anterior.
     *
     * <pre>
     * ((current - previous) / previous) * 100
     * </pre>
     *
     * <p>O resultado possui quatro casas decimais, utilizando
     * HALF_UP.</p>
     *
     * <p>Se o preço anterior for zero, a variação percentual é
     * matematicamente indefinida e o resultado será null.</p>
     */
    private BigDecimal calculateCurrentPriceDeltaPercentage(
        HistoricalOfferObservation previous,
        HistoricalOfferObservation current,
        BigDecimal currentPriceDelta
    ) {

        BigDecimal previousPrice =
            previous.currentPrice()
                .amount();

        if (previousPrice.signum() == 0) {
            return null;
        }

        return currentPriceDelta
            .multiply(
                BigDecimal.valueOf(
                    100
                )
            )
            .divide(
                previousPrice,
                PRICE_PERCENTAGE_SCALE,
                RoundingMode.HALF_UP
            );
    }

    /**
     * Calcula a variação do melhor desconto à vista explicitamente
     * observado.
     *
     * <p>O cálculo não infere desconto a partir de preços.</p>
     *
     * <p>Quando qualquer snapshot não possui desconto à vista
     * reconhecido, não existe comparação suficiente e o resultado
     * será null.</p>
     */
    private BigDecimal calculateCashDiscountDelta(
        HistoricalOfferObservation previous,
        HistoricalOfferObservation current
    ) {

        Optional<CashDiscountObservation> previousDiscount =
            bestCashDiscountSelector.select(
                previous.paymentConditions()
            );

        Optional<CashDiscountObservation> currentDiscount =
            bestCashDiscountSelector.select(
                current.paymentConditions()
            );

        if (previousDiscount.isEmpty()
            || currentDiscount.isEmpty()) {

            return null;
        }

        return currentDiscount.get()
            .discountPercentage()
            .value()
            .subtract(
                previousDiscount.get()
                    .discountPercentage()
                    .value()
            );
    }
}

