package com.raspingamazon.application.momentum;

import com.raspingamazon.domain.history.SnapshotEvolution;
import com.raspingamazon.domain.momentum.MomentumAudit;
import com.raspingamazon.domain.momentum.MomentumResult;
import com.raspingamazon.domain.momentum.MomentumUnavailableReason;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado da orquestração de momentum na camada de aplicação.
 *
 * <p>Este objeto mantém juntas duas informações relacionadas:</p>
 *
 * <ul>
 *     <li>o resultado do algoritmo de momentum;</li>
 *     <li>a evolução histórica utilizada para produzi-lo,
 *         quando existir snapshot anterior.</li>
 * </ul>
 *
 * <p>Ele também realiza a adaptação entre dois contratos diferentes:</p>
 *
 * <ul>
 *     <li>DealEvaluation armazena momentum/version somente quando
 *         existe valor disponível;</li>
 *     <li>MomentumAudit preserva também tentativas indisponíveis.</li>
 * </ul>
 */
public final class MomentumCalculation {

    private final long currentOfferSnapshotId;

    private final MomentumResult result;

    private final SnapshotEvolution evolution;

    private MomentumCalculation(
        long currentOfferSnapshotId,
        MomentumResult result,
        SnapshotEvolution evolution
    ) {

        if (currentOfferSnapshotId <= 0) {
            throw new IllegalArgumentException(
                "MomentumCalculation currentOfferSnapshotId must be positive"
            );
        }

        this.result =
            Objects.requireNonNull(
                result,
                "MomentumCalculation result must not be null"
            );

        if (evolution == null) {

            if (result.isAvailable()) {
                throw new IllegalArgumentException(
                    "Available momentum requires SnapshotEvolution"
                );
            }

            if (result.unavailableReason()
                != MomentumUnavailableReason.NO_PREVIOUS_SNAPSHOT) {

                throw new IllegalArgumentException(
                    "Momentum without SnapshotEvolution must represent NO_PREVIOUS_SNAPSHOT"
                );
            }

        } else {

            if (evolution.currentSnapshotId()
                != currentOfferSnapshotId) {

                throw new IllegalArgumentException(
                    "MomentumCalculation current snapshot must match SnapshotEvolution"
                );
            }

            if (result.unavailableReason()
                == MomentumUnavailableReason.NO_PREVIOUS_SNAPSHOT) {

                throw new IllegalArgumentException(
                    "Momentum with SnapshotEvolution cannot represent NO_PREVIOUS_SNAPSHOT"
                );
            }
        }

        this.currentOfferSnapshotId =
            currentOfferSnapshotId;

        this.evolution =
            evolution;
    }

    /**
     * Cria um cálculo baseado em duas observações históricas.
     */
    public static MomentumCalculation fromEvolution(
        SnapshotEvolution evolution,
        MomentumResult result
    ) {

        Objects.requireNonNull(
            evolution,
            "evolution must not be null"
        );

        return new MomentumCalculation(
            evolution.currentSnapshotId(),
            result,
            evolution
        );
    }

    /**
     * Cria o resultado correspondente à primeira observação
     * histórica conhecida de uma oferta.
     */
    public static MomentumCalculation withoutPreviousSnapshot(
        long currentOfferSnapshotId,
        MomentumResult result
    ) {

        return new MomentumCalculation(
            currentOfferSnapshotId,
            result,
            null
        );
    }

    public long currentOfferSnapshotId() {

        return currentOfferSnapshotId;
    }

    public MomentumResult result() {

        return result;
    }

    public SnapshotEvolution evolution() {

        return evolution;
    }

    /**
     * Valor que deve ser gravado em DealEvaluation.
     *
     * <p>DealEvaluation mantém o contrato histórico segundo o qual
     * momentum e momentumVersion aparecem juntos.</p>
     *
     * <p>Por isso, resultado indisponível é convertido para null.</p>
     */
    public BigDecimal dealEvaluationMomentum() {

        return result.isAvailable()
            ? result.value()
            : null;
    }

    /**
     * Versão que deve ser gravada em DealEvaluation.
     *
     * <p>Quando o resultado é indisponível, DealEvaluation continua
     * usando null. A versão da tentativa permanece preservada em
     * MomentumAudit.</p>
     */
    public String dealEvaluationMomentumVersion() {

        return result.isAvailable()
            ? result.version()
            : null;
    }

    /**
     * Constrói a trilha auditável depois que DealEvaluation recebe
     * sua identidade persistente.
     */
    public MomentumAudit toAudit(
        long dealEvaluationId
    ) {

        if (evolution == null) {

            return MomentumAudit.withoutPreviousSnapshot(
                dealEvaluationId,
                currentOfferSnapshotId,
                result
            );
        }

        return MomentumAudit.fromEvolution(
            dealEvaluationId,
            evolution,
            result
        );
    }
}
