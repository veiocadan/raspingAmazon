package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Motor top-level da interface operacional.
 *
 * <p>Responsabilidades:</p>
 *
 * <ul>
 *     <li>interpretar o primeiro argumento;</li>
 *     <li>exibir ajuda;</li>
 *     <li>localizar o handler do recurso;</li>
 *     <li>encaminhar os argumentos restantes;</li>
 *     <li>converter erros em códigos de saída previsíveis.</li>
 * </ul>
 *
 * <p>Esta classe não conhece banco, SQL, JDBC, regras comerciais
 * ou implementação dos casos de uso.</p>
 */
public final class OperationalCli {

    private final Map<String, CliCommandHandler> handlers;

    private final PrintWriter out;

    private final PrintWriter err;

    public OperationalCli(
        Map<String, CliCommandHandler> handlers,
        PrintWriter out,
        PrintWriter err
    ) {

        Objects.requireNonNull(
            handlers,
            "handlers must not be null"
        );

        this.out =
            Objects.requireNonNull(
                out,
                "out must not be null"
            );

        this.err =
            Objects.requireNonNull(
                err,
                "err must not be null"
            );

        this.handlers =
            validateAndCopyHandlers(
                handlers
            );
    }

    /**
     * Executa uma invocação da CLI sem chamar System.exit().
     *
     * <p>Isso permite que o motor seja testado isoladamente e
     * também reutilizado por outros entrypoints no futuro.</p>
     */
    public CliExitCode run(
        String[] arguments
    ) {

        Objects.requireNonNull(
            arguments,
            "arguments must not be null"
        );

        if (arguments.length == 0) {

            OperationalCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        String commandName =
            arguments[0];

        if (isHelpCommand(
            commandName
        )) {

            if (arguments.length != 1) {

                return usageError(
                    "help does not accept additional arguments"
                );
            }

            OperationalCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        CliCommandHandler handler =
            handlers.get(
                commandName
            );

        if (handler == null) {

            return usageError(
                "unknown command: "
                    + commandName
            );
        }

        List<String> remainingArguments =
            List.copyOf(
                Arrays.asList(
                        arguments
                    )
                    .subList(
                        1,
                        arguments.length
                    )
            );

        try {

            CliExitCode exitCode =
                Objects.requireNonNull(
                    handler.execute(
                        remainingArguments,
                        out,
                        err
                    ),
                    "CliCommandHandler must not return null"
                );

            out.flush();
            err.flush();

            return exitCode;

        } catch (CliUsageException exception) {

            return usageError(
                exception.getMessage()
            );

        } catch (RuntimeException exception) {

            return operationalError(
                exception
            );
        }
    }

    private boolean isHelpCommand(
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

    private CliExitCode usageError(
        String message
    ) {

        err.println(
            "Usage error: "
                + message
        );

        err.println();

        OperationalCliUsage.print(
            err
        );

        err.flush();

        return CliExitCode.USAGE_ERROR;
    }

    private CliExitCode operationalError(
        RuntimeException exception
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

    private Map<String, CliCommandHandler> validateAndCopyHandlers(
        Map<String, CliCommandHandler> source
    ) {

        Map<String, CliCommandHandler> copy =
            new LinkedHashMap<>();

        for (Map.Entry<String, CliCommandHandler> entry
            : source.entrySet()) {

            String name =
                Objects.requireNonNull(
                    entry.getKey(),
                    "handler name must not be null"
                );

            if (name.isBlank()) {
                throw new IllegalArgumentException(
                    "handler name must not be blank"
                );
            }

            if (isHelpCommand(
                name
            )) {

                throw new IllegalArgumentException(
                    "help command is reserved: "
                        + name
                );
            }

            CliCommandHandler handler =
                Objects.requireNonNull(
                    entry.getValue(),
                    "handler must not be null for command "
                        + name
                );

            copy.put(
                name,
                handler
            );
        }

        return Map.copyOf(
            copy
        );
    }
}
