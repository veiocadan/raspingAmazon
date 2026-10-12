package com.raspingamazon.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContinuousProcessingMainTest {

    @Test
    void shouldNotExposeCredentialsFromDaemonFailure() {

        String secret = "whatsapp_TOKEN_TOP_SECRET";

        RuntimeException failure = new IllegalStateException(
            "request failed with token=" + secret,
            new IllegalArgumentException("credential=" + secret)
        );

        String message = ContinuousProcessingMain.failureMessage(failure);

        assertEquals(
            "IllegalStateException (details suppressed)",
            message
        );
        assertFalse(message.contains(secret));
        assertFalse(message.contains("credential="));
    }
}
