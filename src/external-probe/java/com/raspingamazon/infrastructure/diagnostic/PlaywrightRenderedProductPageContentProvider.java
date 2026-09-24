package com.raspingamazon.infrastructure.diagnostic;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContent;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContentProvider;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContentProviderException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Provider diagnóstico de página de produto com DOM renderizado.
 *
 * <p>Esta implementação existe exclusivamente na árvore
 * src/external-probe/java. Portanto ela não faz parte do runtime
 * de produção nem da suíte hermética padrão.</p>
 *
 * <p>O objetivo desta classe é validar a seam criada por
 * ProductPageContentProvider usando um navegador real que executa
 * JavaScript e observa o DOM final da página.</p>
 *
 * <p>Ela deliberadamente não contém regras de negócio, não interpreta
 * pagamento e não decide elegibilidade.</p>
 */
public final class PlaywrightRenderedProductPageContentProvider
    implements ProductPageContentProvider, AutoCloseable {

    private static final Duration NAVIGATION_TIMEOUT =
        Duration.ofSeconds(
            60
        );

    private static final Duration COMMERCIAL_RENDER_TIMEOUT =
        Duration.ofSeconds(
            30
        );

    private final Clock clock;

    private final Playwright playwright;

    private final Browser browser;

    /**
     * Construtor padrão do probe.
     *
     * <p>O modo headless pode ser controlado pela propriedade:</p>
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

    /**
     * Variante injetável para diagnóstico.
     *
     * @param clock relógio do timestamp de aquisição
     * @param headless true para navegador sem interface visual
     */
    public PlaywrightRenderedProductPageContentProvider(
        Clock clock,
        boolean headless
    ) {

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        try {

            this.playwright =
                Playwright.create();

            this.browser =
                playwright.chromium()
                    .launch(
                        new BrowserType.LaunchOptions()
                            .setHeadless(
                                headless
                            )
                    );

        } catch (RuntimeException exception) {

            throw new ProductPageContentProviderException(
                "Failed to start Playwright Chromium",
                exception
            );
        }
    }

    /**
     * Carrega a página, executa o JavaScript e serializa o DOM
     * observado depois da renderização.
     */
    @Override
    public ProductPageContent load(
        URI productUri
    ) {

        Objects.requireNonNull(
            productUri,
            "productUri must not be null"
        );

        BrowserContext context =
            browser.newContext(
                new Browser.NewContextOptions()
                    .setLocale(
                        "pt-BR"
                    )
                    .setViewportSize(
                        1440,
                        1200
                    )
            );

        try {

            Page page =
                context.newPage();

            page.navigate(
                productUri.toString(),
                new Page.NavigateOptions()
                    .setWaitUntil(
                        WaitUntilState.DOMCONTENTLOADED
                    )
                    .setTimeout(
                        NAVIGATION_TIMEOUT.toMillis()
                    )
            );

            page.locator(
                "body"
            ).waitFor();

            bestEffortScroll(
                page,
                "#apex_desktop"
            );

            bestEffortScroll(
                page,
                "#promotionMessageInsideBuyBox_feature_div"
            );

            bestEffortScroll(
                page,
                "#oneTimePaymentPrice_feature_div"
            );

            bestEffortScroll(
                page,
                "#installmentCalculatorCentral_feature_div"
            );

            waitForCommercialRendering(
                page
            );

            String html =
                page.content();

            if (html == null
                || html.isBlank()) {

                throw new ProductPageContentProviderException(
                    "Rendered product page returned an empty DOM"
                );
            }

            URI resolvedUri;

            try {

                resolvedUri =
                    URI.create(
                        page.url()
                    );

            } catch (IllegalArgumentException exception) {

                resolvedUri =
                    productUri;
            }

            OffsetDateTime collectedAt =
                OffsetDateTime.ofInstant(
                    clock.instant(),
                    ZoneOffset.UTC
                );

            return new ProductPageContent(
                productUri,
                resolvedUri,
                html,
                collectedAt
            );

        } catch (ProductPageContentProviderException exception) {

            throw exception;

        } catch (RuntimeException exception) {

            throw new ProductPageContentProviderException(
                "Failed to render Amazon product page",
                exception
            );

        } finally {

            context.close();
        }
    }

    /**
     * Tenta provocar o carregamento de widgets que podem estar fora
     * da região inicialmente visível.
     *
     * <p>A ausência do seletor não é erro: produtos diferentes podem
     * possuir composições diferentes.</p>
     */
    private void bestEffortScroll(
        Page page,
        String selector
    ) {

        try {

            Locator locator =
                page.locator(
                    selector
                );

            if (locator.count() > 0) {

                locator.first()
                    .scrollIntoViewIfNeeded();
            }

        } catch (PlaywrightException ignored) {

            /*
             * É uma tentativa auxiliar de ativação de conteúdo lazy.
             *
             * A validade do DOM será inspecionada pelo probe e pelos
             * parsers, não por esta operação de scroll.
             */
        }
    }

    /**
     * Aguarda, de forma limitada, algum conteúdo comercial dinâmico.
     *
     * <p>O timeout não invalida a aquisição. Mesmo quando a Amazon
     * não preenche esses widgets, queremos salvar o DOM resultante
     * para diagnóstico.</p>
     */
    private void waitForCommercialRendering(
        Page page
    ) {

        String expression =
            """
            () => {
                const ids = [
                    'promotionMessageInsideBuyBox_feature_div',
                    'oneTimePaymentPrice_feature_div',
                    'installmentCalculatorCentral_feature_div'
                ];

                return ids.some(id => {
                    const element =
                        document.getElementById(id);

                    if (!element) {
                        return false;
                    }

                    const text =
                        (element.innerText || '').trim();

                    return text.length > 0;
                });
            }
            """;

        try {

            page.waitForFunction(
                expression,
                null,
                new Page.WaitForFunctionOptions()
                    .setTimeout(
                        COMMERCIAL_RENDER_TIMEOUT
                            .toMillis()
                    )
            );

        } catch (PlaywrightException ignored) {

            /*
             * A página continua sendo retornada.
             *
             * Isso é importante porque um timeout comercial também
             * é evidência válida para a investigação externa.
             */
        }
    }

    @Override
    public void close() {

        try {

            browser.close();

        } finally {

            playwright.close();
        }
    }
}
