package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionResult;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Teste de integração da cadeia de coleta HTTP.
 *
 * <p>Este teste utiliza um servidor HTTP local e controlado para validar
 * a comunicação entre as diferentes partes da infraestrutura de coleta.</p>
 *
 * <p>O servidor local substitui uma fonte externa durante o teste.
 * Isso torna a execução determinística e evita que a suíte dependa da
 * disponibilidade ou do comportamento atual de um serviço externo.</p>
 *
 * <p>O teste valida somente a coleta do conteúdo bruto. Nenhuma regra
 * de parsing, extração de ASIN, interpretação de preço ou regra de
 * elegibilidade é executada aqui.</p>
 */
class AmazonDealsCollectorIntegrationTest {

    /**
     * Conteúdo bruto utilizado como resposta pelo servidor HTTP local.
     *
     * <p>O conteúdo possui apenas texto representativo porque a
     * interpretação da resposta pertence à FASE 6.</p>
     */
    private static final String RESPONSE_CONTENT =
            "controlled-amazon-deals-content";

    /**
     * Testa a comunicação completa entre transporte HTTP, collector
     * genérico e adaptador Amazon.
     *
     * @throws Exception caso a inicialização ou encerramento do servidor
     *                   local apresente uma falha
     */
    @Test
    void shouldCollectRawContentThroughCompleteHttpChain()
            throws Exception {

        HttpServer server = createServer();

        try {
            /*
             * O servidor local é iniciado em uma porta dinâmica.
             * Dessa forma, o teste não depende de uma porta fixa
             * que poderia estar ocupada por outro processo.
             */
            server.start();

            URI source = URI.create(
                    "http://localhost:"
                            + server.getAddress().getPort()
                            + "/deals"
            );

            /*
             * A cadeia abaixo reproduz a composição real da
             * infraestrutura de coleta:
             *
             * HttpClient
             *     ↓
             * JavaHttpTransport
             *     ↓
             * HttpCollectionCollector
             *     ↓
             * AmazonDealsCollector
             *
             * O adaptador Amazon normalmente define a URL funcional
             * da Amazon. Neste teste, utilizamos uma fonte controlada
             * para validar o comportamento HTTP sem depender da rede
             * externa.
             */
            var httpClient = java.net.http.HttpClient.newBuilder()
                    .followRedirects(
                            java.net.http.HttpClient.Redirect.NORMAL
                    )
                    .build();

            var httpTransport =
                    new com.raspingamazon.infrastructure.http.JavaHttpTransport(
                            httpClient,
                            java.time.Duration.ofSeconds(5)
                    );

            var httpCollector =
                    new com.raspingamazon.infrastructure.collection.HttpCollectionCollector(
                            httpTransport,
                            java.time.Clock.systemUTC()
                    );

            /*
             * Para manter o teste isolado da fonte externa, o adaptador
             * recebe um collector que direciona a requisição para o
             * servidor controlado.
             *
             * A substituição ocorre no nível do contrato de coleta,
             * sem alterar o comportamento do transporte HTTP.
             */
            var collector =
                    new com.raspingamazon.application.collection.contract.CollectionCollector() {

                        @Override
                        public CollectionResult collect(
                                com.raspingamazon.application.collection.contract.CollectionRequest request) {

                            var controlledRequest =
                                    new com.raspingamazon.application.collection.contract.CollectionRequest(
                                            source
                                    );

                            return httpCollector.collect(
                                    controlledRequest
                            );
                        }
                    };

            var amazonDealsCollector =
                    new AmazonDealsCollector(collector);

            CollectionResult result =
                    amazonDealsCollector.collect();

            /*
             * O principal objetivo desta integração é verificar que
             * o conteúdo bruto atravessou toda a cadeia sem ser
             * interpretado ou transformado.
             */
            assertEquals(
                    RESPONSE_CONTENT,
                    result.content()
            );

            assertEquals(
                    source.toString(),
                    result.source()
            );

            /*
             * A coleta deve registrar o instante em que ocorreu.
             */
            assertNotNull(result.collectedAt());

            /*
             * Apenas verificamos que o instante é uma representação
             * temporal válida. Não comparamos com um horário exato,
             * pois o teste utiliza o relógio real do sistema.
             */
            OffsetDateTime collectedAt =
                    result.collectedAt();

            assertNotNull(collectedAt.toInstant());
        } finally {
            /*
             * O servidor deve ser encerrado mesmo quando uma asserção
             * falhar. Isso evita deixar uma porta aberta após o teste.
             */
            server.stop(0);
        }
    }

    /**
     * Cria um servidor HTTP local com uma resposta determinística.
     *
     * @return servidor HTTP configurado e ainda não iniciado
     * @throws IOException caso o servidor não possa ser criado
     */
    private HttpServer createServer() throws IOException {

        /*
         * Porta 0 solicita ao sistema operacional uma porta livre.
         */
        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress("localhost", 0),
                        0
                );

        /*
         * O endpoint representa uma fonte HTTP controlada.
         *
         * O conteúdo retornado é deliberadamente bruto. O teste não
         * conhece nenhuma estrutura de produto.
         */
        server.createContext(
                "/deals",
                exchange -> {

                    byte[] responseBytes =
                            RESPONSE_CONTENT.getBytes(
                                    StandardCharsets.UTF_8
                            );

                    exchange.sendResponseHeaders(
                            200,
                            responseBytes.length
                    );

                    try (OutputStream outputStream =
                                 exchange.getResponseBody()) {

                        outputStream.write(
                                responseBytes
                        );
                    }
                }
        );

        return server;
    }
}