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
 * <p>Esta implementação representa a estratégia histórica de aquisição
 * por requisição HTTP simples, sem execução de JavaScript.</p>
 *
 * <p>Ela permanece disponível como adapter de infraestrutura e como
 * ferramenta de teste, diagnóstico e fallback explicitamente composto.
 * A estratégia produtiva da página individual será evoluída para DOM
 * renderizado conforme ADR-0014.</p>
 *
 * <p>Falhas HTTP preservam status e um trecho limitado da resposta
 * para que a política operacional da aplicação possa distinguir
 * rate limit, indisponibilidade, recurso ausente e outras categorias
 * sem interpretar Strings de mensagens de exceção.</p>
 */
public final class HttpProductPageContentProvider
    implements ProductPageContentProvider {

    private static final String USER_AGENT =
        "RaspingAmazon/1.0";

    private static final String ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    private static final String ACCEPT_LANGUAGE =
        "pt-BR,pt;q=0.9,en-US;q=0.7,en;q=0.6";

    private static final Duration REQUEST_TIMEOUT =
        Duration.ofSeconds(
            20
        );

    private static final int MAX_ERROR_BODY_EXCERPT_LENGTH =
        2000;

    private final HttpClient httpClient;

    private final Clock clock;

    /**
     * Construtor padrão.
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
     * Executa uma requisição HTTP e retorna o corpo recebido.
     *
     * <p>Não executa JavaScript e não aguarda widgets dinâmicos.</p>
     *
     * <p>Quando a origem devolve um status HTTP não aceito, o status e
     * um trecho limitado do corpo são preservados na exceção. Isso
     * permite que o classificador operacional central tome a decisão
     * de retry sem depender da mensagem textual desta classe.</p>
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
                    ACCEPT
                )
                .header(
                    "Accept-Language",
                    ACCEPT_LANGUAGE
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

        int statusCode =
            response.statusCode();

        if (statusCode < 200
            || statusCode >= 300) {

            throw new ProductPageContentProviderException(
                "Product page returned HTTP "
                    + statusCode,
                statusCode,
                createBodyExcerpt(
                    response.body()
                )
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

    /**
     * Limita a evidência textual preservada em falhas HTTP.
     *
     * <p>O objetivo é fornecer diagnóstico suficiente sem transportar
     * páginas HTML potencialmente grandes dentro da exceção.</p>
     */
    private String createBodyExcerpt(
        String body
    ) {

        if (body == null) {
            return null;
        }

        if (body.length()
            <= MAX_ERROR_BODY_EXCERPT_LENGTH) {

            return body;
        }

        return body.substring(
            0,
            MAX_ERROR_BODY_EXCERPT_LENGTH
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
