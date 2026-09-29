package com.raspingamazon.domain.publication.selection;

import com.raspingamazon.domain.product.Asin;

import java.time.Instant;
import java.util.Objects;

/**
 * Histórico agregado de publicações externas bem-sucedidas
 * para um ASIN dentro de um escopo de canal e destino.
 *
 * <p>Este objeto representa somente histórico existente.</p>
 *
 * <p>A ausência de uma instância para determinado ASIN significa
 * que não existe publicação bem-sucedida no escopo consultado.
 * Essa ausência não deve ser convertida para uma data artificial.</p>
 *
 * <p>Somente entregas efetivamente concluídas com sucesso
 * participam deste histórico. Geração de Publication, aprovação,
 * enqueue e tentativas falhas não iniciam recorrência.</p>
 */
public record SuccessfulPublicationHistory(
    Asin asin,
    Instant lastSuccessfulPublicationAt,
    long successfulPublicationCount
) {

    public SuccessfulPublicationHistory {

        Objects.requireNonNull(
            asin,
            "asin must not be null"
        );

        Objects.requireNonNull(
            lastSuccessfulPublicationAt,
            "lastSuccessfulPublicationAt must not be null"
        );

        if (successfulPublicationCount <= 0L) {
            throw new IllegalArgumentException(
                "successfulPublicationCount must be positive"
            );
        }
    }
}
