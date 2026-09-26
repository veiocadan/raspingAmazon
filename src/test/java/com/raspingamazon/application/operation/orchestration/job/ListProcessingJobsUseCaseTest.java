package com.raspingamazon.application.operation.orchestration.job;

import com.raspingamazon.application.operation.orchestration.job.port.ProcessingJobOperationalQueryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListProcessingJobsUseCaseTest {

    @Test
    void shouldDelegateSearchToQueryPort() {

        ProcessingJobSearchCriteria criteria =
            ProcessingJobSearchCriteria.firstPage();

        ProcessingJobPage expected =
            new ProcessingJobPage(
                List.of(),
                null
            );

        AtomicReference<ProcessingJobSearchCriteria> received =
            new AtomicReference<>();

        ProcessingJobOperationalQueryPort queryPort =
            value -> {

                received.set(
                    value
                );

                return expected;
            };

        ListProcessingJobsUseCase useCase =
            new ListProcessingJobsUseCase(
                queryPort
            );

        ProcessingJobPage actual =
            useCase.execute(
                criteria
            );

        assertSame(
            criteria,
            received.get()
        );

        assertSame(
            expected,
            actual
        );
    }

    @Test
    void shouldRejectNullCriteria() {

        ListProcessingJobsUseCase useCase =
            new ListProcessingJobsUseCase(
                criteria -> new ProcessingJobPage(
                    List.of(),
                    null
                )
            );

        assertThrows(
            NullPointerException.class,
            () -> useCase.execute(
                null
            )
        );
    }

    @Test
    void shouldRejectNullQueryPort() {

        assertThrows(
            NullPointerException.class,
            () -> new ListProcessingJobsUseCase(
                null
            )
        );
    }

    @Test
    void shouldRejectNullPageReturnedByPort() {

        ListProcessingJobsUseCase useCase =
            new ListProcessingJobsUseCase(
                criteria -> null
            );

        assertThrows(
            NullPointerException.class,
            () -> useCase.execute(
                ProcessingJobSearchCriteria.firstPage()
            )
        );
    }
}
