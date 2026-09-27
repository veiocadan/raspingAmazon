package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.infrastructure.runtime.ContinuousProcessingRuntime;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dono operacional dos recursos do processo contínuo.
 *
 * <p>A composição cria o grafo; esta classe possui o lifecycle físico
 * das Connections e do runtime.</p>
 */
public final class ContinuousProcessingApplication
    implements AutoCloseable {

    private final ContinuousProcessingRuntime runtime;

    private final Connection schedulerConnection;

    private final Connection workerConnection;

    private final AtomicBoolean closed =
        new AtomicBoolean();

    public ContinuousProcessingApplication(
        ContinuousProcessingRuntime runtime,
        Connection schedulerConnection,
        Connection workerConnection
    ) {

        this.runtime =
            Objects.requireNonNull(
                runtime,
                "runtime must not be null"
            );

        this.schedulerConnection =
            Objects.requireNonNull(
                schedulerConnection,
                "schedulerConnection must not be null"
            );

        this.workerConnection =
            Objects.requireNonNull(
                workerConnection,
                "workerConnection must not be null"
            );

        if (schedulerConnection
            == workerConnection) {

            throw new IllegalArgumentException(
                "schedulerConnection and workerConnection "
                    + "must be distinct instances"
            );
        }
    }

    public void start() {

        if (closed.get()) {
            throw new IllegalStateException(
                "Continuous processing application is closed"
            );
        }

        runtime.start();
    }

    public void awaitTermination()
        throws InterruptedException {

        runtime.awaitTermination();
    }

    public boolean isRunning() {

        return runtime.isRunning();
    }

    @Override
    public void close() {

        if (!closed.compareAndSet(
            false,
            true
        )) {

            return;
        }

        RuntimeException failure =
            null;

        try {

            runtime.close();

        } catch (RuntimeException exception) {

            failure =
                exception;
        }

        failure =
            closeConnection(
                workerConnection,
                "worker connection",
                failure
            );

        failure =
            closeConnection(
                schedulerConnection,
                "scheduler connection",
                failure
            );

        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException closeConnection(
        Connection connection,
        String resourceName,
        RuntimeException previousFailure
    ) {

        try {

            connection.close();

            return previousFailure;

        } catch (SQLException exception) {

            ContinuousProcessingBootstrapException closeFailure =
                new ContinuousProcessingBootstrapException(
                    "Could not close "
                        + resourceName,
                    exception
                );

            if (previousFailure == null) {
                return closeFailure;
            }

            previousFailure.addSuppressed(
                closeFailure
            );

            return previousFailure;
        }
    }
}
