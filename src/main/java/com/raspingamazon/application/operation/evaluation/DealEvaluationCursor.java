package com.raspingamazon.application.operation.evaluation;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Cursor estável para paginação operacional de avaliações.
 *
 * <p>A paginação segue a mesma ordenação obrigatória da consulta:</p>
 *
 * <pre>
 * evaluatedAt DESC
 * evaluationId DESC
 * </pre>
 *
 * <p>O cursor representa o último item entregue pela página anterior.</p>
 */
public record DealEvaluationCursor(
    OffsetDateTime evaluatedAt,
    long evaluationId
) {

    public DealEvaluationCursor {

        Objects.requireNonNull(
            evaluatedAt,
            "DealEvaluationCursor evaluatedAt must not be null"
        );

        if (evaluationId <= 0L) {
            throw new IllegalArgumentException(
                "DealEvaluationCursor evaluationId must be positive"
            );
        }
    }
}
