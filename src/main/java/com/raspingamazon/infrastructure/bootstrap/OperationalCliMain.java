package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.presentation.cli.CliExitCode;

import java.io.PrintWriter;

/**
 * Entrypoint de processo da interface operacional.
 *
 * <p>Toda a lógica testável permanece fora desta classe.
 * Este é o único ponto da CLI que traduz CliExitCode para
 * código de saída do processo.</p>
 */
public final class OperationalCliMain {

    private OperationalCliMain() {
    }

    public static void main(
        String[] arguments
    ) {

        PrintWriter out =
            new PrintWriter(
                System.out,
                true
            );

        PrintWriter err =
            new PrintWriter(
                System.err,
                true
            );

        CliExitCode exitCode =
            OperationalCliBootstrap.run(
                arguments,
                out,
                err
            );

        out.flush();
        err.flush();

        if (exitCode != CliExitCode.SUCCESS) {

            System.exit(
                exitCode.code()
            );
        }
    }
}
