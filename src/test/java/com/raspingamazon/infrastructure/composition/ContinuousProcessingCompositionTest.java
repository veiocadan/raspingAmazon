package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.infrastructure.runtime.ContinuousProcessingSchedulerRunner;
import com.raspingamazon.infrastructure.runtime.ContinuousProcessingWorkerRunner;
import com.raspingamazon.infrastructure.runtime.ProcessingSchedulerWaitStrategy;
import com.raspingamazon.infrastructure.runtime.WorkerIdleWaitStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousProcessingCompositionTest {

    @Test
    void shouldCreateSchedulerAndWorkerWithDistinctConnections() {

        ConnectionProbe schedulerProbe =
            new ConnectionProbe();

        ConnectionProbe workerProbe =
            new ConnectionProbe();

        ContinuousProcessingComposition composition =
            createComposition(
                schedulerProbe.connection(),
                workerProbe.connection()
            );

        ContinuousProcessingSchedulerRunner schedulerRunner =
            composition.schedulerRunner();

        ContinuousProcessingWorkerRunner workerRunner =
            composition.workerRunner();

        assertNotNull(
            schedulerRunner
        );

        assertNotNull(
            workerRunner
        );

        /*
         * A simples criação da composição não deve tocar o banco.
         *
         * Os proxies de Connection lançariam AssertionError se qualquer
         * método JDBC funcional fosse chamado durante a montagem.
         */
        assertEquals(
            0,
            schedulerProbe.databaseOperations()
        );

        assertEquals(
            0,
            workerProbe.databaseOperations()
        );
    }

    @Test
    void shouldRejectSameConnectionForSchedulerAndWorker() {

        ConnectionProbe probe =
            new ConnectionProbe();

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () ->
                    createComposition(
                        probe.connection(),
                        probe.connection()
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "must be distinct instances"
                )
        );
    }

    @Test
    void shouldNotCloseConnectionsDuringComposition() {

        ConnectionProbe schedulerProbe =
            new ConnectionProbe();

        ConnectionProbe workerProbe =
            new ConnectionProbe();

        createComposition(
            schedulerProbe.connection(),
            workerProbe.connection()
        );

        assertFalse(
            schedulerProbe.closed()
        );

        assertFalse(
            workerProbe.closed()
        );
    }

    @Test
    void shouldNotStartSchedulerOrWorkerDuringComposition() {

        ConnectionProbe schedulerProbe =
            new ConnectionProbe();

        ConnectionProbe workerProbe =
            new ConnectionProbe();

        AtomicBoolean schedulerWaitUsed =
            new AtomicBoolean();

        AtomicBoolean workerWaitUsed =
            new AtomicBoolean();

        ProcessingSchedulerWaitStrategy schedulerWaitStrategy =
            duration -> {
                schedulerWaitUsed.set(
                    true
                );

                throw new AssertionError(
                    "scheduler must not start during composition"
                );
            };

        WorkerIdleWaitStrategy workerWaitStrategy =
            duration -> {
                workerWaitUsed.set(
                    true
                );

                throw new AssertionError(
                    "worker must not start during composition"
                );
            };

        new ContinuousProcessingComposition(
            schedulerProbe.connection(),
            workerProbe.connection(),
            unusedProxy(
                CollectionCollector.class
            ),
            unusedProxy(
                DealsParser.class
            ),
            unusedProxy(
                ProductEnrichmentClient.class
            ),
            unusedProxy(
                StructuredOperationalLogPort.class
            ),
            Clock.systemUTC(),
            schedulerWaitStrategy,
            workerWaitStrategy,
            settings()
        );

        /*
         * Se algum runner tivesse sido iniciado pelo construtor:
         *
         * - o scheduler utilizaria a wait strategy;
         * - ou tentaria JDBC;
         * - o worker tentaria claim no banco;
         * - ou utilizaria a wait strategy.
         *
         * Nenhuma dessas ações deve ocorrer no 17.8B.
         */
        assertFalse(
            schedulerWaitUsed.get()
        );

        assertFalse(
            workerWaitUsed.get()
        );

        assertEquals(
            0,
            schedulerProbe.databaseOperations()
        );

        assertEquals(
            0,
            workerProbe.databaseOperations()
        );
    }

    @Test
    void shouldExposeOnlyOperationalRunnersAsPublicBehavior() {

        Set<String> publicDeclaredMethods =
            Arrays.stream(
                    ContinuousProcessingComposition.class
                        .getDeclaredMethods()
                )
                .filter(
                    method ->
                        java.lang.reflect.Modifier.isPublic(
                            method.getModifiers()
                        )
                )
                .map(
                    Method::getName
                )
                .collect(
                    Collectors.toSet()
                );

        assertEquals(
            Set.of(
                "schedulerRunner",
                "workerRunner"
            ),
            publicDeclaredMethods
        );
    }

    @Test
    void shouldNotOwnConnectionLifecycle() {

        assertFalse(
            AutoCloseable.class.isAssignableFrom(
                ContinuousProcessingComposition.class
            )
        );
    }

    @Test
    void shouldValidateOperationalSettings() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContinuousProcessingComposition.Settings(
                    "",
                    "scheduler-1",
                    "worker-1",
                    Duration.ofSeconds(
                        1
                    ),
                    Duration.ofMinutes(
                        1
                    ),
                    Duration.ofSeconds(
                        1
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
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContinuousProcessingComposition.Settings(
                    "amazon-deals",
                    "scheduler-1",
                    "worker-1",
                    Duration.ZERO,
                    Duration.ofMinutes(
                        1
                    ),
                    Duration.ofSeconds(
                        1
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
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContinuousProcessingComposition.Settings(
                    "amazon-deals",
                    "scheduler-1",
                    "worker-1",
                    Duration.ofSeconds(
                        1
                    ),
                    Duration.ofMinutes(
                        1
                    ),
                    Duration.ofSeconds(
                        1
                    ),
                    0,
                    5,
                    5,
                    Duration.ofSeconds(
                        30
                    ),
                    Duration.ofMinutes(
                        5
                    )
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContinuousProcessingComposition.Settings(
                    "amazon-deals",
                    "scheduler-1",
                    "worker-1",
                    Duration.ofSeconds(
                        1
                    ),
                    Duration.ofMinutes(
                        1
                    ),
                    Duration.ofSeconds(
                        1
                    ),
                    5,
                    5,
                    5,
                    Duration.ofMinutes(
                        10
                    ),
                    Duration.ofMinutes(
                        5
                    )
                )
        );
    }

    private ContinuousProcessingComposition createComposition(
        Connection schedulerConnection,
        Connection workerConnection
    ) {

        return new ContinuousProcessingComposition(
            schedulerConnection,
            workerConnection,
            unusedProxy(
                CollectionCollector.class
            ),
            unusedProxy(
                DealsParser.class
            ),
            unusedProxy(
                ProductEnrichmentClient.class
            ),
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
    }

    private ContinuousProcessingComposition.Settings settings() {

        return new ContinuousProcessingComposition.Settings(
            "amazon-deals",
            "scheduler-test-1",
            "worker-test-1",
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

                /*
                 * Object methods precisam continuar seguros caso uma
                 * mensagem de diagnóstico futura tente imprimir alguma
                 * dependência durante a montagem.
                 */
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

    /**
     * Connection que permite provar duas coisas:
     *
     * 1. o composition root não começa a acessar o banco;
     * 2. o composition root não fecha a Connection recebida.
     */
    private static final class ConnectionProbe {

        private final AtomicBoolean closed =
            new AtomicBoolean();

        private int databaseOperations;

        private final Connection connection =
            (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{
                    Connection.class
                },
                (proxy, method, args) -> {

                    if (method.getDeclaringClass()
                        == Object.class) {

                        return switch (method.getName()) {

                            case "toString" ->
                                "connection-probe";

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

                    if ("close".equals(
                        method.getName()
                    )) {

                        closed.set(
                            true
                        );

                        return null;
                    }

                    if ("isClosed".equals(
                        method.getName()
                    )) {

                        return closed.get();
                    }

                    /*
                     * Qualquer outra chamada representa utilização
                     * funcional da Connection.
                     *
                     * No 17.8B isso não pode acontecer simplesmente
                     * porque o grafo foi construído.
                     */
                    databaseOperations++;

                    throw new AssertionError(
                        "Connection."
                            + method.getName()
                            + " must not be called during composition"
                    );
                }
            );

        Connection connection() {

            return connection;
        }

        boolean closed() {

            return closed.get();
        }

        int databaseOperations() {

            return databaseOperations;
        }
    }
}
