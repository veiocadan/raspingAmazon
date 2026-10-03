package com.raspingamazon.infrastructure.diagnostic;

import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContent;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContentProvider;

import java.net.URI;
import java.time.Clock;

/**
 * Adapter de compatibilidade dos probes externos.
 *
 * <p>A implementação verdadeira de aquisição renderizada pertence agora
 * ao runtime produtivo. O probe somente configura essa implementação
 * para execução diagnóstica.</p>
 */
public final class PlaywrightRenderedProductPageContentProvider
    implements ProductPageContentProvider, AutoCloseable {

    private final
    com.raspingamazon.infrastructure.amazon.enrichment
        .PlaywrightRenderedProductPageContentProvider
        delegate;

    /**
     * Mantém a propriedade histórica utilizada pelos probes:
     *
     * <pre>
     * -Damazon.probe.headless=false
     * </pre>
     */
    public PlaywrightRenderedProductPageContentProvider() {

        this(
            Clock.systemUTC(),
            Boolean.parseBoolean(
                System.getProperty(
                    "amazon.probe.headless",
                    "true"
                )
            )
        );
    }

    public PlaywrightRenderedProductPageContentProvider(
        Clock clock,
        boolean headless
    ) {

        this.delegate =
            new com.raspingamazon.infrastructure.amazon.enrichment
                .PlaywrightRenderedProductPageContentProvider(
                clock,
                headless
            );
    }

    @Override
    public ProductPageContent load(
        URI productUri
    ) {

        return delegate.load(
            productUri
        );
    }

    @Override
    public void close() {

        delegate.close();
    }
}
