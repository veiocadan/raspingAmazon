package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.publication.ListPublicationsUseCase;
import com.raspingamazon.application.operation.publication.PublicationPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationsCliIntegrationTest {

    @Test
    void shouldDispatchPublicationsListThroughOperationalCli() {

        ListPublicationsUseCase useCase =
            new ListPublicationsUseCase(
                criteria ->
                    new PublicationPage(
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
                    "publications",
                    new PublicationsCliCommand(
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
                    "publications",
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
                    "PUBLICATION_ID\t"
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
    void shouldTranslateInvalidPublicationArgumentsToUsageExitCode() {

        ListPublicationsUseCase useCase =
            new ListPublicationsUseCase(
                criteria ->
                    new PublicationPage(
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
                    "publications",
                    new PublicationsCliCommand(
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
                    "publications",
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
