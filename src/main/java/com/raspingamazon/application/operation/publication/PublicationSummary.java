package com.raspingamazon.application.operation.publication;

import com.raspingamazon.domain.product.Asin;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Read model compacto de Publication para listagem operacional.
 *
 * <p>O texto completo gerado e a URL afiliada são deliberadamente
 * carregados somente pela consulta de detalhe.</p>
 */
public record PublicationSummary(
    long publicationId,
    long dealEvaluationId,
    long productId,
    Asin asin,
    String title,
    String status,
    String templateVersion,
    String commercialPresentationVersion,
    String affiliateLinkVersion,
    OffsetDateTime createdAt
) {

    public PublicationSummary {

        requirePositive(
            publicationId,
            "publicationId"
        );

        requirePositive(
            dealEvaluationId,
            "dealEvaluationId"
        );

        requirePositive(
            productId,
            "productId"
        );

        Objects.requireNonNull(
            asin,
            "PublicationSummary asin must not be null"
        );

        title =
            requireText(
                title,
                "PublicationSummary title must not be blank"
            );

        status =
            requireText(
                status,
                "PublicationSummary status must not be blank"
            );

        templateVersion =
            requireText(
                templateVersion,
                "PublicationSummary templateVersion must not be blank"
            );

        commercialPresentationVersion =
            requireText(
                commercialPresentationVersion,
                "PublicationSummary "
                    + "commercialPresentationVersion must not be blank"
            );

        affiliateLinkVersion =
            requireText(
                affiliateLinkVersion,
                "PublicationSummary affiliateLinkVersion must not be blank"
            );

        Objects.requireNonNull(
            createdAt,
            "PublicationSummary createdAt must not be null"
        );
    }

    private static void requirePositive(
        long value,
        String fieldName
    ) {

        if (value <= 0L) {
            throw new IllegalArgumentException(
                "PublicationSummary "
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
