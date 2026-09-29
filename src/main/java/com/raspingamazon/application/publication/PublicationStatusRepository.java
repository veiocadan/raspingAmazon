package com.raspingamazon.application.publication;

import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;

/**
 * Contrato de persistência das transições de estado de Publication.
 *
 * <p>A validade da transição pertence ao domínio.</p>
 *
 * <p>Exemplo:</p>
 *
 * <pre>
 * publication.markReady();
 * </pre>
 *
 * <p>Depois de a entidade aceitar a transição, esta porta persiste
 * o novo estado com compare-and-set, exigindo que o banco ainda
 * contenha o estado anterior esperado.</p>
 *
 * <p>Assim, uma operação baseada em estado obsoleto não pode
 * sobrescrever silenciosamente uma alteração concorrente.</p>
 */
public interface PublicationStatusRepository {

    /**
     * Persiste o estado atual de uma Publication já persistida,
     * somente se o estado armazenado ainda corresponder ao estado
     * anterior esperado.
     *
     * @param publication entidade já transitada pelo domínio
     * @param expectedStatus estado que deve existir no banco
     *                       antes da atualização
     * @throws IllegalArgumentException quando a Publication ainda
     *                                  não possuir identidade persistida
     *                                  ou não houver mudança de estado
     * @throws IllegalStateException quando a linha não existir ou
     *                               seu estado persistido não for
     *                               o esperado
     */
    void updateStatus(
        Publication publication,
        PublicationStatus expectedStatus
    );
}
