package com.raspingamazon.application.operation.publication.port;

import com.raspingamazon.application.operation.publication.PublicationPage;
import com.raspingamazon.application.operation.publication.PublicationSearchCriteria;

/**
 * Porta da listagem operacional de Publication.
 */
@FunctionalInterface
public interface PublicationOperationalQueryPort {

    PublicationPage search(
        PublicationSearchCriteria criteria
    );
}
