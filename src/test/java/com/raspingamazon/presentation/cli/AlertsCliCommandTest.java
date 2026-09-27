package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.observability.alert.GetOperationalAlertsUseCase;
import com.raspingamazon.application.operation.observability.alert.OperationalAlert;
import com.raspingamazon.application.operation.observability.alert.OperationalAlertPolicy;
import com.raspingamazon.application.operation.observability.alert.OperationalAlertType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertsCliCommandTest {

    private static final Clock FIXED_CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-26T18:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldRenderActiveAlerts() {

        AlertsCliCommand command =
            command(
                List.of(
                    new OperationalAlert(
                        OperationalAlertType.REPEATED_EXTERNAL_FAILURES,
                        null,
                        "amazon-deals-http",
                        3L,
                        null
                    ),
                    new OperationalAlert(
                        OperationalAlertType.DEAD_JOBS,
                        101L,
                        null,
                        2L,
                        null
                    ),
                    new OperationalAlert(
                        OperationalAlertType.ZERO_CANDIDATES,
                        102L,
                        null,
                        0L,
                        null
                    ),
                    new OperationalAlert(
                        OperationalAlertType.SUSPICIOUS_COLLECTION_DROP,
                        103L,
                        null,
                        4L,
                        10L
                    )
                )
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

        String output =
            console.stdout();

        assertTrue(
            output.startsWith(
                "ALERT_TYPE\tRUN_ID\tINTEGRATION\t"
                    + "OBSERVED_COUNT\tREFERENCE_COUNT"
            )
        );

        assertTrue(
            output.contains(
                "REPEATED_EXTERNAL_FAILURES"
                    + "\t-\tamazon-deals-http\t3\t-"
            )
        );

        assertTrue(
            output.contains(
                "DEAD_JOBS\t101\t-\t2\t-"
            )
        );

        assertTrue(
            output.contains(
                "ZERO_CANDIDATES\t102\t-\t0\t-"
            )
        );

        assertTrue(
            output.contains(
                "SUSPICIOUS_COLLECTION_DROP"
                    + "\t103\t-\t4\t10"
            )
        );

        assertTrue(
            console.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldRenderHeaderWhenNoAlertsAreActive() {

        AlertsCliCommand command =
            command(
                List.of()
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
            "ALERT_TYPE\tRUN_ID\tINTEGRATION\t"
                + "OBSERVED_COUNT\tREFERENCE_COUNT"
                + System.lineSeparator(),
            console.stdout()
        );
    }

    @Test
    void shouldPrintAlertsHelp() {

        AlertsCliCommand command =
            command(
                List.of()
            );

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
                    "rasping-amazon alerts list"
                )
        );

        assertTrue(
            console.stdout()
                .contains(
                    "ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD"
                )
        );
    }

    @Test
    void shouldRejectMissingUnknownAndAdditionalArguments() {

        AlertsCliCommand command =
            command(
                List.of()
            );

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "unknown"
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
                    "10"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectNullUseCase() {

        assertThrows(
            NullPointerException.class,
            () -> new AlertsCliCommand(
                null
            )
        );
    }

    private AlertsCliCommand command(
        List<OperationalAlert> alerts
    ) {

        GetOperationalAlertsUseCase useCase =
            new GetOperationalAlertsUseCase(
                (policy, evaluatedAt) ->
                    alerts,
                this::policy,
                FIXED_CLOCK
            );

        return new AlertsCliCommand(
            useCase
        );
    }

    private OperationalAlertPolicy policy() {

        return new OperationalAlertPolicy(
            3,
            Duration.ofMinutes(
                15
            ),
            3,
            new BigDecimal(
                "0.50"
            ),
            5L
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
