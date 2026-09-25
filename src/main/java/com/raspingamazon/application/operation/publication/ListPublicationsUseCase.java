package com.raspingamazon.application.operation.publication;

import com.raspingamazon.application.operation.publication.port.PublicationOperationalQueryPort;

import java.util.Objects;

/**
 * Caso de uso da listagem operacional de Publication.
 */
public final class ListPublicationsUseCase {

    private final PublicationOperationalQueryPort queryPort;

    public ListPublicationsUseCase(
        PublicationOperationalQueryPort queryPort
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "ListPublicationsUseCase queryPort must not be null"
            );
    }

    public PublicationPage execute(
        PublicationSearchCriteria criteria
    ) {

        Objects.requireNonNull(
            criteria,
            "ListPublicationsUseCase criteria must not be null"
        );

        return Objects.requireNonNull(
            queryPort.search(
                criteria
            ),
            "PublicationOperationalQueryPort must not return null"
        );
    }
}
