package com.raspingamazon.application.history;

import com.raspingamazon.domain.product.Asin;

import java.util.Optional;

/**
 * Porta de consulta do estado histórico agregado de um ASIN.
 *
 * <p>Trata-se deliberadamente de um read model. A implementação
 * concreta pode consultar múltiplas tabelas para responder a uma
 * pergunta operacional sem transportar essa estrutura SQL para o
 * domínio.</p>
 */
public interface OfferHistoryStatusQueryPort {

    /**
     * Consulta o status histórico operacional do ASIN.
     *
     * <p>Optional.empty significa que o Product ainda não existe.</p>
     *
     * @param asin ASIN consultado
     * @return status histórico, quando o produto existir
     */
    Optional<OfferHistoryStatus> findByAsin(
        Asin asin
    );
}
