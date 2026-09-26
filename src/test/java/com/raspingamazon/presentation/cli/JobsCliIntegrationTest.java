package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.orchestration.job.ListProcessingJobsUseCase;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobsCliIntegrationTest {

    @Test
    void shouldDispatchJobsListThroughOperationalCli() {

        ListProcessingJobsUseCase useCase =
            new ListProcessingJobsUseCase(
                criteria ->
                    new ProcessingJobPage(
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
                    "jobs",
                    new JobsCliCommand(
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
                    "jobs",
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
                    "JOB_ID\t"
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
    void shouldTranslateInvalidJobArgumentsToUsageExitCode() {

        ListProcessingJobsUseCase useCase =
            new ListProcessingJobsUseCase(
                criteria ->
                    new ProcessingJobPage(
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
                    "jobs",
                    new JobsCliCommand(
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
                    "jobs",
                    "list",
                    "--type",
                    "PUBLISH"
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
                    "--type must be one of"
                )
        );
    }
}
