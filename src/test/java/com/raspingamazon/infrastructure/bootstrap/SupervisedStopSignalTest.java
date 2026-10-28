package com.raspingamazon.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SupervisedStopSignalTest {

    @TempDir
    Path tempDir;

    @Test
    void stopIsTriggeredOnceByFileCreation() throws Exception {
        Path request = tempDir.resolve("stop.request");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch called = new CountDownLatch(1);
        try (SupervisedStopSignal monitor = SupervisedStopSignal.start(request, () -> {
            calls.incrementAndGet();
            called.countDown();
        })) {
            assertFalse(called.await(300, TimeUnit.MILLISECONDS));
            Files.createFile(request);
            assertTrue(called.await(3, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                while (calls.get() == 0) {
                    Thread.sleep(10);
                }
            });
            assertEquals(1, calls.get());
            assertNull(monitor.failure());
        }
    }

    @Test
    void refusesPreexistingStopRequest() throws Exception {
        Path request = tempDir.resolve("stop.request");
        Files.createFile(request);
        assertThrows(IllegalStateException.class,
            () -> SupervisedStopSignal.start(request, () -> fail("unexpected stop")));
    }

    @Test
    void refusesRelativeStopRequest() {
        assertThrows(IllegalArgumentException.class,
            () -> SupervisedStopSignal.start(Path.of("stop.request"), () -> fail("unexpected stop")));
    }

    @Test
    void closeWithoutRequestDoesNotTriggerAction() throws Exception {
        Path request = tempDir.resolve("stop.request");
        AtomicInteger calls = new AtomicInteger();
        try (SupervisedStopSignal ignored =
                 SupervisedStopSignal.start(request, calls::incrementAndGet)) {
            assertFalse(Files.exists(request));
        }
        Files.createFile(request);
        Thread.sleep(400);
        assertEquals(0, calls.get());
    }
}
