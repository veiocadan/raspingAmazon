package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.publication.PublicationPage;
import com.raspingamazon.application.operation.publication.PublicationSearchCriteria;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.PublicationStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPublicationOperationalQueryAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2099-05-10T12:00:00-03:00"
        );

    @Test
    void shouldPagePublicationsInDeterministicOrder()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedContext first =
                    insertContext(
                        connection,
                        "B0PUB14011",
                        "Produto publicação 11"
                    );

                PersistedContext second =
                    insertContext(
                        connection,
                        "B0PUB14012",
                        "Produto publicação 12"
                    );

                PersistedContext older =
                    insertContext(
                        connection,
                        "B0PUB14013",
                        "Produto publicação 13"
                    );

                long firstId =
                    insertPublication(
                        connection,
                        first.evaluationId(),
                        PublicationStatus.CREATED,
                        BASE_TIME,
                        "TEMPLATE_PAGE_1"
                    );

                long secondId =
                    insertPublication(
                        connection,
                        second.evaluationId(),
                        PublicationStatus.CREATED,
                        BASE_TIME,
                        "TEMPLATE_PAGE_2"
                    );

                long olderId =
                    insertPublication(
                        connection,
                        older.evaluationId(),
                        PublicationStatus.CREATED,
                        BASE_TIME.minusHours(
                            1
                        ),
                        "TEMPLATE_PAGE_3"
                    );

                JdbcPublicationOperationalQueryAdapter adapter =
                    new JdbcPublicationOperationalQueryAdapter(
                        connection
                    );

                PublicationPage firstPage =
                    adapter.search(
                        new PublicationSearchCriteria(
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
                        )
                    );

                assertEquals(
                    2,
                    firstPage.items()
                        .size()
                );

                assertEquals(
                    secondId,
                    firstPage.items()
                        .get(0)
                        .publicationId()
                );

                assertEquals(
                    firstId,
                    firstPage.items()
                        .get(1)
                        .publicationId()
                );

                assertTrue(
                    firstPage.hasNextPage()
                );

                PublicationPage secondPage =
                    adapter.search(
                        new PublicationSearchCriteria(
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
                        )
                    );

                assertEquals(
                    1,
                    secondPage.items()
                        .size()
                );

                assertEquals(
                    olderId,
                    secondPage.items()
                        .getFirst()
                        .publicationId()
                );

                assertFalse(
                    secondPage.hasNextPage()
                );
            }
        );
    }

    @Test
    void shouldFilterByStatus()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedContext created =
                    insertContext(
                        connection,
                        "B0PUB14014",
                        "Produto created"
                    );

                PersistedContext published =
                    insertContext(
                        connection,
                        "B0PUB14015",
                        "Produto published"
                    );

                long expectedId =
                    insertPublication(
                        connection,
                        created.evaluationId(),
                        PublicationStatus.CREATED,
                        BASE_TIME,
                        "TEMPLATE_STATUS_CREATED"
                    );

                insertPublication(
                    connection,
                    published.evaluationId(),
                    PublicationStatus.PUBLISHED,
                    BASE_TIME.plusMinutes(
                        1
                    ),
                    "TEMPLATE_STATUS_PUBLISHED"
                );

                JdbcPublicationOperationalQueryAdapter adapter =
                    new JdbcPublicationOperationalQueryAdapter(
                        connection
                    );

                PublicationPage page =
                    adapter.search(
                        new PublicationSearchCriteria(
                            PublicationStatus.CREATED,
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

                assertEquals(
                    expectedId,
                    page.items()
                        .getFirst()
                        .publicationId()
                );

                assertEquals(
                    PublicationStatus.CREATED.name(),
                    page.items()
                        .getFirst()
                        .status()
                );
            }
        );
    }

    @Test
    void shouldFilterByAsinAndEvaluationId()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedContext expected =
                    insertContext(
                        connection,
                        "B0PUB14016",
                        "Produto alvo"
                    );

                PersistedContext other =
                    insertContext(
                        connection,
                        "B0PUB14017",
                        "Produto diferente"
                    );

                long expectedPublicationId =
                    insertPublication(
                        connection,
                        expected.evaluationId(),
                        PublicationStatus.CREATED,
                        BASE_TIME,
                        "TEMPLATE_TARGET"
                    );

                insertPublication(
                    connection,
                    other.evaluationId(),
                    PublicationStatus.CREATED,
                    BASE_TIME.plusMinutes(
                        1
                    ),
                    "TEMPLATE_OTHER"
                );

                JdbcPublicationOperationalQueryAdapter adapter =
                    new JdbcPublicationOperationalQueryAdapter(
                        connection
                    );

                PublicationPage page =
                    adapter.search(
                        new PublicationSearchCriteria(
                            null,
                            new Asin(
                                "B0PUB14016"
                            ),
                            expected.evaluationId(),
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
                    expectedPublicationId,
                    page.items()
                        .getFirst()
                        .publicationId()
                );

                assertEquals(
                    expected.evaluationId(),
                    page.items()
                        .getFirst()
                        .dealEvaluationId()
                );

                assertEquals(
                    "B0PUB14016",
                    page.items()
                        .getFirst()
                        .asin()
                        .value()
                );

                assertEquals(
                    "Produto alvo",
                    page.items()
                        .getFirst()
                        .title()
                );
            }
        );
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcPublicationOperationalQueryAdapter adapter =
                    new JdbcPublicationOperationalQueryAdapter(
                        connection
                    );

                PublicationPage page =
                    adapter.search(
                        new PublicationSearchCriteria(
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

                assertNull(
                    page.nextCursor()
                );
            }
        );
    }

    @Test
    void shouldRejectInvalidAdapterInput()
        throws Exception {

        assertThrows(
            NullPointerException.class,
            () -> new JdbcPublicationOperationalQueryAdapter(
                null
            )
        );

        inTransaction(
            connection -> {

                JdbcPublicationOperationalQueryAdapter adapter =
                    new JdbcPublicationOperationalQueryAdapter(
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

    private PersistedContext insertContext(
        Connection connection,
        String asin,
        String title
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
                productId
            );

        long evaluationId =
            insertEvaluation(
                connection,
                snapshotId
            );

        return new PersistedContext(
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
        long productId
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
                BASE_TIME.minusMinutes(
                    10
                )
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
                "TEST_OPERATIONAL_PUBLICATION"
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

    private long insertEvaluation(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation (
                offer_snapshot_id,
                eligible,
                rejection_reason,
                eligibility_policy_version,
                filter_profile_version,
                score,
                score_version,
                momentum,
                momentum_version,
                evaluated_at
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
                true
            );

            statement.setObject(
                3,
                null
            );

            statement.setString(
                4,
                "TEST_PUBLICATION_ELIGIBILITY"
            );

            statement.setString(
                5,
                "TEST_PUBLICATION_FILTER"
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "80.0000"
                )
            );

            statement.setString(
                7,
                "TEST_PUBLICATION_SCORE"
            );

            statement.setObject(
                8,
                null
            );

            statement.setObject(
                9,
                null
            );

            statement.setObject(
                10,
                BASE_TIME.minusMinutes(
                    5
                )
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

    private long insertPublication(
        Connection connection,
        long evaluationId,
        PublicationStatus status,
        OffsetDateTime createdAt,
        String templateVersion
    ) throws Exception {

        String sql =
            """
            INSERT INTO publication (
                deal_evaluation_id,
                template_version,
                commercial_presentation_version,
                affiliate_link_version,
                generated_text,
                affiliate_url,
                status,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            statement.setString(
                2,
                templateVersion
            );

            statement.setString(
                3,
                "COMMERCIAL_PRESENTATION_TEST"
            );

            statement.setString(
                4,
                "AFFILIATE_LINK_TEST"
            );

            statement.setString(
                5,
                "Texto operacional de publicação"
            );

            statement.setString(
                6,
                "https://www.amazon.com.br/dp/test?tag=test"
            );

            statement.setString(
                7,
                status.name()
            );

            statement.setObject(
                8,
                createdAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "Publication insert returned no id"
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

    private record PersistedContext(
        long productId,
        long snapshotId,
        long evaluationId
    ) {
    }
}
