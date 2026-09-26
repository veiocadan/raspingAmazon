package com.raspingamazon.application.operation.publication.port;

import com.raspingamazon.application.operation.publication.PublicationDetail;

import java.util.Optional;

/**
 * Porta de consulta do detalhe operacional de Publication.
 */
@FunctionalInterface
public interface PublicationOperationalDetailQueryPort {

    Optional<PublicationDetail> findById(
        long publicationId
    );
}
