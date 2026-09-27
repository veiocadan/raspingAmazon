package com.raspingamazon.application.operation.observability.alert;

import java.util.Objects;

/**
 * Alerta operacional derivado dos fatos persistidos.
 *
 * <p>O modelo contém evidência estruturada em vez de uma mensagem
 * textual pré-formatada. A apresentação poderá produzir texto,
 * JSON ou outra representação sem obrigar a query PostgreSQL a
 * conhecer detalhes de interface.</p>
 *
 * @param type tipo do alerta
 * @param runId ProcessingRun relacionada, quando aplicável
 * @param integration integração relacionada, quando aplicável
 * @param observedCount valor observado que disparou o alerta
 * @param referenceCount valor de referência, quando aplicável
 */
public record OperationalAlert(
    OperationalAlertType type,
    Long runId,
    String integration,
    long observedCount,
    Long referenceCount
) {

    public OperationalAlert {

        Objects.requireNonNull(
            type,
            "type must not be null"
        );

        if (runId != null
            && runId <= 0L) {

            throw new IllegalArgumentException(
                "runId must be positive when present"
            );
        }

        if (integration != null
            && integration.isBlank()) {

            throw new IllegalArgumentException(
                "integration must not be blank when present"
            );
        }

        if (observedCount < 0L) {

            throw new IllegalArgumentException(
                "observedCount must not be negative"
            );
        }

        if (referenceCount != null
            && referenceCount < 0L) {

            throw new IllegalArgumentException(
                "referenceCount must not be negative when present"
            );
        }

        validateEvidence(
            type,
            runId,
            integration,
            observedCount,
            referenceCount
        );
    }

    private static void validateEvidence(
        OperationalAlertType type,
        Long runId,
        String integration,
        long observedCount,
        Long referenceCount
    ) {

        switch (type) {

            case REPEATED_EXTERNAL_FAILURES -> {

                if (integration == null) {

                    throw new IllegalArgumentException(
                        "REPEATED_EXTERNAL_FAILURES "
                            + "requires integration"
                    );
                }

                if (observedCount < 1L) {

                    throw new IllegalArgumentException(
                        "REPEATED_EXTERNAL_FAILURES "
                            + "requires at least one observed failure"
                    );
                }

                if (referenceCount != null) {

                    throw new IllegalArgumentException(
                        "REPEATED_EXTERNAL_FAILURES "
                            + "must not define referenceCount"
                    );
                }
            }

            case DEAD_JOBS -> {

                requireRunId(
                    runId,
                    type
                );

                if (observedCount < 1L) {

                    throw new IllegalArgumentException(
                        "DEAD_JOBS requires at least one dead job"
                    );
                }

                if (referenceCount != null) {

                    throw new IllegalArgumentException(
                        "DEAD_JOBS must not define referenceCount"
                    );
                }
            }

            case ZERO_CANDIDATES -> {

                requireRunId(
                    runId,
                    type
                );

                if (observedCount != 0L) {

                    throw new IllegalArgumentException(
                        "ZERO_CANDIDATES requires observedCount equal to 0"
                    );
                }

                if (referenceCount != null) {

                    throw new IllegalArgumentException(
                        "ZERO_CANDIDATES must not define referenceCount"
                    );
                }
            }

            case SUSPICIOUS_COLLECTION_DROP -> {

                requireRunId(
                    runId,
                    type
                );

                if (referenceCount == null
                    || referenceCount <= 0L) {

                    throw new IllegalArgumentException(
                        "SUSPICIOUS_COLLECTION_DROP "
                            + "requires positive referenceCount"
                    );
                }

                if (observedCount >= referenceCount) {

                    throw new IllegalArgumentException(
                        "SUSPICIOUS_COLLECTION_DROP "
                            + "requires observedCount below referenceCount"
                    );
                }
            }
        }
    }

    private static void requireRunId(
        Long runId,
        OperationalAlertType type
    ) {

        if (runId == null) {

            throw new IllegalArgumentException(
                type + " requires runId"
            );
        }
    }
}
