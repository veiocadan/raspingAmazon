package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.domain.product.Asin;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Critérios de pesquisa da consulta operacional de avaliações.
 *
 * <p>Todos os filtros são opcionais, exceto o limite da página.</p>
 *
 * <p>As datas representam um intervalo inclusivo de evaluatedAt.</p>
 *
 * <p>O cursor, quando presente, representa o último item da página
 * anteriormente consumida.</p>
 */
public record DealEvaluationSearchCriteria(
    Boolean eligible,
    Asin asin,
    BigDecimal minimumScore,
    BigDecimal maximumScore,
    OffsetDateTime evaluatedFrom,
    OffsetDateTime evaluatedUntil,
    DealEvaluationCursor after,
    int limit
) {

    public static final int DEFAULT_LIMIT = 50;

    public static final int MAX_LIMIT = 200;

    private static final BigDecimal MINIMUM_ALLOWED_SCORE =
        BigDecimal.ZERO;

    private static final BigDecimal MAXIMUM_ALLOWED_SCORE =
        new BigDecimal(
            "100"
        );

    public DealEvaluationSearchCriteria {

        if (limit <= 0) {
            throw new IllegalArgumentException(
                "DealEvaluationSearchCriteria limit must be positive"
            );
        }

        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                "DealEvaluationSearchCriteria limit must not exceed "
                    + MAX_LIMIT
            );
        }

        validateScore(
            minimumScore,
            "minimumScore"
        );

        validateScore(
            maximumScore,
            "maximumScore"
        );

        if (minimumScore != null
            && maximumScore != null
            && minimumScore.compareTo(
            maximumScore
        ) > 0) {

            throw new IllegalArgumentException(
                "DealEvaluationSearchCriteria minimumScore "
                    + "must not exceed maximumScore"
            );
        }

        if (evaluatedFrom != null
            && evaluatedUntil != null
            && evaluatedFrom.isAfter(
            evaluatedUntil
        )) {

            throw new IllegalArgumentException(
                "DealEvaluationSearchCriteria evaluatedFrom "
                    + "must not be after evaluatedUntil"
            );
        }

        /*
         * Avaliações inelegíveis não possuem score segundo as
         * invariantes atuais de DealEvaluation.
         *
         * Portanto, combinar eligible=false com filtro de score
         * representa uma consulta semanticamente impossível.
         */
        if (Boolean.FALSE.equals(
            eligible
        )
            && (minimumScore != null
            || maximumScore != null)) {

            throw new IllegalArgumentException(
                "Ineligible-only search must not define score filters"
            );
        }
    }

    /**
     * Cria a consulta padrão da primeira página.
     *
     * @return critérios sem filtros e com limite padrão
     */
    public static DealEvaluationSearchCriteria firstPage() {

        return new DealEvaluationSearchCriteria(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            DEFAULT_LIMIT
        );
    }

    private static void validateScore(
        BigDecimal score,
        String fieldName
    ) {

        if (score == null) {
            return;
        }

        if (score.compareTo(
            MINIMUM_ALLOWED_SCORE
        ) < 0
            || score.compareTo(
            MAXIMUM_ALLOWED_SCORE
        ) > 0) {

            throw new IllegalArgumentException(
                "DealEvaluationSearchCriteria "
                    + fieldName
                    + " must be between 0 and 100"
            );
        }
    }
}
