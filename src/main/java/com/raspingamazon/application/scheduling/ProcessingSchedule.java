package com.raspingamazon.application.scheduling;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa a configuração e o estado persistente de um agendamento
 * recorrente de processamento.
 *
 * <p>O agendamento não executa coleta, parsing, enrichment ou avaliação.
 * Ele apenas determina quando uma nova execução lógica pode ser
 * solicitada.</p>
 *
 * <p>O estado é deliberadamente independente de JDBC, PostgreSQL,
 * threads ou mecanismos concretos de temporização.</p>
 */
public record ProcessingSchedule(

    /**
     * Identidade lógica e estável do agendamento.
     */
    String scheduleKey,

    /**
     * Fonte solicitada para as ProcessingRun criadas pelo agendamento.
     */
    URI source,

    /**
     * Indica se novas execuções automáticas podem ser criadas.
     */
    boolean enabled,

    /**
     * Intervalo entre ciclos automáticos.
     */
    Duration interval,

    /**
     * Próximo instante em que o agendamento poderá ser adquirido.
     */
    OffsetDateTime nextRunAt,

    /**
     * Identidade da instância que possui o lease atual.
     *
     * <p>Null quando não existe lease ativo ou persistido.</p>
     */
    String leaseOwner,

    /**
     * Instante de expiração do lease atual.
     *
     * <p>Null quando não existe lease.</p>
     */
    OffsetDateTime leaseExpiresAt,

    /**
     * Janela lógica da última execução confirmada.
     */
    OffsetDateTime lastScheduledFor,

    /**
     * ProcessingRun produzida pela última janela confirmada.
     */
    Long lastProcessingRunId,

    OffsetDateTime createdAt,

    OffsetDateTime updatedAt
) {

    public ProcessingSchedule {

        scheduleKey =
            requireText(
                scheduleKey,
                "ProcessingSchedule scheduleKey must not be blank"
            );

        Objects.requireNonNull(
            source,
            "ProcessingSchedule source must not be null"
        );

        if (!source.isAbsolute()) {
            throw new IllegalArgumentException(
                "ProcessingSchedule source must be absolute"
            );
        }

        Objects.requireNonNull(
            interval,
            "ProcessingSchedule interval must not be null"
        );

        if (interval.isZero()
            || interval.isNegative()) {

            throw new IllegalArgumentException(
                "ProcessingSchedule interval must be positive"
            );
        }

        Objects.requireNonNull(
            nextRunAt,
            "ProcessingSchedule nextRunAt must not be null"
        );

        Objects.requireNonNull(
            createdAt,
            "ProcessingSchedule createdAt must not be null"
        );

        Objects.requireNonNull(
            updatedAt,
            "ProcessingSchedule updatedAt must not be null"
        );

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                "ProcessingSchedule updatedAt must not be before createdAt"
            );
        }

        validateLease(
            leaseOwner,
            leaseExpiresAt
        );

        validateLastExecution(
            lastScheduledFor,
            lastProcessingRunId
        );
    }

    /**
     * Informa se existe lease representado no estado persistente.
     *
     * <p>Este método não afirma que o lease ainda está temporalmente
     * válido. A expiração depende do instante avaliado pelo caso de uso
     * ou adapter.</p>
     */
    public boolean leased() {

        return leaseOwner != null;
    }

    /**
     * Informa se novas execuções automáticas estão pausadas.
     */
    public boolean paused() {

        return !enabled;
    }

    /**
     * Informa se existe uma execução agendada anteriormente confirmada.
     */
    public boolean hasLastExecution() {

        return lastProcessingRunId != null;
    }

    private static void validateLease(
        String leaseOwner,
        OffsetDateTime leaseExpiresAt
    ) {

        if (leaseOwner == null
            && leaseExpiresAt == null) {

            return;
        }

        if (leaseOwner == null
            || leaseExpiresAt == null) {

            throw new IllegalArgumentException(
                "ProcessingSchedule leaseOwner and leaseExpiresAt "
                    + "must both be present or both be null"
            );
        }

        requireText(
            leaseOwner,
            "ProcessingSchedule leaseOwner must not be blank"
        );
    }

    private static void validateLastExecution(
        OffsetDateTime lastScheduledFor,
        Long lastProcessingRunId
    ) {

        if (lastScheduledFor == null
            && lastProcessingRunId == null) {

            return;
        }

        if (lastScheduledFor == null
            || lastProcessingRunId == null) {

            throw new IllegalArgumentException(
                "ProcessingSchedule lastScheduledFor and "
                    + "lastProcessingRunId must both be present "
                    + "or both be null"
            );
        }

        if (lastProcessingRunId <= 0) {
            throw new IllegalArgumentException(
                "ProcessingSchedule lastProcessingRunId must be positive"
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
