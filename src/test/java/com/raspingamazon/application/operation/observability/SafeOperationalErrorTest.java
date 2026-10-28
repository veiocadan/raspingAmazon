package com.raspingamazon.application.operation.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeOperationalErrorTest {

    @Test
    void shouldDescribeOnlyTheTrustedExceptionType() {
        String secret = "jdbc:postgresql://user:password@localhost/db";

        String result = SafeOperationalError.describe(
            new IllegalStateException(secret)
        );

        assertEquals("IllegalStateException (details suppressed)", result);
        assertFalse(result.contains(secret));
    }

    @Test
    void shouldNotReadSensitiveExceptionMethodsOrNestedCause() {

        IllegalStateException secretFailure = new IllegalStateException("SECRET") {
            @Override
            public String getMessage() {
                throw new AssertionError("getMessage() must never be called");
            }

            @Override
            public String getLocalizedMessage() {
                throw new AssertionError("getLocalizedMessage() must never be called");
            }

            @Override
            public String toString() {
                throw new AssertionError("toString() must never be called");
            }
        };

        String result = SafeOperationalError.describe(secretFailure);

        assertEquals("RuntimeException (details suppressed)", result);
    }

    @Test
    void shouldRejectNullFailure() {
        assertThrows(
            NullPointerException.class,
            () -> SafeOperationalError.describe(null)
        );
    }
}
