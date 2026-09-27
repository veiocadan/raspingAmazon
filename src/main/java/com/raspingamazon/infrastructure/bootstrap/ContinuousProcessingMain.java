package com.raspingamazon.infrastructure.bootstrap;

import java.io.PrintWriter;

/**
 * Entrypoint do daemon de processamento contínuo.
 *
 * <p>A lógica de composição permanece em
 * ContinuousProcessingBootstrap. Esta classe trata somente a fronteira
 * da JVM: shutdown hook, exit code e stderr.</p>
 */
public final class ContinuousProcessingMain {

    private static final int EXIT_OPERATIONAL_FAILURE =
        1;

    private static final int EXIT_INTERRUPTED =
        130;

    private ContinuousProcessingMain() {
    }

    public static void main(
        String[] arguments
    ) {

        PrintWriter err =
            new PrintWriter(
                System.err,
                true
            );

        int exitCode =
            run(
                err
            );

        err.flush();

        if (exitCode != 0) {
            System.exit(
                exitCode
            );
        }
    }

    static int run(
        PrintWriter err
    ) {

        ContinuousProcessingApplication application =
            null;

        Thread shutdownHook =
            null;

        try {

            application =
                ContinuousProcessingBootstrap.open();

            ContinuousProcessingApplication hookApplication =
                application;

            PrintWriter hookErr =
                err;

            shutdownHook =
                new Thread(
                    () ->
                        closeFromShutdownHook(
                            hookApplication,
                            hookErr
                        ),
                    "rasping-amazon-shutdown"
                );

            Runtime.getRuntime()
                .addShutdownHook(
                    shutdownHook
                );

            application.start();

            application.awaitTermination();

            return 0;

        } catch (InterruptedException exception) {

            Thread.currentThread()
                .interrupt();

            err.println(
                "Continuous processing interrupted"
            );

            err.flush();

            return EXIT_INTERRUPTED;

        } catch (RuntimeException exception) {

            err.println(
                "Continuous processing failed: "
                    + failureMessage(
                    exception
                )
            );

            err.flush();

            return EXIT_OPERATIONAL_FAILURE;

        } finally {

            removeShutdownHookBestEffort(
                shutdownHook
            );

            closeApplicationBestEffort(
                application,
                err
            );
        }
    }

    private static void closeFromShutdownHook(
        ContinuousProcessingApplication application,
        PrintWriter err
    ) {

        try {

            application.close();

        } catch (RuntimeException exception) {

            err.println(
                "Continuous processing shutdown failed: "
                    + failureMessage(
                    exception
                )
            );

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

            err.println(
                "Continuous processing cleanup failed: "
                    + failureMessage(
                    exception
                )
            );

            err.flush();
        }
    }

    private static void removeShutdownHookBestEffort(
        Thread shutdownHook
    ) {

        if (shutdownHook == null) {
            return;
        }

        try {

            Runtime.getRuntime()
                .removeShutdownHook(
                    shutdownHook
                );

        } catch (IllegalStateException ignored) {

            /*
             * A JVM já entrou em shutdown.
             * Nesse estado o hook está executando ou será executado.
             */
        }
    }

    private static String failureMessage(
        RuntimeException exception
    ) {

        String message =
            exception.getMessage();

        if (message == null
            || message.isBlank()) {

            return exception.getClass()
                .getSimpleName();
        }

        return message;
    }
}
