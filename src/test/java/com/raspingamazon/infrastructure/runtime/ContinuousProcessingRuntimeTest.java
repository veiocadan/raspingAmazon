package com.raspingamazon.infrastructure.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousProcessingRuntimeTest {

    private static final Duration TEST_TIMEOUT =
        Duration.ofSeconds(
            5
        );

    @Test
    void shouldRunSchedulerAndWorkerConcurrentlyAndShutdown() throws Exception {

        CountDownLatch schedulerStarted =
            new CountDownLatch(
                1
            );

        CountDownLatch workerStarted =
            new CountDownLatch(
                1
            );

        AtomicBoolean schedulerStopped =
            new AtomicBoolean();

        AtomicBoolean workerStopped =
            new AtomicBoolean();

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                controlledLoop(
                    schedulerStarted,
                    schedulerStopped
                ),
                controlledLoop(
                    workerStarted,
                    workerStopped
                )
            );

        runtime.start();

        assertTrue(
            schedulerStarted.await(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            )
        );

        assertTrue(
            workerStarted.await(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            )
        );

        assertTrue(
            runtime.isRunning()
        );

        runtime.requestShutdown();

        runtime.awaitTermination();

        assertFalse(
            runtime.isRunning()
        );

        assertTrue(
            runtime.shutdownRequested()
        );

        assertTrue(
            schedulerStopped.get()
        );

        assertTrue(
            workerStopped.get()
        );
    }

    @Test
    void shouldRejectSecondStart() {

        CountDownLatch started =
            new CountDownLatch(
                2
            );

        Consumer<BooleanSupplier> loop =
            signal -> {

                started.countDown();

                runUntilStopped(
                    signal
                );
            };

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                loop,
                loop
            );

        try {

            runtime.start();

            assertThrows(
                IllegalStateException.class,
                runtime::start
            );

        } finally {

            runtime.close();
        }
    }

    @Test
    void shouldPropagateSchedulerFailureAndStopWorker() throws Exception {

        CountDownLatch workerStarted =
            new CountDownLatch(
                1
            );

        AtomicBoolean workerStopped =
            new AtomicBoolean();

        RuntimeException expected =
            new RuntimeException(
                "scheduler failure"
            );

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                signal -> {

                    awaitLatch(
                        workerStarted
                    );

                    throw expected;
                },
                controlledLoop(
                    workerStarted,
                    workerStopped
                )
            );

        runtime.start();

        ContinuousProcessingRuntimeException exception =
            assertThrows(
                ContinuousProcessingRuntimeException.class,
                runtime::awaitTermination
            );

        assertSame(
            expected,
            exception.getCause()
        );

        assertFalse(
            runtime.isRunning()
        );

        assertTrue(
            workerStopped.get()
        );
    }

    @Test
    void shouldPropagateWorkerFailureAndStopScheduler() throws Exception {

        CountDownLatch schedulerStarted =
            new CountDownLatch(
                1
            );

        AtomicBoolean schedulerStopped =
            new AtomicBoolean();

        RuntimeException expected =
            new RuntimeException(
                "worker failure"
            );

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                controlledLoop(
                    schedulerStarted,
                    schedulerStopped
                ),
                signal -> {

                    awaitLatch(
                        schedulerStarted
                    );

                    throw expected;
                }
            );

        runtime.start();

        ContinuousProcessingRuntimeException exception =
            assertThrows(
                ContinuousProcessingRuntimeException.class,
                runtime::awaitTermination
            );

        assertSame(
            expected,
            exception.getCause()
        );

        assertFalse(
            runtime.isRunning()
        );

        assertTrue(
            schedulerStopped.get()
        );
    }

    @Test
    void shouldTreatUnexpectedNormalLoopTerminationAsRuntimeFailure()
        throws Exception {

        CountDownLatch workerStarted =
            new CountDownLatch(
                1
            );

        AtomicBoolean workerStopped =
            new AtomicBoolean();

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                signal -> {

                    awaitLatch(
                        workerStarted
                    );

                    /*
                     * Retorno normal enquanto keepRunning ainda está true.
                     * Para um daemon operacional contínuo isso é falha.
                     */
                },
                controlledLoop(
                    workerStarted,
                    workerStopped
                )
            );

        runtime.start();

        ContinuousProcessingRuntimeException exception =
            assertThrows(
                ContinuousProcessingRuntimeException.class,
                runtime::awaitTermination
            );

        assertTrue(
            exception.getCause()
                instanceof IllegalStateException
        );

        assertTrue(
            exception.getCause()
                .getMessage()
                .contains(
                    "scheduler loop terminated while runtime was active"
                )
        );

        assertTrue(
            workerStopped.get()
        );
    }

    @Test
    void shouldMakeCloseIdempotent() throws Exception {

        CountDownLatch schedulerStarted =
            new CountDownLatch(
                1
            );

        CountDownLatch workerStarted =
            new CountDownLatch(
                1
            );

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                controlledLoop(
                    schedulerStarted,
                    new AtomicBoolean()
                ),
                controlledLoop(
                    workerStarted,
                    new AtomicBoolean()
                )
            );

        runtime.start();

        assertTrue(
            schedulerStarted.await(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            )
        );

        assertTrue(
            workerStarted.await(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            )
        );

        runtime.close();
        runtime.close();

        assertFalse(
            runtime.isRunning()
        );

        assertTrue(
            runtime.shutdownRequested()
        );
    }

    @Test
    void shouldRejectAwaitBeforeStart() {

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                signal -> {
                },
                signal -> {
                }
            );

        assertThrows(
            IllegalStateException.class,
            runtime::awaitTermination
        );
    }

    @Test
    void shouldRejectStartAfterShutdownWasRequested() {

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                signal -> {
                },
                signal -> {
                }
            );

        runtime.requestShutdown();

        assertThrows(
            IllegalStateException.class,
            runtime::start
        );

        assertFalse(
            runtime.isRunning()
        );
    }

    @Test
    void shouldRunEachLoopOnlyOnce() throws Exception {

        AtomicInteger schedulerExecutions =
            new AtomicInteger();

        AtomicInteger workerExecutions =
            new AtomicInteger();

        CountDownLatch bothStarted =
            new CountDownLatch(
                2
            );

        ContinuousProcessingRuntime runtime =
            new ContinuousProcessingRuntime(
                signal -> {

                    schedulerExecutions.incrementAndGet();
                    bothStarted.countDown();

                    runUntilStopped(
                        signal
                    );
                },
                signal -> {

                    workerExecutions.incrementAndGet();
                    bothStarted.countDown();

                    runUntilStopped(
                        signal
                    );
                }
            );

        runtime.start();

        assertTrue(
            bothStarted.await(
                TEST_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            )
        );

        runtime.requestShutdown();

        runtime.awaitTermination();

        assertEquals(
            1,
            schedulerExecutions.get()
        );

        assertEquals(
            1,
            workerExecutions.get()
        );
    }

    private static Consumer<BooleanSupplier> controlledLoop(
        CountDownLatch started,
        AtomicBoolean stopped
    ) {

        return signal -> {

            started.countDown();

            try {

                runUntilStopped(
                    signal
                );

            } finally {

                stopped.set(
                    true
                );
            }
        };
    }

    private static void runUntilStopped(
        BooleanSupplier signal
    ) {

        while (signal.getAsBoolean()) {

            try {

                Thread.sleep(
                    50
                );

            } catch (InterruptedException exception) {

                /*
                 * O interrupt é o mecanismo usado pelo runtime para
                 * acordar loops que estejam parados em espera.
                 *
                 * O sinal compartilhado define se devemos realmente
                 * encerrar.
                 */
                if (!signal.getAsBoolean()) {
                    return;
                }

                Thread.currentThread()
                    .interrupt();

                return;
            }
        }
    }

    private static void awaitLatch(
        CountDownLatch latch
    ) {

        try {

            boolean completed =
                latch.await(
                    TEST_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            if (!completed) {
                throw new AssertionError(
                    "Timed out waiting for test coordination"
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                .interrupt();

            throw new AssertionError(
                "Test coordination was interrupted",
                exception
            );
        }
    }
}
