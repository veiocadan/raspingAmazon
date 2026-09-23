package com.raspingamazon.application.publication.presentation;

import com.raspingamazon.application.publication.PublicationData;

/**
 * Política responsável por selecionar os fatos comerciais
 * que devem ser disponibilizados para geração de publicação.
 *
 * <p>Esta política não:</p>
 *
 * <ul>
 *     <li>decide elegibilidade;</li>
 *     <li>aplica filtros comerciais;</li>
 *     <li>recalcula score;</li>
 *     <li>formata texto;</li>
 *     <li>gera link de associado;</li>
 *     <li>persiste Publication.</li>
 * </ul>
 *
 * <p>Ela transforma dados persistidos em uma representação
 * comercial determinística para consumo posterior pelo template.</p>
 */
public interface CommercialPresentationPolicy {

    /**
     * Retorna a versão semântica da política.
     */
    String version();

    /**
     * Produz a apresentação comercial correspondente aos dados
     * persistidos da oferta avaliada.
     *
     * @param publicationData dados persistidos da publicação
     * @return apresentação comercial selecionada pela política
     */
    CommercialPresentation present(
        PublicationData publicationData
    );
}
