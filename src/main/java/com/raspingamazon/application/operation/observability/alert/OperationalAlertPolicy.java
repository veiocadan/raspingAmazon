package com.raspingamazon.application.operation.observability.alert;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

/**
 * Política explícita utilizada para detectar alertas operacionais.
 *
 * <p>Os limiares não ficam escondidos no SQL nem codificados dentro
 * de adapters PostgreSQL.</p>
 *
 * <p>A fonte arquitetural da FASE 16 exige alertas para falhas
 * recorrentes e mudanças suspeitas na coleta, mas não determina os
 * respectivos valores numéricos. Por isso esta política precisa ser
 * fornecida explicitamente pela composição.</p>
 *
 * @param repeatedExternalFailureThreshold quantidade mínima de falhas
 *                                         externas para considerar
 *                                         recorrência
 * @param repeatedExternalFailureWindow janela usada para contar essas
 *                                      falhas
 * @param suspiciousCollectionLookbackRuns quantidade de runs anteriores
 *                                         utilizadas como referência
 * @param suspiciousCollectionDropFraction fração mínima de queda para
 *                                         considerar o volume suspeito;
 *                                         deve estar entre zero e um
 * @param suspiciousCollectionMinimumBaselineCandidates referência mínima
 *                                                      para que uma queda
 *                                                      seja avaliada
 */
public record OperationalAlertPolicy(
    int repeatedExternalFailureThreshold,
    Duration repeatedExternalFailureWindow,
    int suspiciousCollectionLookbackRuns,
    BigDecimal suspiciousCollectionDropFraction,
    long suspiciousCollectionMinimumBaselineCandidates
) {

    private static final BigDecimal ZERO =
        BigDecimal.ZERO;

    private static final BigDecimal ONE =
        BigDecimal.ONE;

    public OperationalAlertPolicy {

        if (repeatedExternalFailureThreshold < 2) {

            throw new IllegalArgumentException(
                "repeatedExternalFailureThreshold must be at least 2"
            );
        }

        Objects.requireNonNull(
            repeatedExternalFailureWindow,
            "repeatedExternalFailureWindow must not be null"
        );

        if (repeatedExternalFailureWindow.isZero()
            || repeatedExternalFailureWindow.isNegative()) {

            throw new IllegalArgumentException(
                "repeatedExternalFailureWindow must be positive"
            );
        }

        if (suspiciousCollectionLookbackRuns < 1) {

            throw new IllegalArgumentException(
                "suspiciousCollectionLookbackRuns must be at least 1"
            );
        }

        Objects.requireNonNull(
            suspiciousCollectionDropFraction,
            "suspiciousCollectionDropFraction must not be null"
        );

        if (suspiciousCollectionDropFraction.compareTo(
            ZERO
        ) <= 0
            || suspiciousCollectionDropFraction.compareTo(
            ONE
        ) >= 0) {

            throw new IllegalArgumentException(
                "suspiciousCollectionDropFraction "
                    + "must be greater than 0 and less than 1"
            );
        }

        if (suspiciousCollectionMinimumBaselineCandidates < 1L) {

            throw new IllegalArgumentException(
                "suspiciousCollectionMinimumBaselineCandidates "
                    + "must be at least 1"
            );
        }
    }
}
