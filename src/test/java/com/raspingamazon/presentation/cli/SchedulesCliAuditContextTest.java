package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.scheduling.*;
import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SchedulesCliAuditContextTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-13T15:00:00Z");

    @Test
    void shouldForwardExplicitReasonForEveryAdministrativeAction() {
        for (String action : List.of("pause", "resume", "interval")) {
            AtomicReference<ScheduleAdministrativeContext> captured = new AtomicReference<>();
            SchedulesCliCommand command = command(captured);
            StringWriter output = new StringWriter();
            List<String> args = action.equals("interval")
                ? List.of("interval", "amazon-deals", "PT1H", "--reason", "change-ticket-42")
                : List.of(action, "amazon-deals", "--reason", "change-ticket-42");

            assertEquals(CliExitCode.SUCCESS,
                command.execute(args, new PrintWriter(output), new PrintWriter(new StringWriter())));
            assertEquals("change-ticket-42", captured.get().reason());
            assertTrue(captured.get().actor().startsWith("local-cli:"));
        }
    }

    @Test
    void shouldRetainHistoricalCommandWithoutExplicitReason() {
        AtomicReference<ScheduleAdministrativeContext> captured = new AtomicReference<>();
        SchedulesCliCommand command = command(captured);
        assertEquals(CliExitCode.SUCCESS,
            command.execute(List.of("pause", "amazon-deals"),
                new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter())));
        assertEquals("cli-command:schedules-pause", captured.get().reason());
    }

    @Test
    void shouldRejectUnknownOptionWithoutWriting() {
        AtomicReference<ScheduleAdministrativeContext> captured = new AtomicReference<>();
        SchedulesCliCommand command = command(captured);
        assertThrows(CliUsageException.class,
            () -> command.execute(List.of("resume", "amazon-deals", "--actor", "forged"),
                new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter())));
        assertNull(captured.get());
    }

    @Test
    void shouldRejectBlankReasonWithoutWriting() {
        AtomicReference<ScheduleAdministrativeContext> captured = new AtomicReference<>();
        SchedulesCliCommand command = command(captured);
        assertThrows(CliUsageException.class,
            () -> command.execute(List.of("pause", "amazon-deals", "--reason", " "),
                new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter())));
        assertNull(captured.get());
    }

    private static SchedulesCliCommand command(AtomicReference<ScheduleAdministrativeContext> captured) {
        ProcessingSchedule initial = new ProcessingSchedule(
            "amazon-deals", URI.create("https://www.amazon.com.br/deals"), false,
            Duration.ofMinutes(30), NOW, null, null, null, null, NOW.minusDays(1), NOW.minusDays(1)
        );
        ProcessingSchedulePort port = (ProcessingSchedulePort) Proxy.newProxyInstance(
            ProcessingSchedulePort.class.getClassLoader(),
            new Class<?>[]{ProcessingSchedulePort.class},
            (proxy, method, args) -> {
                if (method.getName().equals("findByKey")) return Optional.of(initial);
                if (method.getName().equals("pause") || method.getName().equals("resume")
                    || method.getName().equals("changeInterval")) {
                    captured.set((ScheduleAdministrativeContext) args[args.length - 1]);
                    return initial;
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneOffset.UTC);
        return new SchedulesCliCommand(
            new GetProcessingScheduleUseCase(port),
            new PauseProcessingScheduleUseCase(port, clock),
            new ResumeProcessingScheduleUseCase(port, clock),
            new ChangeProcessingScheduleIntervalUseCase(port, clock)
        );
    }
}
