package com.raspingamazon.domain.publication.selection;

import com.raspingamazon.domain.product.Asin;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Candidato à seleção operacional de publicação.
 *
 * <p>Este objeto representa uma DealEvaluation já elegível e
 * pontuada, enriquecida com o histórico de publicação referente
 * ao canal e destino em que a decisão será tomada.</p>
 *
 * <p>Score e histórico possuem responsabilidades diferentes:</p>
 *
 * <pre>
 * score
 *     -> qualidade comercial
 *
 * successfulPublicationHistory
 *     -> recorrência operacional
 * </pre>
 *
 * <p>A ausência de histórico significa que o ASIN nunca teve
 * publicação bem-sucedida no canal e destino informados.</p>
 */
public record PublicationSelectionCandidate(
    long dealEvaluationId,
    Asin asin,
    BigDecimal score,
    String channel,
    String destination,
    SuccessfulPublicationHistory successfulPublicationHistory
) {

    public PublicationSelectionCandidate {

        if (dealEvaluationId <= 0L) {
            throw new IllegalArgumentException(
                "dealEvaluationId must be positive"
            );
        }

        Objects.requireNonNull(
            asin,
            "asin must not be null"
        );

        Objects.requireNonNull(
            score,
            "score must not be null"
        );

        channel =
            requireText(
                channel,
                "channel"
            );

        destination =
            requireText(
                destination,
                "destination"
            );

        if (successfulPublicationHistory != null
            && !successfulPublicationHistory.asin()
            .equals(asin)) {

            throw new IllegalArgumentException(
                "successfulPublicationHistory ASIN "
                    + "must match candidate ASIN"
            );
        }
    }

    /**
     * Indica se nenhuma publicação bem-sucedida existe para este
     * ASIN no canal e destino do candidato.
     */
    public boolean neverSuccessfullyPublished() {
        return successfulPublicationHistory == null;
    }

    /**
     * Expõe o histórico como Optional para consumidores do domínio
     * sem obrigá-los a trabalhar diretamente com null.
     */
    public Optional<SuccessfulPublicationHistory>
    successfulHistory() {

        return Optional.ofNullable(
            successfulPublicationHistory
        );
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }
}
