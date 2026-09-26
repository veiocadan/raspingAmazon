package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.observability.alert.GetOperationalAlertsUseCase;
import com.raspingamazon.application.operation.observability.alert.OperationalAlert;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

/**
 * Comando operacional de leitura dos alertas ativos.
 *
 * <p>Cada execucao representa uma avaliacao unica do estado
 * persistido.</p>
 *
 * <p>Este handler nao executa polling, scheduler, retry, workers
 * ou qualquer mutacao do pipeline.</p>
 */
public final class AlertsCliCommand
    implements CliCommandHandler {

    private static final String HEADER =
        String.join(
            "\t",
            "ALERT_TYPE",
            "RUN_ID",
            "INTEGRATION",
            "OBSERVED_COUNT",
            "REFERENCE_COUNT"
        );

    private final GetOperationalAlertsUseCase
        getOperationalAlertsUseCase;

    public AlertsCliCommand(
        GetOperationalAlertsUseCase getOperationalAlertsUseCase
    ) {

        this.getOperationalAlertsUseCase =
            Objects.requireNonNull(
                getOperationalAlertsUseCase,
                "getOperationalAlertsUseCase must not be null"
            );
    }

    @Override
    public CliExitCode execute(
        List<String> arguments,
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

        if (arguments.isEmpty()) {

            throw new CliUsageException(
                "alerts requires an action"
            );
        }

        String action =
            arguments.getFirst();

        if (isHelp(
            action
        )) {

            if (arguments.size() != 1) {

                throw new CliUsageException(
                    "alerts help does not accept additional arguments"
                );
            }

            AlertsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        if (!"list".equals(
            action
        )) {

            throw new CliUsageException(
                "unknown alerts action: "
                    + action
            );
        }

        return executeList(
            arguments.subList(
                1,
                arguments.size()
            ),
            out
        );
    }

    private CliExitCode executeList(
        List<String> arguments,
        PrintWriter out
    ) {

        if (arguments.size() == 1
            && isHelp(
            arguments.getFirst()
        )) {

            AlertsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        if (!arguments.isEmpty()) {

            throw new CliUsageException(
                "alerts list does not accept options "
                    + "or additional arguments"
            );
        }

        List<OperationalAlert> alerts =
            getOperationalAlertsUseCase.execute();

        renderAlerts(
            alerts,
            out
        );

        return CliExitCode.SUCCESS;
    }

    private void renderAlerts(
        List<OperationalAlert> alerts,
        PrintWriter out
    ) {

        out.println(
            HEADER
        );

        for (OperationalAlert alert : alerts) {

            out.println(
                renderAlert(
                    alert
                )
            );
        }

        out.flush();
    }

    private String renderAlert(
        OperationalAlert alert
    ) {

        return String.join(
            "\t",
            CliText.enumName(
                alert.type()
            ),
            nullableLong(
                alert.runId()
            ),
            CliText.text(
                alert.integration()
            ),
            Long.toString(
                alert.observedCount()
            ),
            nullableLong(
                alert.referenceCount()
            )
        );
    }

    private String nullableLong(
        Long value
    ) {

        if (value == null) {
            return "-";
        }

        return Long.toString(
            value
        );
    }

    private boolean isHelp(
        String argument
    ) {

        return "help".equals(
            argument
        )
            || "--help".equals(
            argument
        )
            || "-h".equals(
            argument
        );
    }
}
