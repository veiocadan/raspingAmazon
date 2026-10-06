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
import com.raspingamazon.application.collection.contract.SourceChangedException;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;

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
 * <p>Cada aquisição cria um BrowserContext independente. Cookies, sessão
 * e estado de navegação de um produto não contaminam a aquisição
 * seguinte, sem iniciar um Chromium novo para cada produto.</p>
 *
 * <p>O acesso aos objetos Playwright é serializado porque os objetos da
 * instância pertencem ao mesmo lifecycle físico.</p>
 *
 * <p>Depois da renderização e antes de entregar o HTML aos parsers, o
 * documento passa por validação defensiva da fonte.</p>
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

    private final AmazonProductPageDocumentValidator
        documentValidator;

    private final Playwright playwright;

    private final Browser browser;

    private boolean closed;

    public PlaywrightRenderedProductPageContentProvider() {

        this(
            Clock.systemUTC(),
            true
        );
    }

    public PlaywrightRenderedProductPageContentProvider(
        Clock clock
    ) {

        this(
            clock,
            true
        );
    }

    public PlaywrightRenderedProductPageContentProvider(
        Clock clock,
        boolean headless
    ) {

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.documentValidator =
            new AmazonProductPageDocumentValidator();

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

        } catch (
            SourceRestrictionException
            | SourceChangedException exception
        ) {

            ProductPageContentProviderException wrapped =
                new ProductPageContentProviderException(
                    "Amazon product page failed source validation",
                    exception
                );

            activeFailure =
                wrapped;

            throw wrapped;

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
                "Rendered product page did not expose "
                    + "a navigation response"
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

        /*
         * Fail closed.
         *
         * Nenhum parser recebe o documento antes da validação da fonte.
         */
        documentValidator.validate(
            html
        );

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
             * O scroll é auxiliar e não determina validade do documento.
             */
        }
    }

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
             * Ausência desses widgets não representa, isoladamente,
             * SOURCE_CHANGED.
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
