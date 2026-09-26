package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.orchestration.job.ListProcessingJobsUseCase;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobCursor;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobPage;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobSearchCriteria;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobSummary;
import com.raspingamazon.application.operation.orchestration.job.port.ProcessingJobOperationalQueryPort;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;
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

class JobsCliCommandTest {

    private static final OffsetDateTime AVAILABLE_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:10:00-03:00"
        );

    private static final OffsetDateTime LOCKED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:11:00-03:00"
        );

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:00-03:00"
        );

    private static final OffsetDateTime UPDATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:12:00-03:00"
        );

    @Test
    void shouldListJobsWithDefaultCriteria() {

        AtomicReference<ProcessingJobSearchCriteria> received =
            new AtomicReference<>();

        JobsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new ProcessingJobPage(
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
            ProcessingJobSearchCriteria.DEFAULT_LIMIT,
            received.get()
                .limit()
        );

        assertNull(
            received.get()
                .type()
        );

        assertNull(
            received.get()
                .status()
        );

        assertNull(
            received.get()
                .lastFailureType()
        );

        assertNull(
            received.get()
                .after()
        );

        assertTrue(
            console.stdout()
                .startsWith(
                    "JOB_ID\t"
                )
        );
    }

    @Test
    void shouldParseAllJobFilters() {

        AtomicReference<ProcessingJobSearchCriteria> received =
            new AtomicReference<>();

        JobsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new ProcessingJobPage(
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
                "--type",
                "evaluate_deal",
                "--status",
                "retry_wait",
                "--failure-type",
                "transient",
                "--run-id",
                "101",
                "--candidate-id",
                "202",
                "--snapshot-id",
                "303",
                "--from",
                "2026-09-01T00:00:00-03:00",
                "--until",
                "2026-09-24T23:59:59-03:00",
                "--after-at",
                "2026-09-20T12:00:00-03:00",
                "--after-id",
                "404",
                "--limit",
                "25"
            ),
            console.out(),
            console.err()
        );

        ProcessingJobSearchCriteria expected =
            new ProcessingJobSearchCriteria(
                ProcessingJobType.EVALUATE_DEAL,
                ProcessingJobStatus.RETRY_WAIT,
                ProcessingFailureType.TRANSIENT,
                101L,
                202L,
                303L,
                OffsetDateTime.parse(
                    "2026-09-01T00:00:00-03:00"
                ),
                OffsetDateTime.parse(
                    "2026-09-24T23:59:59-03:00"
                ),
                new ProcessingJobCursor(
                    OffsetDateTime.parse(
                        "2026-09-20T12:00:00-03:00"
                    ),
                    404L
                ),
                25
            );

        assertEquals(
            expected,
            received.get()
        );
    }

    @Test
    void shouldRenderJobAndNextCursor() {

        ProcessingJobSummary summary =
            summary();

        ProcessingJobCursor cursor =
            new ProcessingJobCursor(
                CREATED_AT,
                404L
            );

        JobsCliCommand command =
            command(
                criteria ->
                    new ProcessingJobPage(
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
                "404\tEVALUATE_DEAL\tRETRY_WAIT\t101\t202\t303"
            )
        );

        assertTrue(
            output.contains(
                "\t2\t5\t3\t"
            )
        );

        assertTrue(
            output.contains(
                "\tTRANSIENT\tERR_HTTP\tFalha temporaria sem quebrar TSV\t"
            )
        );

        assertTrue(
            output.contains(
                "worker 01"
            )
        );

        assertTrue(
            output.contains(
                "job key 404"
            )
        );

        assertTrue(
            output.contains(
                "NEXT_CURSOR\t"
                    + CREATED_AT
                    + "\t404"
            )
        );
    }

    @Test
    void shouldAcceptEnumsCaseInsensitively() {

        AtomicReference<ProcessingJobSearchCriteria> received =
            new AtomicReference<>();

        JobsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new ProcessingJobPage(
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
                "--type",
                "collect_deals",
                "--status",
                "succeeded",
                "--failure-type",
                "permanent"
            ),
            console.out(),
            console.err()
        );

        assertEquals(
            ProcessingJobType.COLLECT_DEALS,
            received.get()
                .type()
        );

        assertEquals(
            ProcessingJobStatus.SUCCEEDED,
            received.get()
                .status()
        );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            received.get()
                .lastFailureType()
        );
    }

    @Test
    void shouldRejectUnknownEnums() {

        JobsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--type",
                    "PUBLISH"
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
                    "--status",
                    "UNKNOWN"
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
                    "--failure-type",
                    "UNKNOWN"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRequireBothCursorComponents() {

        JobsCliCommand command =
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
                    CREATED_AT.toString()
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectInvalidIdsRangeAndLimit() {

        JobsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--run-id",
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
                    "--candidate-id",
                    "invalid"
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

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--limit",
                    "201"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectDuplicateUnknownAndMissingOptions() {

        JobsCliCommand command =
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
                    "--snapshot-id"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldPrintJobsHelp() {

        JobsCliCommand command =
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
                    "rasping-amazon jobs list"
                )
        );

        assertTrue(
            console.stdout()
                .contains(
                    "--failure-type"
                )
        );
    }

    @Test
    void shouldRejectUnknownJobAction() {

        JobsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliUsageException exception =
            assertThrows(
                CliUsageException.class,
                () -> command.execute(
                    List.of(
                        "retry"
                    ),
                    console.out(),
                    console.err()
                )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "unknown jobs action"
                )
        );
    }

    private ProcessingJobSummary summary() {

        return new ProcessingJobSummary(
            404L,
            ProcessingJobType.EVALUATE_DEAL,
            ProcessingJobStatus.RETRY_WAIT,
            101L,
            202L,
            303L,
            "job\tkey\n404",
            2,
            5,
            AVAILABLE_AT,
            LOCKED_AT,
            "worker\t01",
            ProcessingFailureType.TRANSIENT,
            "ERR_HTTP",
            "Falha temporaria\nsem quebrar TSV",
            CREATED_AT,
            UPDATED_AT,
            null
        );
    }

    private JobsCliCommand emptyCommand() {

        return command(
            criteria ->
                new ProcessingJobPage(
                    List.of(),
                    null
                )
        );
    }

    private JobsCliCommand command(
        ProcessingJobOperationalQueryPort port
    ) {

        return new JobsCliCommand(
            new ListProcessingJobsUseCase(
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
