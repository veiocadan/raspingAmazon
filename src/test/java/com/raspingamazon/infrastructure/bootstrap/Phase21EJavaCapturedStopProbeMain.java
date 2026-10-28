package com.raspingamazon.infrastructure.bootstrap;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FASE 21-E-D7: processo Java artificial para validar stdout, stderr e
 * SupervisedStopSignal na mesma JVM, sem iniciar ContinuousProcessingMain.
 * Nao acessa banco, rede, credenciais, canais externos ou logs reais.
 */
public final class Phase21EJavaCapturedStopProbeMain {

    private static final int EXIT_COOPERATIVE = 0;
    private static final int EXIT_TIMEOUT = 75;
    private static final int EXIT_PREEXISTING = 76;
    private static final int EXIT_INVALID = 77;

    private Phase21EJavaCapturedStopProbeMain() {
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
            Path base = repo.resolve("target").resolve("retencao-21eb1-testes").normalize();
            Path request = Path.of(args[0]).toAbsolutePath().normalize();
            Path ready = Path.of(args[1]).toAbsolutePath().normalize();
            int timeoutMillis = Integer.parseInt(args[2]);

            if (timeoutMillis < 1500 || timeoutMillis > 12000
                || !"java-d7-stop.request".equals(request.getFileName().toString())
                || !"java-d7-ready.marker".equals(ready.getFileName().toString())
                || request.getParent() == null
                || !request.getParent().equals(ready.getParent())
                || !base.equals(request.getParent().getParent())
                || !request.getParent().getFileName().toString().matches("cenario-[0-9a-f]{32}")
                || !Files.isDirectory(request.getParent(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(request.getParent())
                || Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) {
                return EXIT_INVALID;
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger invocations = new AtomicInteger();
            // Ponte real de parada supervisionada; nao usa fixture PowerShell.
            try (SupervisedStopSignal signal = SupervisedStopSignal.start(request, () -> {
                invocations.incrementAndGet();
                latch.countDown();
            })) {
                // Evento estruturado compatível com catalogo v1, dados totalmente artificiais.
                String timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
                System.out.println("{\"timestamp\":\"" + timestamp
                    + "\",\"level\":\"INFO\",\"event\":\"processing.job.claimed\""
                    + ",\"component\":\"processing-worker\",\"operation\":\"execute-job\""
                    + ",\"outcome\":\"CLAIMED\",\"runId\":7,\"jobId\":11}");
                System.out.flush();
                // stderr eh sempre conteudo nao confiavel e nao pode ser persistido.
                System.err.println("TOKEN_FICTICIO_D7_NAO_PERSISTIR");
                System.err.flush();
                if (System.out.checkError() || System.err.checkError()) {
                    return EXIT_INVALID;
                }
                Files.createFile(ready);
                boolean stopped = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
                if (!stopped) {
                    return EXIT_TIMEOUT;
                }
                return invocations.get() == 1 && signal.failure() == null
                    ? EXIT_COOPERATIVE : EXIT_INVALID;
            }
        } catch (IllegalStateException exception) {
            // Um pedido preexistente bloqueia antes da readiness e dos logs.
            return EXIT_PREEXISTING;
        } catch (Exception | LinkageError exception) {
            // Nunca imprimir exception, caminhos ou qualquer mensagem de filhos.
            return EXIT_INVALID;
        }
    }
}
