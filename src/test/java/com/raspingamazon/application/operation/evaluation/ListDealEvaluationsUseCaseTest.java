package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalQueryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListDealEvaluationsUseCaseTest {

    @Test
    void shouldDelegateSearchToOperationalQueryPort() {

        DealEvaluationSearchCriteria criteria =
            DealEvaluationSearchCriteria.firstPage();

        DealEvaluationPage expectedPage =
            new DealEvaluationPage(
                List.of(),
                null
            );

        AtomicReference<DealEvaluationSearchCriteria> receivedCriteria =
            new AtomicReference<>();

        DealEvaluationOperationalQueryPort queryPort =
            received -> {

                receivedCriteria.set(
                    received
                );

                return expectedPage;
            };

        ListDealEvaluationsUseCase useCase =
            new ListDealEvaluationsUseCase(
                queryPort
            );

        DealEvaluationPage actualPage =
            useCase.execute(
                criteria
            );

        assertSame(
            criteria,
            receivedCriteria.get()
        );

        assertSame(
            expectedPage,
            actualPage
        );
    }

    @Test
    void shouldRejectNullCriteria() {

        ListDealEvaluationsUseCase useCase =
            new ListDealEvaluationsUseCase(
                criteria -> new DealEvaluationPage(
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
            () -> new ListDealEvaluationsUseCase(
                null
            )
        );
    }

    @Test
    void shouldRejectNullPageReturnedByPort() {

        ListDealEvaluationsUseCase useCase =
            new ListDealEvaluationsUseCase(
                criteria -> null
            );

        assertThrows(
            NullPointerException.class,
            () -> useCase.execute(
                DealEvaluationSearchCriteria.firstPage()
            )
        );
    }
}
