package com.raspingamazon.infrastructure.http;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.HttpTransport;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Implementação de transporte HTTP baseada no cliente HTTP da plataforma Java.
 *
 * <p>A classe é responsável exclusivamente pelo transporte. Ela não conhece
 * Amazon, ofertas, produtos, HTML ou regras de negócio.</p>
 *
 * <p>A requisição utiliza uma identificação estável do projeto e declara
 * explicitamente os formatos e idiomas aceitos.</p>
 */
public final class JavaHttpTransport
    implements HttpTransport {

    private static final String USER_AGENT =
        "RaspingAmazon/1.0";

    private static final String ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    private static final String ACCEPT_LANGUAGE =
        "pt-BR,pt;q=0.9,en-US;q=0.7,en;q=0.6";

    private final HttpClient httpClient;

    private final Duration requestTimeout;

    public JavaHttpTransport(
        HttpClient httpClient,
        Duration requestTimeout
    ) {
        if (httpClient == null) {
            throw new NullPointerException(
                "HTTP client must not be null"
            );
        }

        if (requestTimeout == null) {
            throw new NullPointerException(
                "Request timeout must not be null"
            );
        }

        if (requestTimeout.isZero()
            || requestTimeout.isNegative()) {

            throw new IllegalArgumentException(
                "Request timeout must be positive"
            );
        }

        this.httpClient =
            httpClient;

        this.requestTimeout =
            requestTimeout;
    }

    @Override
    public HttpTransportResponse get(
        URI uri
    ) {
        if (uri == null) {
            throw new NullPointerException(
                "HTTP URI must not be null"
            );
        }

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(
                    uri
                )
                .timeout(
                    requestTimeout
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

        try {
            HttpResponse<String> response =
                httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                );

            return new HttpTransportResponse(
                response.statusCode(),
                response.body()
            );

        } catch (InterruptedException exception) {

            Thread.currentThread()
                .interrupt();

            throw new CollectionException(
                "HTTP request was interrupted",
                exception
            );

        } catch (Exception exception) {

            throw new CollectionException(
                "HTTP request failed",
                exception
            );
        }
    }
}
