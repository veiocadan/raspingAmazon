package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.selection.PublicationSelectionSourceCandidate;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionSourceQueryPort;
import com.raspingamazon.domain.product.Asin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementação PostgreSQL da consulta de candidatos de seleção
 * pertencentes a uma ProcessingRun.
 *
 * <p>A consulta utiliza exclusivamente fatos já persistidos.</p>
 *
 * <pre>
 * deal_candidate.processing_run_id
 *              |
 *              v
 * deal_candidate.offer_snapshot_id
 *              |
 *              v
 * deal_evaluation.offer_snapshot_id
 * </pre>
 *
 * <p>A coluna deal_candidate.offer_snapshot_id é nullable por
 * definição. Um candidato ainda não enriquecido simplesmente não
 * aparece no resultado.</p>
 *
 * <p>Uma DealEvaluation sem score também não participa da seleção
 * operacional. Score ausente não é convertido em zero.</p>
 */
public final class JdbcPublicationSelectionSourceQueryAdapter
    implements PublicationSelectionSourceQueryPort {

    private final Connection connection;

    public JdbcPublicationSelectionSourceQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public List<PublicationSelectionSourceCandidate>
    findEligibleScoredByProcessingRunId(
        long processingRunId
    ) {

        validateProcessingRunId(
            processingRunId
        );

        String sql =
            """
            SELECT
                evaluation.id AS deal_evaluation_id,
                candidate.asin,
                evaluation.score
            FROM deal_candidate AS candidate
            INNER JOIN deal_evaluation AS evaluation
                ON evaluation.offer_snapshot_id =
                   candidate.offer_snapshot_id
            WHERE candidate.processing_run_id = ?
              AND evaluation.eligible = true
              AND evaluation.score IS NOT NULL
            ORDER BY
                candidate.id ASC
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                processingRunId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                List<PublicationSelectionSourceCandidate> candidates =
                    new ArrayList<>();

                while (resultSet.next()) {

                    candidates.add(
                        new PublicationSelectionSourceCandidate(
                            resultSet.getLong(
                                "deal_evaluation_id"
                            ),
                            new Asin(
                                resultSet.getString(
                                    "asin"
                                )
                            ),
                            resultSet.getBigDecimal(
                                "score"
                            )
                        )
                    );
                }

                return List.copyOf(
                    candidates
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load publication selection "
                    + "source candidates for ProcessingRun "
                    + processingRunId,
                exception
            );
        }
    }

    private void validateProcessingRunId(
        long processingRunId
    ) {

        if (processingRunId <= 0L) {

            throw new IllegalArgumentException(
                "processingRunId must be positive"
            );
        }
    }
}
