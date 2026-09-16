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
 * <p>O transporte utiliza um User-Agent estável para identificar de forma
 * explícita e reproduzível o cliente responsável pela coleta.</p>
 */
public final class JavaHttpTransport implements HttpTransport {

    /**
     * Identificação estável do cliente de coleta.
     *
     * <p>O valor não é aleatório porque a reprodutibilidade da coleta
     * é um requisito importante da FASE 5.</p>
     */
    private static final String USER_AGENT = "RaspingAmazon/1.0";

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * Cria um transporte HTTP com o cliente e timeout fornecidos.
     *
     * @param httpClient cliente HTTP da plataforma Java
     * @param requestTimeout tempo máximo permitido para a requisição
     */
    public JavaHttpTransport(
            HttpClient httpClient,
            Duration requestTimeout
    ) {
        if (httpClient == null) {
            throw new NullPointerException("HTTP client must not be null");
        }

        if (requestTimeout == null) {
            throw new NullPointerException(
                    "Request timeout must not be null"
            );
        }

        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "Request timeout must be positive"
            );
        }

        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Executa uma requisição GET e converte a resposta para o contrato
     * independente da implementação HTTP.
     *
     * @param uri URI absoluta do recurso
     * @return status HTTP e corpo recebido
     * @throws CollectionException em caso de falha de transporte
     */
    @Override
    public HttpTransportResponse get(URI uri) {
        if (uri == null) {
            throw new NullPointerException("HTTP URI must not be null");
        }

        var request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            var response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            return new HttpTransportResponse(
                    response.statusCode(),
                    response.body()
            );
        } catch (InterruptedException exception) {
            // Restauramos a flag de interrupção antes de propagar a falha.
            Thread.currentThread().interrupt();

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
