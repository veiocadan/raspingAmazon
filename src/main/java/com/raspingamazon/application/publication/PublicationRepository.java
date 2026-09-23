package com.raspingamazon.application.publication;

import com.raspingamazon.domain.publication.Publication;

/**
 * Contrato de persistência de Publication.
 *
 * <p>A identidade idempotente da geração é formada por:</p>
 *
 * <ul>
 *     <li>DealEvaluation;</li>
 *     <li>versão do template;</li>
 *     <li>versão da política comercial;</li>
 *     <li>versão do gerador de link de associado.</li>
 * </ul>
 *
 * <p>Persistir novamente a mesma identidade deve devolver a
 * Publication já existente em vez de criar uma duplicata.</p>
 */
public interface PublicationRepository {

    /**
     * Persiste uma nova publicação de forma idempotente.
     *
     * @param publication publicação ainda não persistida
     * @return publicação persistida ou a publicação já existente
     *         para a mesma identidade de geração
     */
    Publication save(
        Publication publication
    );
}
