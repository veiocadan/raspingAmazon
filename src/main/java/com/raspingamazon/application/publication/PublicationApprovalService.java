package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.port.PublicationQueryPort;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.util.Objects;

/**
 * Serviço de aplicação responsável pela aprovação manual de
 * uma Publication.
 *
 * <p>Fluxo:</p>
 *
 * <pre>
 * carregar Publication
 *        ↓
 * validar transição no domínio
 *        ↓
 * CREATED -> READY
 *        ↓
 * persistir compare-and-set
 * </pre>
 *
 * <p>Este serviço não conhece canais, outbox, providers externos
 * ou tentativas de publicação.</p>
 */
public final class PublicationApprovalService {

    private final PublicationQueryPort publicationQueryPort;

    private final PublicationStatusRepository
        publicationStatusRepository;

    public PublicationApprovalService(
        PublicationQueryPort publicationQueryPort,
        PublicationStatusRepository publicationStatusRepository
    ) {

        this.publicationQueryPort =
            Objects.requireNonNull(
                publicationQueryPort,
                "publicationQueryPort must not be null"
            );

        this.publicationStatusRepository =
            Objects.requireNonNull(
                publicationStatusRepository,
                "publicationStatusRepository must not be null"
            );
    }

    /**
     * Aprova uma Publication CREATED, tornando-a READY.
     *
     * <p>A operação não é silenciosamente idempotente.</p>
     *
     * <p>Uma Publication que já esteja READY, PUBLISHED ou FAILED
     * será rejeitada pela própria entidade de domínio, preservando
     * a máquina de estados existente.</p>
     *
     * @param publicationId identidade persistente da publicação
     * @return publicação após a transição para READY
     */
    public Publication approve(
        long publicationId
    ) {

        if (publicationId <= 0L) {

            throw new IllegalArgumentException(
                "publicationId must be positive"
            );
        }

        Publication publication =
            publicationQueryPort.findById(
                    publicationId
                )
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Publication not found: "
                                + publicationId
                        )
                );

        validateLoadedIdentity(
            publicationId,
            publication
        );

        PublicationStatus expectedStatus =
            publication.status();

        /*
         * A validade da transição permanece no domínio.
         *
         * Atualmente somente CREATED pode executar markReady().
         */
        publication.markReady();

        /*
         * A persistência utiliza o estado anterior capturado antes
         * da transição. Se outra instância alterou a linha entre
         * leitura e UPDATE, o compare-and-set falhará.
         */
        publicationStatusRepository.updateStatus(
            publication,
            expectedStatus
        );

        return publication;
    }

    private void validateLoadedIdentity(
        long requestedPublicationId,
        Publication publication
    ) {

        Objects.requireNonNull(
            publication,
            "publicationQueryPort returned null Publication"
        );

        Long loadedId =
            publication.id();

        if (loadedId == null
            || loadedId != requestedPublicationId) {

            throw new IllegalStateException(
                "publicationQueryPort returned a Publication "
                    + "with unexpected identity"
            );
        }
    }
}
