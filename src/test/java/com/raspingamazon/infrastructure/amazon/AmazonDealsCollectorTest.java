package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do adaptador de promoções da Amazon Brasil.
 *
 * <p>O coletor genérico é substituído por uma implementação controlada
 * pelo teste. Dessa forma, o teste valida somente a responsabilidade
 * específica do adaptador Amazon.</p>
 */
class AmazonDealsCollectorTest {

    @Test
    void shouldCollectAmazonDealsSource() {
        AtomicReference<CollectionRequest> receivedRequest =
                new AtomicReference<>();

        CollectionCollector collector = request -> {
            receivedRequest.set(request);

            return new CollectionResult(
                    "amazon-deals-content",
                    OffsetDateTime.parse(
                            "2026-09-16T22:00:00Z"
                    ),
                    request.source().toString()
            );
        };

        var amazonDealsCollector =
                new AmazonDealsCollector(collector);

        var result = amazonDealsCollector.collect();

        assertEquals(
                "amazon-deals-content",
                result.content()
        );

        assertEquals(
                "https://www.amazon.com.br/deals",
                result.source()
        );

        assertEquals(
                "https://www.amazon.com.br/deals",
                receivedRequest.get().source().toString()
        );
    }

    @Test
    void shouldPropagateCollectionException() {
        var expectedException =
                new CollectionException("HTTP request failed");

        CollectionCollector collector = request -> {
            throw expectedException;
        };

        var amazonDealsCollector =
                new AmazonDealsCollector(collector);

        CollectionException exception = assertThrows(
                CollectionException.class,
                amazonDealsCollector::collect
        );

        assertSame(
                expectedException,
                exception
        );
    }

    @Test
    void shouldWrapUnexpectedCollectionFailure() {
        var cause = new IllegalStateException(
                "Unexpected collector failure"
        );

        CollectionCollector collector = request -> {
            throw cause;
        };

        var amazonDealsCollector =
                new AmazonDealsCollector(collector);

        CollectionException exception = assertThrows(
                CollectionException.class,
                amazonDealsCollector::collect
        );

        assertEquals(
                "Amazon deals collection failed",
                exception.getMessage()
        );

        assertSame(
                cause,
                exception.getCause()
        );
    }

    @Test
    void shouldRejectNullCollectionCollector() {
        assertThrows(
                NullPointerException.class,
                () -> new AmazonDealsCollector(null)
        );
    }
}