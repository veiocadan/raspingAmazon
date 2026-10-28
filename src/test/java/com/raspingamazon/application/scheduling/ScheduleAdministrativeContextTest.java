package com.raspingamazon.application.scheduling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleAdministrativeContextTest {

    @Test
    void shouldTrimAuditFields() {
        ScheduleAdministrativeContext ctx =
            new ScheduleAdministrativeContext(" operator ", " ticket-42 ");
        assertEquals("operator", ctx.actor());
        assertEquals("ticket-42", ctx.reason());
    }

    @Test
    void shouldRejectMissingUnboundedOrControlValues() {
        assertThrows(NullPointerException.class,
            () -> new ScheduleAdministrativeContext(null, "ticket"));
        assertThrows(IllegalArgumentException.class,
            () -> new ScheduleAdministrativeContext(" ", "ticket"));
        assertThrows(IllegalArgumentException.class,
            () -> new ScheduleAdministrativeContext("operator", " "));
        assertThrows(IllegalArgumentException.class,
            () -> new ScheduleAdministrativeContext("operator", "line\nbreak"));
        assertThrows(IllegalArgumentException.class,
            () -> new ScheduleAdministrativeContext("x".repeat(121), "reason"));
        assertThrows(IllegalArgumentException.class,
            () -> new ScheduleAdministrativeContext("operator", "x".repeat(301)));
    }

    @Test
    void shouldProvideHistoricalReferenceWithoutHumanIdentityClaims() {
        ScheduleAdministrativeContext ctx =
            ScheduleAdministrativeContext.useCaseReference("pause");
        assertEquals("application-usecase", ctx.actor());
        assertEquals("use-case:schedules-pause", ctx.reason());
    }
}
