package com.raspingamazon.application.orchestration.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.orchestration.port.DealEvaluationLookupPort;
import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLoadPort;
import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLockPort;
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
    void shouldLockLoadAndEvaluatePersistedSnapshot() {

        List<String> events =
            new ArrayList<>();

        AtomicInteger lookups =
            new AtomicInteger();

        DealEvaluationLookupPort lookup =
            snapshotId -> {

                int invocation =
                    lookups.incrementAndGet();

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

                return Optional.of(
                    snapshot
                );
            };

        OfferSnapshotEvaluationLockPort lockPort =
            snapshotId -> {

                assertEquals(
                    SNAPSHOT_ID,
                    snapshotId
                );

                events.add(
                    "lock"
                );
            };

        AtomicReference<OfferSnapshot> evaluatedSnapshot =
            new AtomicReference<>();

        AtomicReference<OffsetDateTime> evaluatedAt =
            new AtomicReference<>();

        DealEvaluationProcessingPort processor =
            (offerSnapshot, evaluationTime) -> {

                events.add(
                    "evaluate"
                );

                evaluatedSnapshot.set(
                    offerSnapshot
                );

                evaluatedAt.set(
                    evaluationTime
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
                lockPort,
                processor,
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
                "lock",
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
            evaluatedAt.get()
        );

        assertEquals(
            1,
            transaction.executions
        );
    }

    @Test
    void shouldReturnImmediatelyWhenEvaluationAlreadyExists() {

        DealEvaluationLookupPort lookup =
            snapshotId ->
                OptionalLong.of(
                    900L
                );

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId -> {
                throw new AssertionError(
                    "snapshot must not be loaded"
                );
            };

        OfferSnapshotEvaluationLockPort lockPort =
            snapshotId -> {
                throw new AssertionError(
                    "snapshot must not be locked"
                );
            };

        DealEvaluationProcessingPort processor =
            (snapshot, evaluatedAt) -> {
                throw new AssertionError(
                    "evaluation must not run"
                );
            };

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                new ArrayList<>()
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                lockPort,
                processor,
                transaction,
                CLOCK
            );

        useCase.execute(
            SNAPSHOT_ID
        );

        assertEquals(
            0,
            transaction.executions
        );
    }

    @Test
    void shouldFailWhenSnapshotDoesNotExist() {

        DealEvaluationLookupPort lookup =
            snapshotId ->
                OptionalLong.empty();

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId ->
                Optional.empty();

        OfferSnapshotEvaluationLockPort lockPort =
            snapshotId -> {
                throw new AssertionError(
                    "missing snapshot must not be locked"
                );
            };

        DealEvaluationProcessingPort processor =
            (snapshot, evaluatedAt) -> {
                throw new AssertionError(
                    "evaluation must not run"
                );
            };

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                new ArrayList<>()
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                lockPort,
                processor,
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
            "OfferSnapshot not found: 300",
            exception.getMessage()
        );

        assertEquals(
            0,
            transaction.executions
        );
    }

    @Test
    void shouldStopInsideTransactionWhenEvaluationAppearsAfterLock() {

        List<String> events =
            new ArrayList<>();

        AtomicInteger lookups =
            new AtomicInteger();

        DealEvaluationLookupPort lookup =
            snapshotId -> {

                int invocation =
                    lookups.incrementAndGet();

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

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId -> {

                events.add(
                    "load"
                );

                return Optional.of(
                    createSnapshot()
                );
            };

        OfferSnapshotEvaluationLockPort lockPort =
            snapshotId ->
                events.add(
                    "lock"
                );

        DealEvaluationProcessingPort processor =
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
                lockPort,
                processor,
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
                "lock",
                "lookup-2",
                "transaction-commit"
            ),
            events
        );

        assertEquals(
            1,
            transaction.executions
        );
    }

    @Test
    void shouldAcquireLockBeforeSecondEvaluationLookup() {

        List<String> events =
            new ArrayList<>();

        AtomicInteger lookupCount =
            new AtomicInteger();

        DealEvaluationLookupPort lookup =
            snapshotId -> {

                int count =
                    lookupCount.incrementAndGet();

                events.add(
                    "lookup-" + count
                );

                return OptionalLong.empty();
            };

        OfferSnapshotEvaluationLoadPort loader =
            snapshotId -> {

                events.add(
                    "load"
                );

                return Optional.of(
                    createSnapshot()
                );
            };

        OfferSnapshotEvaluationLockPort lockPort =
            snapshotId ->
                events.add(
                    "lock"
                );

        DealEvaluationProcessingPort processor =
            (snapshot, evaluatedAt) ->
                events.add(
                    "evaluate"
                );

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                events
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                lockPort,
                processor,
                transaction,
                CLOCK
            );

        useCase.execute(
            SNAPSHOT_ID
        );

        assertEquals(
            "lock",
            events.get(
                3
            )
        );

        assertEquals(
            "lookup-2",
            events.get(
                4
            )
        );
    }

    @Test
    void shouldRejectInvalidSnapshotId() {

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

        OfferSnapshotEvaluationLockPort lockPort =
            snapshotId -> {
                throw new AssertionError(
                    "lock must not run"
                );
            };

        DealEvaluationProcessingPort processor =
            (snapshot, evaluatedAt) -> {
                throw new AssertionError(
                    "processor must not run"
                );
            };

        RecordingTransactionPort transaction =
            new RecordingTransactionPort(
                new ArrayList<>()
            );

        EvaluateDealUseCase useCase =
            new EvaluateDealUseCase(
                lookup,
                loader,
                lockPort,
                processor,
                transaction,
                CLOCK
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                0
            )
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
