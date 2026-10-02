package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;

import java.util.List;
import java.util.Objects;

/**
 * Resultado observável do dispatch automático das Publications
 * pertencentes a uma execução persistida de seleção.
 *
 * <p>selectedCandidateCount representa quantos candidatos a
 * SelectionRun classificou como SELECTED.</p>
 *
 * <p>cadenceUnavailableCount representa quantos desses candidatos
 * não foram processados porque não existia mais slot dentro da
 * janela operacional.</p>
 *
 * <p>interruptedByReservationRevalidation indica que o processamento
 * foi interrompido porque a outbox observou mudança concorrente de
 * quota/cadência durante a reserva definitiva.</p>
 */
public record PublicationSelectionDispatchResult(
    long selectionRunId,
    int selectedCandidateCount,
    int cadenceUnavailableCount,
    boolean interruptedByReservationRevalidation,
    List<Item> items
) {

    public PublicationSelectionDispatchResult {

        if (selectionRunId <= 0L) {

            throw new IllegalArgumentException(
                "selectionRunId must be positive"
            );
        }

        if (selectedCandidateCount < 0) {

            throw new IllegalArgumentException(
                "selectedCandidateCount must not be negative"
            );
        }

        if (cadenceUnavailableCount < 0
            || cadenceUnavailableCount > selectedCandidateCount) {

            throw new IllegalArgumentException(
                "cadenceUnavailableCount must be between zero "
                    + "and selectedCandidateCount"
            );
        }

        Objects.requireNonNull(
            items,
            "items must not be null"
        );

        items =
            List.copyOf(
                items
            );

        for (Item item : items) {

            Objects.requireNonNull(
                item,
                "items must not contain null"
            );
        }

        if (items.size()
            + cadenceUnavailableCount
            > selectedCandidateCount) {

            throw new IllegalArgumentException(
                "attempted items plus cadence unavailable candidates "
                    + "must not exceed selectedCandidateCount"
            );
        }
    }

    /**
     * Construtor de compatibilidade com a semântica anterior,
     * onde todo candidato recebido era imediatamente tentado.
     */
    public PublicationSelectionDispatchResult(
        long selectionRunId,
        List<Item> items
    ) {

        this(
            selectionRunId,
            sizeOf(
                items
            ),
            0,
            false,
            items
        );
    }

    public int attemptedCount() {

        return items.size();
    }

    public int unprocessedSelectedCount() {

        return selectedCandidateCount
            - attemptedCount();
    }

    public long reservedCount() {

        return items.stream()
            .filter(
                Item::deliveryReserved
            )
            .count();
    }

    public long enqueuedCount() {

        return items.stream()
            .filter(
                item ->
                    item.enqueueResult()
                        .enqueued()
            )
            .count();
    }

    public long alreadyEnqueuedCount() {

        return items.stream()
            .filter(
                item ->
                    item.enqueueResult()
                        .alreadyEnqueued()
            )
            .count();
    }

    public long quotaExhaustedCount() {

        return items.stream()
            .filter(
                item ->
                    item.enqueueResult()
                        .quotaExhausted()
            )
            .count();
    }

    public long staleSelectionCount() {

        return items.stream()
            .filter(
                item ->
                    item.enqueueResult()
                        .staleSelection()
            )
            .count();
    }

    private static int sizeOf(
        List<Item> items
    ) {

        Objects.requireNonNull(
            items,
            "items must not be null"
        );

        return items.size();
    }

    /**
     * Resultado de uma Publication selecionada individualmente.
     */
    public record Item(
        long dealEvaluationId,
        long publicationId,
        PublicationOutboxEnqueueResult enqueueResult
    ) {

        public Item {

            if (dealEvaluationId <= 0L) {

                throw new IllegalArgumentException(
                    "dealEvaluationId must be positive"
                );
            }

            if (publicationId <= 0L) {

                throw new IllegalArgumentException(
                    "publicationId must be positive"
                );
            }

            Objects.requireNonNull(
                enqueueResult,
                "enqueueResult must not be null"
            );
        }

        public boolean deliveryReserved() {

            return enqueueResult.enqueued()
                || enqueueResult.alreadyEnqueued();
        }
    }
}
