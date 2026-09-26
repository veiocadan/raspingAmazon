package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.publication.GetPublicationDetailUseCase;
import com.raspingamazon.application.operation.publication.ListPublicationsUseCase;
import com.raspingamazon.application.operation.publication.PublicationDetail;
import com.raspingamazon.application.operation.publication.PublicationPage;
import com.raspingamazon.application.operation.publication.PublicationSummary;
import com.raspingamazon.domain.product.Asin;
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

class PublicationsCliIntegrationTest {

    @Test
    void shouldDispatchPublicationsListThroughOperationalCli() {

        OperationalCli cli =
            createCli(
                publicationId ->
                    Optional.empty()
            );

        TestConsole console =
            TestConsole.current();

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
            console.stdout()
                .startsWith(
                    "PUBLICATION_ID\t"
                )
        );

        assertTrue(
            console.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldTranslateInvalidPublicationArgumentsToUsageExitCode() {

        OperationalCli cli =
            createCli(
                publicationId ->
                    Optional.empty()
            );

        TestConsole console =
            TestConsole.current();

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
            console.stderr()
                .contains(
                    "--status must be one of"
                )
        );
    }

    @Test
    void shouldDispatchPublicationShowThroughOperationalCli() {

        PublicationSummary summary =
            new PublicationSummary(
                51L,
                41L,
                31L,
                new Asin(
                    "B0CLI14005"
                ),
                "Produto integrado",
                "CREATED",
                "TEMPLATE_V1",
                "COMMERCIAL_V1",
                "AFFILIATE_V2",
                OffsetDateTime.parse(
                    "2026-09-24T20:30:00-03:00"
                )
            );

        PublicationDetail detail =
            new PublicationDetail(
                summary,
                "Mensagem integrada",
                "https://example.invalid/publication"
            );

        OperationalCli cli =
            createCli(
                publicationId ->
                    Optional.of(
                        detail
                    )
            );

        TestConsole console =
            TestConsole.current();

        CliExitCode result =
            cli.run(
                new String[]{
                    "publications",
                    "show",
                    "51"
                }
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            console.stdout()
                .contains(
                    "PUBLICATION_ID\t51"
                )
        );

        assertTrue(
            console.stdout()
                .contains(
                    "Mensagem integrada"
                )
        );

        assertTrue(
            console.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldPreservePublicationNotFoundExitCode() {

        OperationalCli cli =
            createCli(
                publicationId ->
                    Optional.empty()
            );

        TestConsole console =
            TestConsole.current();

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

        assertTrue(
            console.stderr()
                .contains(
                    "Publication not found: 999"
                )
        );
    }

    private OperationalCli createCli(
        com.raspingamazon.application.operation.publication.port.PublicationOperationalDetailQueryPort detailPort
    ) {

        ListPublicationsUseCase listUseCase =
            new ListPublicationsUseCase(
                criteria ->
                    new PublicationPage(
                        List.of(),
                        null
                    )
            );

        GetPublicationDetailUseCase detailUseCase =
            new GetPublicationDetailUseCase(
                detailPort
            );

        PublicationsCliCommand publications =
            new PublicationsCliCommand(
                listUseCase,
                detailUseCase
            );

        TestConsole console =
            TestConsole.create();

        return new OperationalCli(
            Map.of(
                "publications",
                publications
            ),
            console.out(),
            console.err()
        );
    }

    private static final class TestConsole {

        private static final ThreadLocal<TestConsole> CURRENT =
            new ThreadLocal<>();

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

        static TestConsole create() {

            TestConsole console =
                new TestConsole();

            CURRENT.set(
                console
            );

            return console;
        }

        static TestConsole current() {

            return CURRENT.get();
        }

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
