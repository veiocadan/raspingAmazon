package com.raspingamazon.application.publication.selection;

import com.raspingamazon.domain.product.Asin;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Candidato já qualificado pelas etapas comerciais anteriores
 * e pronto para entrar na seleção operacional de publicação.
 *
 * <p>Este contrato não recalcula elegibilidade nem score.</p>
 *
 * <p>O chamador deve fornecer somente DealEvaluations que já
 * estejam aptas a participar da priorização operacional.</p>
 */
public record PublicationSelectionSourceCandidate(
    long dealEvaluationId,
    Asin asin,
    BigDecimal score
) {

    public PublicationSelectionSourceCandidate {

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
    }
}
