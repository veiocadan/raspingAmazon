package com.raspingamazon.domain.momentum;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado versionado do cálculo de momentum.
 *
 * <p>O resultado pode assumir dois estados:</p>
 *
 * <ul>
 *     <li>disponível: possui valor e não possui motivo de
 *         indisponibilidade;</li>
 *     <li>indisponível: não possui valor e preserva explicitamente
 *         o motivo.</li>
 * </ul>
 *
 * <p>A versão é preservada inclusive quando o resultado é
 * indisponível. Isso permite registrar qual algoritmo tentou
 * produzir o indicador.</p>
 *
 * <p>Na integração com DealEvaluation, um resultado indisponível
 * continuará sendo representado por momentum=null e
 * momentumVersion=null, conforme o contrato atual da entidade.
 * A informação detalhada poderá ser preservada pela trilha
 * auditável da FASE 11-E.</p>
 */
public final class MomentumResult {

    private final String version;

    private final BigDecimal value;

    private final MomentumUnavailableReason unavailableReason;

    private MomentumResult(
        String version,
        BigDecimal value,
        MomentumUnavailableReason unavailableReason
    ) {

        this.version =
            requireText(
                version,
                "MomentumResult version must not be blank"
            );

        /*
         * Os dois estados válidos são:
         *
         * AVAILABLE:
         * value != null
         * unavailableReason == null
         *
         * UNAVAILABLE:
         * value == null
         * unavailableReason != null
         */
        if (value == null
            && unavailableReason == null) {

            throw new IllegalArgumentException(
                "Unavailable MomentumResult must have unavailableReason"
            );
        }

        if (value != null
            && unavailableReason != null) {

            throw new IllegalArgumentException(
                "Available MomentumResult must not have unavailableReason"
            );
        }

        this.value = value;
        this.unavailableReason =
            unavailableReason;
    }

    /**
     * Cria um resultado disponível.
     */
    public static MomentumResult available(
        String version,
        BigDecimal value
    ) {

        Objects.requireNonNull(
            value,
            "MomentumResult value must not be null"
        );

        return new MomentumResult(
            version,
            value,
            null
        );
    }

    /**
     * Cria um resultado indisponível.
     */
    public static MomentumResult unavailable(
        String version,
        MomentumUnavailableReason reason
    ) {

        Objects.requireNonNull(
            reason,
            "MomentumResult unavailableReason must not be null"
        );

        return new MomentumResult(
            version,
            null,
            reason
        );
    }

    /**
     * Retorna true quando o algoritmo conseguiu produzir momentum.
     */
    public boolean isAvailable() {

        return value != null;
    }

    public String version() {

        return version;
    }

    public BigDecimal value() {

        return value;
    }

    public MomentumUnavailableReason unavailableReason() {

        return unavailableReason;
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
