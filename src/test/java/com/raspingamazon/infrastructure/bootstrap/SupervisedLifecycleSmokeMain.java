package com.raspingamazon.infrastructure.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Processo inofensivo de teste do protocolo de supervisao.
 *
 * Nunca instancia bootstrap produtivo, banco, worker, scheduler ou cliente HTTP.
 * Nao faz parte dos entrypoints de producao e nao depende de segredos.
 */
public final class SupervisedLifecycleSmokeMain {

    private static final String READY_ENV = "RASPING_SUPERVISED_SMOKE_READY_FILE";

    private SupervisedLifecycleSmokeMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) {
            throw new IllegalArgumentException("Smoke main does not accept arguments");
        }

        String readyText = System.getenv(READY_ENV);
        String stopText = System.getenv(SupervisedStopSignal.ENVIRONMENT_VARIABLE);
        if (readyText == null || readyText.isBlank() || stopText == null || stopText.isBlank()) {
            throw new IllegalStateException("Smoke supervisor paths are required");
        }

        Path readyFile = Path.of(readyText).toAbsolutePath().normalize();
        Path stopFile = Path.of(stopText).toAbsolutePath().normalize();
        if (!readyFile.isAbsolute() || !stopFile.isAbsolute()
                || !readyFile.getParent().equals(stopFile.getParent())
                || Files.exists(readyFile) || Files.exists(stopFile)) {
            throw new IllegalStateException("Invalid isolated smoke supervisor directory");
        }

        CountDownLatch stopped = new CountDownLatch(1);
        try (SupervisedStopSignal signal = SupervisedStopSignal.startIfConfigured(stopped::countDown)) {
            // Apenas sinaliza readiness ao script de teste, em diretorio temporario privado.
            Files.writeString(readyFile, "READY\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (!stopped.await(45L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Isolated smoke stop request timed out");
            }
            if (signal.failure() != null) {
                throw new IllegalStateException("Isolated smoke stop signal failed");
            }
        }
    }
}
