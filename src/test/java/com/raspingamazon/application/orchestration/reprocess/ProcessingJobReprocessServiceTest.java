package com.raspingamazon.application.orchestration.reprocess;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingJobReprocessServiceTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-10-04T03:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    @Test
    void shouldDelegateSameRequestWithSingleClockSnapshot() {

        ProcessingJobReprocessRequest request =
            new ProcessingJobReprocessRequest(
                50L,
                "request-50",
                "operator",
                "manual recovery"
            );

        AtomicReference<ProcessingJobReprocessRequest>
            capturedRequest =
                new AtomicReference<>();

        AtomicReference<OffsetDateTime>
            capturedRequestedAt =
                new AtomicReference<>();

        ProcessingJobReprocessResult expected =
            new ProcessingJobReprocessResult(
                70L,
                "request-50",
                deadJob(),
                true
            );

        ProcessingJobReprocessService service =
            new ProcessingJobReprocessService(
                (
                    actualRequest,
                    requestedAt
                ) -> {

                    capturedRequest.set(
                        actualRequest
                    );

                    capturedRequestedAt.set(
                        requestedAt
                    );

                    return expected;
                },
                CLOCK
            );

        ProcessingJobReprocessResult actual =
            service.reprocess(
                request
            );

        assertSame(
            request,
            capturedRequest.get()
        );

        assertEquals(
            NOW,
            capturedRequestedAt.get()
                .toInstant()
        );

        assertSame(
            expected,
            actual
        );
    }

    @Test
    void shouldRejectNullRequest() {

        ProcessingJobReprocessService service =
            new ProcessingJobReprocessService(
                (
                    request,
                    requestedAt
                ) -> {
                    throw new AssertionError();
                },
                CLOCK
            );

        assertThrows(
            NullPointerException.class,
            () ->
                service.reprocess(
                    null
                )
        );
    }

    @Test
    void shouldRejectNullPortResult() {

        ProcessingJobReprocessService service =
            new ProcessingJobReprocessService(
                (
                    request,
                    requestedAt
                ) -> null,
                CLOCK
            );

        assertThrows(
            NullPointerException.class,
            () ->
                service.reprocess(
                    new ProcessingJobReprocessRequest(
                        50L,
                        "request-50",
                        "operator",
                        "manual recovery"
                    )
                )
        );
    }

    private ProcessingJob deadJob() {

        OffsetDateTime now =
            OffsetDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC
            );

        return new ProcessingJob(
            50L,
            ProcessingJobType.PUBLICATION_DISPATCH,
            ProcessingJobStatus.DEAD,
            60L,
            null,
            null,
            "publication-dispatch:60",
            3,
            3,
            now.minusMinutes(
                10
            ),
            null,
            null,
            ProcessingFailureType.PERMANENT,
            "PUBLICATION_BLOCKED",
            "Publication run is blocked",
            now.minusHours(
                1
            ),
            now,
            now
        );
    }
}
