package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingJobReprocessCompositionTest {

    @Test
    void shouldCreateOperationalReprocessUseCaseWithoutTouchingDatabase() {

        ProcessingJobReprocessService service =
            ProcessingJobReprocessComposition.create(
                connectionProxy(),
                Clock.systemUTC()
            );

        assertNotNull(
            service
        );
    }

    @Test
    void shouldRejectNullDependencies() {

        assertThrows(
            NullPointerException.class,
            () ->
                ProcessingJobReprocessComposition.create(
                    null,
                    Clock.systemUTC()
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                ProcessingJobReprocessComposition.create(
                    connectionProxy(),
                    null
                )
        );
    }

    private static Connection connectionProxy() {

        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{
                Connection.class
            },
            (
                proxy,
                method,
                args
            ) -> {

                if (method.getDeclaringClass()
                    == Object.class) {

                    return switch (method.getName()) {

                        case "toString" ->
                            "processing-job-reprocess-connection-proxy";

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
