package com.raspingamazon.domain.history;

import com.raspingamazon.domain.product.Asin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa a evolução observada entre dois snapshots consecutivos
 * de um mesmo ASIN.
 *
 * <p>O objeto contém fatos derivados da comparação temporal, mas não
 * atribui interpretação de momentum, elegibilidade ou score.</p>
 *
 * <p>Os deltas utilizam BigDecimal porque uma evolução pode ser
 * positiva, negativa ou igual a zero.</p>
 *
 * <p>Exemplos:</p>
 *
 * <pre>
 * soldPercentageDelta = +6
 * currentPriceDelta = -10.00
 * currentPriceDeltaPercentage = -10.0000
 * cashDiscountDelta = +5
 * </pre>
 *
 * <p>Campos de delta podem ser nulos quando a comparação não possuir
 * informação suficiente para produzi-los.</p>
 */
public record SnapshotEvolution(
    Asin asin,
    long previousSnapshotId,
    long currentSnapshotId,
    OffsetDateTime previousCollectedAt,
    OffsetDateTime currentCollectedAt,
    BigDecimal soldPercentageDelta,
    BigDecimal currentPriceDelta,
    BigDecimal currentPriceDeltaPercentage,
    BigDecimal cashDiscountDelta
) {

    public SnapshotEvolution {

        asin = Objects.requireNonNull(
            asin,
            "SnapshotEvolution asin must not be null"
        );

        if (previousSnapshotId <= 0) {
            throw new IllegalArgumentException(
                "SnapshotEvolution previousSnapshotId must be positive"
            );
        }

        if (currentSnapshotId <= 0) {
            throw new IllegalArgumentException(
                "SnapshotEvolution currentSnapshotId must be positive"
            );
        }

        if (previousSnapshotId == currentSnapshotId) {
            throw new IllegalArgumentException(
                "SnapshotEvolution snapshots must be different"
            );
        }

        previousCollectedAt = Objects.requireNonNull(
            previousCollectedAt,
            "SnapshotEvolution previousCollectedAt must not be null"
        );

        currentCollectedAt = Objects.requireNonNull(
            currentCollectedAt,
            "SnapshotEvolution currentCollectedAt must not be null"
        );

        if (!currentCollectedAt.isAfter(previousCollectedAt)) {
            throw new IllegalArgumentException(
                "SnapshotEvolution currentCollectedAt must be after previousCollectedAt"
            );
        }
    }

    /**
     * Retorna o intervalo temporal exato entre as duas observações.
     *
     * <p>O valor é derivado dos timestamps para evitar armazenar
     * informação redundante e potencialmente inconsistente.</p>
     */
    public long elapsedSeconds() {

        return Duration.between(
            previousCollectedAt,
            currentCollectedAt
        ).toSeconds();
    }

    /**
     * Indica se foi possível calcular a evolução de percentual vendido.
     */
    public boolean hasSoldPercentageDelta() {

        return soldPercentageDelta != null;
    }

    /**
     * Indica se foi possível calcular a variação percentual de preço.
     */
    public boolean hasCurrentPriceDeltaPercentage() {

        return currentPriceDeltaPercentage != null;
    }

    /**
     * Indica se foi possível comparar o melhor desconto à vista.
     */
    public boolean hasCashDiscountDelta() {

        return cashDiscountDelta != null;
    }
}
