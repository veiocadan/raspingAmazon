package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunCursor;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSearchCriteria;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSummary;
import com.raspingamazon.application.operation.orchestration.run.port.ProcessingRunOperationalQueryPort;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunsCliCommandTest {

    private static final OffsetDateTime REQUESTED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:00-03:00"
        );

    private static final OffsetDateTime STARTED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:05-03:00"
        );

    private static final OffsetDateTime COMPLETED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:10-03:00"
        );

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T19:59:59-03:00"
        );

    private static final OffsetDateTime UPDATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:10-03:00"
        );

    @Test
    void shouldListRunsWithDefaultCriteria() {

        AtomicReference<ProcessingRunSearchCriteria> received =
            new AtomicReference<>();

        RunsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new ProcessingRunPage(
                        List.of(),
                        null
                    );
                }
            );

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            command.execute(
                List.of(
                    "list"
                ),
                console.out(),
                console.err()
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertEquals(
            ProcessingRunSearchCriteria.DEFAULT_LIMIT,
            received.get()
                .limit()
        );

        assertNull(
            received.get()
                .status()
        );

        assertNull(
            received.get()
                .requestedFrom()
        );

        assertNull(
            received.get()
                .requestedUntil()
        );

        assertNull(
            received.get()
                .after()
        );

        assertTrue(
            console.stdout()
                .startsWith(
                    "RUN_ID\t"
                )
        );
    }

    @Test
    void shouldParseAllRunFilters() {

        AtomicReference<ProcessingRunSearchCriteria> received =
            new AtomicReference<>();

        RunsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new ProcessingRunPage(
                        List.of(),
                        null
                    );
                }
            );

        TestConsole console =
            new TestConsole();

        command.execute(
            List.of(
                "list",
                "--status",
                "failed",
                "--from",
                "2026-09-01T00:00:00-03:00",
                "--until",
                "2026-09-24T23:59:59-03:00",
                "--after-at",
                "2026-09-20T12:00:00-03:00",
                "--after-id",
                "101",
                "--limit",
                "25"
            ),
            console.out(),
            console.err()
        );

        ProcessingRunSearchCriteria expected =
            new ProcessingRunSearchCriteria(
                ProcessingRunStatus.FAILED,
                OffsetDateTime.parse(
                    "2026-09-01T00:00:00-03:00"
                ),
                OffsetDateTime.parse(
                    "2026-09-24T23:59:59-03:00"
                ),
                new ProcessingRunCursor(
                    OffsetDateTime.parse(
                        "2026-09-20T12:00:00-03:00"
                    ),
                    101L
                ),
                25
            );

        assertEquals(
            expected,
            received.get()
        );
    }

    @Test
    void shouldRenderRunAndNextCursor() {

        ProcessingRunSummary summary =
            summary();

        ProcessingRunCursor cursor =
            new ProcessingRunCursor(
                REQUESTED_AT,
                101L
            );

        RunsCliCommand command =
            command(
                criteria ->
                    new ProcessingRunPage(
                        List.of(
                            summary
                        ),
                        cursor
                    )
            );

        TestConsole console =
            new TestConsole();

        command.execute(
            List.of(
                "list"
            ),
            console.out(),
            console.err()
        );

        String output =
            console.stdout();

        assertTrue(
            output.contains(
                "101\trun-101\tFAILED\t"
                    + REQUESTED_AT
            )
        );

        assertTrue(
            output.contains(
                "\tERR_TEST\tFalha de teste sem quebrar TSV"
            )
        );

        assertTrue(
            output.contains(
                "https://example.invalid/deals page"
            )
        );

        assertTrue(
            output.contains(
                "NEXT_CURSOR\t"
                    + REQUESTED_AT
                    + "\t101"
            )
        );
    }

    @Test
    void shouldAcceptRunStatusCaseInsensitively() {

        AtomicReference<ProcessingRunSearchCriteria> received =
            new AtomicReference<>();

        RunsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new ProcessingRunPage(
                        List.of(),
                        null
                    );
                }
            );

        TestConsole console =
            new TestConsole();

        command.execute(
            List.of(
                "list",
                "--status",
                "completed"
            ),
            console.out(),
            console.err()
        );

        assertEquals(
            ProcessingRunStatus.COMPLETED,
            received.get()
                .status()
        );
    }

    @Test
    void shouldRejectUnknownStatus() {

        RunsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliUsageException exception =
            assertThrows(
                CliUsageException.class,
                () -> command.execute(
                    List.of(
                        "list",
                        "--status",
                        "UNKNOWN"
                    ),
                    console.out(),
                    console.err()
                )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "PENDING"
                )
        );
    }

    @Test
    void shouldRequireBothCursorComponents() {

        RunsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--after-id",
                    "10"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--after-at",
                    REQUESTED_AT.toString()
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectDuplicateUnknownAndMissingOptions() {

        RunsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--limit",
                    "10",
                    "--limit",
                    "20"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--unknown",
                    "value"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--limit"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectInvalidScalarValuesAndInvalidRange() {

        RunsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--from",
                    "invalid-date"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--limit",
                    "0"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--from",
                    "2026-09-25T00:00:00-03:00",
                    "--until",
                    "2026-09-24T00:00:00-03:00"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldPrintRunsHelp() {

        RunsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            command.execute(
                List.of(
                    "help"
                ),
                console.out(),
                console.err()
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            console.stdout()
                .contains(
                    "rasping-amazon runs list"
                )
        );
    }

    @Test
    void shouldRejectUnknownRunAction() {

        RunsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliUsageException exception =
            assertThrows(
                CliUsageException.class,
                () -> command.execute(
                    List.of(
                        "delete"
                    ),
                    console.out(),
                    console.err()
                )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "unknown runs action"
                )
        );
    }

    private ProcessingRunSummary summary() {

        return new ProcessingRunSummary(
            101L,
            "run-101",
            "https://example.invalid/deals\tpage",
            ProcessingRunStatus.FAILED,
            REQUESTED_AT,
            STARTED_AT,
            COMPLETED_AT,
            "ERR_TEST",
            "Falha de teste\nsem quebrar TSV",
            CREATED_AT,
            UPDATED_AT
        );
    }

    private RunsCliCommand emptyCommand() {

        return command(
            criteria ->
                new ProcessingRunPage(
                    List.of(),
                    null
                )
        );
    }

    private RunsCliCommand command(
        ProcessingRunOperationalQueryPort port
    ) {

        return new RunsCliCommand(
            new ListProcessingRunsUseCase(
                port
            )
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
    }
}
