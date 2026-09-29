package com.raspingamazon.application.publication.port;

import com.raspingamazon.domain.publication.Publication;

import java.util.Optional;

/**
 * Porta de leitura de Publication por sua identidade persistente.
 *
 * <p>A aplicação depende somente deste contrato e não conhece
 * JDBC, SQL nem a forma utilizada para reconstruir a
 * DealEvaluation associada.</p>
 */
public interface PublicationQueryPort {

    /**
     * Localiza uma publicação persistida.
     *
     * @param publicationId identidade persistente da publicação
     * @return publicação quando encontrada
     */
    Optional<Publication> findById(
        long publicationId
    );
}
