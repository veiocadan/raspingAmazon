package com.raspingamazon.infrastructure.amazon.enrichment;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Provider produtivo da página individual Amazon baseado em DOM
 * renderizado.
 *
 * <p>Uma única instância de Playwright e uma única instância de Browser
 * são mantidas durante o lifecycle do provider.</p>
 *
 * <p>Cada aquisição cria um BrowserContext independente. Dessa forma
 * cookies, sessão e estado de navegação de um produto não contaminam a
 * aquisição seguinte, sem pagar o custo de iniciar um novo Chromium para
 * cada produto.</p>
 *
 * <p>O acesso aos objetos Playwright é serializado. O Playwright Java não
 * é thread-safe; portanto load() e close() nunca podem executar chamadas
 * Playwright simultaneamente sobre esta instância.</p>
 *
 * <p>Esta classe somente adquire e renderiza conteúdo. Ela não interpreta
 * seller, delivery, reviews, preços ou condições comerciais.</p>
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

    private boolean closed;

    /**
     * Construção padrão de produção.
     *
     * <p>O Chromium é executado em modo headless.</p>
     */
    public PlaywrightRenderedProductPageContentProvider() {

        this(
            Clock.systemUTC(),
            true
        );
    }

    /**
     * Construção produtiva com relógio compartilhado.
     *
     * @param clock relógio utilizado em collectedAt
     */
    public PlaywrightRenderedProductPageContentProvider(
        Clock clock
    ) {

        this(
            clock,
            true
        );
    }

    /**
     * Variante completa utilizada também pelos probes externos.
     *
     * @param clock relógio da aquisição
     * @param headless true para execução sem interface visual
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

        Playwright createdPlaywright =
            null;

        Browser createdBrowser =
            null;

        try {

            createdPlaywright =
                Playwright.create();

            createdBrowser =
                createdPlaywright
                    .chromium()
                    .launch(
                        new BrowserType.LaunchOptions()
                            .setHeadless(
                                headless
                            )
                    );

        } catch (RuntimeException exception) {

            if (createdBrowser != null) {

                try {

                    createdBrowser.close();

                } catch (RuntimeException closeFailure) {

                    exception.addSuppressed(
                        closeFailure
                    );
                }
            }

            if (createdPlaywright != null) {

                try {

                    createdPlaywright.close();

                } catch (RuntimeException closeFailure) {

                    exception.addSuppressed(
                        closeFailure
                    );
                }
            }

            throw new ProductPageContentProviderException(
                "Failed to start Playwright Chromium",
                exception
            );
        }

        this.playwright =
            createdPlaywright;

        this.browser =
            createdBrowser;
    }

    /**
     * Renderiza uma página individual.
     *
     * <p>O método é synchronized porque Browser, BrowserContext, Page e
     * demais objetos Playwright pertencentes a esta instância não podem
     * ser utilizados concorrentemente.</p>
     */
    @Override
    public synchronized ProductPageContent load(
        URI productUri
    ) {

        Objects.requireNonNull(
            productUri,
            "productUri must not be null"
        );

        requireOpen();

        BrowserContext context =
            null;

        RuntimeException activeFailure =
            null;

        try {

            context =
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

            return loadRenderedContent(
                context,
                productUri
            );

        } catch (ProductPageContentProviderException exception) {

            activeFailure =
                exception;

            throw exception;

        } catch (RuntimeException exception) {

            ProductPageContentProviderException wrapped =
                new ProductPageContentProviderException(
                    "Failed to render Amazon product page",
                    exception
                );

            activeFailure =
                wrapped;

            throw wrapped;

        } finally {

            closeContext(
                context,
                activeFailure
            );
        }
    }

    private ProductPageContent loadRenderedContent(
        BrowserContext context,
        URI productUri
    ) {

        Page page =
            context.newPage();

        Response navigationResponse =
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

        if (navigationResponse == null) {

            throw new ProductPageContentProviderException(
                "Rendered product page did not expose a navigation response"
            );
        }

        int httpStatusCode =
            navigationResponse.status();

        if (httpStatusCode < 200
            || httpStatusCode >= 300) {

            throw new ProductPageContentProviderException(
                "Rendered product page returned HTTP "
                    + httpStatusCode,
                httpStatusCode,
                null
            );
        }

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

        URI resolvedUri =
            resolveUri(
                page,
                productUri
            );

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
    }

    /**
     * Tenta provocar o carregamento de widgets lazy.
     *
     * <p>A ausência de um desses seletores não é, isoladamente, uma
     * falha. Produtos diferentes podem apresentar estruturas
     * comerciais diferentes.</p>
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
             * O scroll é auxiliar.
             *
             * A validação semântica do conteúdo pertence às etapas
             * posteriores da FASE 20-C.
             */
        }
    }

    /**
     * Aguarda por tempo limitado conteúdo comercial que costuma ser
     * preenchido dinamicamente.
     *
     * <p>O timeout desta espera não invalida sozinho a aquisição. O DOM
     * final continua sendo evidência e será validado separadamente.</p>
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
             * Não convertemos ausência desses widgets em SOURCE_CHANGED.
             *
             * A FASE 20-C3 introduzirá validação explícita do documento,
             * challenge/CAPTCHA e mudança estrutural.
             */
        }
    }

    private URI resolveUri(
        Page page,
        URI fallback
    ) {

        String pageUrl =
            page.url();

        if (pageUrl == null
            || pageUrl.isBlank()) {

            return fallback;
        }

        try {

            return URI.create(
                pageUrl
            );

        } catch (IllegalArgumentException exception) {

            return fallback;
        }
    }

    /**
     * Preserva a falha funcional original caso o fechamento do contexto
     * também falhe.
     */
    private void closeContext(
        BrowserContext context,
        RuntimeException activeFailure
    ) {

        if (context == null) {
            return;
        }

        try {

            context.close();

        } catch (RuntimeException closeFailure) {

            if (activeFailure != null) {

                activeFailure.addSuppressed(
                    closeFailure
                );

                return;
            }

            throw new ProductPageContentProviderException(
                "Failed to close rendered product page context",
                closeFailure
            );
        }
    }

    private void requireOpen() {

        if (closed) {

            throw new IllegalStateException(
                "Rendered product page provider is closed"
            );
        }
    }

    /**
     * Encerra primeiro o browser e depois a instância Playwright.
     *
     * <p>close() é idempotente.</p>
     */
    @Override
    public synchronized void close() {

        if (closed) {
            return;
        }

        closed =
            true;

        RuntimeException failure =
            null;

        try {

            browser.close();

        } catch (RuntimeException exception) {

            failure =
                exception;
        }

        try {

            playwright.close();

        } catch (RuntimeException exception) {

            if (failure == null) {

                failure =
                    exception;

            } else {

                failure.addSuppressed(
                    exception
                );
            }
        }

        if (failure != null) {

            throw new ProductPageContentProviderException(
                "Failed to close Playwright product page provider",
                failure
            );
        }
    }
}
