package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.scheduling.ProcessingSchedule;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingScheduleAdapter;
import com.raspingamazon.presentation.cli.CliExitCode;
import com.raspingamazon.presentation.cli.OperationalCli;
import com.raspingamazon.presentation.cli.OperationalCliFactory;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class OperationalSchedulesCliIntegrationTest {

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T18:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-27T18:00:00Z"
            ),
            ZoneOffset.UTC
        );

    private static final Duration INITIAL_INTERVAL =
        Duration.ofMinutes(
            15
        );

    @Test
    void shouldControlPersistedScheduleThroughOperationalCli()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            "fase17-cli-"
                + UUID.randomUUID();

        try {

            createSchedule(
                config,
                scheduleKey
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     );

                 OperationalInterfaceComposition composition =
                     OperationalInterfaceComposition
                         .fromOwnedConnection(
                             connection,
                             CLOCK
                         )) {

                TestConsole console =
                    new TestConsole();

                OperationalCli cli =
                    createCli(
                        composition,
                        console
                    );

                /*
                 * STATUS
                 */
                CliExitCode status =
                    cli.run(
                        new String[]{
                            "schedules",
                            "status",
                            scheduleKey
                        }
                    );

                assertEquals(
                    CliExitCode.SUCCESS,
                    status
                );

                assertTrue(
                    console.stdout()
                        .contains(
                            "SCHEDULE_KEY\t"
                                + scheduleKey
                        )
                );

                assertTrue(
                    console.stdout()
                        .contains(
                            "ENABLED\ttrue"
                        )
                );

                assertTrue(
                    console.stdout()
                        .contains(
                            "INTERVAL\tPT15M"
                        )
                );

                /*
                 * PAUSE
                 */
                console.reset();

                CliExitCode pause =
                    cli.run(
                        new String[]{
                            "schedules",
                            "pause",
                            scheduleKey
                        }
                    );

                assertEquals(
                    CliExitCode.SUCCESS,
                    pause
                );

                assertTrue(
                    console.stdout()
                        .contains(
                            "ENABLED\tfalse"
                        )
                );

                ProcessingSchedule paused =
                    composition.getProcessingSchedule()
                        .execute(
                            scheduleKey
                        )
                        .orElseThrow();

                assertFalse(
                    paused.enabled()
                );

                /*
                 * RESUME
                 *
                 * O Clock da composição é fixo, portanto a próxima
                 * janela precisa ser exatamente NOW.
                 */
                console.reset();

                CliExitCode resume =
                    cli.run(
                        new String[]{
                            "schedules",
                            "resume",
                            scheduleKey
                        }
                    );

                assertEquals(
                    CliExitCode.SUCCESS,
                    resume
                );

                ProcessingSchedule resumed =
                    composition.getProcessingSchedule()
                        .execute(
                            scheduleKey
                        )
                        .orElseThrow();

                assertTrue(
                    resumed.enabled()
                );

                assertSameInstant(
                    NOW,
                    resumed.nextRunAt()
                );

                /*
                 * INTERVAL
                 */
                console.reset();

                CliExitCode interval =
                    cli.run(
                        new String[]{
                            "schedules",
                            "interval",
                            scheduleKey,
                            "PT30M"
                        }
                    );

                assertEquals(
                    CliExitCode.SUCCESS,
                    interval
                );

                ProcessingSchedule changed =
                    composition.getProcessingSchedule()
                        .execute(
                            scheduleKey
                        )
                        .orElseThrow();

                assertEquals(
                    Duration.ofMinutes(
                        30
                    ),
                    changed.interval()
                );

                assertSameInstant(
                    NOW.plusMinutes(
                        30
                    ),
                    changed.nextRunAt()
                );

                assertTrue(
                    console.stdout()
                        .contains(
                            "INTERVAL\tPT30M"
                        )
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldReturnNotFoundThroughComposedSchedulesCli()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );

             OperationalInterfaceComposition composition =
                 OperationalInterfaceComposition
                     .fromOwnedConnection(
                         connection,
                         CLOCK
                     )) {

            TestConsole console =
                new TestConsole();

            OperationalCli cli =
                createCli(
                    composition,
                    console
                );

            String missingKey =
                "missing-"
                    + UUID.randomUUID();

            CliExitCode result =
                cli.run(
                    new String[]{
                        "schedules",
                        "status",
                        missingKey
                    }
                );

            assertEquals(
                CliExitCode.NOT_FOUND,
                result
            );

            assertTrue(
                console.stderr()
                    .contains(
                        "Schedule not found: "
                            + missingKey
                    )
            );
        }
    }

    private OperationalCli createCli(
        OperationalInterfaceComposition composition,
        TestConsole console
    ) {

        return OperationalCliFactory.create(
            composition.listDealEvaluations(),
            composition.getDealEvaluationDetail(),
            composition.listProcessingRuns(),
            composition.getProcessingRunDetail(),
            composition.listProcessingJobs(),
            composition.listPublications(),
            composition.getPublicationDetail(),
            composition.getOperationalAlerts(),
            composition.getProcessingSchedule(),
            composition.pauseProcessingSchedule(),
            composition.resumeProcessingSchedule(),
            composition.changeProcessingScheduleInterval(),
            console.out(),
            console.err()
        );
    }

    private void createSchedule(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingScheduleAdapter adapter =
                new JdbcProcessingScheduleAdapter(
                    connection
                );

            adapter.saveIfAbsent(
                new ProcessingSchedule(
                    scheduleKey,
                    SOURCE,
                    true,
                    INITIAL_INTERVAL,
                    NOW.plusMinutes(
                        15
                    ),
                    null,
                    null,
                    null,
                    null,
                    NOW.minusDays(
                        1
                    ),
                    NOW.minusDays(
                        1
                    )
                )
            );
        }
    }

    private void cleanup(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );

             PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM processing_schedule
                     WHERE schedule_key = ?
                     """
                 )) {

            statement.setString(
                1,
                scheduleKey
            );

            statement.executeUpdate();
        }
    }

    private void assertSameInstant(
        OffsetDateTime expected,
        OffsetDateTime actual
    ) {

        assertEquals(
            expected.toInstant(),
            actual.toInstant()
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

        private PrintWriter out() {

            return out;
        }

        private PrintWriter err() {

            return err;
        }

        private String stdout() {

            out.flush();

            return stdout.toString(
                StandardCharsets.UTF_8
            );
        }

        private String stderr() {

            err.flush();

            return stderr.toString(
                StandardCharsets.UTF_8
            );
        }

        private void reset() {

            out.flush();
            err.flush();

            stdout.reset();
            stderr.reset();
        }
    }
}
