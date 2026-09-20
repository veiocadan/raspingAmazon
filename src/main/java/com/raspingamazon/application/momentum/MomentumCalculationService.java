package com.raspingamazon.application.momentum;

import com.raspingamazon.application.history.OfferHistoryQueryPort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.history.HistoricalOfferObservation;
import com.raspingamazon.domain.history.SnapshotEvolution;
import com.raspingamazon.domain.history.SnapshotEvolutionCalculator;
import com.raspingamazon.domain.momentum.MomentumEngine;
import com.raspingamazon.domain.momentum.MomentumResult;
import com.raspingamazon.domain.momentum.MomentumUnavailableReason;

import java.util.Objects;
import java.util.Optional;

/**
 * Serviço de aplicação responsável por orquestrar o cálculo
 * histórico de momentum para um OfferSnapshot já persistido.
 *
 * <p>Responsabilidades:</p>
 *
 * <ol>
 *     <li>localizar a observação imediatamente anterior;</li>
 *     <li>construir a representação histórica do snapshot atual;</li>
 *     <li>calcular a evolução entre as duas observações;</li>
 *     <li>executar o algoritmo versionado de momentum.</li>
 * </ol>
 *
 * <p>Este serviço não persiste DealEvaluation ou MomentumAudit.</p>
 *
 * <p>Ele também não altera elegibilidade, filtros comerciais
 * ou score.</p>
 */
public final class MomentumCalculationService {

    private final OfferHistoryQueryPort
        offerHistoryQueryPort;

    private final SnapshotEvolutionCalculator
        snapshotEvolutionCalculator;

    private final MomentumEngine
        momentumEngine;

    public MomentumCalculationService(
        OfferHistoryQueryPort offerHistoryQueryPort,
        SnapshotEvolutionCalculator snapshotEvolutionCalculator,
        MomentumEngine momentumEngine
    ) {

        this.offerHistoryQueryPort =
            Objects.requireNonNull(
                offerHistoryQueryPort,
                "offerHistoryQueryPort must not be null"
            );

        this.snapshotEvolutionCalculator =
            Objects.requireNonNull(
                snapshotEvolutionCalculator,
                "snapshotEvolutionCalculator must not be null"
            );

        this.momentumEngine =
            Objects.requireNonNull(
                momentumEngine,
                "momentumEngine must not be null"
            );
    }

    /**
     * Calcula o contexto histórico do snapshot atual.
     *
     * <p>O snapshot precisa já possuir identidade persistente porque
     * essa identidade será utilizada tanto na evolução quanto na
     * futura auditoria.</p>
     */
    public MomentumCalculation calculate(
        OfferSnapshot currentSnapshot
    ) {

        Objects.requireNonNull(
            currentSnapshot,
            "currentSnapshot must not be null"
        );

        Long currentSnapshotId =
            currentSnapshot.id();

        if (currentSnapshotId == null
            || currentSnapshotId <= 0) {

            throw new IllegalArgumentException(
                "Momentum requires a persisted OfferSnapshot"
            );
        }

        Optional<HistoricalOfferObservation> previous =
            offerHistoryQueryPort.findPreviousByAsin(
                currentSnapshot.product().asin(),
                currentSnapshot.collectedAt()
            );

        /*
         * Primeira observação conhecida.
         *
         * Não existe SnapshotEvolution porque não há dois pontos
         * temporais para comparar.
         */
        if (previous.isEmpty()) {

            MomentumResult result =
                MomentumResult.unavailable(
                    MomentumEngine.VERSION,
                    MomentumUnavailableReason
                        .NO_PREVIOUS_SNAPSHOT
                );

            return MomentumCalculation
                .withoutPreviousSnapshot(
                    currentSnapshotId,
                    result
                );
        }

        HistoricalOfferObservation currentObservation =
            toHistoricalObservation(
                currentSnapshot
            );

        SnapshotEvolution evolution =
            snapshotEvolutionCalculator.calculate(
                previous.get(),
                currentObservation
            );

        MomentumResult result =
            momentumEngine.calculate(
                evolution
            );

        return MomentumCalculation.fromEvolution(
            evolution,
            result
        );
    }

    /**
     * Converte o snapshot atual para a mesma projeção histórica
     * utilizada pelo cálculo de evolução.
     *
     * <p>Não consultamos novamente o snapshot atual no banco porque
     * ele já está disponível no fluxo, com todas as condições
     * comerciais que acabaram de ser persistidas.</p>
     */
    private HistoricalOfferObservation toHistoricalObservation(
        OfferSnapshot snapshot
    ) {

        return new HistoricalOfferObservation(
            snapshot.id(),
            snapshot.product().asin(),
            snapshot.collectedAt(),
            snapshot.currentPrice(),
            snapshot.soldPercentage(),
            snapshot.source(),
            snapshot.paymentConditions()
        );
    }
}
