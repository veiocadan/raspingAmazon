package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSearchCriteria;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSummary;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcDealEvaluationOperationalQueryAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2099-01-10T12:00:00-03:00"
        );

    @Test
    void shouldReturnKeysetPageInDeterministicOrder()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedEvaluation sameTimeFirst =
                    insertEvaluation(
                        connection,
                        "B0OP140001",
                        "Produto primeiro",
                        BASE_TIME.minusMinutes(
                            10
                        ),
                        BASE_TIME,
                        true,
                        null,
                        new BigDecimal(
                            "80.0000"
                        ),
                        new BigDecimal(
                            "10.0000"
                        )
                    );

                PersistedEvaluation sameTimeSecond =
                    insertEvaluation(
                        connection,
                        "B0OP140002",
                        "Produto segundo",
                        BASE_TIME.minusMinutes(
                            9
                        ),
                        BASE_TIME,
                        true,
                        null,
                        new BigDecimal(
                            "90.0000"
                        ),
                        new BigDecimal(
                            "20.0000"
                        )
                    );

                PersistedEvaluation older =
                    insertEvaluation(
                        connection,
                        "B0OP140003",
                        "Produto antigo",
                        BASE_TIME.minusHours(
                            2
                        ),
                        BASE_TIME.minusHours(
                            1
                        ),
                        true,
                        null,
                        new BigDecimal(
                            "70.0000"
                        ),
                        null
                    );

                JdbcDealEvaluationOperationalQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalQueryAdapter(
                        connection
                    );

                DealEvaluationSearchCriteria firstCriteria =
                    new DealEvaluationSearchCriteria(
                        null,
                        null,
                        null,
                        null,
                        BASE_TIME.minusHours(
                            2
                        ),
                        BASE_TIME.plusHours(
                            1
                        ),
                        null,
                        2
                    );

                DealEvaluationPage firstPage =
                    adapter.search(
                        firstCriteria
                    );

                assertEquals(
                    2,
                    firstPage.items()
                        .size()
                );

                assertEquals(
                    sameTimeSecond.evaluationId(),
                    firstPage.items()
                        .get(0)
                        .evaluationId()
                );

                assertEquals(
                    sameTimeFirst.evaluationId(),
                    firstPage.items()
                        .get(1)
                        .evaluationId()
                );

                assertTrue(
                    firstPage.hasNextPage()
                );

                assertEquals(
                    sameTimeFirst.evaluationId(),
                    firstPage.nextCursor()
                        .evaluationId()
                );

                DealEvaluationSearchCriteria secondCriteria =
                    new DealEvaluationSearchCriteria(
                        null,
                        null,
                        null,
                        null,
                        BASE_TIME.minusHours(
                            2
                        ),
                        BASE_TIME.plusHours(
                            1
                        ),
                        firstPage.nextCursor(),
                        2
                    );

                DealEvaluationPage secondPage =
                    adapter.search(
                        secondCriteria
                    );

                assertEquals(
                    1,
                    secondPage.items()
                        .size()
                );

                assertEquals(
                    older.evaluationId(),
                    secondPage.items()
                        .getFirst()
                        .evaluationId()
                );

                assertFalse(
                    secondPage.hasNextPage()
                );

                assertNull(
                    secondPage.nextCursor()
                );
            }
        );
    }

    @Test
    void shouldFilterByEligibilityAndScore()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedEvaluation expected =
                    insertEvaluation(
                        connection,
                        "B0OP140004",
                        "Produto score esperado",
                        BASE_TIME,
                        BASE_TIME.plusMinutes(
                            1
                        ),
                        true,
                        null,
                        new BigDecimal(
                            "85.0000"
                        ),
                        null
                    );

                insertEvaluation(
                    connection,
                    "B0OP140005",
                    "Produto score alto",
                    BASE_TIME,
                    BASE_TIME.plusMinutes(
                        2
                    ),
                    true,
                    null,
                    new BigDecimal(
                        "95.0000"
                    ),
                    null
                );

                insertEvaluation(
                    connection,
                    "B0OP140006",
                    "Produto inelegível",
                    BASE_TIME,
                    BASE_TIME.plusMinutes(
                        3
                    ),
                    false,
                    RejectionReason.RATING_BELOW_MINIMUM,
                    null,
                    null
                );

                JdbcDealEvaluationOperationalQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalQueryAdapter(
                        connection
                    );

                DealEvaluationPage page =
                    adapter.search(
                        new DealEvaluationSearchCriteria(
                            true,
                            null,
                            new BigDecimal(
                                "80"
                            ),
                            new BigDecimal(
                                "90"
                            ),
                            BASE_TIME.minusHours(
                                1
                            ),
                            BASE_TIME.plusHours(
                                1
                            ),
                            null,
                            50
                        )
                    );

                assertEquals(
                    1,
                    page.items()
                        .size()
                );

                assertEquals(
                    expected.evaluationId(),
                    page.items()
                        .getFirst()
                        .evaluationId()
                );

                assertTrue(
                    page.items()
                        .getFirst()
                        .eligible()
                );

                assertEquals(
                    new BigDecimal(
                        "85.0000"
                    ),
                    page.items()
                        .getFirst()
                        .score()
                );
            }
        );
    }

    @Test
    void shouldFilterByAsin()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedEvaluation expected =
                    insertEvaluation(
                        connection,
                        "B0OP140007",
                        "Produto ASIN esperado",
                        BASE_TIME,
                        BASE_TIME.plusMinutes(
                            10
                        ),
                        true,
                        null,
                        new BigDecimal(
                            "77.0000"
                        ),
                        null
                    );

                insertEvaluation(
                    connection,
                    "B0OP140008",
                    "Produto ASIN diferente",
                    BASE_TIME,
                    BASE_TIME.plusMinutes(
                        11
                    ),
                    true,
                    null,
                    new BigDecimal(
                        "88.0000"
                    ),
                    null
                );

                JdbcDealEvaluationOperationalQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalQueryAdapter(
                        connection
                    );

                DealEvaluationPage page =
                    adapter.search(
                        new DealEvaluationSearchCriteria(
                            null,
                            new Asin(
                                "B0OP140007"
                            ),
                            null,
                            null,
                            BASE_TIME.minusHours(
                                1
                            ),
                            BASE_TIME.plusHours(
                                1
                            ),
                            null,
                            50
                        )
                    );

                assertEquals(
                    1,
                    page.items()
                        .size()
                );

                DealEvaluationSummary summary =
                    page.items()
                        .getFirst();

                assertEquals(
                    expected.evaluationId(),
                    summary.evaluationId()
                );

                assertEquals(
                    "B0OP140007",
                    summary.asin()
                        .value()
                );

                assertEquals(
                    "Produto ASIN esperado",
                    summary.title()
                );

                assertEquals(
                    new BigDecimal(
                        "99.90"
                    ),
                    summary.currentPrice()
                        .amount()
                );
            }
        );
    }

    @Test
    void shouldFilterByInclusiveEvaluationRange()
        throws Exception {

        inTransaction(
            connection -> {

                OffsetDateTime from =
                    BASE_TIME.plusDays(
                        1
                    );

                OffsetDateTime until =
                    from.plusHours(
                        1
                    );

                PersistedEvaluation lowerBoundary =
                    insertEvaluation(
                        connection,
                        "B0OP140009",
                        "Produto limite inferior",
                        from.minusMinutes(
                            5
                        ),
                        from,
                        true,
                        null,
                        new BigDecimal(
                            "60.0000"
                        ),
                        null
                    );

                PersistedEvaluation upperBoundary =
                    insertEvaluation(
                        connection,
                        "B0OP140010",
                        "Produto limite superior",
                        until.minusMinutes(
                            5
                        ),
                        until,
                        true,
                        null,
                        new BigDecimal(
                            "61.0000"
                        ),
                        null
                    );

                insertEvaluation(
                    connection,
                    "B0OP140011",
                    "Produto fora do período",
                    until.plusMinutes(
                        1
                    ),
                    until.plusMinutes(
                        1
                    ),
                    true,
                    null,
                    new BigDecimal(
                        "62.0000"
                    ),
                    null
                );

                JdbcDealEvaluationOperationalQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalQueryAdapter(
                        connection
                    );

                DealEvaluationPage page =
                    adapter.search(
                        new DealEvaluationSearchCriteria(
                            null,
                            null,
                            null,
                            null,
                            from,
                            until,
                            null,
                            50
                        )
                    );

                assertEquals(
                    2,
                    page.items()
                        .size()
                );

                List<Long> evaluationIds =
                    page.items()
                        .stream()
                        .map(
                            DealEvaluationSummary::evaluationId
                        )
                        .toList();

                assertTrue(
                    evaluationIds.contains(
                        lowerBoundary.evaluationId()
                    )
                );

                assertTrue(
                    evaluationIds.contains(
                        upperBoundary.evaluationId()
                    )
                );
            }
        );
    }

    @Test
    void shouldReturnEmptyPageWhenNoRowsMatch()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcDealEvaluationOperationalQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalQueryAdapter(
                        connection
                    );

                DealEvaluationPage page =
                    adapter.search(
                        new DealEvaluationSearchCriteria(
                            null,
                            null,
                            null,
                            null,
                            OffsetDateTime.parse(
                                "2199-01-01T00:00:00Z"
                            ),
                            OffsetDateTime.parse(
                                "2199-01-02T00:00:00Z"
                            ),
                            null,
                            50
                        )
                    );

                assertTrue(
                    page.items()
                        .isEmpty()
                );

                assertFalse(
                    page.hasNextPage()
                );

                assertNull(
                    page.nextCursor()
                );
            }
        );
    }

    @Test
    void shouldRejectNullCriteria()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcDealEvaluationOperationalQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalQueryAdapter(
                        connection
                    );

                assertThrows(
                    NullPointerException.class,
                    () -> adapter.search(
                        null
                    )
                );
            }
        );
    }

    @Test
    void shouldRejectNullConnection() {

        assertThrows(
            NullPointerException.class,
            () -> new JdbcDealEvaluationOperationalQueryAdapter(
                null
            )
        );
    }

    private void inTransaction(
        TransactionTest transactionTest
    ) throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                transactionTest.execute(
                    connection
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private PersistedEvaluation insertEvaluation(
        Connection connection,
        String asin,
        String title,
        OffsetDateTime collectedAt,
        OffsetDateTime evaluatedAt,
        boolean eligible,
        RejectionReason rejectionReason,
        BigDecimal score,
        BigDecimal momentum
    ) throws Exception {

        long productId =
            insertProduct(
                connection,
                asin,
                title
            );

        long snapshotId =
            insertSnapshot(
                connection,
                productId,
                collectedAt
            );

        long evaluationId =
            insertDealEvaluation(
                connection,
                snapshotId,
                evaluatedAt,
                eligible,
                rejectionReason,
                score,
                momentum
            );

        return new PersistedEvaluation(
            productId,
            snapshotId,
            evaluationId
        );
    }

    private long insertProduct(
        Connection connection,
        String asin,
        String title
    ) throws Exception {

        String sql =
            """
            INSERT INTO product (
                asin,
                title,
                image_url,
                product_url
            )
            VALUES (?, ?, ?, ?)
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
                title
            );

            statement.setString(
                3,
                "https://example.invalid/"
                    + asin
                    + ".jpg"
            );

            statement.setString(
                4,
                "https://www.amazon.com.br/dp/"
                    + asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "Product insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertSnapshot(
        Connection connection,
        long productId,
        OffsetDateTime collectedAt
    ) throws Exception {

        String sql =
            """
            INSERT INTO offer_snapshot (
                product_id,
                collected_at,
                current_price,
                seller_name,
                delivery_provider,
                source
            )
            VALUES (?, ?, ?, ?, ?, ?)
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
                collectedAt
            );

            statement.setBigDecimal(
                3,
                new BigDecimal(
                    "99.90"
                )
            );

            statement.setString(
                4,
                "Amazon.com.br"
            );

            statement.setString(
                5,
                "Amazon.com.br"
            );

            statement.setString(
                6,
                "TEST_OPERATIONAL_QUERY"
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "OfferSnapshot insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertDealEvaluation(
        Connection connection,
        long snapshotId,
        OffsetDateTime evaluatedAt,
        boolean eligible,
        RejectionReason rejectionReason,
        BigDecimal score,
        BigDecimal momentum
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation (
                offer_snapshot_id,
                eligible,
                rejection_reason,
                score,
                momentum,
                evaluated_at,
                eligibility_policy_version,
                filter_profile_version,
                score_version,
                momentum_version
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setBoolean(
                2,
                eligible
            );

            statement.setString(
                3,
                rejectionReason == null
                    ? null
                    : rejectionReason.name()
            );

            statement.setBigDecimal(
                4,
                score
            );

            statement.setBigDecimal(
                5,
                momentum
            );

            statement.setObject(
                6,
                evaluatedAt
            );

            statement.setString(
                7,
                "TEST_OPERATIONAL_ELIGIBILITY"
            );

            statement.setString(
                8,
                "TEST_OPERATIONAL_FILTER"
            );

            statement.setString(
                9,
                score == null
                    ? null
                    : "TEST_OPERATIONAL_SCORE"
            );

            statement.setString(
                10,
                momentum == null
                    ? null
                    : "TEST_OPERATIONAL_MOMENTUM"
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "DealEvaluation insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    @FunctionalInterface
    private interface TransactionTest {

        void execute(
            Connection connection
        ) throws Exception;
    }

    private record PersistedEvaluation(
        long productId,
        long snapshotId,
        long evaluationId
    ) {
    }
}
