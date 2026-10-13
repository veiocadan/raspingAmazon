package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.infrastructure.runtime.ContinuousProcessingRuntime;
import com.raspingamazon.infrastructure.runtime.FailureLifecycleRuntimeFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Exercita caminhos reais de fechamento de ContinuousProcessingMain e
 * ContinuousProcessingApplication com recursos de teste em memoria.
 * Nao acessa PostgreSQL, Amazon, Flyway ou publicacoes.
 */
class ContinuousProcessingFailurePathsTest {

    private static final String SECRET = "TOKEN_PRIVATE_NOT_FOR_LOG";

    @TempDir
    Path tempDir;

    @Test
    void simulatedRecoveryFailureBeforeOwnershipDoesNotLeakSecret() throws Exception {
        StringWriter output = new StringWriter();
        int code = ContinuousProcessingMain.run(
            new PrintWriter(output, true), authorizedPolicy(),
            () -> {
                throw new IllegalStateException("recovery failed: " + SECRET);
            }
        );

        assertEquals(1, code);
        assertTrue(output.toString().contains("IllegalStateException (details suppressed)"));
        assertFalse(output.toString().contains(SECRET));
    }

    @Test
    void loopFailureStopsBothLoopsAndClosesResourcesInReverseOrder() throws Exception {
        List<String> closed = new ArrayList<>();
        ContinuousProcessingRuntime runtime = FailureLifecycleRuntimeFixture.create(
            keepRunning -> { throw new IllegalStateException(SECRET); },
            ContinuousProcessingFailurePathsTest::spinUntilShutdown
        );
        ContinuousProcessingApplication app = application(runtime, closed,
            null, null);
        StringWriter output = new StringWriter();
        int code = assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
            ContinuousProcessingMain.run(
                new PrintWriter(output, true), authorizedPolicy(), () -> app));

        assertEquals(1, code);
        assertEquals(List.of("resource-2", "resource-1", "worker", "scheduler"), closed);
        assertFalse(output.toString().contains(SECRET));
    }

    @Test
    void gracefulShutdownClosesEverythingOnceAndReturnsZero() throws Exception {
        List<String> closed = new ArrayList<>();
        CountDownLatch entered = new CountDownLatch(2);
        Consumer<BooleanSupplier> loop = keepRunning -> {
            entered.countDown();
            spinUntilShutdown(keepRunning);
        };
        ContinuousProcessingRuntime runtime = FailureLifecycleRuntimeFixture.create(loop, loop);
        ContinuousProcessingApplication app = application(runtime, closed,
            null, null);
        Thread stopper = requestShutdownWhenBothReady(entered, runtime);
        StringWriter output = new StringWriter();
        int code = assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
            ContinuousProcessingMain.run(
                new PrintWriter(output, true), authorizedPolicy(), () -> app));
        stopper.join(1_000);

        assertEquals(0, code);
        assertEquals(List.of("resource-2", "resource-1", "worker", "scheduler"), closed);
        assertFalse(stopper.isAlive());
        assertFalse(output.toString().contains(SECRET));
    }

    @Test
    void resourceCloseFailureChangesOtherwiseSuccessfulExitToFailure() throws Exception {
        List<String> closed = new ArrayList<>();
        CountDownLatch entered = new CountDownLatch(2);
        Consumer<BooleanSupplier> loop = keepRunning -> {
            entered.countDown();
            spinUntilShutdown(keepRunning);
        };
        ContinuousProcessingRuntime runtime = FailureLifecycleRuntimeFixture.create(loop, loop);
        ContinuousProcessingApplication app = application(runtime, closed,
            "resource-2", null);
        Thread stopper = requestShutdownWhenBothReady(entered, runtime);
        StringWriter output = new StringWriter();
        int code = assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
            ContinuousProcessingMain.run(
                new PrintWriter(output, true), authorizedPolicy(), () -> app));
        stopper.join(1_000);

        assertEquals(1, code);
        assertEquals(List.of("resource-2", "resource-1", "worker", "scheduler"), closed);
        assertTrue(output.toString().contains("cleanup failed"));
        assertFalse(output.toString().contains(SECRET));
    }

    @Test
    void jdbcCloseFailureDoesNotSkipClosingRemainingConnection() throws Exception {
        List<String> closed = new ArrayList<>();
        CountDownLatch entered = new CountDownLatch(2);
        Consumer<BooleanSupplier> loop = keepRunning -> {
            entered.countDown();
            spinUntilShutdown(keepRunning);
        };
        ContinuousProcessingRuntime runtime = FailureLifecycleRuntimeFixture.create(loop, loop);
        ContinuousProcessingApplication app = application(runtime, closed,
            null, "worker");
        Thread stopper = requestShutdownWhenBothReady(entered, runtime);
        StringWriter output = new StringWriter();
        int code = assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
            ContinuousProcessingMain.run(
                new PrintWriter(output, true), authorizedPolicy(), () -> app));
        stopper.join(1_000);

        assertEquals(1, code);
        assertEquals(List.of("resource-2", "resource-1", "worker", "scheduler"), closed);
        assertTrue(output.toString().contains("cleanup failed"));
        assertFalse(output.toString().contains(SECRET));
    }

    private Path authorizedPolicy() throws Exception {
        Path path = tempDir.resolve("test-only-policy.json");
        Files.writeString(path, """
            {
              "schema_version": 1,
              "execution_mode": "supervised",
              "initial_schedule_interval": "PT1H",
              "daemon_start_authorized": true,
              "flyway_migrate_authorized": false,
              "external_actions_authorized": true,
              "automatic_restart_authorized": false,
              "shutdown_grace_seconds": 30
            }
            """, StandardCharsets.UTF_8);
        return path;
    }

    private static ContinuousProcessingApplication application(
        ContinuousProcessingRuntime runtime,
        List<String> closed,
        String failingResource,
        String failingConnection
    ) {
        AutoCloseable first = resource("resource-1", closed, failingResource);
        AutoCloseable second = resource("resource-2", closed, failingResource);
        return new ContinuousProcessingApplication(runtime,
            connection("scheduler", closed, failingConnection),
            connection("worker", closed, failingConnection),
            List.of(first, second));
    }

    private static AutoCloseable resource(
        String name, List<String> closed, String failingResource
    ) {
        return () -> {
            closed.add(name);
            if (name.equals(failingResource)) {
                throw new IllegalStateException("resource closing: " + SECRET);
            }
        };
    }

    private static Connection connection(
        String name, List<String> closed, String failingConnection
    ) {
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                if (method.getName().equals("close")) {
                    closed.add(name);
                    if (name.equals(failingConnection)) {
                        throw new SQLException("database password=" + SECRET);
                    }
                    return null;
                }
                if (method.getName().equals("toString")) {
                    return "fake-connection-" + name;
                }
                throw new AssertionError("Unexpected JDBC operation in isolated test");
            }
        );
    }

    private static void spinUntilShutdown(BooleanSupplier keepRunning) {
        while (keepRunning.getAsBoolean()) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
        }
    }

    private static Thread requestShutdownWhenBothReady(
        CountDownLatch entered, ContinuousProcessingRuntime runtime
    ) {
        Thread stopper = new Thread(() -> {
            try {
                if (!entered.await(4, TimeUnit.SECONDS)) {
                    runtime.requestShutdown();
                    return;
                }
                runtime.requestShutdown();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runtime.requestShutdown();
            }
        }, "test-only-runtime-stop");
        stopper.setDaemon(true);
        stopper.start();
        return stopper;
    }
}
