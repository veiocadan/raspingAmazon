package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingRun;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.orchestration.port.ProcessingRunRepositoryPort;
import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;
import com.raspingamazon.application.shared.port.TransactionPort;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenta transformar uma janela vencida de scheduling em trabalho
 * durável da orquestração existente.
 *
 * <p>Este caso de uso não executa coleta.</p>
 *
 * <p>Responsabilidades:</p>
 *
 * <ol>
 *     <li>adquirir atomicamente uma janela vencida;</li>
 *     <li>criar a ProcessingRun idempotente;</li>
 *     <li>criar o COLLECT_DEALS idempotente;</li>
 *     <li>confirmar a janela e avançar nextRunAt.</li>
 * </ol>
 *
 * <p>As quatro operações pertencem à mesma unidade transacional.
 * Portanto uma falha intermediária não pode deixar parcialmente
 * persistidos lease, run, job ou confirmação do schedule.</p>
 */
public final class ScheduleProcessingRunUseCase {

    private static final String SCHEDULED_RUN_KEY_PREFIX =
        "scheduled:";

    private static final String COLLECT_JOB_KEY_PREFIX =
        "collect:";

    private final ProcessingSchedulePort schedulePort;

    private final ProcessingRunRepositoryPort runRepository;

    private final ProcessingJobQueuePort jobQueue;

    private final TransactionPort transactionPort;

    private final Clock clock;

    private final Duration leaseDuration;

    private final int collectionMaxAttempts;

