package com.raspingamazon.application.operation.evaluation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Projeção operacional da trilha persistida de momentum.
 *
 * <p>Este objeto representa fatos históricos da auditoria e não
 * executa o algoritmo de momentum.</p>
 */
public record OperationalMomentumAudit(
    long auditId,
    String calculationVersion,
    String status,
    String unavailableReason,
    Long previousOfferSnapshotId,
    Long elapsedSeconds,
    BigDecimal soldPercentageDelta,
    BigDecimal currentPriceDelta,
    BigDecimal currentPriceDeltaPercentage,
    BigDecimal cashDiscountDelta,
    BigDecimal momentum,
    OffsetDateTime createdAt
) {

    public OperationalMomentumAudit {

        if (auditId <= 0L) {
            throw new IllegalArgumentException(
                "OperationalMomentumAudit auditId must be positive"
            );
        }

        calculationVersion =
            requireText(
                calculationVersion,
                "OperationalMomentumAudit "
                    + "calculationVersion must not be blank"
            );

        status =
            requireText(
                status,
                "OperationalMomentumAudit status must not be blank"
            );

        unavailableReason =
            optionalText(
                unavailableReason,
                "OperationalMomentumAudit "
                    + "unavailableReason must not be blank"
            );

        if (previousOfferSnapshotId != null
            && previousOfferSnapshotId <= 0L) {

            throw new IllegalArgumentException(
                "OperationalMomentumAudit "
                    + "previousOfferSnapshotId must be positive"
            );
        }

        if (elapsedSeconds != null
            && elapsedSeconds <= 0L) {

            throw new IllegalArgumentException(
                "OperationalMomentumAudit "
                    + "elapsedSeconds must be positive"
            );
        }

        Objects.requireNonNull(
            createdAt,
            "OperationalMomentumAudit createdAt must not be null"
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

    private static String optionalText(
        String value,
        String message
    ) {

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
