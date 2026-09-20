package com.raspingamazon.domain.momentum;

import com.raspingamazon.domain.history.SnapshotEvolution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Motor determinístico do MOMENTUM_V1.
 *
 * <p>O MOMENTUM_V1 mede a velocidade de evolução do percentual
 * vendido em pontos percentuais por hora.</p>
 *
 * <p>Conceitualmente:</p>
 *
 * <pre>
 * momentum =
 *     soldPercentageDelta
 *     -------------------
 *        elapsedHours
 * </pre>
 *
 * <p>A implementação utiliza segundos diretamente:</p>
 *
 * <pre>
 * momentum =
 *     soldPercentageDelta * 3600
 *     --------------------------
 *          elapsedSeconds
 * </pre>
 *
 * <p>Essa forma evita perder precisão quando o intervalo não possui
 * uma quantidade inteira de horas.</p>
 *
 * <p>O resultado utiliza escala 4 e HALF_UP.</p>
 *
 * <p>Preço e desconto fazem parte da evolução histórica, mas não
 * participam do MOMENTUM_V1. Qualquer combinação futura desses
 * sinais exigirá nova versão do algoritmo.</p>
 */
public final class MomentumEngine {

    public static final String VERSION =
        "MOMENTUM_V1";

    private static final int MOMENTUM_SCALE = 4;

    private static final BigDecimal
        SECONDS_PER_HOUR =
        BigDecimal.valueOf(
            3600
        );

    /**
     * Calcula o momentum de uma evolução já validada.
     *
     * @param evolution evolução entre dois snapshots
     * @return resultado versionado
     */
    public MomentumResult calculate(
        SnapshotEvolution evolution
    ) {

        Objects.requireNonNull(
            evolution,
            "SnapshotEvolution must not be null"
        );

        /*
         * MOMENTUM_V1 depende exclusivamente da evolução
         * do percentual vendido.
         */
        if (!evolution.hasSoldPercentageDelta()) {

            return MomentumResult.unavailable(
                VERSION,
                MomentumUnavailableReason
                    .SOLD_PERCENTAGE_UNAVAILABLE
            );
        }

        long elapsedSeconds =
            evolution.elapsedSeconds();

        /*
         * SnapshotEvolution já protege esta invariável.
         *
         * A checagem permanece aqui como defesa de fronteira:
         * o motor nunca deve executar uma divisão por intervalo
         * não positivo.
         */
        if (elapsedSeconds <= 0) {

            throw new IllegalStateException(
                "Momentum requires a positive elapsed interval"
            );
        }

        BigDecimal numerator =
            evolution.soldPercentageDelta()
                .multiply(
                    SECONDS_PER_HOUR
                );

        BigDecimal momentum =
            numerator.divide(
                BigDecimal.valueOf(
                    elapsedSeconds
                ),
                MOMENTUM_SCALE,
                RoundingMode.HALF_UP
            );

        return MomentumResult.available(
            VERSION,
            momentum
        );
    }
}
