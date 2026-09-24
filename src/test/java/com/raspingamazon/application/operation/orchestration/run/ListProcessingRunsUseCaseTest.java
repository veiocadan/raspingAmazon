package com.raspingamazon.application.operation.orchestration.run;

import com.raspingamazon.application.operation.orchestration.run.port.ProcessingRunOperationalQueryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListProcessingRunsUseCaseTest {

    @Test
    void shouldDelegateSearchToQueryPort() {

        ProcessingRunSearchCriteria criteria =
            ProcessingRunSearchCriteria.firstPage();

        ProcessingRunPage expected =
            new ProcessingRunPage(
                List.of(),
                null
            );

        AtomicReference<ProcessingRunSearchCriteria> received =
            new AtomicReference<>();

        ProcessingRunOperationalQueryPort queryPort =
            value -> {

                received.set(
                    value
                );

                return expected;
            };

        ListProcessingRunsUseCase useCase =
            new ListProcessingRunsUseCase(
                queryPort
            );

        ProcessingRunPage actual =
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

        ListProcessingRunsUseCase useCase =
            new ListProcessingRunsUseCase(
                criteria -> new ProcessingRunPage(
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
            () -> new ListProcessingRunsUseCase(
                null
            )
        );
    }

    @Test
    void shouldRejectNullPageReturnedByPort() {

        ListProcessingRunsUseCase useCase =
            new ListProcessingRunsUseCase(
                criteria -> null
            );

        assertThrows(
            NullPointerException.class,
            () -> useCase.execute(
                ProcessingRunSearchCriteria.firstPage()
            )
        );
    }
}