    public ScheduleProcessingRunUseCase(
        ProcessingSchedulePort schedulePort,
        ProcessingRunRepositoryPort runRepository,
        ProcessingJobQueuePort jobQueue,
        TransactionPort transactionPort,
        Clock clock,
        Duration leaseDuration,
        int collectionMaxAttempts
    ) {

        this.schedulePort =
            Objects.requireNonNull(
                schedulePort,
                "schedulePort must not be null"
            );

        this.runRepository =
            Objects.requireNonNull(
                runRepository,
                "runRepository must not be null"
            );

        this.jobQueue =
            Objects.requireNonNull(
                jobQueue,
                "jobQueue must not be null"
            );

        this.transactionPort =
            Objects.requireNonNull(
                transactionPort,
                "transactionPort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.leaseDuration =
            requirePositiveDuration(
                leaseDuration,
                "leaseDuration"
            );

        if (collectionMaxAttempts <= 0) {
            throw new IllegalArgumentException(
                "collectionMaxAttempts must be positive"
            );
        }

        this.collectionMaxAttempts =
            collectionMaxAttempts;
    }

    /**
     * Tenta criar o trabalho correspondente à janela atualmente
     * vencida do schedule.
     *
     * @param scheduleKey agendamento a verificar
     * @param schedulerInstanceId instância operacional que disputa
     *                            o lease
     * @return execução criada, ou vazio quando não há janela elegível
     */
    public Optional<ScheduledProcessingRun> execute(
        String scheduleKey,
        String schedulerInstanceId
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        String validatedSchedulerInstanceId =
            requireText(
                schedulerInstanceId,
                "schedulerInstanceId must not be blank"
            );

        OffsetDateTime now =
            OffsetDateTime.now(
                clock
            );

        return transactionPort.execute(
            () -> scheduleInsideTransaction(
                validatedScheduleKey,
                validatedSchedulerInstanceId,
                now
            )
        );
    }

    private Optional<ScheduledProcessingRun>
    scheduleInsideTransaction(
        String scheduleKey,
        String schedulerInstanceId,
        OffsetDateTime now
    ) {

        Optional<ProcessingScheduleLease> acquired =
            schedulePort.tryAcquireDue(
                scheduleKey,
                schedulerInstanceId,
                now,
                leaseDuration
            );

        if (acquired.isEmpty()) {
            return Optional.empty();
        }

        ProcessingScheduleLease lease =
            acquired.orElseThrow();

        ProcessingRun run =
            createProcessingRun(
                lease
            );

        ProcessingJob job =
            createCollectJob(
                run,
                lease
            );

        OffsetDateTime nextRunAt =
            calculateNextRunAt(
                lease,
                now
            );

        schedulePort.confirmScheduled(
            lease.scheduleKey(),
            lease.leaseOwner(),
            lease.scheduledFor(),
            requirePersistedRunId(
                run
            ),
            nextRunAt,
            now
        );

        return Optional.of(
            new ScheduledProcessingRun(
                lease.scheduleKey(),
                lease.scheduledFor(),
                requirePersistedRunId(
                    run
                ),
                requirePersistedJobId(
                    job
                ),
                nextRunAt
            )
        );
    }

    private ProcessingRun createProcessingRun(
        ProcessingScheduleLease lease
    ) {

        ProcessingRun requested =
            new ProcessingRun(
                null,
                scheduledRunKey(
                    lease
                ),
                lease.source(),
                ProcessingRunStatus.PENDING,
                lease.scheduledFor(),
                null,
                null,
                null,
                null
            );

        return Objects.requireNonNull(
            runRepository.save(
                requested
            ),
            "runRepository must not return null"
        );
    }

    private ProcessingJob createCollectJob(
        ProcessingRun run,
        ProcessingScheduleLease lease
    ) {

        long runId =
            requirePersistedRunId(
                run
            );

        ProcessingJobSubmission submission =
            ProcessingJobSubmission.collectDeals(
                runId,
                collectJobKey(
                    runId
                ),
                collectionMaxAttempts,
                lease.scheduledFor()
            );

        return Objects.requireNonNull(
            jobQueue.enqueue(
                submission
            ),
            "jobQueue must not return null"
        );
    }

    /**
     * Impede catch-up ilimitado.
     *
     * <p>Se a próxima janela natural ainda estiver no futuro, ela é
     * preservada.</p>
     *
     * <p>Se já tiver sido ultrapassada durante downtime, o scheduler
     * avança a partir de "now", descartando a rajada de janelas
     * intermediárias perdidas.</p>
     */
    private OffsetDateTime calculateNextRunAt(
        ProcessingScheduleLease lease,
        OffsetDateTime now
    ) {

        OffsetDateTime naturalNext =
            addDuration(
                lease.scheduledFor(),
                lease.interval(),
                "scheduledFor + interval cannot be represented"
            );

        if (naturalNext.isAfter(
            now
        )) {

            return naturalNext;
        }

        return addDuration(
            now,
            lease.interval(),
            "now + interval cannot be represented"
        );
    }

    /**
     * A identidade usa Instant para que offsets diferentes que
     * representam o mesmo instante produzam a mesma runKey.
     */
    private String scheduledRunKey(
        ProcessingScheduleLease lease
    ) {

        return SCHEDULED_RUN_KEY_PREFIX
            + lease.scheduleKey()
            + ":"
            + lease.scheduledFor()
            .toInstant();
    }

    private String collectJobKey(
        long processingRunId
    ) {

        return COLLECT_JOB_KEY_PREFIX
            + processingRunId;
    }

    private long requirePersistedRunId(
        ProcessingRun run
    ) {

        if (run.id() == null
            || run.id() <= 0) {

            throw new IllegalStateException(
                "runRepository must return a persisted ProcessingRun"
            );
        }

        return run.id();
    }

    private long requirePersistedJobId(
        ProcessingJob job
    ) {

        if (job.id() <= 0) {
            throw new IllegalStateException(
                "jobQueue must return a persisted ProcessingJob"
            );
        }

        return job.id();
    }

    private static Duration requirePositiveDuration(
        Duration duration,
        String name
    ) {

        Objects.requireNonNull(
            duration,
            name + " must not be null"
        );

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return duration;
    }

    private static OffsetDateTime addDuration(
        OffsetDateTime instant,
        Duration duration,
        String message
    ) {

        try {
            return instant.plus(
                duration
            );

        } catch (DateTimeException
                 | ArithmeticException exception) {

            throw new IllegalArgumentException(
                message,
                exception
            );
        }
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
