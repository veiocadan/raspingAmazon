package com.raspingamazon.application.parsing.contract;

import com.raspingamazon.application.collection.contract.CollectionResult;

import java.util.List;

/**
 * Contrato responsável pela transformação do conteúdo bruto coletado
 * em registros estruturados de ofertas descobertas.
 *
 * <p>O parser não aplica regras de negócio, não calcula score,
 * não determina elegibilidade e não persiste dados.</p>
 */
public interface DealsParser {

    /**
     * Interpreta uma coleta já realizada.
     *
     * @param collectionResult conteúdo bruto e metadados da coleta
     * @return ofertas normalizadas encontradas no conteúdo
     */
    List<ParsedDeal> parse(CollectionResult collectionResult);
}