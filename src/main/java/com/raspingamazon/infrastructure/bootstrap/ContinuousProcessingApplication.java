package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.infrastructure.runtime.ContinuousProcessingRuntime;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dono operacional dos recursos físicos do processo contínuo.
 *
 * <p>A composição cria o grafo de objetos. Esta classe possui o
 * lifecycle físico do runtime, dos recursos externos gerenciados e das
 * Connections JDBC.</p>
 *
 * <p>A ordem de fechamento é deliberada:</p>
 *
 * <pre>
 * runtime
 *     ↓
 * recursos externos gerenciados
 *     ↓
 * worker connection
 *     ↓
 * scheduler connection
 * </pre>
 *
 * <p>O runtime é encerrado antes do browser para garantir que nenhuma
 * execução de enrichment continue usando o recurso enquanto ele é
 * fechado.</p>
 */
public final class ContinuousProcessingApplication
    implements AutoCloseable {

    private final ContinuousProcessingRuntime runtime;

    private final Connection schedulerConnection;

    private final Connection workerConnection;

    private final List<AutoCloseable> managedResources;

    private final AtomicBoolean closed =
        new AtomicBoolean();

    /**
     * Construtor histórico mantido para compatibilidade.
     */
    public ContinuousProcessingApplication(
        ContinuousProcessingRuntime runtime,
        Connection schedulerConnection,
        Connection workerConnection
    ) {

        this(
            runtime,
            schedulerConnection,
            workerConnection,
            List.of()
        );
    }

    /**
     * Construtor com recursos externos cujo lifecycle pertence ao
     * processo contínuo.
     */
    public ContinuousProcessingApplication(
        ContinuousProcessingRuntime runtime,
        Connection schedulerConnection,
        Connection workerConnection,
        List<? extends AutoCloseable> managedResources
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

        Objects.requireNonNull(
            managedResources,
            "managedResources must not be null"
        );

        this.managedResources =
            List.copyOf(
                managedResources
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
            closeManagedResources(
                failure
            );

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

    /**
     * Recursos são fechados em ordem inversa à ordem recebida.
     *
     * <p>Isso permite que futuras compositions registrem recursos com
     * relações de dependência sem mudar novamente a semântica desta
     * classe.</p>
     */
    private RuntimeException closeManagedResources(
        RuntimeException previousFailure
    ) {

        RuntimeException failure =
            previousFailure;

        for (int index =
             managedResources.size() - 1;
             index >= 0;
             index--) {

            AutoCloseable resource =
                managedResources.get(
                    index
                );

            try {

                resource.close();

            } catch (Exception exception) {

                RuntimeException closeFailure;

                if (exception
                    instanceof RuntimeException
                    runtimeException) {

                    closeFailure =
                        runtimeException;

                } else {

                    closeFailure =
                        new ContinuousProcessingBootstrapException(
                            "Could not close managed resource "
                                + index,
                            exception
                        );
                }

                failure =
                    mergeFailure(
                        failure,
                        closeFailure
                    );
            }
        }

        return failure;
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

            return mergeFailure(
                previousFailure,
                closeFailure
            );
        }
    }

    private RuntimeException mergeFailure(
        RuntimeException previousFailure,
        RuntimeException closeFailure
    ) {

        if (previousFailure == null) {
            return closeFailure;
        }

        previousFailure.addSuppressed(
            closeFailure
        );

        return previousFailure;
    }
}
