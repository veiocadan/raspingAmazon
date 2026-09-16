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
 * Testes do coletor HTTP.
 *
 * <p>O transporte HTTP é substituído por uma implementação controlada
 * pelo teste. Isso permite validar exclusivamente a responsabilidade
 * do collector, sem depender de rede ou de serviços externos.</p>
 *
 * <p>O teste também utiliza um Clock fixo para tornar o instante da
 * coleta determinístico e reproduzível.</p>
 */
class HttpCollectionCollectorTest {

    private static final URI SOURCE =
            URI.create("https://example.com/deals");

    private static final Instant COLLECTION_INSTANT =
            Instant.parse("2026-09-16T22:00:00Z");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    COLLECTION_INSTANT,
                    ZoneOffset.UTC
            );

    @Test
    void shouldTransformHttpResponseIntoCollectionResult() {
        HttpTransport transport = uri -> {
            assertEquals(SOURCE, uri);

            return new HttpTransportResponse(
                    200,
                    "payload"
            );
        };

        var collector = new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
        );

        var request = new CollectionRequest(SOURCE);

        var result = collector.collect(request);

        assertEquals("payload", result.content());

        /*
         * Comparamos o instante temporal, e não sua representação textual.
         * Isso evita que diferenças legítimas de formatação, como a omissão
         * de segundos iguais a zero, provoquem uma falha no teste.
         */
        assertEquals(
                COLLECTION_INSTANT,
                result.collectedAt().toInstant()
        );

        assertEquals(
                "https://example.com/deals",
                result.source()
        );
    }

    @Test
    void shouldPropagateCollectionExceptionFromTransport() {
        var expectedException = new CollectionException(
                "HTTP request failed"
        );

        HttpTransport transport = uri -> {
            throw expectedException;
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

        /*
         * A exceção de transporte já pertence ao contrato de coleta.
         * Portanto, o collector deve propagá-la sem criar outra exceção.
         */
        assertEquals(expectedException, exception);
    }

    @Test
    void shouldRejectNullRequest() {
        HttpTransport transport = uri ->
                new HttpTransportResponse(
                        200,
                        "payload"
                );

        var collector = new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
        );

        assertThrows(
                NullPointerException.class,
                () -> collector.collect(null)
        );
    }

    @Test
    void shouldRejectNullTransport() {
        assertThrows(
                NullPointerException.class,
                () -> new HttpCollectionCollector(
                        null,
                        FIXED_CLOCK
                )
        );
    }

    @Test
    void shouldRejectNullClock() {
        HttpTransport transport = uri ->
                new HttpTransportResponse(
                        200,
                        "payload"
                );

        assertThrows(
                NullPointerException.class,
                () -> new HttpCollectionCollector(
                        transport,
                        null
                )
        );
    }
}