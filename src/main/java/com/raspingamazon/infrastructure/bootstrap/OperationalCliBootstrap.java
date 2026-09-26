package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.infrastructure.composition.OperationalInterfaceComposition;
import com.raspingamazon.presentation.cli.CliExitCode;
import com.raspingamazon.presentation.cli.OperationalCli;
import com.raspingamazon.presentation.cli.OperationalCliFactory;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bootstrap da interface operacional.
 *
 * <p>Esta classe pertence à borda externa da aplicação e pode
 * conhecer simultaneamente a composition root de infraestrutura
 * e a factory da apresentação.</p>
 *
 * <p>Ela abre a composição somente para comandos que realmente
 * dependem dos casos de uso operacionais.</p>
 */
public final class OperationalCliBootstrap {

    private static final Set<String> RESOURCE_COMMANDS =
        Set.of(
            "evaluations",
            "runs",
            "jobs",
            "publications"
        );

    private OperationalCliBootstrap() {
    }

    public static CliExitCode run(
        String[] arguments,
        PrintWriter out,
        PrintWriter err
    ) {

        Objects.requireNonNull(
            arguments,
            "arguments must not be null"
        );

        Objects.requireNonNull(
            out,
            "out must not be null"
        );

        Objects.requireNonNull(
            err,
            "err must not be null"
        );

        if (!requiresComposition(
            arguments
        )) {

            return createCompositionFreeCli(
                out,
                err
            ).run(
                arguments
            );
        }

        try (OperationalInterfaceComposition composition =
                 OperationalInterfaceComposition.open()) {

            OperationalCli cli =
                OperationalCliFactory.create(
                    composition.listDealEvaluations(),
                    composition.getDealEvaluationDetail(),
                    composition.listProcessingRuns(),
                    composition.listProcessingJobs(),
                    composition.listPublications(),
                    composition.getPublicationDetail(),
                    out,
                    err
                );

            return cli.run(
                arguments
            );

        } catch (RuntimeException exception) {

            return operationalError(
                exception,
                err
            );
        }
    }

    static boolean requiresComposition(
        String[] arguments
    ) {

        Objects.requireNonNull(
            arguments,
            "arguments must not be null"
        );

        if (arguments.length == 0) {
            return false;
        }

        String command =
            arguments[0];

        if (isGlobalHelp(
            command
        )) {
            return false;
        }

        return RESOURCE_COMMANDS.contains(
            command
        );
    }

    private static OperationalCli createCompositionFreeCli(
        PrintWriter out,
        PrintWriter err
    ) {

        return new OperationalCli(
            Map.of(),
            out,
            err
        );
    }

    private static boolean isGlobalHelp(
        String value
    ) {

        return "help".equals(
            value
        )
            || "--help".equals(
            value
        )
            || "-h".equals(
            value
        );
    }

    private static CliExitCode operationalError(
        RuntimeException exception,
        PrintWriter err
    ) {

        String message =
            exception.getMessage();

        if (message == null
            || message.isBlank()) {

            message =
                exception.getClass()
                    .getSimpleName();
        }

        err.println(
            "Operational error: "
                + message
        );

        err.flush();

        return CliExitCode.OPERATIONAL_ERROR;
    }
}
