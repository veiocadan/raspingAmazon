package com.raspingamazon.infrastructure.bootstrap;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ponte cooperativa entre supervisor Windows e ciclo de vida da aplicacao.
 *
 * <p>Quando RASPING_SUPERVISED_STOP_REQUEST_FILE estiver configurada,
 * a existencia de um arquivo regular previamente inexistente sinaliza
 * encerramento. Nao executa SQL, comandos externos nem cria arquivos.</p>
 *
 * <p>O supervisor e responsavel por proteger o diretorio com ACL de
 * Windows, verificar PID + creation time e criar o pedido de maneira
 * exclusiva. O arquivo nao e uma credencial.</p>
 */
public final class SupervisedStopSignal implements AutoCloseable {

    public static final String ENVIRONMENT_VARIABLE =
        "RASPING_SUPERVISED_STOP_REQUEST_FILE";

    private static final long POLL_MILLIS = 250L;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<RuntimeException> failure =
        new AtomicReference<>();
    private final Thread watcher;

    private SupervisedStopSignal(Thread watcher) {
        this.watcher = watcher;
    }

    public static SupervisedStopSignal startIfConfigured(Runnable stopAction) {
        Objects.requireNonNull(stopAction, "stopAction must not be null");
        String rawPath = System.getenv(ENVIRONMENT_VARIABLE);
        if (rawPath == null || rawPath.isBlank()) {
            return new SupervisedStopSignal(null);
        }
        return start(Path.of(rawPath), stopAction);
    }

    /** Variante de teste sem depender de variaveis globais do SO. */
    static SupervisedStopSignal start(Path requestFile, Runnable stopAction) {
        Objects.requireNonNull(requestFile, "requestFile must not be null");
        Objects.requireNonNull(stopAction, "stopAction must not be null");
        if (!requestFile.isAbsolute()) {
            throw new IllegalArgumentException("Supervised stop request path must be absolute");
        }
        Path normalized = requestFile.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(parent)) {
            throw new IllegalStateException("Supervised stop request directory is not valid");
        }
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Preexisting supervised stop request; refuse startup");
        }

        AtomicReference<SupervisedStopSignal> instance = new AtomicReference<>();
        Thread watcher = new Thread(() -> instance.get().watch(normalized, stopAction),
            "rasping-amazon-supervised-stop");
        watcher.setDaemon(true);
        SupervisedStopSignal monitor = new SupervisedStopSignal(watcher);
        instance.set(monitor);
        watcher.start();
        return monitor;
    }

    private void watch(Path requestFile, Runnable stopAction) {
        while (!closed.get()) {
            if (Files.exists(requestFile, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(requestFile, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(requestFile)) {
                    failure.compareAndSet(null,
                        new IllegalStateException("Invalid supervised stop request file"));
                }
                try {
                    stopAction.run();
                } catch (RuntimeException exception) {
                    failure.compareAndSet(null,
                        new IllegalStateException("Supervised graceful shutdown failed", exception));
                }
                return;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public RuntimeException failure() {
        return failure.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true) || watcher == null) {
            return;
        }
        watcher.interrupt();
        if (Thread.currentThread() == watcher) {
            return;
        }
        boolean interrupted = false;
        try {
            watcher.join(1_000L);
        } catch (InterruptedException exception) {
            interrupted = true;
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
