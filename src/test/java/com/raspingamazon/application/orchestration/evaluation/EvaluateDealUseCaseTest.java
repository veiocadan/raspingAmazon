package com.raspingamazon.application.orchestration.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.orchestration.port.DealEvaluationLookupPort;
import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLoadPort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluateDealUseCaseTest {

    private static final long SNAPSHOT_ID =
        300L;

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-22T23:30:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-22T23:30:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldLoadAndEvaluatePersistedSnapshot() {

        List<String> events =
            new ArrayList<>();

        AtomicInteger lookupCalls =
            new AtomicInteger();

        DealEvaluationLookupPort lookup =
            snapshotId -> {

                assertEquals(
                    SNAPSHOT_ID,
                    snapshotId
                );

                int invocation =
                    lookupCalls.incrementAndGet();

                events.add(
                    "lookup-" + invocation
                );

                return OptionalLong.empty();
            };

        OfferSnapshot snapshot =
            createSnapshot();

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId -> {

                events.add(
                    "load"
                );

                assertEquals(
                    SNAPSHOT_ID,
                    snapshotId
                );

                return Optional.of(
                    snapshot
                );
            };

        AtomicReference<OfferSnapshot>
            evaluatedSnapshot =
            new AtomicReference<>();

        AtomicReference<OffsetDateTime>
            capturedEvaluatedAt =
            new AtomicReference<>();

        DealEvaluationProcessingPort processing =
            (value, evaluatedAt) -> {

                events.add(
                    "evaluate"
                );

                evaluatedSnapshot.set(
                    value
                );

                capturedEvaluatedAt.set(
                    evaluatedAt
                );
            };

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                events
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                processing,
                transaction,
                CLOCK
            );

        useCase.execute(
            SNAPSHOT_ID
        );

        assertEquals(
            List.of(
                "lookup-1",
                "load",
                "transaction-begin",
                "lookup-2",
                "evaluate",
                "transaction-commit"
            ),
            events
        );

        assertSame(
            snapshot,
            evaluatedSnapshot.get()
        );

        assertEquals(
            EVALUATED_AT,
            capturedEvaluatedAt.get()
        );

        assertEquals(
            2,
            lookupCalls.get()
        );

        assertEquals(
            1,
            transaction.executions
        );
    }

    @Test
    void shouldReturnImmediatelyWhenEvaluationAlreadyExists() {

        List<String> events =
            new ArrayList<>();

        DealEvaluationLookupPort lookup =
            snapshotId -> {

                events.add(
                    "lookup"
                );

                return OptionalLong.of(
                    900L
                );
            };

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId -> {
                throw new AssertionError(
                    "snapshot must not be loaded"
                );
            };

        DealEvaluationProcessingPort processing =
            (snapshot, evaluatedAt) -> {
                throw new AssertionError(
                    "evaluation must not run"
                );
            };

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                events
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                processing,
                transaction,
                CLOCK
            );

        useCase.execute(
            SNAPSHOT_ID
        );

        assertEquals(
            List.of(
                "lookup"
            ),
            events
        );

        assertEquals(
            0,
            transaction.executions
        );
    }

    @Test
    void shouldFailWhenSnapshotDoesNotExist() {

        List<String> events =
            new ArrayList<>();

        DealEvaluationLookupPort lookup =
            snapshotId -> {

                events.add(
                    "lookup"
                );

                return OptionalLong.empty();
            };

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId -> {

                events.add(
                    "load"
                );

                return Optional.empty();
            };

        DealEvaluationProcessingPort processing =
            (snapshot, evaluatedAt) -> {
                throw new AssertionError(
                    "evaluation must not run"
                );
            };

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                events
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                processing,
                transaction,
                CLOCK
            );

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                    SNAPSHOT_ID
                )
            );

        assertEquals(
            "OfferSnapshot not found: "
                + SNAPSHOT_ID,
            exception.getMessage()
        );

        assertEquals(
            List.of(
                "lookup",
                "load"
            ),
            events
        );

        assertEquals(
            0,
            transaction.executions
        );
    }

    @Test
    void shouldStopInsideTransactionWhenConcurrentEvaluationAppears() {

        List<String> events =
            new ArrayList<>();

        AtomicInteger lookupCalls =
            new AtomicInteger();

        DealEvaluationLookupPort lookup =
            snapshotId -> {

                int invocation =
                    lookupCalls.incrementAndGet();

                events.add(
                    "lookup-" + invocation
                );

                if (invocation == 1) {
                    return OptionalLong.empty();
                }

                return OptionalLong.of(
                    900L
                );
            };

        OfferSnapshot snapshot =
            createSnapshot();

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId -> {

                events.add(
                    "load"
                );

                return Optional.of(
                    snapshot
                );
            };

        AtomicInteger evaluationCalls =
            new AtomicInteger();

        DealEvaluationProcessingPort processing =
            (value, evaluatedAt) -> {

                evaluationCalls.incrementAndGet();

                events.add(
                    "evaluate"
                );
            };

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                events
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                processing,
                transaction,
                CLOCK
            );

        useCase.execute(
            SNAPSHOT_ID
        );

        assertEquals(
            List.of(
                "lookup-1",
                "load",
                "transaction-begin",
                "lookup-2",
                "transaction-commit"
            ),
            events
        );

        assertEquals(
            0,
            evaluationCalls.get()
        );

        assertEquals(
            2,
            lookupCalls.get()
        );

        assertEquals(
            1,
            transaction.executions
        );
    }

    @Test
    void shouldRejectInvalidSnapshotId() {

        List<String> events =
            new ArrayList<>();

        DealEvaluationLookupPort lookup =
            snapshotId -> {
                throw new AssertionError(
                    "lookup must not run"
                );
            };

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId -> {
                throw new AssertionError(
                    "loader must not run"
                );
            };

        DealEvaluationProcessingPort processing =
            (snapshot, evaluatedAt) -> {
                throw new AssertionError(
                    "evaluation must not run"
                );
            };

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                events
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                processing,
                transaction,
                CLOCK
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                0L
            )
        );

        assertTrue(
            events.isEmpty()
        );

        assertEquals(
            0,
            transaction.executions
        );
    }

    private static OfferSnapshot createSnapshot() {

        Product product =
            new Product(
                200L,
                new Asin(
                    "B087WLJH8Y"
                ),
                "Produto de avaliação",
                "https://example.invalid/image.jpg",
                "https://www.amazon.com.br/dp/B087WLJH8Y"
            );

        return new OfferSnapshot(
            SNAPSHOT_ID,
            product,
            OffsetDateTime.parse(
                "2026-09-22T23:00:00Z"
            ),
            new Money(
                new BigDecimal(
                    "99.90"
                )
            ),
            new Money(
                new BigDecimal(
                    "129.90"
                )
            ),
            new Money(
                new BigDecimal(
                    "119.90"
                )
            ),
            new Percentage(
                new BigDecimal(
                    "42.00"
                )
            ),
            4.6,
            58363L,
            "Amazon.com.br",
            "Amazon.com.br",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "https://www.amazon.com.br/deals",
            List.of()
        );
    }

    private static final class RecordingTransactionPort
        implements TransactionPort {

        private final List<String> events;

        private int executions;

        private RecordingTransactionPort(
            List<String> events
        ) {

            this.events =
                events;
        }

        @Override
        public <T> T execute(
            Supplier<T> operation
        ) {

            executions++;

            events.add(
                "transaction-begin"
            );

            try {

                T result =
                    operation.get();

                events.add(
                    "transaction-commit"
                );

                return result;

            } catch (RuntimeException exception) {

                events.add(
                    "transaction-rollback"
                );

                throw exception;
            }
        }
    }
}
