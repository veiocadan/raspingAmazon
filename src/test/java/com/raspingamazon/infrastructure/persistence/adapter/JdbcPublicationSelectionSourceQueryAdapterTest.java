package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.selection.PublicationSelectionSourceCandidate;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationSelectionSourceQueryAdapterTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-30T23:45:00Z"
        );

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    @Test
    void shouldLoadOnlyEligibleScoredCandidatesFromRequestedProcessingRun()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                long requestedRunId =
                    insertProcessingRun(
                        connection,
                        uniqueValue(
                            "requested-run"
                        )
                    );

                long anotherRunId =
                    insertProcessingRun(
                        connection,
                        uniqueValue(
                            "another-run"
                        )
                    );

                /*
                 * Candidato 1:
                 *
                 * pertence ao run solicitado,
                 * foi enriquecido,
                 * possui avaliação elegível,
                 * possui score.
                 *
                 * DEVE ser retornado.
                 */
                CandidateFixture selectedFixture =
                    createCandidateWithSnapshot(
                        connection,
                        requestedRunId,
                        "B0SRC00001",
                        uniqueValue(
                            "source-selected"
                        )
                    );

                long expectedEvaluationId =
                    insertEvaluation(
                        connection,
                        selectedFixture.offerSnapshotId(),
                        new BigDecimal(
                            "91.2500"
                        )
                    );

                /*
                 * Candidato 2:
                 *
                 * pertence ao run solicitado e já possui
                 * DealEvaluation, mas ainda não possui score.
                 *
                 * NÃO deve ser convertido em score zero e
                 * NÃO deve entrar na seleção.
                 */
                CandidateFixture unscoredFixture =
                    createCandidateWithSnapshot(
                        connection,
                        requestedRunId,
                        "B0SRC00002",
                        uniqueValue(
                            "source-unscored"
                        )
                    );

                insertEvaluation(
                    connection,
                    unscoredFixture.offerSnapshotId(),
                    null
                );

                /*
                 * Candidato 3:
                 *
                 * pertence ao run solicitado, mas ainda não possui
                 * OfferSnapshot correlacionado.
                 *
                 * É um estado válido da orquestração e deve ser
                 * simplesmente ignorado pela fonte de seleção.
                 */
                insertUnlinkedCandidate(
                    connection,
                    requestedRunId,
                    "B0SRC00003",
                    uniqueValue(
                        "source-unlinked"
                    )
                );

                /*
                 * Candidato 4:
                 *
                 * é elegível e pontuado, porém pertence a outro run.
                 *
                 * NÃO pode vazar para a seleção solicitada.
                 */
                CandidateFixture anotherRunFixture =
                    createCandidateWithSnapshot(
                        connection,
                        anotherRunId,
                        "B0SRC00004",
                        uniqueValue(
                            "source-other-run"
                        )
                    );

                insertEvaluation(
                    connection,
                    anotherRunFixture.offerSnapshotId(),
                    new BigDecimal(
                        "99.0000"
                    )
                );

                JdbcPublicationSelectionSourceQueryAdapter adapter =
                    new JdbcPublicationSelectionSourceQueryAdapter(
                        connection
                    );

                List<PublicationSelectionSourceCandidate> candidates =
                    adapter.findEligibleScoredByProcessingRunId(
                        requestedRunId
                    );

                assertEquals(
                    1,
                    candidates.size()
                );

                PublicationSelectionSourceCandidate candidate =
                    candidates.getFirst();

                assertEquals(
                    expectedEvaluationId,
                    candidate.dealEvaluationId()
                );

                assertEquals(
                    "B0SRC00001",
                    candidate.asin()
                        .value()
                );

                assertEquals(
                    0,
                    candidate.score()
                        .compareTo(
                            new BigDecimal(
                                "91.2500"
                            )
                        )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReturnEmptyListWhenRunHasNoEligibleScoredCandidates()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                long processingRunId =
                    insertProcessingRun(
                        connection,
                        uniqueValue(
                            "empty-run"
                        )
                    );

                insertUnlinkedCandidate(
                    connection,
                    processingRunId,
                    "B0SRC00005",
                    uniqueValue(
                        "source-empty"
                    )
                );

                JdbcPublicationSelectionSourceQueryAdapter adapter =
                    new JdbcPublicationSelectionSourceQueryAdapter(
                        connection
                    );

                List<PublicationSelectionSourceCandidate> candidates =
                    adapter.findEligibleScoredByProcessingRunId(
                        processingRunId
                    );

                assertTrue(
                    candidates.isEmpty()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectNonPositiveProcessingRunIdBeforeDatabaseQuery()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcPublicationSelectionSourceQueryAdapter adapter =
                new JdbcPublicationSelectionSourceQueryAdapter(
                    connection
                );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    adapter.findEligibleScoredByProcessingRunId(
                        0L
                    )
            );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    adapter.findEligibleScoredByProcessingRunId(
                        -1L
                    )
            );
        }
    }

    private long insertProcessingRun(
        Connection connection,
        String runKey
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at
            )
            VALUES (
                ?,
                'https://www.amazon.com.br/deals',
                'COMPLETED',
                ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                runKey
            );

            statement.setObject(
                2,
                NOW
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private CandidateFixture createCandidateWithSnapshot(
        Connection connection,
        long processingRunId,
        String asin,
        String source
    ) throws Exception {

        long productId =
            insertProduct(
                connection,
                asin
            );

        long offerSnapshotId =
            insertOfferSnapshot(
                connection,
                productId,
                source
            );

        insertCandidate(
            connection,
            processingRunId,
            asin,
            source,
            offerSnapshotId
        );

        return new CandidateFixture(
            offerSnapshotId
        );
    }

    private long insertProduct(
        Connection connection,
        String asin
    ) throws Exception {

        String sql =
            """
            INSERT INTO product (
                asin,
                title,
                image_url,
                product_url
            )
            VALUES (
                ?,
                ?,
                NULL,
                ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                asin
            );

            statement.setString(
                2,
                "Produto " + asin
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/" + asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertOfferSnapshot(
        Connection connection,
        long productId,
        String source
    ) throws Exception {

        String sql =
            """
            INSERT INTO offer_snapshot (
                product_id,
                collected_at,
                current_price,
                previous_price,
                discount_percentage,
                sold_percentage,
                rating,
                review_count,
                seller_name,
                delivery_provider,
                source
            )
            VALUES (
                ?,
                ?,
                99.90,
                129.90,
                23.10,
                50.00,
                4.8,
                1000,
                'Amazon.com.br',
                'Amazon',
                ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                productId
            );

            statement.setObject(
                2,
                NOW.minusMinutes(
                    30
                )
            );

            statement.setString(
                3,
                source
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void insertCandidate(
        Connection connection,
        long processingRunId,
        String asin,
        String source,
        Long offerSnapshotId
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_candidate (
                processing_run_id,
                asin,
                product_url,
                title,
                image_url,
                current_price,
                basis_price,
                previous_price,
                sold_percentage,
                rating,
                review_count,
                collected_at,
                source,
                offer_snapshot_id
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                NULL,
                99.90,
                129.90,
                129.90,
                50.00,
                4.8,
                1000,
                ?,
                ?,
                ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                processingRunId
            );

            statement.setString(
                2,
                asin
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/" + asin
            );

            statement.setString(
                4,
                "Produto " + asin
            );

            statement.setObject(
                5,
                NOW.minusMinutes(
                    30
                )
            );

            statement.setString(
                6,
                source
            );

            if (offerSnapshotId == null) {

                statement.setNull(
                    7,
                    Types.BIGINT
                );

            } else {

                statement.setLong(
                    7,
                    offerSnapshotId
                );
            }

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private void insertUnlinkedCandidate(
        Connection connection,
        long processingRunId,
        String asin,
        String source
    ) throws Exception {

        insertCandidate(
            connection,
            processingRunId,
            asin,
            source,
            null
        );
    }

    private long insertEvaluation(
        Connection connection,
        long offerSnapshotId,
        BigDecimal score
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation (
                offer_snapshot_id,
                eligible,
                rejection_reason,
                eligibility_policy_version,
                score,
                score_version,
                evaluated_at
            )
            VALUES (
                ?,
                true,
                NULL,
                'D4D_SOURCE_TEST_ELIGIBILITY_V1',
                ?,
                ?,
                ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                offerSnapshotId
            );

            if (score == null) {

                statement.setNull(
                    2,
                    Types.NUMERIC
                );

                statement.setNull(
                    3,
                    Types.VARCHAR
                );

            } else {

                statement.setBigDecimal(
                    2,
                    score
                );

                statement.setString(
                    3,
                    "SCORE_V1"
                );
            }

            statement.setObject(
                4,
                NOW.minusMinutes(
                    10
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private String uniqueValue(
        String prefix
    ) {

        return prefix
            + "-"
            + SEQUENCE.incrementAndGet()
            + "-"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }

    private record CandidateFixture(
        long offerSnapshotId
    ) {
    }
}
