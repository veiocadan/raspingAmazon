package com.raspingamazon.infrastructure.config;

import com.raspingamazon.application.operation.observability.alert.OperationalAlertPolicy;
import com.raspingamazon.application.operation.observability.alert.port.OperationalAlertPolicyProvider;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega a política de alertas operacionais a partir do ambiente.
 *
 * <p>Nenhum limiar numérico possui default nesta classe. A fonte do
 * projeto define os tipos de alerta, mas não define seus valores
 * operacionais definitivos.</p>
 *
 * <p>As variáveis somente são lidas quando load() é chamado. Criar
 * este provider não torna configuração de alertas obrigatória para
 * outros comandos da interface operacional.</p>
 */
public final class EnvironmentOperationalAlertPolicyProvider
    implements OperationalAlertPolicyProvider {

    public static final String
        REPEATED_EXTERNAL_FAILURE_THRESHOLD =
        "ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD";

    public static final String
        REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS =
        "ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS";

    public static final String
        SUSPICIOUS_COLLECTION_LOOKBACK_RUNS =
        "ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS";

    public static final String
        SUSPICIOUS_COLLECTION_DROP_FRACTION =
        "ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION";

    public static final String
        SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES =
        "ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES";

    private final Map<String, String>
        environment;

    /**
     * Provider de produção baseado no ambiente do processo.
     */
    public EnvironmentOperationalAlertPolicyProvider() {

        /*
         * Não interpretamos valores no construtor.
         *
         * A leitura semântica acontece somente em load().
         */
        this(
            System.getenv()
        );
    }

    /**
     * Variante injetável para testes.
     *
     * <p>O mapa é copiado defensivamente.</p>
     */
    public EnvironmentOperationalAlertPolicyProvider(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        this.environment =
            Map.copyOf(
                environment
            );
    }

    @Override
    public OperationalAlertPolicy load() {

        int repeatedFailureThreshold =
            readInt(
                REPEATED_EXTERNAL_FAILURE_THRESHOLD
            );

        long repeatedFailureWindowSeconds =
            readLong(
                REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS
            );

        int suspiciousCollectionLookbackRuns =
            readInt(
                SUSPICIOUS_COLLECTION_LOOKBACK_RUNS
            );

        BigDecimal suspiciousCollectionDropFraction =
            readBigDecimal(
                SUSPICIOUS_COLLECTION_DROP_FRACTION
            );

        long suspiciousCollectionMinimumBaselineCandidates =
            readLong(
                SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES
            );

        try {

            return new OperationalAlertPolicy(
                repeatedFailureThreshold,
                Duration.ofSeconds(
                    repeatedFailureWindowSeconds
                ),
                suspiciousCollectionLookbackRuns,
                suspiciousCollectionDropFraction,
                suspiciousCollectionMinimumBaselineCandidates
            );

        } catch (RuntimeException exception) {

            throw new IllegalStateException(
                "Invalid operational alert policy configuration",
                exception
            );
        }
    }

    private int readInt(
        String variableName
    ) {

        String value =
            readRequired(
                variableName
            );

        try {

            return Integer.parseInt(
                value
            );

        } catch (NumberFormatException exception) {

            throw invalidNumber(
                variableName,
                value,
                "integer",
                exception
            );
        }
    }

    private long readLong(
        String variableName
    ) {

        String value =
            readRequired(
                variableName
            );

        try {

            return Long.parseLong(
                value
            );

        } catch (NumberFormatException exception) {

            throw invalidNumber(
                variableName,
                value,
                "long integer",
                exception
            );
        }
    }

    private BigDecimal readBigDecimal(
        String variableName
    ) {

        String value =
            readRequired(
                variableName
            );

        try {

            return new BigDecimal(
                value
            );

        } catch (NumberFormatException exception) {

            throw invalidNumber(
                variableName,
                value,
                "decimal",
                exception
            );
        }
    }

    private String readRequired(
        String variableName
    ) {

        String value =
            environment.get(
                variableName
            );

        if (value == null
            || value.isBlank()) {

            throw new IllegalStateException(
                "Required operational alert environment variable "
                    + "is missing: "
                    + variableName
            );
        }

        return value.trim();
    }

    private IllegalStateException invalidNumber(
        String variableName,
        String value,
        String expectedType,
        RuntimeException cause
    ) {

        return new IllegalStateException(
            "Invalid "
                + expectedType
                + " value for operational alert environment variable "
                + variableName
                + ": "
                + value,
            cause
        );
    }
}
