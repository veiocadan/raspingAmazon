package com.raspingamazon.infrastructure.collection;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.HttpTransport;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes para respostas HTTP que representam falhas da fonte.
 *
 * <p>Este teste verifica que uma resposta HTTP de erro não seja tratada
 * como se fosse uma coleta válida.</p>
 *
 * <p>A responsabilidade aqui ainda é exclusivamente operacional:
 * identificar uma resposta HTTP inadequada. Nenhum conteúdo de produto
 * é interpretado.</p>
 */
class HttpCollectionCollectorHttpErrorTest {

    /**
     * URI fictícia utilizada pelo teste.
     */
    private static final URI SOURCE =
            URI.create("https://example.com/deals");

    /**
     * Relógio fixo utilizado para manter o teste determinístico.
     */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    Instant.parse("2026-09-16T22:00:00Z"),
                    ZoneOffset.UTC
            );

    /**
     * Verifica que uma resposta HTTP 503 seja registrada como falha
     * de coleta.
     *
     * <p>O código 503 representa uma indisponibilidade temporária
     * do serviço. O collector não deve entregar esse conteúdo como
     * uma coleta válida para as etapas seguintes.</p>
     */
    @Test
    void shouldRejectServiceUnavailableResponse() {

        HttpTransport transport = uri -> {
            assertEquals(SOURCE, uri);

            return new HttpTransportResponse(
                    503,
                    "Service Unavailable"
            );
        };

        var collector = new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
        );

        var request = new CollectionRequest(SOURCE);

        CollectionException exception = assertThrows(
                CollectionException.class,
                () -> collector.collect(request)
        );

        assertEquals(
                "HTTP response status indicates collection failure: 503",
                exception.getMessage()
        );
    }

    /**
     * Verifica que uma resposta HTTP 404 também seja considerada
     * uma falha de coleta.
     */
    @Test
    void shouldRejectNotFoundResponse() {

        HttpTransport transport = uri ->
                new HttpTransportResponse(
                        404,
                        "Not Found"
                );

        var collector = new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
        );

        var request = new CollectionRequest(SOURCE);

        CollectionException exception = assertThrows(
                CollectionException.class,
                () -> collector.collect(request)
        );

        assertEquals(
                "HTTP response status indicates collection failure: 404",
                exception.getMessage()
        );
    }

    /**
     * Verifica que respostas HTTP de sucesso continuem sendo aceitas.
     */
    @Test
    void shouldAcceptSuccessfulResponse() {

        HttpTransport transport = uri ->
                new HttpTransportResponse(
                        200,
                        "valid-content"
                );

        var collector = new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
        );

        var request = new CollectionRequest(SOURCE);

        var result = collector.collect(request);

        assertEquals(
                "valid-content",
                result.content()
        );

        assertEquals(
                "https://example.com/deals",
                result.source()
        );

        assertEquals(
                Instant.parse("2026-09-16T22:00:00Z"),
                result.collectedAt().toInstant()
        );
    }
}