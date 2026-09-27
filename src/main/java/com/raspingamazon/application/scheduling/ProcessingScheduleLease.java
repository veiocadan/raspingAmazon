package com.raspingamazon.application.scheduling;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa o direito temporário adquirido por uma instância
 * para processar uma janela lógica de agendamento.
 *
 * <p>A aquisição concreta poderá ser implementada com PostgreSQL,
 * mas esse detalhe não faz parte do contrato da aplicação.</p>
 */
public record ProcessingScheduleLease(

    String scheduleKey,

    URI source,

    Duration interval,

    /**
     * Janela lógica que deve originar a ProcessingRun.
     */
    OffsetDateTime scheduledFor,

    /**
     * Instância proprietária do lease.
     */
    String leaseOwner,

    /**
     * Instante após o qual outra instância poderá recuperar
     * a janela caso ela não tenha sido confirmada.
     */
    OffsetDateTime leaseExpiresAt
) {

    public ProcessingScheduleLease {

        scheduleKey =
            requireText(
                scheduleKey,
                "ProcessingScheduleLease scheduleKey must not be blank"
            );

        Objects.requireNonNull(
            source,
            "ProcessingScheduleLease source must not be null"
        );

        if (!source.isAbsolute()) {
            throw new IllegalArgumentException(
                "ProcessingScheduleLease source must be absolute"
            );
        }

        Objects.requireNonNull(
            interval,
            "ProcessingScheduleLease interval must not be null"
        );

        if (interval.isZero()
            || interval.isNegative()) {

            throw new IllegalArgumentException(
                "ProcessingScheduleLease interval must be positive"
            );
        }

        Objects.requireNonNull(
            scheduledFor,
            "ProcessingScheduleLease scheduledFor must not be null"
        );

        leaseOwner =
            requireText(
                leaseOwner,
                "ProcessingScheduleLease leaseOwner must not be blank"
            );

        Objects.requireNonNull(
            leaseExpiresAt,
            "ProcessingScheduleLease leaseExpiresAt must not be null"
        );
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
