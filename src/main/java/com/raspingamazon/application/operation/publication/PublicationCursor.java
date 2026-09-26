package com.raspingamazon.application.operation.publication;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Cursor da paginação operacional de Publication.
 *
 * <p>A ordenação contratada é:</p>
 *
 * <pre>
 * createdAt DESC
 * publicationId DESC
 * </pre>
 */
public record PublicationCursor(
    OffsetDateTime createdAt,
    long publicationId
) {

    public PublicationCursor {

        Objects.requireNonNull(
            createdAt,
            "PublicationCursor createdAt must not be null"
        );

        if (publicationId <= 0L) {
            throw new IllegalArgumentException(
                "PublicationCursor publicationId must be positive"
            );
        }
    }
}
