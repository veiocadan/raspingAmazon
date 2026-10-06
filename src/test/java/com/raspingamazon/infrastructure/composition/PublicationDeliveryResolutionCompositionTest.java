package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationDeliveryResolutionCompositionTest {

    @Test
    void shouldCreateOperationalResolutionUseCaseWithoutTouchingDatabase() {

        PublicationDeliveryResolutionService service =
            PublicationDeliveryResolutionComposition.create(
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
                PublicationDeliveryResolutionComposition.create(
                    null,
                    Clock.systemUTC()
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                PublicationDeliveryResolutionComposition.create(
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
                            "publication-delivery-resolution-connection-proxy";

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
