package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.port.PublicationDispatchReconciliationCandidateQueryPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Consulta JDBC de ProcessingRuns que podem ser reconciliadas com
 * PUBLICATION_DISPATCH.
 *
 * <p>Esta consulta é somente um pré-filtro operacional.</p>
 *
 * <p>A autoridade final para classificar uma ProcessingRun como:</p>
 *
 * <pre>
 * READY
 * IN_PROGRESS
 * BLOCKED
 * </pre>
 *
 * <p>continua sendo ProcessingRunPublicationReadinessQueryPort.</p>
 *
 * <p>O pré-filtro evita starvation no reconciliador. Uma run
 * COMPLETED que ainda possui enrichment/evaluation claramente ativo
 * não ocupa indefinidamente as primeiras posições do lote.</p>
 *
 * <p>São candidatas:</p>
 *
 * <ul>
 *     <li>runs FAILED ainda sem PUBLICATION_DISPATCH;</li>
 *     <li>runs COMPLETED sem trabalho downstream ativo e ainda sem
 *     PUBLICATION_DISPATCH.</li>
 * </ul>
 *
 * <p>Para runs COMPLETED, "sem trabalho downstream ativo" significa
 * que não existe candidato cuja produção ainda esteja sendo
 * legitimamente executada por:</p>
 *
 * <ul>
 *     <li>ENRICH_DEAL PENDING/RUNNING/RETRY_WAIT sem snapshot;</li>
 *     <li>EVALUATE_DEAL PENDING/RUNNING/RETRY_WAIT sem
 *     DealEvaluation persistida.</li>
 * </ul>
 *
 * <p>Estados inconsistentes ou terminais não são decididos nesta
 * classe. Eles passam pelo pré-filtro e serão classificados pelo
 * readiness autoritativo.</p>
 */
public final class
JdbcPublicationDispatchReconciliationCandidateQueryAdapter
    implements PublicationDispatchReconciliationCandidateQueryPort {

    private final Connection connection;

    public JdbcPublicationDispatchReconciliationCandidateQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public List<Long> findCandidates(
        int limit
    ) {

        if (limit <= 0) {

            throw new IllegalArgumentException(
                "limit must be positive"
            );
        }

        String sql =
            """
            SELECT
                run.id

            FROM processing_run AS run

            WHERE run.status IN (
                'COMPLETED',
                'FAILED'
            )

              /*
               * Uma vez criado o trabalho durável desta run,
               * ela sai definitivamente da reconciliação.
               *
               * O estado PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/DEAD
               * do próprio PUBLICATION_DISPATCH é responsabilidade
               * da fila e do worker, não deste reconciliador.
               */
              AND NOT EXISTS (
                  SELECT 1
                  FROM processing_job AS dispatch_job
                  WHERE dispatch_job.job_type =
                      'PUBLICATION_DISPATCH'
                    AND dispatch_job.processing_run_id =
                        run.id
              )

              AND (
                  /*
                   * FAILED já é uma condição acionável.
                   *
                   * O readiness posteriormente classificará
                   * definitivamente a run como BLOCKED.
                   */
                  run.status = 'FAILED'

                  OR

                  (
                      run.status = 'COMPLETED'

                      /*
                       * Uma run COMPLETED só entra no lote quando
                       * nenhum de seus candidatos possui trabalho
                       * downstream claramente ativo.
                       *
                       * Isso é apenas otimização/pré-filtro.
                       * O readiness F1 ainda revalida a fotografia.
                       */
                      AND NOT EXISTS (
                          SELECT 1

                          FROM deal_candidate AS candidate

                          WHERE candidate.processing_run_id =
                              run.id

                            AND (
                                /*
                                 * Enrichment ainda pode produzir
                                 * o OfferSnapshot.
                                 */
                                (
                                    candidate.offer_snapshot_id
                                        IS NULL

                                    AND EXISTS (
                                        SELECT 1
                                        FROM processing_job
                                            AS enrichment_job

                                        WHERE enrichment_job.job_type =
                                            'ENRICH_DEAL'

                                          AND
                                          enrichment_job.deal_candidate_id =
                                            candidate.id

                                          AND enrichment_job.status IN (
                                              'PENDING',
                                              'RUNNING',
                                              'RETRY_WAIT'
                                          )
                                    )
                                )

                                OR

                                /*
                                 * O OfferSnapshot existe, ainda não
                                 * existe DealEvaluation e há trabalho
                                 * de avaliação ativo capaz de
                                 * produzi-la.
                                 */
                                (
                                    candidate.offer_snapshot_id
                                        IS NOT NULL

                                    AND NOT EXISTS (
                                        SELECT 1
                                        FROM deal_evaluation
                                            AS evaluation

                                        WHERE evaluation.offer_snapshot_id =
                                            candidate.offer_snapshot_id
                                    )

                                    AND EXISTS (
                                        SELECT 1
                                        FROM processing_job
                                            AS evaluation_job

                                        WHERE evaluation_job.job_type =
                                            'EVALUATE_DEAL'

                                          AND
                                          evaluation_job.offer_snapshot_id =
                                            candidate.offer_snapshot_id

                                          AND evaluation_job.status IN (
                                              'PENDING',
                                              'RUNNING',
                                              'RETRY_WAIT'
                                          )
                                    )
                                )
                            )
                      )
                  )
              )

            ORDER BY
                run.id ASC

            LIMIT ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setInt(
                1,
                limit
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                List<Long> processingRunIds =
                    new ArrayList<>();

                while (resultSet.next()) {

                    long processingRunId =
                        resultSet.getLong(
                            "id"
                        );

                    if (processingRunId <= 0L) {

                        throw new IllegalStateException(
                            "Persisted ProcessingRun id "
                                + "must be positive"
                        );
                    }

                    processingRunIds.add(
                        processingRunId
                    );
                }

                return List.copyOf(
                    processingRunIds
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to query publication dispatch "
                    + "reconciliation candidates",
                exception
            );
        }
    }
}
