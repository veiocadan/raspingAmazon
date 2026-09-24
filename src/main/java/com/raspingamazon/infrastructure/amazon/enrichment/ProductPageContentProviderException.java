package com.raspingamazon.infrastructure.amazon.enrichment;

/**
 * Falha de infraestrutura durante a aquisição do conteúdo de uma
 * página individual de produto.
 *
 * <p>A exceção é compartilhada pelos providers para que o
 * AmazonProductPageEnrichmentClient não precise conhecer detalhes
 * de HTTP, navegador ou qualquer mecanismo concreto de aquisição.</p>
 */
public final class ProductPageContentProviderException
    extends RuntimeException {

    public ProductPageContentProviderException(
        String message
    ) {
        super(
            message
        );
    }

    public ProductPageContentProviderException(
        String message,
        Throwable cause
    ) {
        super(
            message,
            cause
        );
    }
}
