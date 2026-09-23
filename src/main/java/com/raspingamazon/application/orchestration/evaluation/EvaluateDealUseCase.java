package com.raspingamazon.application.orchestration.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.orchestration.port.DealEvaluationLookupPort;
import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLoadPort;
import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLockPort;
import com.raspingamazon.domain.deal.OfferSnapshot;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso de uso da etapa EVALUATE_DEAL.
 *
 * <p>Essa etapa trabalha exclusivamente com dados já persistidos.
 * Nenhuma chamada externa à Amazon é permitida aqui.</p>
 *
 * <p>Responsabilidades:</p>
 *
 * <ol>
 *     <li>detectar avaliação já concluída;</li>
 *     <li>reconstruir o OfferSnapshot persistido;</li>
 *     <li>serializar avaliações concorrentes do mesmo snapshot;</li>
 *     <li>executar elegibilidade, filtros, score e momentum;</li>
 *     <li>persistir DealEvaluation e MomentumAudit.</li>
 * </ol>
 */
public final class EvaluateDealUseCase {

    private final DealEvaluationLookupPort
        evaluationLookup;

    private final OfferSnapshotEvaluationLoadPort
        snapshotLoadPort;

    private final OfferSnapshotEvaluationLockPort
        snapshotLockPort;

    private final DealEvaluationProcessingPort
        evaluationProcessingPort;

    private final TransactionPort
        transactionPort;

    private final Clock
        clock;

    public EvaluateDealUseCase(
        DealEvaluationLookupPort evaluationLookup,
        OfferSnapshotEvaluationLoadPort snapshotLoadPort,
        OfferSnapshotEvaluationLockPort snapshotLockPort,
        DealEvaluationProcessingPort evaluationProcessingPort,
        TransactionPort transactionPort,
        Clock clock
    ) {

        this.evaluationLookup =
            Objects.requireNonNull(
                evaluationLookup,
                "evaluationLookup must not be null"
            );

        this.snapshotLoadPort =
            Objects.requireNonNull(
                snapshotLoadPort,
                "snapshotLoadPort must not be null"
            );

        this.snapshotLockPort =
            Objects.requireNonNull(
                snapshotLockPort,
                "snapshotLockPort must not be null"
            );

        this.evaluationProcessingPort =
            Objects.requireNonNull(
                evaluationProcessingPort,
                "evaluationProcessingPort must not be null"
            );

        this.transactionPort =
            Objects.requireNonNull(
                transactionPort,
                "transactionPort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Executa a avaliação de um OfferSnapshot.
     *
     * <p>Quando já existir DealEvaluation persistida, a execução
     * termina imediatamente. Isso cobre o cenário em que o commit
     * ocorreu mas o worker caiu antes de marcar o ProcessingJob
     * como SUCCEEDED.</p>
     *
     * @param offerSnapshotId identidade persistente do snapshot
     */
    public void execute(
        long offerSnapshotId
    ) {

        if (offerSnapshotId <= 0) {
            throw new IllegalArgumentException(
                "offerSnapshotId must be positive"
            );
        }

        /*
         * Fast path idempotente.
         *
         * Evita reconstrução do agregado e abertura desnecessária
         * de transação quando a avaliação já existe.
         */
        if (evaluationLookup
            .findEvaluationIdByOfferSnapshotId(
                offerSnapshotId
            )
            .isPresent()) {

            return;
        }

        OfferSnapshot snapshot =
            snapshotLoadPort.findById(
                    offerSnapshotId
                )
                .orElseThrow(
                    () -> new IllegalArgumentException(
                        "OfferSnapshot not found: "
                            + offerSnapshotId
                    )
                );

        OffsetDateTime evaluatedAt =
            OffsetDateTime.now(
                clock
            );

        transactionPort.execute(
            () -> {

                /*
                 * A linha do snapshot é a unidade de serialização.
                 *
                 * Dois workers avaliando o mesmo snapshot podem passar
                 * simultaneamente pelo fast path acima.
                 *
                 * Aqui somente um prossegue por vez. O segundo aguarda
                 * o commit do primeiro e somente depois executa o
                 * segundo lookup.
                 */
                snapshotLockPort.lockById(
                    offerSnapshotId
                );

                /*
                 * Depois da aquisição do lock esta verificação fecha
                 * a corrida concorrente.
                 *
                 * A constraint UNIQUE da tabela deal_evaluation
                 * permanece como defesa adicional do schema.
                 */
                if (evaluationLookup
                    .findEvaluationIdByOfferSnapshotId(
                        offerSnapshotId
                    )
                    .isPresent()) {

                    return null;
                }

                evaluationProcessingPort
                    .evaluateAndPersist(
                        snapshot,
                        evaluatedAt
                    );

                return null;
            }
        );
    }
}
