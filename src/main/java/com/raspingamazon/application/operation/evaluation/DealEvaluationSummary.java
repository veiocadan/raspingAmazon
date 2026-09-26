package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Read model compacto de uma avaliação apresentada na listagem
 * operacional.
 *
 * <p>Este objeto não reconstrói o agregado DealEvaluation completo.</p>
 *
 * <p>Ele contém somente informações necessárias para navegação e
 * diagnóstico inicial da interface operacional.</p>
 */
public record DealEvaluationSummary(
    long evaluationId,
    long offerSnapshotId,
    long productId,
    Asin asin,
    String title,
    Money currentPrice,
    boolean eligible,
    RejectionReason rejectionReason,
    BigDecimal score,
    BigDecimal momentum,
    OffsetDateTime collectedAt,
    OffsetDateTime evaluatedAt
) {

    private static final BigDecimal MINIMUM_ALLOWED_SCORE =
        BigDecimal.ZERO;

    private static final BigDecimal MAXIMUM_ALLOWED_SCORE =
        new BigDecimal(
            "100"
        );

    public DealEvaluationSummary {

        requirePositive(
            evaluationId,
            "evaluationId"
        );

        requirePositive(
            offerSnapshotId,
            "offerSnapshotId"
        );

        requirePositive(
            productId,
            "productId"
        );

        Objects.requireNonNull(
            asin,
            "DealEvaluationSummary asin must not be null"
        );

        title =
            requireText(
                title,
                "DealEvaluationSummary title must not be blank"
            );

        Objects.requireNonNull(
            currentPrice,
            "DealEvaluationSummary currentPrice must not be null"
        );

        Objects.requireNonNull(
            collectedAt,
            "DealEvaluationSummary collectedAt must not be null"
        );

        Objects.requireNonNull(
            evaluatedAt,
            "DealEvaluationSummary evaluatedAt must not be null"
        );

        if (eligible
            && rejectionReason != null) {

            throw new IllegalArgumentException(
                "Eligible DealEvaluationSummary "
                    + "must not have rejectionReason"
            );
        }

        if (!eligible
            && rejectionReason == null) {

            throw new IllegalArgumentException(
                "Ineligible DealEvaluationSummary "
                    + "must have rejectionReason"
            );
        }

        if (score != null) {

            if (!eligible) {
                throw new IllegalArgumentException(
                    "Ineligible DealEvaluationSummary "
                        + "must not have score"
                );
            }

            if (score.compareTo(
                MINIMUM_ALLOWED_SCORE
            ) < 0
                || score.compareTo(
                MAXIMUM_ALLOWED_SCORE
            ) > 0) {

                throw new IllegalArgumentException(
                    "DealEvaluationSummary score "
                        + "must be between 0 and 100"
                );
            }
        }
    }

    private static void requirePositive(
        long value,
        String fieldName
    ) {

        if (value <= 0L) {
            throw new IllegalArgumentException(
                "DealEvaluationSummary "
                    + fieldName
                    + " must be positive"
            );
        }
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
