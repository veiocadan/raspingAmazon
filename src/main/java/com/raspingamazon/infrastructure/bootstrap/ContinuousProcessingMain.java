package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.application.operation.observability.SafeOperationalError;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Entrypoint do daemon de processamento continuo.
 *
 * <p>Antes de abrir qualquer conexao ou executar recovery, exige a politica
 * versionada com autorizacoes explicitas. O launcher Windows possui um
 * bloqueio adicional; chamar esta classe diretamente nao o contorna.</p>
 *
 * <p>A parada supervisionada continua cooperativa e sem taskkill.</p>
 */
public final class ContinuousProcessingMain {

    private static final int EXIT_OPERATIONAL_FAILURE = 1;
    private static final int EXIT_INTERRUPTED = 130;

    private ContinuousProcessingMain() {
    }

    public static void main(String[] arguments) {
        PrintWriter err = new PrintWriter(System.err, true);
        int exitCode = run(err);
        err.flush();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(PrintWriter err) {
        return run(
            err,
            DaemonStartupPolicyGate.defaultPolicyPath(),
            ContinuousProcessingBootstrap::open
        );
    }

    /**
     * Injetar caminho e abertura somente nos testes do mesmo pacote.
     * O main publico nunca aceita override de politica via argumento/env.
     */
    static int run(
        PrintWriter err,
        Path policyPath,
        Supplier<ContinuousProcessingApplication> applicationOpener
    ) {
        ContinuousProcessingApplication application = null;
        SupervisedStopSignal stopSignal = null;
        Thread shutdownHook = null;

        try {
            // Esta verificacao DEVE anteceder qualquer bootstrap, JDBC,
            // recovery, worker, scheduler ou integracao externa.
            DaemonStartupPolicyGate.requireAuthorized(policyPath);

            application = applicationOpener.get();
            ContinuousProcessingApplication hookApplication = application;
            PrintWriter hookErr = err;

            shutdownHook = new Thread(
                () -> closeFromShutdownHook(hookApplication, hookErr),
                "rasping-amazon-shutdown"
            );
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            stopSignal = SupervisedStopSignal.startIfConfigured(application::close);
            application.start();
            application.awaitTermination();

            if (stopSignal.failure() != null) {
                throw stopSignal.failure();
            }
            return 0;

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            err.println("Continuous processing interrupted");
            err.flush();
            return EXIT_INTERRUPTED;

        } catch (RuntimeException exception) {
            err.println("Continuous processing failed: " + failureMessage(exception));
            err.flush();
            return EXIT_OPERATIONAL_FAILURE;

        } finally {
            if (stopSignal != null) {
                stopSignal.close();
            }
            removeShutdownHookBestEffort(shutdownHook);
            closeApplicationBestEffort(application, err);
        }
    }

    private static void closeFromShutdownHook(
        ContinuousProcessingApplication application,
        PrintWriter err
    ) {
        try {
            application.close();
        } catch (RuntimeException exception) {
            err.println("Continuous processing shutdown failed: "
                + failureMessage(exception));
            err.flush();
        }
    }

    private static void closeApplicationBestEffort(
        ContinuousProcessingApplication application,
        PrintWriter err
    ) {
        if (application == null) {
            return;
        }
        try {
            application.close();
        } catch (RuntimeException exception) {
            err.println("Continuous processing cleanup failed: "
                + failureMessage(exception));
            err.flush();
        }
    }

    private static void removeShutdownHookBestEffort(Thread shutdownHook) {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // A JVM ja iniciou shutdown; o hook ainda pertence a ela.
        }
    }

    // Visibilidade package-private preservada para os testes historicos.
    // Nao usar getMessage() nem toString() em falhas operacionais.
    static String failureMessage(RuntimeException exception) {
        return SafeOperationalError.describe(exception);
    }
}
