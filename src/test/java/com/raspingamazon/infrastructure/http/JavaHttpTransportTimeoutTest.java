package com.raspingamazon.infrastructure.http;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes de timeout do transporte HTTP baseado na plataforma Java.
 *
 * <p>O teste utiliza um servidor HTTP local para produzir uma resposta
 * deliberadamente lenta. Isso permite validar o comportamento de timeout
 * sem depender de serviços externos.</p>
 *
 * <p>A responsabilidade deste teste é exclusivamente o transporte HTTP.
 * Não são exercitados Amazon, HTML, JSON, ASIN ou regras de negócio.</p>
 */
class JavaHttpTransportTimeoutTest {

    /**
     * Tempo curto utilizado pelo transporte para provocar o timeout
     * de forma determinística.
     */
    private static final Duration REQUEST_TIMEOUT =
            Duration.ofMillis(100);

    /**
     * Tempo suficientemente maior que o timeout do cliente para que
     * o servidor local não consiga responder antes do cancelamento.
     */
    private static final Duration SERVER_DELAY =
            Duration.ofMillis(500);

    /**
     * Verifica que uma requisição que ultrapassa o timeout seja
     * convertida em {@link CollectionException}.
     *
     * @throws Exception caso a infraestrutura HTTP local não possa
     *                   ser criada
     */
    @Test
    void shouldConvertHttpTimeoutIntoCollectionException()
            throws Exception {

        HttpServer server = createSlowServer();

        try {
            server.start();

            URI source = URI.create(
                    "http://localhost:"
                            + server.getAddress().getPort()
                            + "/slow"
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
                            REQUEST_TIMEOUT
                    );

            CollectionException exception =
                    assertThrows(
                            CollectionException.class,
                            () -> transport.get(source)
                    );

            /*
             * A camada de transporte deve expor uma falha operacional
             * padronizada para a camada de coleta.
             */
            assertEquals(
                    "HTTP request failed",
                    exception.getMessage()
            );

            /*
             * A causa original deve ser preservada. Isso mantém
             * informações úteis para diagnóstico sem expor detalhes
             * de implementação para o contrato superior.
             */
            assertEquals(
                    true,
                    exception.getCause() != null
            );

        } finally {
            /*
             * O servidor deve ser encerrado independentemente do
             * resultado do teste.
             */
            server.stop(0);
        }
    }

    /**
     * Cria um servidor HTTP local cuja resposta é propositalmente lenta.
     *
     * @return servidor HTTP configurado e ainda não iniciado
     * @throws IOException caso o servidor não possa ser criado
     */
    private HttpServer createSlowServer()
            throws IOException {

        /*
         * A porta 0 permite que o sistema operacional escolha uma
         * porta livre, evitando conflitos com outros processos.
         */
        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress("localhost", 0),
                        0
                );

        server.createContext(
                "/slow",
                exchange -> {

                    try {
                        /*
                         * O atraso é maior que o timeout configurado
                         * no JavaHttpTransport.
                         */
                        Thread.sleep(
                                SERVER_DELAY.toMillis()
                        );
                    } catch (InterruptedException exception) {
                        /*
                         * O servidor pode receber interrupção quando
                         * o cliente abandona a requisição.
                         */
                        Thread.currentThread().interrupt();
                    }

                    byte[] response =
                            "slow-response"
                                    .getBytes();

                    /*
                     * Se a conexão ainda estiver disponível, envia
                     * uma resposta. Caso contrário, a exceção fica
                     * restrita ao ciclo de vida do servidor de teste.
                     */
                    try {
                        exchange.sendResponseHeaders(
                                200,
                                response.length
                        );

                        try (OutputStream outputStream =
                                     exchange.getResponseBody()) {

                            outputStream.write(response);
                        }
                    } catch (IOException ignored) {
                        /*
                         * O cliente pode ter encerrado a conexão após
                         * o timeout. Isso é esperado neste teste.
                         */
                    }
                }
        );

        return server;
    }
}