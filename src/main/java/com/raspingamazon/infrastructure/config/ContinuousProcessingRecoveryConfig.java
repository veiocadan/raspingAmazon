package com.raspingamazon.infrastructure.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuração técnica da recuperação executada antes do runtime
 * contínuo ser liberado.
 *
 * <p>Esta configuração não contém regra comercial nem frequência
 * funcional. Ela controla somente:</p>
 *
 * <ul>
 *     <li>expiração de leases técnicos;</li>
 *     <li>tamanho de lotes de recuperação;</li>
 *     <li>limite de reconciliação;</li>
 *     <li>máximo de tentativas do job PUBLICATION_DISPATCH.</li>
 * </ul>
 */
public record ContinuousProcessingRecoveryConfig(
    Duration processingJobLeaseDuration,
    int processingJobRecoveryBatchSize,
    Duration publicationOutboxLeaseDuration,
    int publicationDispatchMaxAttempts,
    int publicationDispatchReconciliationLimit
) {

    public ContinuousProcessingRecoveryConfig {

        processingJobLeaseDuration =
            requirePositiveDuration(
                processingJobLeaseDuration,
                "processingJobLeaseDuration"
            );

        processingJobRecoveryBatchSize =
            requirePositiveInt(
                processingJobRecoveryBatchSize,
                "processingJobRecoveryBatchSize"
            );

        publicationOutboxLeaseDuration =
            requirePositiveDuration(
                publicationOutboxLeaseDuration,
                "publicationOutboxLeaseDuration"
            );

        publicationDispatchMaxAttempts =
            requirePositiveInt(
                publicationDispatchMaxAttempts,
                "publicationDispatchMaxAttempts"
            );

        publicationDispatchReconciliationLimit =
            requirePositiveInt(
                publicationDispatchReconciliationLimit,
                "publicationDispatchReconciliationLimit"
            );
    }

    private static Duration requirePositiveDuration(
        Duration value,
        String name
    ) {

        Objects.requireNonNull(
            value,
            name + " must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return value;
    }

    private static int requirePositiveInt(
        int value,
        String name
    ) {

        if (value <= 0) {

            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return value;
    }
}
