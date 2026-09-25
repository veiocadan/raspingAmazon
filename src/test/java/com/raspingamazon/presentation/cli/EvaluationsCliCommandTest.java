package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.DealEvaluationCursor;
import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSearchCriteria;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSummary;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationsCliCommandTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:05:00-03:00"
        );

    @Test
    void shouldListEvaluationsWithDefaultCriteria() {

        AtomicReference<DealEvaluationSearchCriteria> received =
            new AtomicReference<>();

        EvaluationsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new DealEvaluationPage(
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
            DealEvaluationSearchCriteria.DEFAULT_LIMIT,
            received.get()
                .limit()
        );

        assertNull(
            received.get()
                .eligible()
        );

        assertNull(
            received.get()
                .after()
        );

        assertTrue(
            console.stdout()
                .startsWith(
                    "EVALUATION_ID\t"
                )
        );
    }

    @Test
    void shouldParseAllListFilters() {

        AtomicReference<DealEvaluationSearchCriteria> received =
            new AtomicReference<>();

        EvaluationsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new DealEvaluationPage(
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
                "--eligible",
                "true",
                "--asin",
                "B0CLI14001",
                "--min-score",
                "70.5000",
                "--max-score",
                "95.0000",
                "--from",
                "2026-09-01T00:00:00-03:00",
                "--until",
                "2026-09-24T23:59:59-03:00",
                "--after-at",
                "2026-09-20T12:00:00-03:00",
                "--after-id",
                "123",
                "--limit",
                "25"
            ),
            console.out(),
            console.err()
        );

        DealEvaluationSearchCriteria expected =
            new DealEvaluationSearchCriteria(
                Boolean.TRUE,
                new Asin(
                    "B0CLI14001"
                ),
                new BigDecimal(
                    "70.5000"
                ),
                new BigDecimal(
                    "95.0000"
                ),
                OffsetDateTime.parse(
                    "2026-09-01T00:00:00-03:00"
                ),
                OffsetDateTime.parse(
                    "2026-09-24T23:59:59-03:00"
                ),
                new DealEvaluationCursor(
                    OffsetDateTime.parse(
                        "2026-09-20T12:00:00-03:00"
                    ),
                    123L
                ),
                25
            );

        assertEquals(
            expected,
            received.get()
        );
    }

    @Test
    void shouldRenderEvaluationAndNextCursor() {

        DealEvaluationSummary summary =
            new DealEvaluationSummary(
                123L,
                456L,
                789L,
                new Asin(
                    "B0CLI14002"
                ),
                "Produto\tcom\nquebra",
                Money.of(
                    "129.90"
                ),
                true,
                null,
                new BigDecimal(
                    "84.2500"
                ),
                new BigDecimal(
                    "0.7500"
                ),
                COLLECTED_AT,
                EVALUATED_AT
            );

        DealEvaluationCursor cursor =
            new DealEvaluationCursor(
                EVALUATED_AT,
                123L
            );

        EvaluationsCliCommand command =
            command(
                criteria ->
                    new DealEvaluationPage(
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
                "123\t456\t789\tB0CLI14002\ttrue"
            )
        );

        assertTrue(
            output.contains(
                "\t84.2500\t0.7500\t129.90\t-\t"
            )
        );

        assertTrue(
            output.contains(
                "Produto com quebra"
            )
        );

        assertTrue(
            output.contains(
                "NEXT_CURSOR\t"
                    + EVALUATED_AT
                    + "\t123"
            )
        );
    }

    @Test
    void shouldRequireBothCursorComponents() {

        EvaluationsCliCommand command =
            command(
                criteria ->
                    new DealEvaluationPage(
                        List.of(),
                        null
                    )
            );

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
                    "2026-09-24T20:00:00-03:00"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectDuplicateOption() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliUsageException exception =
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

        assertTrue(
            exception.getMessage()
                .contains(
                    "duplicate option"
                )
        );
    }

    @Test
    void shouldRejectUnknownOptionAndMissingValue() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--unknown",
                    "x"
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
    void shouldRejectInvalidScalarValues() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--eligible",
                    "maybe"
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
                    "--min-score",
                    "abc"
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
                    "not-a-date"
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
                    "zero"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldExposeCriteriaValidationAsUsageError() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--eligible",
                    "false",
                    "--min-score",
                    "50"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldPrintEvaluationsHelp() {

        EvaluationsCliCommand command =
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
                    "rasping-amazon evaluations list"
                )
        );
    }

    @Test
    void shouldRejectUnknownEvaluationAction() {

        EvaluationsCliCommand command =
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
                    "unknown evaluations action"
                )
        );
    }

    private EvaluationsCliCommand emptyCommand() {

        return command(
            criteria ->
                new DealEvaluationPage(
                    List.of(),
                    null
                )
        );
    }

    private EvaluationsCliCommand command(
        com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalQueryPort port
    ) {

        return new EvaluationsCliCommand(
            new ListDealEvaluationsUseCase(
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
