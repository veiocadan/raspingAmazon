package com.raspingamazon.infrastructure.amazon.enrichment;

import java.net.URI;

/**
 * Porta interna da infraestrutura para aquisição do conteúdo de uma
 * página individual de produto.
 *
 * <p>Este contrato deliberadamente não interpreta o conteúdo.</p>
 *
 * <p>Implementações possíveis incluem:</p>
 *
 * <ul>
 *     <li>HTTP bruto;</li>
 *     <li>DOM renderizado por navegador;</li>
 *     <li>fixtures controladas em testes.</li>
 * </ul>
 *
 * <p>Seller, delivery e condições de pagamento continuam sendo
 * responsabilidade dos parsers especializados.</p>
 */
public interface ProductPageContentProvider {

    /**
     * Adquire o conteúdo observado para uma página de produto.
     *
     * @param productUri URI da página individual
     * @return conteúdo adquirido com metadados da operação
     */
    ProductPageContent load(
        URI productUri
    );
}
