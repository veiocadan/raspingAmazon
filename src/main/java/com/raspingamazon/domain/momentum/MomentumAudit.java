package com.raspingamazon.domain.momentum;

import com.raspingamazon.domain.history.SnapshotEvolution;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa a trilha auditável de uma tentativa de cálculo
 * de momentum.
 *
 * <p>O objeto preserva:</p>
 *
 * <ul>
 *     <li>a DealEvaluation à qual o cálculo pertence;</li>
 *     <li>o snapshot atual da avaliação;</li>
 *     <li>o resultado versionado do algoritmo;</li>
 *     <li>a evolução histórica utilizada, quando existir.</li>
 * </ul>
 *
 * <p>O snapshot atual é mantido neste objeto mesmo não sendo
 * redundantly persistido na tabela de auditoria. Ele permite ao
 * repository validar que a evolução pertence realmente ao snapshot
 * associado à DealEvaluation.</p>
 */
public final class MomentumAudit {

    private final Long id;

    private final long dealEvaluationId;

    private final long currentOfferSnapshotId;

    private final MomentumResult result;

    private final SnapshotEvolution evolution;

    private MomentumAudit(
        Long id,
        long dealEvaluationId,
        long currentOfferSnapshotId,
        MomentumResult result,
        SnapshotEvolution evolution
    ) {

        if (id != null && id <= 0) {
            throw new IllegalArgumentException(
                "MomentumAudit id must be positive when present"
            );
        }

        if (dealEvaluationId <= 0) {
            throw new IllegalArgumentException(
                "MomentumAudit dealEvaluationId must be positive"
            );
        }

        if (currentOfferSnapshotId <= 0) {
            throw new IllegalArgumentException(
                "MomentumAudit currentOfferSnapshotId must be positive"
            );
        }

        this.result =
            Objects.requireNonNull(
                result,
                "MomentumAudit result must not be null"
            );

        /*
         * Quando existe evolução, o snapshot atual da evolução deve
         * ser exatamente o snapshot associado à avaliação auditada.
         */
        if (evolution != null
            && evolution.currentSnapshotId()
            != currentOfferSnapshotId) {

            throw new IllegalArgumentException(
                "MomentumAudit current snapshot must match SnapshotEvolution"
            );
        }

        /*
         * Um resultado sem evolução representa especificamente o
         * cenário em que ainda não existe snapshot anterior.
         */
        if (evolution == null) {

            if (result.isAvailable()) {
                throw new IllegalArgumentException(
                    "Available momentum requires SnapshotEvolution"
                );
            }

            if (result.unavailableReason()
                != MomentumUnavailableReason.NO_PREVIOUS_SNAPSHOT) {

                throw new IllegalArgumentException(
                    "Momentum without SnapshotEvolution must be unavailable because no previous snapshot exists"
                );
            }
        }

        /*
         * Se existe evolução, não é coerente afirmar que não existe
         * snapshot anterior.
         */
        if (evolution != null
            && result.unavailableReason()
            == MomentumUnavailableReason.NO_PREVIOUS_SNAPSHOT) {

            throw new IllegalArgumentException(
                "Momentum with SnapshotEvolution cannot use NO_PREVIOUS_SNAPSHOT"
            );
        }

        this.id = id;
        this.dealEvaluationId =
            dealEvaluationId;
        this.currentOfferSnapshotId =
            currentOfferSnapshotId;
        this.evolution =
            evolution;
    }

    /**
     * Cria uma auditoria baseada em comparação histórica.
     *
     * <p>É utilizada tanto para resultado disponível quanto para
     * resultado indisponível quando existe snapshot anterior.</p>
     */
    public static MomentumAudit fromEvolution(
        long dealEvaluationId,
        SnapshotEvolution evolution,
        MomentumResult result
    ) {

        Objects.requireNonNull(
            evolution,
            "evolution must not be null"
        );

        return new MomentumAudit(
            null,
            dealEvaluationId,
            evolution.currentSnapshotId(),
            result,
            evolution
        );
    }

    /**
     * Cria a auditoria da primeira observação conhecida.
     *
     * <p>Neste cenário não existe SnapshotEvolution porque não existe
     * snapshot anterior para comparação.</p>
     */
    public static MomentumAudit withoutPreviousSnapshot(
        long dealEvaluationId,
        long currentOfferSnapshotId,
        MomentumResult result
    ) {

        return new MomentumAudit(
            null,
            dealEvaluationId,
            currentOfferSnapshotId,
            result,
            null
        );
    }

    /**
     * Retorna uma cópia com a identidade persistente atribuída
     * pela infraestrutura.
     */
    public MomentumAudit withId(
        long persistedId
    ) {

        return new MomentumAudit(
            persistedId,
            dealEvaluationId,
            currentOfferSnapshotId,
            result,
            evolution
        );
    }

    public Long id() {

        return id;
    }

    public long dealEvaluationId() {

        return dealEvaluationId;
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

    public Long previousOfferSnapshotId() {

        return evolution == null
            ? null
            : evolution.previousSnapshotId();
    }

    public Long elapsedSeconds() {

        return evolution == null
            ? null
            : evolution.elapsedSeconds();
    }

    public BigDecimal soldPercentageDelta() {

        return evolution == null
            ? null
            : evolution.soldPercentageDelta();
    }

    public BigDecimal currentPriceDelta() {

        return evolution == null
            ? null
            : evolution.currentPriceDelta();
    }

    public BigDecimal currentPriceDeltaPercentage() {

        return evolution == null
            ? null
            : evolution.currentPriceDeltaPercentage();
    }

    public BigDecimal cashDiscountDelta() {

        return evolution == null
            ? null
            : evolution.cashDiscountDelta();
    }

    public BigDecimal momentum() {

        return result.value();
    }

    public String calculationVersion() {

        return result.version();
    }

    public MomentumUnavailableReason unavailableReason() {

        return result.unavailableReason();
    }

    public boolean isAvailable() {

        return result.isAvailable();
    }
}
