package com.raspingamazon.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPublicationAttemptStartAdapterTransactionBoundaryTest {

    private static final OffsetDateTime STARTED_AT =
        OffsetDateTime.parse(
            "2026-10-03T20:30:00Z"
        );

    @Test
    void shouldRejectExternalTransactionBeforeExecutingSql() {

        Connection connection =
            connectionWithAutoCommit(
                false
            );

        JdbcPublicationAttemptStartAdapter adapter =
            new JdbcPublicationAttemptStartAdapter(
                connection
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    adapter.start(
                        10L,
                        "publication-worker",
                        STARTED_AT
                    )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "autoCommit=true"
                )
        );
    }

    @Test
    void shouldRejectInvalidOutboxIdBeforeTouchingConnection() {

        Connection connection =
            connectionThatMustNotBeTouched();

        JdbcPublicationAttemptStartAdapter adapter =
            new JdbcPublicationAttemptStartAdapter(
                connection
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                adapter.start(
                    0L,
                    "publication-worker",
                    STARTED_AT
                )
        );
    }

    @Test
    void shouldRejectBlankWorkerBeforeTouchingConnection() {

        Connection connection =
            connectionThatMustNotBeTouched();

        JdbcPublicationAttemptStartAdapter adapter =
            new JdbcPublicationAttemptStartAdapter(
                connection
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                adapter.start(
                    10L,
                    "   ",
                    STARTED_AT
                )
        );
    }

    @Test
    void shouldRejectMissingStartedAtBeforeTouchingConnection() {

        Connection connection =
            connectionThatMustNotBeTouched();

        JdbcPublicationAttemptStartAdapter adapter =
            new JdbcPublicationAttemptStartAdapter(
                connection
            );

        assertThrows(
            NullPointerException.class,
            () ->
                adapter.start(
                    10L,
                    "publication-worker",
                    null
                )
        );
    }

    private Connection connectionWithAutoCommit(
        boolean autoCommit
    ) {

        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{
                Connection.class
            },
            (
                proxy,
                method,
                arguments
            ) -> {

                String methodName =
                    method.getName();

                if ("getAutoCommit".equals(
                    methodName
                )) {

                    return autoCommit;
                }

                if ("toString".equals(
                    methodName
                )) {

                    return "PublicationAttemptStartBoundaryConnection";
                }

                if ("hashCode".equals(
                    methodName
                )) {

                    return System.identityHashCode(
                        proxy
                    );
                }

                if ("equals".equals(
                    methodName
                )) {

                    return proxy
                        == arguments[0];
                }

                throw new AssertionError(
                    "Unexpected JDBC call before transaction "
                        + "boundary validation: "
                        + methodName
                );
            }
        );
    }

    private Connection connectionThatMustNotBeTouched() {

        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{
                Connection.class
            },
            (
                proxy,
                method,
                arguments
            ) -> {

                String methodName =
                    method.getName();

                if ("toString".equals(
                    methodName
                )) {

                    return "UntouchedPublicationAttemptConnection";
                }

                if ("hashCode".equals(
                    methodName
                )) {

                    return System.identityHashCode(
                        proxy
                    );
                }

                if ("equals".equals(
                    methodName
                )) {

                    return proxy
                        == arguments[0];
                }

                throw new AssertionError(
                    "Connection must not be touched for invalid "
                        + "application arguments. Unexpected call: "
                        + methodName
                );
            }
        );
    }
}
