package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.collection.contract.CollectionException;

/**
 * Falha técnica ocorrida durante a aquisição do conteúdo de uma
 * página individual de produto.
 *
 * <p>A exceção pertence à infraestrutura de aquisição da página, mas
 * reutiliza o contrato funcional de CollectionException para preservar
 * informações operacionais que já são compreendidas pelo classificador
 * central de falhas.</p>
 *
 * <p>Assim, providers HTTP, browser ou implementações futuras podem
 * preservar, quando disponível:</p>
 *
 * <ul>
 *     <li>status HTTP;</li>
 *     <li>trecho diagnóstico da resposta;</li>
 *     <li>causa técnica original.</li>
 * </ul>
 *
 * <p>Isso não faz o enrichment depender da tecnologia concreta de
 * aquisição e também evita criar uma segunda taxonomia de falhas
 * exclusiva da página individual.</p>
 */
public final class ProductPageContentProviderException
    extends CollectionException {

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

    public ProductPageContentProviderException(
        String message,
        int httpStatusCode,
        String responseBodyExcerpt
    ) {

        super(
            message,
            httpStatusCode,
            responseBodyExcerpt
        );
    }
}
