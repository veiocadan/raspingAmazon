package com.raspingamazon.infrastructure.bootstrap;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FASE 21-E-D6: fixture exclusivamente de teste para ponte Java/PowerShell.
 * Nao importa ContinuousProcessingMain, JDBC, rede ou recursos externos.
 * Nao le arquivos de credenciais nem produz logs de dados reais.
 */
public final class Phase21EJavaStopProbeMain {

    private static final int EXIT_OK = 0;
    private static final int EXIT_TIMEOUT = 75;
    private static final int EXIT_PREEXISTING = 76;
    private static final int EXIT_INVALID = 77;

    private Phase21EJavaStopProbeMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args == null || args.length != 3) {
            return EXIT_INVALID;
        }

        try {
            Path repo = Path.of("").toAbsolutePath().normalize();
            Path base = repo.resolve("target")
                .resolve("retencao-21eb1-testes").normalize();
            Path request = Path.of(args[0]).toAbsolutePath().normalize();
            Path ready = Path.of(args[1]).toAbsolutePath().normalize();
            int timeoutMillis = Integer.parseInt(args[2]);

            if (timeoutMillis < 1500 || timeoutMillis > 5000
                || !request.getFileName().toString().equals("java-d6-stop.request")
                || !ready.getFileName().toString().equals("java-d6-ready.marker")
                || request.getParent() == null
                || !request.getParent().equals(ready.getParent())
                || !base.equals(request.getParent().getParent())
                || !request.getParent().getFileName().toString()
                    .matches("cenario-[0-9a-f]{32}")
                || !Files.isDirectory(request.getParent(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(request.getParent())
                || Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) {
                return EXIT_INVALID;
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();

            // Usa exatamente a implementacao Java de parada existente.
            try (SupervisedStopSignal signal =
                     SupervisedStopSignal.start(request, () -> {
                         calls.incrementAndGet();
                         latch.countDown();
                     })) {
                Files.createFile(ready);
                boolean requested = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
                if (!requested) {
                    return EXIT_TIMEOUT;
                }
                return calls.get() == 1 && signal.failure() == null
                    ? EXIT_OK : EXIT_INVALID;
            }
        } catch (IllegalStateException exception) {
            // Pedido preexistente deve impedir o inicio da ponte Java.
            return EXIT_PREEXISTING;
        } catch (Exception | LinkageError exception) {
            // Sem stack trace ou mensagens que possam refletir caminhos/segredos.
            return EXIT_INVALID;
        }
    }
}
