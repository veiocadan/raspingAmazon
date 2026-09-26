package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalDetailQueryPort;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetDealEvaluationDetailUseCaseTest {

    private static final OffsetDateTime TIME =
        OffsetDateTime.parse(
            "2026-09-24T20:00:00-03:00"
        );

    @Test
    void shouldDelegateEvaluationIdToQueryPort() {

        DealEvaluationDetail expected =
            detail();

        AtomicLong receivedId =
            new AtomicLong();

        DealEvaluationOperationalDetailQueryPort queryPort =
            evaluationId -> {

                receivedId.set(
                    evaluationId
                );

                return Optional.of(
                    expected
                );
            };

        GetDealEvaluationDetailUseCase useCase =
            new GetDealEvaluationDetailUseCase(
                queryPort
            );

        DealEvaluationDetail actual =
            useCase.execute(
                    30L
                )
                .orElseThrow();

        assertSame(
            expected,
            actual
        );

        assertEquals(
            30L,
            receivedId.get()
        );
    }

    @Test
    void shouldPreserveEmptyResult() {

        GetDealEvaluationDetailUseCase useCase =
            new GetDealEvaluationDetailUseCase(
                evaluationId -> Optional.empty()
            );

        assertTrue(
            useCase.execute(
                    30L
                )
                .isEmpty()
        );
    }

    @Test
    void shouldRejectNonPositiveEvaluationId() {

        GetDealEvaluationDetailUseCase useCase =
            new GetDealEvaluationDetailUseCase(
                evaluationId -> Optional.empty()
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                0L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                -1L
            )
        );
    }

    @Test
    void shouldRejectNullQueryPort() {

        assertThrows(
            NullPointerException.class,
            () -> new GetDealEvaluationDetailUseCase(
                null
            )
        );
    }

    private DealEvaluationDetail detail() {

        DealEvaluationSummary summary =
            new DealEvaluationSummary(
                30L,
                20L,
                10L,
                new Asin(
                    "B0DET14002"
                ),
                "Produto operacional",
                Money.of(
                    "99.90"
                ),
                true,
                null,
                null,
                null,
                TIME.minusMinutes(
                    5
                ),
                TIME
            );

        return new DealEvaluationDetail(
            summary,
            "https://www.amazon.com.br/dp/B0DET14002",
            null,
            null,
            null,
            null,
            null,
            "Amazon.com.br",
            "Amazon.com.br",
            "TEST",
            "TEST_ELIGIBILITY",
            null,
            null,
            List.of(),
            List.of(),
            null,
            null
        );
    }
}
