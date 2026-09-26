package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunsCliIntegrationTest {

    @Test
    void shouldDispatchRunsListThroughOperationalCli() {

        ListProcessingRunsUseCase useCase =
            new ListProcessingRunsUseCase(
                criteria ->
                    new ProcessingRunPage(
                        List.of(),
                        null
                    )
            );

        ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        OperationalCli cli =
            new OperationalCli(
                Map.of(
                    "runs",
                    new RunsCliCommand(
                        useCase
                    )
                ),
                new PrintWriter(
                    stdout,
                    true,
                    StandardCharsets.UTF_8
                ),
                new PrintWriter(
                    stderr,
                    true,
                    StandardCharsets.UTF_8
                )
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "runs",
                    "list",
                    "--limit",
                    "10"
                }
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            stdout.toString(
                    StandardCharsets.UTF_8
                )
                .startsWith(
                    "RUN_ID\t"
                )
        );

        assertTrue(
            stderr.toString(
                    StandardCharsets.UTF_8
                )
                .isEmpty()
        );
    }

    @Test
    void shouldTranslateInvalidRunArgumentsToUsageExitCode() {

        ListProcessingRunsUseCase useCase =
            new ListProcessingRunsUseCase(
                criteria ->
                    new ProcessingRunPage(
                        List.of(),
                        null
                    )
            );

        ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        OperationalCli cli =
            new OperationalCli(
                Map.of(
                    "runs",
                    new RunsCliCommand(
                        useCase
                    )
                ),
                new PrintWriter(
                    stdout,
                    true,
                    StandardCharsets.UTF_8
                ),
                new PrintWriter(
                    stderr,
                    true,
                    StandardCharsets.UTF_8
                )
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "runs",
                    "list",
                    "--status",
                    "UNKNOWN"
                }
            );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            result
        );

        assertTrue(
            stderr.toString(
                    StandardCharsets.UTF_8
                )
                .contains(
                    "--status must be one of"
                )
        );
    }
}
