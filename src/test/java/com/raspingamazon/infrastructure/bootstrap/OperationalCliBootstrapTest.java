package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.presentation.cli.CliExitCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalCliBootstrapTest {

    @Test
    void shouldShowGlobalHelpWithoutComposition() {

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            OperationalCliBootstrap.run(
                new String[]{
                    "help"
                },
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
                    "Rasping Amazon - interface operacional"
                )
        );

        assertTrue(
            console.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldShowUsageWithoutCompositionWhenNoArgumentsExist() {

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            OperationalCliBootstrap.run(
                new String[0],
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
                    "Rasping Amazon - interface operacional"
                )
        );
    }

    @Test
    void shouldRejectUnknownTopLevelCommandWithoutComposition() {

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            OperationalCliBootstrap.run(
                new String[]{
                    "unknown"
                },
                console.out(),
                console.err()
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
    }

    @Test
    void shouldIdentifyCommandsThatRequireOperationalComposition() {

        assertTrue(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "evaluations",
                    "list"
                }
            )
        );

        assertTrue(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "runs",
                    "list"
                }
            )
        );

        assertTrue(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "jobs",
                    "list"
                }
            )
        );

        assertTrue(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "publications",
                    "list"
                }
            )
        );

        assertFalse(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "help"
                }
            )
        );

        assertFalse(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "--help"
                }
            )
        );

        assertFalse(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "unknown"
                }
            )
        );

        assertFalse(
            OperationalCliBootstrap.requiresComposition(
                new String[0]
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

        String stderr() {

            err.flush();

            return stderr.toString(
                StandardCharsets.UTF_8
            );
        }
    }
}
