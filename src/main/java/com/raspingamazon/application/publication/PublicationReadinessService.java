package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.port.PublicationQueryPort;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.util.Objects;

/**
 * Caso de uso responsável por garantir automaticamente que uma
 * Publication gerada esteja liberada para o mecanismo de despacho.
 *
 * <p>Este serviço não representa aprovação humana.</p>
 *
 * <p>READY significa que a Publication foi liberada pelas regras
 * automáticas aplicáveis e pode prosseguir para a outbox.</p>
 *
 * <p>Semântica:</p>
 *
 * <pre>
 * CREATED
 *     -> transiciona para READY
 *     -> persiste com compare-and-set
 *
 * READY
 *     -> retorna sem nova escrita
 *
 * PUBLISHED / FAILED
 *     -> não executa transição automática
 * </pre>
 *
 * <p>A operação é idempotente para CREATED/READY e tolera a corrida
 * legítima em que outra execução tenha realizado CREATED -> READY
 * entre a leitura e o compare-and-set.</p>
 *
 * <p>Este serviço não conhece canais, Telegram, WhatsApp, outbox,
 * quotas ou providers externos.</p>
 */
public final class PublicationReadinessService
    implements PublicationReadinessUseCase {

    private final PublicationQueryPort publicationQueryPort;

    private final PublicationStatusRepository
        publicationStatusRepository;

    public PublicationReadinessService(
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
     * Garante que a Publication esteja READY para despacho.
     *
     * @param publicationId identidade persistente da Publication
     * @return Publication em estado READY
     */
    @Override
    public Publication ensureReady(
        long publicationId
    ) {

        validatePublicationId(
            publicationId
        );

        Publication publication =
            loadPublication(
                publicationId
            );

        if (publication.status()
            == PublicationStatus.READY) {

            return publication;
        }

        if (publication.status()
            != PublicationStatus.CREATED) {

            throw new IllegalStateException(
                "Publication "
                    + publicationId
                    + " cannot become READY from status "
                    + publication.status()
            );
        }

        /*
         * A validade da transição continua pertencendo ao domínio.
         */
        publication.markReady();

        try {

            publicationStatusRepository.updateStatus(
                publication,
                PublicationStatus.CREATED
            );

            return publication;

        } catch (IllegalStateException concurrentConflict) {

            /*
             * Uma segunda execução do pipeline pode ter carregado
             * CREATED ao mesmo tempo e vencido o compare-and-set.
             *
             * Nesse caso recarregamos o estado persistido.
             *
             * Se já estiver READY, a finalidade desta operação foi
             * alcançada e a execução é considerada idempotente.
             *
             * Qualquer outro estado preserva a falha original.
             */
            Publication reloaded =
                reloadAfterConcurrentConflict(
                    publicationId,
                    concurrentConflict
                );

            if (reloaded.status()
                == PublicationStatus.READY) {

                return reloaded;
            }

            throw concurrentConflict;
        }
    }

    private Publication loadPublication(
        long publicationId
    ) {

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

        return publication;
    }

    private Publication reloadAfterConcurrentConflict(
        long publicationId,
        IllegalStateException originalFailure
    ) {

        Publication publication =
            publicationQueryPort.findById(
                    publicationId
                )
                .orElseThrow(
                    () ->
                        originalFailure
                );

        validateLoadedIdentity(
            publicationId,
            publication
        );

        return publication;
    }

    private void validatePublicationId(
        long publicationId
    ) {

        if (publicationId <= 0L) {

            throw new IllegalArgumentException(
                "publicationId must be positive"
            );
        }
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
