package com.raspingamazon.infrastructure.runtime;

import com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingApplication;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa o ownership de recursos externos do processo contínuo.
 *
 * <p>O runtime não é iniciado nestes cenários. Assim o teste permanece
 * hermético e exercita exclusivamente a política de cleanup da
 * aplicação.</p>
 */
class ContinuousProcessingApplicationManagedResourceTest {

    @Test
    void shouldCloseManagedResourceBeforeDatabaseConnections() {

        List<String> events =
            new ArrayList<>();

        ContinuousProcessingRuntime runtime =
            idleRuntime();

        Connection schedulerConnection =
            recordingConnection(
                "scheduler",
                events
            );

        Connection workerConnection =
            recordingConnection(
                "worker",
                events
            );

        AutoCloseable managedResource =
            () ->
                events.add(
                    "resource"
                );

        ContinuousProcessingApplication application =
            new ContinuousProcessingApplication(
                runtime,
                schedulerConnection,
                workerConnection,
                List.of(
                    managedResource
                )
            );

        application.close();

        assertEquals(
            List.of(
                "resource",
                "worker",
                "scheduler"
            ),
            events
        );
    }

    @Test
    void shouldCloseManagedResourcesOnlyOnce() {

        AtomicInteger resourceCloseCount =
            new AtomicInteger();

        List<String> events =
            new ArrayList<>();

        ContinuousProcessingApplication application =
            new ContinuousProcessingApplication(
                idleRuntime(),
                recordingConnection(
                    "scheduler",
                    events
                ),
                recordingConnection(
                    "worker",
                    events
                ),
                List.of(
                    (AutoCloseable)
                        resourceCloseCount::incrementAndGet
                )
            );

        application.close();
        application.close();

        assertEquals(
            1,
            resourceCloseCount.get()
        );

        assertEquals(
            List.of(
                "worker",
                "scheduler"
            ),
            events
        );
    }

    @Test
    void shouldStillCloseConnectionsWhenManagedResourceCloseFails() {

        List<String> events =
            new ArrayList<>();

        IllegalStateException expected =
            new IllegalStateException(
                "resource close failed"
            );

        AutoCloseable failingResource =
            () -> {

                events.add(
                    "resource"
                );

                throw expected;
            };

        ContinuousProcessingApplication application =
            new ContinuousProcessingApplication(
                idleRuntime(),
                recordingConnection(
                    "scheduler",
                    events
                ),
                recordingConnection(
                    "worker",
                    events
                ),
                List.of(
                    failingResource
                )
            );

        IllegalStateException thrown =
            assertThrows(
                IllegalStateException.class,
                application::close
            );

        assertSame(
            expected,
            thrown
        );

        assertEquals(
            List.of(
                "resource",
                "worker",
                "scheduler"
            ),
            events
        );
    }

    /**
     * O construtor package-private do runtime fica acessível porque este
     * teste pertence ao package infrastructure.runtime.
     */
    private ContinuousProcessingRuntime idleRuntime() {

        return new ContinuousProcessingRuntime(
            keepRunning -> {
            },
            keepRunning -> {
            }
        );
    }

    private Connection recordingConnection(
        String name,
        List<String> events
    ) {

        return (Connection)
            Proxy.newProxyInstance(
                ContinuousProcessingApplicationManagedResourceTest
                    .class
                    .getClassLoader(),
                new Class<?>[]{
                    Connection.class
                },
                (
                    proxy,
                    method,
                    arguments
                ) -> {

                    if ("close".equals(
                        method.getName()
                    )) {

                        events.add(
                            name
                        );

                        return null;
                    }

                    if ("toString".equals(
                        method.getName()
                    )) {

                        return name;
                    }

                    Class<?> returnType =
                        method.getReturnType();

                    if (returnType == boolean.class) {
                        return false;
                    }

                    if (returnType == byte.class) {
                        return (byte) 0;
                    }

                    if (returnType == short.class) {
                        return (short) 0;
                    }

                    if (returnType == int.class) {
                        return 0;
                    }

                    if (returnType == long.class) {
                        return 0L;
                    }

                    if (returnType == float.class) {
                        return 0.0F;
                    }

                    if (returnType == double.class) {
                        return 0.0D;
                    }

                    if (returnType == char.class) {
                        return '\0';
                    }

                    return null;
                }
            );
    }
}
