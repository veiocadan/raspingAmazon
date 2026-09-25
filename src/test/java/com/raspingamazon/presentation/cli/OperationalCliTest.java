package com.raspingamazon.presentation.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalCliTest {

    @Test
    void shouldPrintHelpWhenNoArgumentsAreProvided() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            console.createCli(
                Map.of()
            );

        CliExitCode result =
            cli.run(
                new String[0]
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            console.stdout()
                .contains(
                    "Rasping Amazon - interface operacional"
                )
        );

        assertTrue(
            console.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldPrintHelpForSupportedHelpAliases() {

        for (String argument
            : List.of(
            "help",
            "--help",
            "-h"
        )) {

            TestConsole console =
                new TestConsole();

            OperationalCli cli =
                console.createCli(
                    Map.of()
                );

            CliExitCode result =
                cli.run(
                    new String[]{
                        argument
                    }
                );

            assertEquals(
                CliExitCode.SUCCESS,
                result
            );

            assertTrue(
                console.stdout()
                    .contains(
                        "Uso:"
                    )
            );

            assertTrue(
                console.stderr()
                    .isEmpty()
            );
        }
    }

    @Test
    void shouldRejectAdditionalArgumentsAfterHelp() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            console.createCli(
                Map.of()
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "help",
                    "extra"
                }
            );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            result
        );

        assertTrue(
            console.stderr()
                .contains(
                    "help does not accept additional arguments"
                )
        );
    }

    @Test
    void shouldRejectUnknownCommand() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            console.createCli(
                Map.of()
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "unknown"
                }
            );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            result
        );

        assertTrue(
            console.stderr()
                .contains(
                    "unknown command: unknown"
                )
        );

        assertTrue(
            console.stderr()
                .contains(
                    "Uso:"
                )
        );
    }

    @Test
    void shouldDispatchRemainingArgumentsToHandler() {

        TestConsole console =
            new TestConsole();

        AtomicReference<List<String>> received =
            new AtomicReference<>();

        CliCommandHandler handler =
            (
                arguments,
                out,
                err
            ) -> {

                received.set(
                    arguments
                );

                out.println(
                    "executed"
                );

                return CliExitCode.SUCCESS;
            };

        OperationalCli cli =
            console.createCli(
                Map.of(
                    "evaluations",
                    handler
                )
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "evaluations",
                    "list",
                    "--limit",
                    "10"
                }
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertEquals(
            List.of(
                "list",
                "--limit",
                "10"
            ),
            received.get()
        );

        assertEquals(
            "executed",
            console.stdout()
                .trim()
        );
    }

    @Test
    void shouldConvertUsageExceptionToUsageExitCode() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            console.createCli(
                Map.of(
                    "evaluations",
                    (
                        arguments,
                        out,
                        err
                    ) -> {

                        throw new CliUsageException(
                            "invalid evaluation arguments"
                        );
                    }
                )
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "evaluations",
                    "bad"
                }
            );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            result
        );

        assertTrue(
            console.stderr()
                .contains(
                    "invalid evaluation arguments"
                )
        );

        assertTrue(
            console.stderr()
                .contains(
                    "Uso:"
                )
        );
    }

    @Test
    void shouldConvertRuntimeFailureToOperationalError() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            console.createCli(
                Map.of(
                    "jobs",
                    (
                        arguments,
                        out,
                        err
                    ) -> {

                        throw new IllegalStateException(
                            "database unavailable"
                        );
                    }
                )
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "jobs",
                    "list"
                }
            );

        assertEquals(
            CliExitCode.OPERATIONAL_ERROR,
            result
        );

        assertTrue(
            console.stderr()
                .contains(
                    "Operational error: database unavailable"
                )
        );

        assertFalse(
            console.stderr()
                .contains(
                    "Uso:"
                )
        );
    }

    @Test
    void shouldPreserveHandlerExitCode() {

        TestConsole console =
            new TestConsole();

        OperationalCli cli =
            console.createCli(
                Map.of(
                    "publications",
                    (
                        arguments,
                        out,
                        err
                    ) -> CliExitCode.NOT_FOUND
                )
            );

        CliExitCode result =
            cli.run(
                new String[]{
                    "publications",
                    "show",
                    "999"
                }
            );

        assertEquals(
            CliExitCode.NOT_FOUND,
            result
        );
    }

    @Test
    void shouldDefensivelyCopyHandlerMap() {

        TestConsole console =
            new TestConsole();

        Map<String, CliCommandHandler> mutable =
            new LinkedHashMap<>();

        mutable.put(
            "runs",
            (
                arguments,
                out,
                err
            ) -> CliExitCode.SUCCESS
        );

        OperationalCli cli =
            console.createCli(
                mutable
            );

        mutable.clear();

        CliExitCode result =
            cli.run(
                new String[]{
                    "runs"
                }
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );
    }

    @Test
    void shouldRejectReservedHelpHandlerName() {

        TestConsole console =
            new TestConsole();

        assertThrows(
            IllegalArgumentException.class,
            () ->
                console.createCli(
                    Map.of(
                        "help",
                        (
                            arguments,
                            out,
                            err
                        ) -> CliExitCode.SUCCESS
                    )
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

        OperationalCli createCli(
            Map<String, CliCommandHandler> handlers
        ) {

            return new OperationalCli(
                handlers,
                out,
                err
            );
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
