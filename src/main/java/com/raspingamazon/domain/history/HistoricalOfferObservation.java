package com.raspingamazon.domain.history;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Representa uma observação histórica mínima de uma oferta.
 *
 * <p>Este objeto não substitui OfferSnapshot.</p>
 *
 * <p>OfferSnapshot representa a observação utilizada pelo fluxo
 * operacional. HistoricalOfferObservation representa a projeção
 * necessária para consultas e cálculos temporais da FASE 11.</p>
 *
 * <p>A projeção preserva somente os fatos necessários para
 * histórico e evolução:</p>
 *
 * <ul>
 *     <li>identidade do snapshot;</li>
 *     <li>ASIN;</li>
 *     <li>instante da observação;</li>
 *     <li>preço atual;</li>
 *     <li>percentual vendido, quando disponível;</li>
 *     <li>origem;</li>
 *     <li>condições comerciais observadas.</li>
 * </ul>
 *
 * <p>A escolha do melhor desconto à vista não ocorre aqui.
 * Essa interpretação continua pertencendo ao domínio comercial.</p>
 */
public record HistoricalOfferObservation(
    long snapshotId,
    Asin asin,
    OffsetDateTime collectedAt,
    Money currentPrice,
    Percentage soldPercentage,
    String source,
    List<PaymentCondition> paymentConditions
) {

    public HistoricalOfferObservation {

        if (snapshotId <= 0) {
            throw new IllegalArgumentException(
                "HistoricalOfferObservation snapshotId must be positive"
            );
        }

        asin = Objects.requireNonNull(
            asin,
            "HistoricalOfferObservation asin must not be null"
        );

        collectedAt = Objects.requireNonNull(
            collectedAt,
            "HistoricalOfferObservation collectedAt must not be null"
        );

        currentPrice = Objects.requireNonNull(
            currentPrice,
            "HistoricalOfferObservation currentPrice must not be null"
        );

        source = requireText(
            source,
            "HistoricalOfferObservation source must not be blank"
        );

        Objects.requireNonNull(
            paymentConditions,
            "HistoricalOfferObservation paymentConditions must not be null"
        );

        paymentConditions = List.copyOf(
            paymentConditions
        );
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
