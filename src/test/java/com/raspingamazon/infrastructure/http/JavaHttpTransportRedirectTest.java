package com.raspingamazon.infrastructure.http;

import com.raspingamazon.application.collection.contract.HttpTransportResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes de redirecionamento do transporte HTTP.
 *
 * <p>O teste utiliza um servidor HTTP local e controlado para verificar
 * que o cliente segue um redirecionamento HTTP e entrega ao collector
 * a resposta do endereço final.</p>
 *
 * <p>Esta validação permanece exclusivamente na camada de transporte.
 * Nenhum conteúdo de produto, HTML, JSON ou ASIN é interpretado.</p>
 */
class JavaHttpTransportRedirectTest {

    /**
     * Conteúdo retornado pelo endpoint final.
     */
    private static final String FINAL_CONTENT =
            "redirected-content";

    /**
     * Verifica que um redirecionamento HTTP seja seguido normalmente.
     *
     * @throws Exception caso o servidor HTTP local não possa ser
     *                   inicializado ou utilizado
     */
    @Test
    void shouldFollowHttpRedirect() throws Exception {

        HttpServer server = createRedirectServer();

        try {
            /*
             * O servidor utiliza uma porta dinâmica para evitar conflito
             * com outros processos da máquina.
             */
            server.start();

            int port = server.getAddress().getPort();

            URI redirectUri = URI.create(
                    "http://localhost:"
                            + port
                            + "/redirect"
            );

            HttpClient httpClient =
                    HttpClient.newBuilder()
                            .followRedirects(
                                    HttpClient.Redirect.NORMAL
                            )
                            .build();

            var transport =
                    new JavaHttpTransport(
                            httpClient,
                            java.time.Duration.ofSeconds(5)
                    );

            HttpTransportResponse response =
                    transport.get(redirectUri);

            /*
             * O transporte deve entregar o conteúdo obtido depois
             * do redirecionamento.
             */
            assertEquals(
                    200,
                    response.statusCode()
            );

            assertEquals(
                    FINAL_CONTENT,
                    response.body()
            );

        } finally {
            /*
             * O servidor deve ser encerrado mesmo quando o teste
             * apresentar uma falha.
             */
            server.stop(0);
        }
    }

    /**
     * Cria o servidor HTTP utilizado pelo teste.
     *
     * <p>O endpoint {@code /redirect} responde com HTTP 302 e aponta
     * para {@code /final}. O endpoint {@code /final} fornece o conteúdo
     * efetivamente coletado.</p>
     *
     * @return servidor configurado e ainda não iniciado
     * @throws IOException caso o servidor não possa ser criado
     */
    private HttpServer createRedirectServer()
            throws IOException {

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress("localhost", 0),
                        0
                );

        /*
         * Endpoint inicial da requisição.
         */
        server.createContext(
                "/redirect",
                exchange -> {

                    String location =
                            "http://localhost:"
                                    + exchange.getLocalAddress()
                                    .getPort()
                                    + "/final";

                    /*
                     * HTTP 302 indica que o recurso deve ser buscado
                     * em outro endereço.
                     */
                    exchange.getResponseHeaders()
                            .add(
                                    "Location",
                                    location
                            );

                    exchange.sendResponseHeaders(
                            302,
                            -1
                    );

                    exchange.close();
                }
        );

        /*
         * Endpoint final após o redirecionamento.
         */
        server.createContext(
                "/final",
                exchange -> {

                    byte[] response =
                            FINAL_CONTENT.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            );

                    exchange.sendResponseHeaders(
                            200,
                            response.length
                    );

                    try (OutputStream outputStream =
                                 exchange.getResponseBody()) {

                        outputStream.write(response);
                    }
                }
        );

        return server;
    }
}