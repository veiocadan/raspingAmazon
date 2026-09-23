package com.raspingamazon.application.orchestration.collection;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.ProcessingRun;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.port.DealCandidateRepositoryPort;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.orchestration.port.ProcessingRunRepositoryPort;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectDealsUseCaseTest {

    private static final long RUN_ID =
        10L;

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-22T22:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-22T22:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldCollectParsePersistCandidatesAndEnqueueEnrichment()
        throws Exception {

        List<String> events =
            new ArrayList<>();

        RecordingRunRepository runRepository =
            new RecordingRunRepository(
                events,
                pendingRun()
            );

        RecordingCandidateRepository candidateRepository =
            new RecordingCandidateRepository(
                events
            );

        RecordingQueue queue =
            new RecordingQueue(
                events
            );

        CollectionCollector collector =
            request -> {

                events.add(
                    "collect"
                );

                assertEquals(
                    URI.create(
                        "https://www.amazon.com.br/deals"
                    ),
                    request.source()
                );

                return collectionResult();
            };

        DealsParser parser =
            result -> {

                events.add(
                    "parse"
                );

                return List.of(
                    parsedDeal(
                        "B087WLJH8Y"
                    ),
                    parsedDeal(
                        "B012345678"
                    )
                );
            };

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort(
                events
            );

        CollectDealsUseCase useCase =
            new CollectDealsUseCase(
                runRepository,
                candidateRepository,
                queue,
                collector,
                parser,
                transactionPort,
                CLOCK,
                5
            );

        ProcessingRun result =
            useCase.execute(
                RUN_ID
            );

        assertEquals(
            ProcessingRunStatus.COMPLETED,
            result.status()
        );

        assertEquals(
            2,
            candidateRepository.savedCandidates.size()
        );

        assertEquals(
            2,
            queue.submissions.size()
        );

        assertEquals(
            ProcessingJobType.ENRICH_DEAL,
            queue.submissions.get(0)
                .type()
        );

        assertEquals(
            100L,
            queue.submissions.get(0)
                .dealCandidateId()
        );

        assertEquals(
            "enrich:100",
            queue.submissions.get(0)
                .idempotencyKey()
        );

        assertEquals(
            5,
            queue.submissions.get(0)
                .maxAttempts()
        );

        assertEquals(
            ProcessingJobType.ENRICH_DEAL,
            queue.submissions.get(1)
                .type()
        );

        assertEquals(
            101L,
            queue.submissions.get(1)
                .dealCandidateId()
        );

        assertEquals(
            "enrich:101",
            queue.submissions.get(1)
                .idempotencyKey()
        );

        assertEquals(
            List.of(
                "run-find",
                "transaction-begin",
                "run-running",
                "transaction-commit",
                "collect",
                "parse",
                "transaction-begin",
                "candidate-save",
                "enqueue",
                "candidate-save",
                "enqueue",
                "run-completed",
                "transaction-commit"
            ),
            events
        );

        assertEquals(
            2,
            transactionPort.executions
        );
    }

    @Test
    void shouldReturnImmediatelyWhenRunIsAlreadyCompleted() {

        List<String> events =
            new ArrayList<>();

        ProcessingRun completedRun =
            completedRun();

        RecordingRunRepository runRepository =
            new RecordingRunRepository(
                events,
                completedRun
            );

        RecordingCandidateRepository candidateRepository =
            new RecordingCandidateRepository(
                events
            );

        RecordingQueue queue =
            new RecordingQueue(
                events
            );

        CollectionCollector collector =
            request -> {
                throw new AssertionError(
                    "collection must not run"
                );
            };

        DealsParser parser =
            result -> {
                throw new AssertionError(
                    "parser must not run"
                );
            };

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort(
                events
            );

        CollectDealsUseCase useCase =
            new CollectDealsUseCase(
                runRepository,
                candidateRepository,
                queue,
                collector,
                parser,
                transactionPort,
                CLOCK,
                5
            );

        ProcessingRun result =
            useCase.execute(
                RUN_ID
            );

        assertSame(
            completedRun,
            result
        );

        assertEquals(
            List.of(
                "run-find"
            ),
            events
        );

        assertEquals(
            0,
            transactionPort.executions
        );

        assertTrue(
            candidateRepository.savedCandidates.isEmpty()
        );

        assertTrue(
            queue.submissions.isEmpty()
        );
    }

    @Test
    void shouldMarkRunFailedWhenCollectionFails() {

        List<String> events =
            new ArrayList<>();

        RecordingRunRepository runRepository =
            new RecordingRunRepository(
                events,
                pendingRun()
            );

        RecordingCandidateRepository candidateRepository =
            new RecordingCandidateRepository(
                events
            );

        RecordingQueue queue =
            new RecordingQueue(
                events
            );

        RuntimeException controlledFailure =
            new IllegalStateException(
                "controlled collection failure"
            );

        CollectionCollector collector =
            request -> {

                events.add(
                    "collect"
                );

                throw controlledFailure;
            };

        DealsParser parser =
            result -> {
                throw new AssertionError(
                    "parser must not run"
                );
            };

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort(
                events
            );

        CollectDealsUseCase useCase =
            new CollectDealsUseCase(
                runRepository,
                candidateRepository,
                queue,
                collector,
                parser,
                transactionPort,
                CLOCK,
                5
            );

        RuntimeException thrown =
            assertThrows(
                RuntimeException.class,
                () -> useCase.execute(
                    RUN_ID
                )
            );

        assertSame(
            controlledFailure,
            thrown
        );

        assertEquals(
            ProcessingRunStatus.FAILED,
            runRepository.current.status()
        );

        assertEquals(
            "COLLECT_DEALS_FAILED",
            runRepository.current.lastErrorCode()
        );

        assertEquals(
            "controlled collection failure",
            runRepository.current.lastErrorMessage()
        );

        assertEquals(
            List.of(
                "run-find",
                "transaction-begin",
                "run-running",
                "transaction-commit",
                "collect",
                "transaction-begin",
                "run-failed",
                "transaction-commit"
            ),
            events
        );

        assertTrue(
            candidateRepository.savedCandidates.isEmpty()
        );

        assertTrue(
            queue.submissions.isEmpty()
        );
    }

    @Test
    void shouldReenterRunAlreadyMarkedRunning() {

        List<String> events =
            new ArrayList<>();

        RecordingRunRepository runRepository =
            new RecordingRunRepository(
                events,
                runningRun()
            );

        RecordingCandidateRepository candidateRepository =
            new RecordingCandidateRepository(
                events
            );

        RecordingQueue queue =
            new RecordingQueue(
                events
            );

        CollectionCollector collector =
            request -> {

                events.add(
                    "collect"
                );

                return collectionResult();
            };

        DealsParser parser =
            result -> {

                events.add(
                    "parse"
                );

                return List.of(
                    parsedDeal(
                        "B087WLJH8Y"
                    )
                );
            };

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort(
                events
            );

        CollectDealsUseCase useCase =
            new CollectDealsUseCase(
                runRepository,
                candidateRepository,
                queue,
                collector,
                parser,
                transactionPort,
                CLOCK,
                5
            );

        ProcessingRun result =
            useCase.execute(
                RUN_ID
            );

        assertEquals(
            ProcessingRunStatus.COMPLETED,
            result.status()
        );

        /*
         * Não existe segunda chamada a markRunning.
         *
         * O estado RUNNING representa reentrada após execução
         * interrompida anteriormente.
         */
        assertEquals(
            List.of(
                "run-find",
                "collect",
                "parse",
                "transaction-begin",
                "candidate-save",
                "enqueue",
                "run-completed",
                "transaction-commit"
            ),
            events
        );

        assertEquals(
            1,
            transactionPort.executions
        );
    }

    private static ProcessingRun pendingRun() {

        return new ProcessingRun(
            RUN_ID,
            "collect-run-10",
            URI.create(
                "https://www.amazon.com.br/deals"
            ),
            ProcessingRunStatus.PENDING,
            NOW,
            null,
            null,
            null,
            null
        );
    }

    private static ProcessingRun runningRun() {

        return new ProcessingRun(
            RUN_ID,
            "collect-run-10",
            URI.create(
                "https://www.amazon.com.br/deals"
            ),
            ProcessingRunStatus.RUNNING,
            NOW.minusMinutes(
                5
            ),
            NOW.minusMinutes(
                4
            ),
            null,
            null,
            null
        );
    }

    private static ProcessingRun completedRun() {

        return new ProcessingRun(
            RUN_ID,
            "collect-run-10",
            URI.create(
                "https://www.amazon.com.br/deals"
            ),
            ProcessingRunStatus.COMPLETED,
            NOW.minusMinutes(
                5
            ),
            NOW.minusMinutes(
                4
            ),
            NOW.minusMinutes(
                1
            ),
            null,
            null
        );
    }

    private static CollectionResult collectionResult() {

        return new CollectionResult(
            "controlled-content",
            NOW,
            "https://www.amazon.com.br/deals"
        );
    }

    private static ParsedDeal parsedDeal(
        String asin
    ) {

        return new ParsedDeal(
            asin,
            "https://www.amazon.com.br/dp/"
                + asin,
            "Produto de teste",
            "https://example.com/image.jpg",
            new BigDecimal(
                "99.90"
            ),
            new BigDecimal(
                "129.90"
            ),
            new BigDecimal(
                "119.90"
            ),
            new BigDecimal(
                "42.00"
            ),
            4.6,
            58363L,
            NOW,
            "https://www.amazon.com.br/deals"
        );
    }

    private static final class RecordingRunRepository
        implements ProcessingRunRepositoryPort {

        private final List<String> events;

        private ProcessingRun current;

        private RecordingRunRepository(
            List<String> events,
            ProcessingRun current
        ) {

            this.events =
                events;

            this.current =
                current;
        }

        @Override
        public ProcessingRun save(
            ProcessingRun run
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProcessingRun> findById(
            long id
        ) {

            events.add(
                "run-find"
            );

            if (current.id() == id) {
                return Optional.of(
                    current
                );
            }

            return Optional.empty();
        }

        @Override
        public ProcessingRun markRunning(
            long id,
            OffsetDateTime startedAt
        ) {

            events.add(
                "run-running"
            );

            current =
                new ProcessingRun(
                    current.id(),
                    current.runKey(),
                    current.source(),
                    ProcessingRunStatus.RUNNING,
                    current.requestedAt(),
                    startedAt,
                    null,
                    null,
                    null
                );

            return current;
        }

        @Override
        public ProcessingRun markCompleted(
            long id,
            OffsetDateTime completedAt
        ) {

            events.add(
                "run-completed"
            );

            current =
                new ProcessingRun(
                    current.id(),
                    current.runKey(),
                    current.source(),
                    ProcessingRunStatus.COMPLETED,
                    current.requestedAt(),
                    current.startedAt(),
                    completedAt,
                    null,
                    null
                );

            return current;
        }

        @Override
        public ProcessingRun markFailed(
            long id,
            String errorCode,
            String errorMessage,
            OffsetDateTime failedAt
        ) {

            events.add(
                "run-failed"
            );

            current =
                new ProcessingRun(
                    current.id(),
                    current.runKey(),
                    current.source(),
                    ProcessingRunStatus.FAILED,
                    current.requestedAt(),
                    current.startedAt(),
                    failedAt,
                    errorCode,
                    errorMessage
                );

            return current;
        }
    }

    private static final class RecordingCandidateRepository
        implements DealCandidateRepositoryPort {

        private final List<String> events;

        private final List<DealCandidate>
            savedCandidates =
            new ArrayList<>();

        private long nextId =
            100L;

        private RecordingCandidateRepository(
            List<String> events
        ) {

            this.events =
                events;
        }

        @Override
        public DealCandidate save(
            DealCandidate candidate
        ) {

            events.add(
                "candidate-save"
            );

            DealCandidate persisted =
                new DealCandidate(
                    nextId++,
                    candidate.processingRunId(),
                    candidate.parsedDeal()
                );

            savedCandidates.add(
                persisted
            );

            return persisted;
        }

        @Override
        public Optional<DealCandidate> findById(
            long id
        ) {

            return savedCandidates.stream()
                .filter(
                    candidate ->
                        candidate.id() == id
                )
                .findFirst();
        }
    }

    private static final class RecordingQueue
        implements ProcessingJobQueuePort {

        private final List<String> events;

        private final List<ProcessingJobSubmission>
            submissions =
            new ArrayList<>();

        private long nextId =
            1000L;

        private RecordingQueue(
            List<String> events
        ) {

            this.events =
                events;
        }

        @Override
        public ProcessingJob enqueue(
            ProcessingJobSubmission submission
        ) {

            events.add(
                "enqueue"
            );

            submissions.add(
                submission
            );

            return new ProcessingJob(
                nextId++,
                submission.type(),
                ProcessingJobStatus.PENDING,
                submission.processingRunId(),
                submission.dealCandidateId(),
                submission.offerSnapshotId(),
                submission.idempotencyKey(),
                0,
                submission.maxAttempts(),
                submission.availableAt(),
                null,
                null,
                null,
                null,
                null,
                submission.availableAt(),
                submission.availableAt(),
                null
            );
        }

        @Override
        public Optional<ProcessingJob> claimNext(
            String workerId,
            OffsetDateTime claimedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingJob markSucceeded(
            long jobId,
            String workerId,
            OffsetDateTime finishedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingJob scheduleRetry(
            long jobId,
            String workerId,
            ProcessingFailure failure,
            OffsetDateTime availableAt,
            OffsetDateTime failedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingJob markDead(
            long jobId,
            String workerId,
            ProcessingFailure failure,
            OffsetDateTime failedAt
        ) {

            throw new UnsupportedOperationException();
        }
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
