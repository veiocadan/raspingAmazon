package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.publication.ListPublicationsUseCase;
import com.raspingamazon.application.operation.publication.PublicationCursor;
import com.raspingamazon.application.operation.publication.PublicationPage;
import com.raspingamazon.application.operation.publication.PublicationSearchCriteria;
import com.raspingamazon.application.operation.publication.PublicationSummary;
import com.raspingamazon.application.operation.publication.port.PublicationOperationalQueryPort;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.PublicationStatus;
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

class PublicationsCliCommandTest {

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:30:00-03:00"
        );

    @Test
    void shouldListPublicationsWithDefaultCriteria() {

        AtomicReference<PublicationSearchCriteria> received =
            new AtomicReference<>();

        PublicationsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new PublicationPage(
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
            PublicationSearchCriteria.DEFAULT_LIMIT,
            received.get()
                .limit()
        );

        assertNull(
            received.get()
                .status()
        );

        assertNull(
            received.get()
                .after()
        );

        assertTrue(
            console.stdout()
                .startsWith(
                    "PUBLICATION_ID\t"
                )
        );
    }

    @Test
    void shouldParseAllPublicationFilters() {

        AtomicReference<PublicationSearchCriteria> received =
            new AtomicReference<>();

        PublicationsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new PublicationPage(
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
                "created",
                "--asin",
                "B0CLI14003",
                "--evaluation-id",
                "41",
                "--from",
                "2026-09-01T00:00:00-03:00",
                "--until",
                "2026-09-24T23:59:59-03:00",
                "--after-at",
                "2026-09-20T12:00:00-03:00",
                "--after-id",
                "51",
                "--limit",
                "25"
            ),
            console.out(),
            console.err()
        );

        PublicationSearchCriteria expected =
            new PublicationSearchCriteria(
                PublicationStatus.CREATED,
                new Asin(
                    "B0CLI14003"
                ),
                41L,
                OffsetDateTime.parse(
                    "2026-09-01T00:00:00-03:00"
                ),
                OffsetDateTime.parse(
                    "2026-09-24T23:59:59-03:00"
                ),
                new PublicationCursor(
                    OffsetDateTime.parse(
                        "2026-09-20T12:00:00-03:00"
                    ),
                    51L
                ),
                25
            );

        assertEquals(
            expected,
            received.get()
        );
    }

    @Test
    void shouldRenderPublicationAndNextCursor() {

        PublicationSummary summary =
            new PublicationSummary(
                51L,
                41L,
                31L,
                new Asin(
                    "B0CLI14004"
                ),
                "Produto\tcom\nquebra",
                "CREATED",
                "TEMPLATE_V1",
                "COMMERCIAL_V1",
                "AFFILIATE_V2",
                CREATED_AT
            );

        PublicationCursor cursor =
            new PublicationCursor(
                CREATED_AT,
                51L
            );

        PublicationsCliCommand command =
            command(
                criteria ->
                    new PublicationPage(
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
                "51\t41\t31\tB0CLI14004\tCREATED"
            )
        );

        assertTrue(
            output.contains(
                "\tTEMPLATE_V1\tCOMMERCIAL_V1\tAFFILIATE_V2\t"
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
                    + CREATED_AT
                    + "\t51"
            )
        );
    }

    @Test
    void shouldAcceptPublicationStatusCaseInsensitively() {

        AtomicReference<PublicationSearchCriteria> received =
            new AtomicReference<>();

        PublicationsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new PublicationPage(
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
                "published"
            ),
            console.out(),
            console.err()
        );

        assertEquals(
            PublicationStatus.PUBLISHED,
            received.get()
                .status()
        );
    }

    @Test
    void shouldRejectUnknownStatus() {

        PublicationsCliCommand command =
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
                    "CREATED"
                )
        );
    }

    @Test
    void shouldRequireBothCursorComponents() {

        PublicationsCliCommand command =
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
    void shouldRejectDuplicateUnknownAndMissingOptions() {

        PublicationsCliCommand command =
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
    void shouldRejectInvalidScalarValues() {

        PublicationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--evaluation-id",
                    "zero"
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
    }

    @Test
    void shouldPrintPublicationsHelp() {

        PublicationsCliCommand command =
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
                    "rasping-amazon publications list"
                )
        );
    }

    @Test
    void shouldRejectUnknownPublicationAction() {

        PublicationsCliCommand command =
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
                    "unknown publications action"
                )
        );
    }

    private PublicationsCliCommand emptyCommand() {

        return command(
            criteria ->
                new PublicationPage(
                    List.of(),
                    null
                )
        );
    }

    private PublicationsCliCommand command(
        PublicationOperationalQueryPort port
    ) {

        return new PublicationsCliCommand(
            new ListPublicationsUseCase(
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
