package com.raspingamazon.application.publication.affiliate;

/**
 * Contrato para geração versionada de links de associado.
 *
 * <p>O restante da aplicação não deve concatenar parâmetros
 * de rastreamento diretamente.</p>
 */
public interface AffiliateLinkGenerator {

    /**
     * Retorna a versão semântica da estratégia.
     */
    String version();

    /**
     * Gera um link de associado a partir de uma URL de produto.
     *
     * @param productUrl URL persistida do produto
     * @return link de associado versionado
     */
    AffiliateLink generate(
        String productUrl
    );
}
