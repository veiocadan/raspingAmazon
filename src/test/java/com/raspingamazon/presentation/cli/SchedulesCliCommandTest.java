package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.scheduling.ChangeProcessingScheduleIntervalUseCase;
import com.raspingamazon.application.scheduling.GetProcessingScheduleUseCase;
import com.raspingamazon.application.scheduling.PauseProcessingScheduleUseCase;
import com.raspingamazon.application.scheduling.ProcessingSchedule;
import com.raspingamazon.application.scheduling.ProcessingScheduleLease;
import com.raspingamazon.application.scheduling.ResumeProcessingScheduleUseCase;
import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulesCliCommandTest {

    private static final String SCHEDULE_KEY =
        "amazon-deals";

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

    private static final Duration INTERVAL =
        Duration.ofMinutes(
            15
        );

    @Test
    void shouldRenderScheduleStatus() {

        TestFixture fixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "status",
                        SCHEDULE_KEY
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "SCHEDULE_KEY\tamazon-deals"
                )
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "ENABLED\ttrue"
                )
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "INTERVAL\tPT15M"
                )
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "LEASED\tfalse"
                )
        );
    }

    @Test
    void shouldReturnNotFoundForMissingStatus() {

        TestFixture fixture =
            createFixture(
                null
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "status",
                        SCHEDULE_KEY
                    }
                );

        assertEquals(
            CliExitCode.NOT_FOUND,
            result
        );

        assertTrue(
            fixture.stderr()
                .contains(
                    "Schedule not found: amazon-deals"
                )
        );
    }

    @Test
    void shouldPauseSchedule() {

        TestFixture fixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "pause",
                        SCHEDULE_KEY
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "ENABLED\tfalse"
                )
        );

        assertFalse(
            fixture.port()
                .schedule()
                .enabled()
        );
    }

    @Test
    void shouldResumeScheduleImmediately() {

        TestFixture fixture =
            createFixture(
                pausedSchedule()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "resume",
                        SCHEDULE_KEY
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.port()
                .schedule()
                .enabled()
        );

        assertEquals(
            NOW,
            fixture.port()
                .schedule()
                .nextRunAt()
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "NEXT_RUN_AT\t2026-09-27T18:00Z"
                )
        );
    }

    @Test
    void shouldChangeScheduleInterval() {

        TestFixture fixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "interval",
                        SCHEDULE_KEY,
                        "PT30M"
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertEquals(
            Duration.ofMinutes(
                30
            ),
            fixture.port()
                .schedule()
                .interval()
        );

        assertEquals(
            NOW.plusMinutes(
                30
            ),
            fixture.port()
                .schedule()
                .nextRunAt()
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "INTERVAL\tPT30M"
                )
        );
    }

    @Test
    void shouldReturnNotFoundForMutation() {

        TestFixture fixture =
            createFixture(
                null
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "pause",
                        SCHEDULE_KEY
                    }
                );

        assertEquals(
            CliExitCode.NOT_FOUND,
            result
        );

        assertTrue(
            fixture.stderr()
                .contains(
                    "Schedule not found: amazon-deals"
                )
        );
    }

    @Test
    void shouldRejectMissingAction() {

        TestFixture fixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules"
                    }
                );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            result
        );

        assertTrue(
            fixture.stderr()
                .contains(
                    "schedules requires an action"
                )
        );
    }

    @Test
    void shouldRejectUnknownAction() {

        TestFixture fixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "unknown"
                    }
                );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            result
        );

        assertTrue(
            fixture.stderr()
                .contains(
                    "unknown schedules action: unknown"
                )
        );
    }

    @Test
    void shouldRejectMalformedDuration() {

        TestFixture fixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "interval",
                        SCHEDULE_KEY,
                        "30-minutes"
                    }
                );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            result
        );

        assertTrue(
            fixture.stderr()
                .contains(
                    "duration must be a positive ISO-8601 duration"
                )
        );
    }

    @Test
    void shouldRejectNonPositiveDuration() {

        TestFixture zeroFixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode zeroResult =
            zeroFixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "interval",
                        SCHEDULE_KEY,
                        "PT0S"
                    }
                );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            zeroResult
        );

        TestFixture negativeFixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode negativeResult =
            negativeFixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "interval",
                        SCHEDULE_KEY,
                        "-PT1M"
                    }
                );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            negativeResult
        );
    }

    @Test
    void shouldRenderSchedulesHelp() {

        TestFixture fixture =
            createFixture(
                activeSchedule()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "schedules",
                        "help"
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "schedules status <schedule-key>"
                )
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "PT15M"
                )
        );
    }

    private TestFixture createFixture(
        ProcessingSchedule initialSchedule
    ) {

        InMemorySchedulePort port =
            new InMemorySchedulePort(
                initialSchedule
            );

        GetProcessingScheduleUseCase getUseCase =
            new GetProcessingScheduleUseCase(
                port
            );

        PauseProcessingScheduleUseCase pauseUseCase =
            new PauseProcessingScheduleUseCase(
                port,
                CLOCK
            );

        ResumeProcessingScheduleUseCase resumeUseCase =
            new ResumeProcessingScheduleUseCase(
                port,
                CLOCK
            );

        ChangeProcessingScheduleIntervalUseCase intervalUseCase =
            new ChangeProcessingScheduleIntervalUseCase(
                port,
                CLOCK
            );

        SchedulesCliCommand command =
            new SchedulesCliCommand(
                getUseCase,
                pauseUseCase,
                resumeUseCase,
                intervalUseCase
            );

        ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        PrintWriter out =
            new PrintWriter(
                stdout,
                true,
                StandardCharsets.UTF_8
            );

        PrintWriter err =
            new PrintWriter(
                stderr,
                true,
                StandardCharsets.UTF_8
            );

        OperationalCli cli =
            new OperationalCli(
                Map.of(
                    "schedules",
                    command
                ),
                out,
                err
            );

        return new TestFixture(
            cli,
            port,
            out,
            err,
            stdout,
            stderr
        );
    }

    private ProcessingSchedule activeSchedule() {

        return new ProcessingSchedule(
            SCHEDULE_KEY,
            SOURCE,
            true,
            INTERVAL,
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
        );
    }

    private ProcessingSchedule pausedSchedule() {

        return new ProcessingSchedule(
            SCHEDULE_KEY,
            SOURCE,
            false,
            INTERVAL,
            NOW.plusHours(
                2
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
        );
    }

    private static final class InMemorySchedulePort
        implements ProcessingSchedulePort {

        private ProcessingSchedule schedule;

        private InMemorySchedulePort(
            ProcessingSchedule schedule
        ) {

            this.schedule =
                schedule;
        }

        private ProcessingSchedule schedule() {

            return schedule;
        }

        @Override
        public ProcessingSchedule saveIfAbsent(
            ProcessingSchedule schedule
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProcessingSchedule> findByKey(
            String scheduleKey
        ) {

            return Optional.ofNullable(
                schedule
            );
        }

        @Override
        public Optional<ProcessingScheduleLease> tryAcquireDue(
            String scheduleKey,
            String leaseOwner,
            OffsetDateTime now,
            Duration leaseDuration
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingSchedule confirmScheduled(
            String scheduleKey,
            String leaseOwner,
            OffsetDateTime scheduledFor,
            long processingRunId,
            OffsetDateTime nextRunAt,
            OffsetDateTime confirmedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingSchedule releaseLease(
            String scheduleKey,
            String leaseOwner,
            OffsetDateTime releasedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingSchedule pause(
            String scheduleKey,
            OffsetDateTime changedAt
        ) {

            schedule =
                new ProcessingSchedule(
                    schedule.scheduleKey(),
                    schedule.source(),
                    false,
                    schedule.interval(),
                    schedule.nextRunAt(),
                    schedule.leaseOwner(),
                    schedule.leaseExpiresAt(),
                    schedule.lastScheduledFor(),
                    schedule.lastProcessingRunId(),
                    schedule.createdAt(),
                    changedAt
                );

            return schedule;
        }

        @Override
        public ProcessingSchedule resume(
            String scheduleKey,
            OffsetDateTime nextRunAt,
            OffsetDateTime changedAt
        ) {

            schedule =
                new ProcessingSchedule(
                    schedule.scheduleKey(),
                    schedule.source(),
                    true,
                    schedule.interval(),
                    nextRunAt,
                    null,
                    null,
                    schedule.lastScheduledFor(),
                    schedule.lastProcessingRunId(),
                    schedule.createdAt(),
                    changedAt
                );

            return schedule;
        }

        @Override
        public ProcessingSchedule changeInterval(
            String scheduleKey,
            Duration interval,
            OffsetDateTime nextRunAt,
            OffsetDateTime changedAt
        ) {

            schedule =
                new ProcessingSchedule(
                    schedule.scheduleKey(),
                    schedule.source(),
                    schedule.enabled(),
                    interval,
                    nextRunAt,
                    null,
                    null,
                    schedule.lastScheduledFor(),
                    schedule.lastProcessingRunId(),
                    schedule.createdAt(),
                    changedAt
                );

            return schedule;
        }
    }

    private record TestFixture(
        OperationalCli cli,
        InMemorySchedulePort port,
        PrintWriter out,
        PrintWriter err,
        ByteArrayOutputStream stdoutBuffer,
        ByteArrayOutputStream stderrBuffer
    ) {

        private String stdout() {

            out.flush();

            return stdoutBuffer.toString(
                StandardCharsets.UTF_8
            );
        }

        private String stderr() {

            err.flush();

            return stderrBuffer.toString(
                StandardCharsets.UTF_8
            );
        }
    }
}
