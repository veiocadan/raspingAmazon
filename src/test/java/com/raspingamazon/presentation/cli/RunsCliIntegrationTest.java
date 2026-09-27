package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.orchestration.run.GetProcessingRunDetailUseCase;
import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunDetail;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunJobMetrics;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPipelineMetrics;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSummary;
import com.raspingamazon.application.operation.orchestration.run.port.ProcessingRunOperationalDetailQueryPort;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunsCliIntegrationTest {

    private static final OffsetDateTime TIME =
        OffsetDateTime.parse(
            "2026-09-26T09:00:00-03:00"
        );

    @Test
    void shouldDispatchRunsListThroughOperationalCli() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            createCli(
                runId ->
                    Optional.empty(),
                console
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
            console.stdout()
                .startsWith(
                    "RUN_ID\t"
                )
        );

        assertTrue(
            console.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldDispatchRunsShowThroughOperationalCli() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            createCli(
                runId -> {

                    assertEquals(
                        101L,
                        runId
                    );

                    return Optional.of(
                        detail()
                    );
                },
                console
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "runs",
                    "show",
                    "101"
                }
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        String output =
            console.stdout();

        assertTrue(
            output.contains(
                "RUN_ID\t101"
            )
        );

        assertTrue(
            output.contains(
                "COLLECTED_CANDIDATES\t3"
            )
        );

        assertTrue(
            output.contains(
                "ENRICHED_CANDIDATES\t2"
            )
        );

        assertTrue(
            output.contains(
                "PUBLICATIONS_GENERATED\t3"
            )
        );

        assertTrue(
            output.contains(
                "TOTAL_JOBS\t6"
            )
        );

        assertTrue(
            output.contains(
                "DEAD_JOBS\t1"
            )
        );

        assertTrue(
            output.contains(
                "RETRY_ATTEMPTS\t3"
            )
        );

        assertTrue(
            console.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldTranslateInvalidRunArgumentsToUsageExitCode() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            createCli(
                runId ->
                    Optional.empty(),
                console
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
            console.stderr()
                .contains(
                    "--status must be one of"
                )
        );
    }

    private OperationalCli createCli(
        ProcessingRunOperationalDetailQueryPort detailPort,
        TestConsole console
    ) {

        ListProcessingRunsUseCase listUseCase =
            new ListProcessingRunsUseCase(
                criteria ->
                    new ProcessingRunPage(
                        List.of(),
                        null
                    )
            );

        GetProcessingRunDetailUseCase detailUseCase =
            new GetProcessingRunDetailUseCase(
                detailPort
            );

        return new OperationalCli(
            Map.of(
                "runs",
                new RunsCliCommand(
                    listUseCase,
                    detailUseCase
                )
            ),
            console.out(),
            console.err()
        );
    }

    private ProcessingRunDetail detail() {

        ProcessingRunSummary summary =
            new ProcessingRunSummary(
                101L,
                "run-integration-101",
                "https://www.amazon.com.br/deals",
                ProcessingRunStatus.COMPLETED,
                TIME.minusMinutes(
                    10
                ),
                TIME.minusMinutes(
                    9
                ),
                TIME,
                null,
                null,
                TIME.minusMinutes(
                    10
                ),
                TIME
            );

        ProcessingRunPipelineMetrics pipeline =
            new ProcessingRunPipelineMetrics(
                3L,
                2L,
                2L,
                1L,
                1L,
                3L
            );

        ProcessingRunJobMetrics jobs =
            new ProcessingRunJobMetrics(
                6L,
                1L,
                0L,
                1L,
                3L,
                1L,
                8L,
                3L
            );

        return new ProcessingRunDetail(
            summary,
            pipeline,
            jobs
        );
    }

    private static final class TestConsole {

        private final ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        private final ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        private final PrintWriter out =
            new PrintWriter(
                stdout,
                true,
                StandardCharsets.UTF_8
            );

        private final PrintWriter err =
            new PrintWriter(
                stderr,
                true,
                StandardCharsets.UTF_8
            );

        PrintWriter out() {

            return out;
        }

        PrintWriter err() {

            return err;
        }

        String stdout() {

            out.flush();

            return stdout.toString(
                StandardCharsets.UTF_8
            );
        }

        String stderr() {

            err.flush();

            return stderr.toString(
                StandardCharsets.UTF_8
            );
        }
    }
}
