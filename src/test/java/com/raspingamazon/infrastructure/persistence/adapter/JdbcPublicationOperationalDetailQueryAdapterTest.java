package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.publication.PublicationDetail;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPublicationOperationalDetailQueryAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2099-06-10T12:00:00-03:00"
        );

    @Test
    void shouldLoadCompletePublicationDetail()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedContext context =
                    insertContext(
                        connection,
                        "B0PUB14031",
                        "Produto publicação detalhada"
                    );

                long publicationId =
                    insertPublication(
                        connection,
                        context.evaluationId(),
                        "CREATED",
                        "TEMPLATE_DETAIL_V1",
                        "Texto completo para inspeção operacional",
                        "https://www.amazon.com.br/dp/B0PUB14031?tag=test",
                        BASE_TIME
                    );

                JdbcPublicationOperationalDetailQueryAdapter adapter =
                    new JdbcPublicationOperationalDetailQueryAdapter(
                        connection
                    );

                PublicationDetail detail =
                    adapter.findById(
                            publicationId
                        )
                        .orElseThrow();

                assertEquals(
                    publicationId,
                    detail.summary()
                        .publicationId()
                );

                assertEquals(
                    context.evaluationId(),
                    detail.summary()
                        .dealEvaluationId()
                );

                assertEquals(
                    context.productId(),
                    detail.summary()
                        .productId()
                );

                assertEquals(
                    "B0PUB14031",
                    detail.summary()
                        .asin()
                        .value()
                );

                assertEquals(
                    "Produto publicação detalhada",
                    detail.summary()
                        .title()
                );

                assertEquals(
                    "CREATED",
                    detail.summary()
                        .status()
                );

                assertEquals(
                    "TEMPLATE_DETAIL_V1",
                    detail.summary()
                        .templateVersion()
                );

                assertEquals(
                    "COMMERCIAL_PRESENTATION_TEST",
                    detail.summary()
                        .commercialPresentationVersion()
                );

                assertEquals(
                    "AFFILIATE_LINK_TEST",
                    detail.summary()
                        .affiliateLinkVersion()
                );

                assertEquals(
                    "Texto completo para inspeção operacional",
                    detail.generatedText()
                );

                assertEquals(
                    "https://www.amazon.com.br/dp/B0PUB14031?tag=test",
                    detail.affiliateUrl()
                );

                assertEquals(
                    BASE_TIME.toInstant(),
                    detail.summary()
                        .createdAt()
                        .toInstant()
                );
            }
        );
    }

    @Test
    void shouldLoadHistoricalPublicationWithoutAffiliateUrl()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedContext context =
                    insertContext(
                        connection,
                        "B0PUB14032",
                        "Produto histórico"
                    );

                long publicationId =
                    insertPublication(
                        connection,
                        context.evaluationId(),
                        "CREATED",
                        "LEGACY_TEMPLATE",
                        "Texto histórico",
                        null,
                        BASE_TIME.plusMinutes(
                            1
                        )
                    );

                JdbcPublicationOperationalDetailQueryAdapter adapter =
                    new JdbcPublicationOperationalDetailQueryAdapter(
                        connection
                    );

                PublicationDetail detail =
                    adapter.findById(
                            publicationId
                        )
                        .orElseThrow();

                assertEquals(
                    "Texto histórico",
                    detail.generatedText()
                );

                assertNull(
                    detail.affiliateUrl()
                );
            }
        );
    }

    @Test
    void shouldPreserveUnexpectedPersistedStatusForDiagnosis()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedContext context =
                    insertContext(
                        connection,
                        "B0PUB14033",
                        "Produto status histórico"
                    );

                long publicationId =
                    insertPublication(
                        connection,
                        context.evaluationId(),
                        "LEGACY_STATUS",
                        "LEGACY_STATUS_TEMPLATE",
                        "Texto com status histórico",
                        "https://example.invalid/legacy",
                        BASE_TIME.plusMinutes(
                            2
                        )
                    );

                JdbcPublicationOperationalDetailQueryAdapter adapter =
                    new JdbcPublicationOperationalDetailQueryAdapter(
                        connection
                    );

                PublicationDetail detail =
                    adapter.findById(
                            publicationId
                        )
                        .orElseThrow();

                assertEquals(
                    "LEGACY_STATUS",
                    detail.summary()
                        .status()
                );
            }
        );
    }

    @Test
    void shouldReturnEmptyWhenPublicationDoesNotExist()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcPublicationOperationalDetailQueryAdapter adapter =
                    new JdbcPublicationOperationalDetailQueryAdapter(
                        connection
                    );

                Optional<PublicationDetail> result =
                    adapter.findById(
                        Long.MAX_VALUE
                    );

                assertTrue(
                    result.isEmpty()
                );
            }
        );
    }

    @Test
    void shouldRejectInvalidAdapterInput()
        throws Exception {

        assertThrows(
            NullPointerException.class,
            () -> new JdbcPublicationOperationalDetailQueryAdapter(
                null
            )
        );

        inTransaction(
            connection -> {

                JdbcPublicationOperationalDetailQueryAdapter adapter =
                    new JdbcPublicationOperationalDetailQueryAdapter(
                        connection
                    );

                assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.findById(
                        0L
                    )
                );

                assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.findById(
                        -1L
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
                "TEST_OPERATIONAL_PUBLICATION_DETAIL"
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
                "TEST_PUBLICATION_DETAIL_ELIGIBILITY"
            );

            statement.setString(
                5,
                "TEST_PUBLICATION_DETAIL_FILTER"
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "80.0000"
                )
            );

            statement.setString(
                7,
                "TEST_PUBLICATION_DETAIL_SCORE"
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
        String status,
        String templateVersion,
        String generatedText,
        String affiliateUrl,
        OffsetDateTime createdAt
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
                generatedText
            );

            statement.setString(
                6,
                affiliateUrl
            );

            statement.setString(
                7,
                status
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
