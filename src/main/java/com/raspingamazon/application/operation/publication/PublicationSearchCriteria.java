package com.raspingamazon.application.operation.publication;

import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.time.OffsetDateTime;

/**
 * Critérios da listagem operacional de Publication.
 *
 * <p>Todos os filtros são opcionais, exceto o limite.</p>
 */
public record PublicationSearchCriteria(
    PublicationStatus status,
    Asin asin,
    Long dealEvaluationId,
    OffsetDateTime createdFrom,
    OffsetDateTime createdUntil,
    PublicationCursor after,
    int limit
) {

    public static final int DEFAULT_LIMIT = 50;

    public static final int MAX_LIMIT = 200;

    public PublicationSearchCriteria {

        if (dealEvaluationId != null
            && dealEvaluationId <= 0L) {

            throw new IllegalArgumentException(
                "PublicationSearchCriteria "
                    + "dealEvaluationId must be positive"
            );
        }

        if (createdFrom != null
            && createdUntil != null
            && createdFrom.isAfter(
            createdUntil
        )) {

            throw new IllegalArgumentException(
                "PublicationSearchCriteria createdFrom "
                    + "must not be after createdUntil"
            );
        }

        if (limit <= 0) {
            throw new IllegalArgumentException(
                "PublicationSearchCriteria limit must be positive"
            );
        }

        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                "PublicationSearchCriteria limit must not exceed "
                    + MAX_LIMIT
            );
        }
    }

    public static PublicationSearchCriteria firstPage() {

        return new PublicationSearchCriteria(
            null,
            null,
            null,
            null,
            null,
            null,
            DEFAULT_LIMIT
        );
    }
}
