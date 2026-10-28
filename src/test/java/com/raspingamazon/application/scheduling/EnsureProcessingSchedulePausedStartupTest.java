package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato da inicializacao com agendamento preexistente pausado.
 * Nao faz conexao JDBC, nao executa bootstrap fisico nem trabalho externo.
 */
class EnsureProcessingSchedulePausedStartupTest {

    private static final URI SOURCE = URI.create(
        "https://www.amazon.com.br/deals"
    );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse("2026-10-13T18:00:00Z");

    @Test
    void mustPreserveExistingPausedScheduleWithoutRescheduling() {
        ProcessingSchedule persisted = new ProcessingSchedule(
            "amazon-deals", SOURCE, false, Duration.ofHours(1),
            NOW.minusHours(5), null, null, null, null,
            NOW.minusDays(1), NOW.minusDays(1)
        );

        AtomicReference<ProcessingSchedule> attemptedInsert =
            new AtomicReference<>();
        ProcessingSchedulePort port = simulatedPort(persisted, attemptedInsert);

        ProcessingSchedule returned = new EnsureProcessingScheduleUseCase(
            port, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)
        ).execute("amazon-deals", SOURCE, Duration.ofHours(1));

        assertSame(persisted, returned);
        assertFalse(returned.enabled());
        assertEquals(NOW.minusHours(5), returned.nextRunAt());
        assertEquals(Duration.ofHours(1), returned.interval());
        assertTrue(attemptedInsert.get().enabled(),
            "Sem o registro persistido, o bootstrap criaria um schedule habilitado");
    }

    @Test
    void mustNotMistakeProposedInitialStateForPersistedState() {
        ProcessingSchedule persisted = new ProcessingSchedule(
            "amazon-deals", SOURCE, false, Duration.ofHours(3),
            NOW.plusHours(2), null, null, null, null,
            NOW.minusDays(2), NOW.minusDays(2)
        );

        AtomicReference<ProcessingSchedule> attemptedInsert =
            new AtomicReference<>();
        ProcessingSchedulePort port = simulatedPort(persisted, attemptedInsert);

        ProcessingSchedule returned = new EnsureProcessingScheduleUseCase(
            port, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)
        ).execute("amazon-deals", SOURCE, Duration.ofHours(1));

        assertFalse(returned.enabled());
        assertEquals(Duration.ofHours(3), returned.interval());
        assertEquals(NOW.plusHours(2), returned.nextRunAt());
        assertEquals(Duration.ofHours(1), attemptedInsert.get().interval());
        assertEquals(NOW, attemptedInsert.get().nextRunAt());
    }

    private static ProcessingSchedulePort simulatedPort(
        ProcessingSchedule persisted,
        AtomicReference<ProcessingSchedule> attemptedInsert
    ) {
        return (ProcessingSchedulePort) Proxy.newProxyInstance(
            ProcessingSchedulePort.class.getClassLoader(),
            new Class<?>[]{ProcessingSchedulePort.class},
            (proxy, method, args) -> {
                if (method.getName().equals("saveIfAbsent")) {
                    attemptedInsert.set((ProcessingSchedule) args[0]);
                    return persisted;
                }
                throw new UnsupportedOperationException(
                    "Unexpected scheduling operation: " + method.getName()
                );
            }
        );
    }
}
