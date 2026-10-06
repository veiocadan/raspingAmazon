package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.infrastructure.runtime.ProcessingSchedulerWaitStrategy;
import com.raspingamazon.infrastructure.runtime.WorkerIdleWaitStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContinuousProcessingPublicationDispatchCompositionTest {

    @Test
    void shouldAcceptPublicationDispatchHandlerWithoutInvokingItDuringComposition() {

        AtomicInteger publicationDispatchCalls =
            new AtomicInteger();

        ContinuousProcessingComposition composition =
            new ContinuousProcessingComposition(
                connectionProxy(
                    "scheduler"
                ),
                connectionProxy(
                    "worker"
                ),
                unusedProxy(
                    CollectionCollector.class
                ),
                unusedProxy(
                    DealsParser.class
                ),
                unusedProxy(
                    ProductEnrichmentClient.class
                ),
                processingRunId ->
                    publicationDispatchCalls.incrementAndGet(),
                unusedProxy(
                    StructuredOperationalLogPort.class
                ),
                Clock.systemUTC(),
                unusedProxy(
                    ProcessingSchedulerWaitStrategy.class
                ),
                unusedProxy(
                    WorkerIdleWaitStrategy.class
                ),
                settings()
            );

        assertNotNull(
            composition.schedulerRunner()
        );

        assertNotNull(
            composition.workerRunner()
        );

        /*
         * Composition monta o grafo, mas não executa trabalho.
         *
         * O handler de PUBLICATION_DISPATCH só poderá ser chamado
         * posteriormente pelo ProcessingWorker ao receber um job
         * durável desse tipo.
         */
        assertEquals(
            0,
            publicationDispatchCalls.get()
        );
    }

    @Test
    void shouldRejectNullPublicationDispatchHandler() {

        NullPointerException exception =
            assertThrows(
                NullPointerException.class,
                () ->
                    new ContinuousProcessingComposition(
                        connectionProxy(
                            "scheduler"
                        ),
                        connectionProxy(
                            "worker"
                        ),
                        unusedProxy(
                            CollectionCollector.class
                        ),
                        unusedProxy(
                            DealsParser.class
                        ),
                        unusedProxy(
                            ProductEnrichmentClient.class
                        ),
                        null,
                        unusedProxy(
                            StructuredOperationalLogPort.class
                        ),
                        Clock.systemUTC(),
                        unusedProxy(
                            ProcessingSchedulerWaitStrategy.class
                        ),
                        unusedProxy(
                            WorkerIdleWaitStrategy.class
                        ),
                        settings()
                    )
            );

        assertEquals(
            "publicationDispatchHandler must not be null",
            exception.getMessage()
        );
    }

    private ContinuousProcessingComposition.Settings settings() {

        return new ContinuousProcessingComposition.Settings(
            "amazon-deals",
            "scheduler-publication-dispatch-test",
            "worker-publication-dispatch-test",
            Duration.ofSeconds(
                2
            ),
            Duration.ofMinutes(
                1
            ),
            Duration.ofMillis(
                250
            ),
            5,
            5,
            5,
            Duration.ofSeconds(
                30
            ),
            Duration.ofMinutes(
                5
            )
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T unusedProxy(
        Class<T> type
    ) {

        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{
                type
            },
            (proxy, method, args) -> {

                if (method.getDeclaringClass()
                    == Object.class) {

                    return switch (method.getName()) {

                        case "toString" ->
                            "unused-proxy:"
                                + type.getSimpleName();

                        case "hashCode" ->
                            System.identityHashCode(
                                proxy
                            );

                        case "equals" ->
                            proxy
                                == (
                                args == null
                                    || args.length == 0
                                    ? null
                                    : args[0]
                            );

                        default ->
                            throw new UnsupportedOperationException(
                                method.getName()
                            );
                    };
                }

                throw new AssertionError(
                    type.getSimpleName()
                        + "."
                        + method.getName()
                        + " must not be called during composition"
                );
            }
        );
    }

    private static Connection connectionProxy(
        String name
    ) {

        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{
                Connection.class
            },
            (proxy, method, args) -> {

                if (method.getDeclaringClass()
                    == Object.class) {

                    return switch (method.getName()) {

                        case "toString" ->
                            "connection-proxy:"
                                + name;

                        case "hashCode" ->
                            System.identityHashCode(
                                proxy
                            );

                        case "equals" ->
                            proxy
                                == (
                                args == null
                                    || args.length == 0
                                    ? null
                                    : args[0]
                            );

                        default ->
                            throw new UnsupportedOperationException(
                                method.getName()
                            );
                    };
                }

                throw new AssertionError(
                    "Connection."
                        + method.getName()
                        + " must not be called during composition"
                );
            }
        );
    }
}
