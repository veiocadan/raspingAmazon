package com.raspingamazon.application.operation.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * Página operacional de avaliações.
 *
 * <p>A página utiliza keyset pagination através de
 * DealEvaluationCursor.</p>
 *
 * <p>Não existe contagem total obrigatória. A presença de nextCursor
 * indica que existe continuidade conhecida para a consulta.</p>
 */
public record DealEvaluationPage(
    List<DealEvaluationSummary> items,
    DealEvaluationCursor nextCursor
) {

    public DealEvaluationPage {

        Objects.requireNonNull(
            items,
            "DealEvaluationPage items must not be null"
        );

        items =
            List.copyOf(
                items
            );

        validateOrdering(
            items
        );

        if (items.isEmpty()
            && nextCursor != null) {

            throw new IllegalArgumentException(
                "Empty DealEvaluationPage must not have nextCursor"
            );
        }

        if (nextCursor != null) {

            DealEvaluationSummary lastItem =
                items.get(
                    items.size() - 1
                );

            DealEvaluationCursor expectedCursor =
                new DealEvaluationCursor(
                    lastItem.evaluatedAt(),
                    lastItem.evaluationId()
                );

            if (!expectedCursor.equals(
                nextCursor
            )) {

                throw new IllegalArgumentException(
                    "DealEvaluationPage nextCursor "
                        + "must represent the last page item"
                );
            }
        }
    }

    public boolean hasNextPage() {
        return nextCursor != null;
    }

    private static void validateOrdering(
        List<DealEvaluationSummary> items
    ) {

        for (int index = 1;
             index < items.size();
             index++) {

            DealEvaluationSummary previous =
                items.get(
                    index - 1
                );

            DealEvaluationSummary current =
                items.get(
                    index
                );

            if (previous.evaluatedAt()
                .isBefore(
                    current.evaluatedAt()
                )) {

                throw new IllegalArgumentException(
                    "DealEvaluationPage items must be ordered "
                        + "by evaluatedAt DESC and evaluationId DESC"
                );
            }

            if (previous.evaluatedAt()
                .isEqual(
                    current.evaluatedAt()
                )
                && previous.evaluationId()
                <= current.evaluationId()) {

                throw new IllegalArgumentException(
                    "DealEvaluationPage items must be ordered "
                        + "by evaluatedAt DESC and evaluationId DESC"
                );
            }
        }
    }
}
