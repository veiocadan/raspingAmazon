package com.raspingamazon.infrastructure.amazon.enrichment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Provider de página individual baseado em HTTP convencional.
 *
 * <p>Esta implementação representa exatamente a estratégia utilizada
 * historicamente pelo projeto: uma requisição HTTP simples, sem
 * execução de JavaScript e sem renderização de DOM.</p>
 *
 * <p>A existência deste adapter separado permite que uma estratégia
 * de página renderizada seja adicionada posteriormente sem misturar
 * browser automation com parsing ou regras de negócio.</p>
 */
public final class HttpProductPageContentProvider
    implements ProductPageContentProvider {

    private static final String USER_AGENT =
        "RaspingAmazon/1.0";

    private static final Duration REQUEST_TIMEOUT =
        Duration.ofSeconds(
            20
        );

    private final HttpClient httpClient;

    private final Clock clock;

    /**
     * Construtor padrão de produção.
     */
    public HttpProductPageContentProvider() {
        this(
            createDefaultHttpClient(),
            Clock.systemUTC()
        );
    }

    /**
     * Variante compatível com compositions que já possuem um
     * HttpClient compartilhado.
     *
     * @param httpClient cliente HTTP compartilhado
     */
    public HttpProductPageContentProvider(
        HttpClient httpClient
    ) {
        this(
            httpClient,
            Clock.systemUTC()
        );
    }

    /**
     * Variante totalmente injetável para testes.
     *
     * @param httpClient cliente HTTP
     * @param clock relógio utilizado no timestamp da aquisição
     */
    public HttpProductPageContentProvider(
        HttpClient httpClient,
        Clock clock
    ) {

        this.httpClient =
            Objects.requireNonNull(
                httpClient,
                "httpClient must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Executa uma única requisição HTTP e retorna o corpo recebido.
     *
     * <p>Não executa JavaScript e não tenta aguardar widgets
     * dinâmicos da Amazon.</p>
     */
    @Override
    public ProductPageContent load(
        URI productUri
    ) {

        Objects.requireNonNull(
            productUri,
            "productUri must not be null"
        );

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(
                    productUri
                )
                .timeout(
                    REQUEST_TIMEOUT
                )
                .header(
                    "User-Agent",
                    USER_AGENT
                )
                .header(
                    "Accept",
                    "text/html"
                )
                .GET()
                .build();

        HttpResponse<String> response;

        try {

            response =
                httpClient.send(
                    request,
                    HttpResponse
                        .BodyHandlers
                        .ofString()
                );

        } catch (InterruptedException exception) {

            Thread.currentThread()
                .interrupt();

            throw new ProductPageContentProviderException(
                "Product page request was interrupted",
                exception
            );

        } catch (Exception exception) {

            throw new ProductPageContentProviderException(
                "Failed to retrieve product page",
                exception
            );
        }

        if (response.statusCode() < 200
            || response.statusCode() >= 300) {

            throw new ProductPageContentProviderException(
                "Product page returned HTTP "
                    + response.statusCode()
            );
        }

        String html =
            response.body();

        if (html == null
            || html.isBlank()) {

            throw new ProductPageContentProviderException(
                "Product page returned an empty response"
            );
        }

        URI resolvedUri =
            response.uri() == null
                ? productUri
                : response.uri();

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

    private static HttpClient createDefaultHttpClient() {

        return HttpClient.newBuilder()
            .connectTimeout(
                REQUEST_TIMEOUT
            )
            .followRedirects(
                HttpClient.Redirect.NORMAL
            )
            .build();
    }
}
