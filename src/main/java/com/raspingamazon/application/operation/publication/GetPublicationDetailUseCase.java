package com.raspingamazon.application.operation.publication;

import com.raspingamazon.application.operation.publication.port.PublicationOperationalDetailQueryPort;

import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso da consulta detalhada de Publication.
 */
public final class GetPublicationDetailUseCase {

    private final PublicationOperationalDetailQueryPort queryPort;

    public GetPublicationDetailUseCase(
        PublicationOperationalDetailQueryPort queryPort
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "GetPublicationDetailUseCase queryPort must not be null"
            );
    }

    public Optional<PublicationDetail> execute(
        long publicationId
    ) {

        if (publicationId <= 0L) {
            throw new IllegalArgumentException(
                "publicationId must be positive"
            );
        }

        return Objects.requireNonNull(
            queryPort.findById(
                publicationId
            ),
            "PublicationOperationalDetailQueryPort must not return null"
        );
    }
}
