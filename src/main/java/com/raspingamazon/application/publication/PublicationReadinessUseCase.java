package com.raspingamazon.application.publication;

import com.raspingamazon.domain.publication.Publication;

/**
 * Contrato do caso de uso que garante automaticamente que uma
 * Publication esteja READY para o mecanismo de despacho.
 *
 * <p>Não representa aprovação humana.</p>
 */
@FunctionalInterface
public interface PublicationReadinessUseCase {

    Publication ensureReady(
        long publicationId
    );
}
