package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcEnrichedOfferLookupAdapterTest {

    private static final String ASIN =
        "B0LOOKUP01";

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-22T20:00:00-03:00"
        );

    private static final String SOURCE =
        "https://www.amazon.com.br/deals";

    @Test
    void shouldFindPersistedSnapshotForCandidate()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long productId =
            0L;

        long snapshotId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            productId =
                insertProduct(
                    connection
                );

            snapshotId =
                insertSnapshot(
                    connection,
                    productId
                );

            JdbcEnrichedOfferLookupAdapter adapter =
                new JdbcEnrichedOfferLookupAdapter(
                    connection
                );

            OptionalLong result =
                adapter.findSnapshotId(
                    createCandidate()
                );

            assertTrue(
                result.isPresent()
            );

            assertEquals(
                snapshotId,
                result.getAsLong()
            );

        } finally {

            deleteTestData(
                config,
                snapshotId,
                productId
            );
        }
    }

    @Test
    void shouldReturnEmptyWhenSnapshotDoesNotExist()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcEnrichedOfferLookupAdapter adapter =
                new JdbcEnrichedOfferLookupAdapter(
                    connection
                );

            OptionalLong result =
                adapter.findSnapshotId(
                    createCandidate()
                );

            assertTrue(
                result.isEmpty()
            );
        }
    }

    private DealCandidate createCandidate() {

        ParsedDeal parsedDeal =
            new ParsedDeal(
                ASIN,
                "https://www.amazon.com.br/dp/" + ASIN,
                "Produto lookup",
                "https://example.invalid/image.jpg",
                new BigDecimal(
                    "99.90"
                ),
                new BigDecimal(
                    "129.90"
                ),
                new BigDecimal(
                    "119.90"
                ),
                new BigDecimal(
                    "42.00"
                ),
                4.6,
                58363L,
                COLLECTED_AT,
                SOURCE
            );

        return new DealCandidate(
            100L,
            10L,
            parsedDeal
        );
    }

    private long insertProduct(
        Connection connection
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
                ASIN
            );

            statement.setString(
                2,
                "Produto lookup"
            );

            statement.setString(
                3,
                "https://example.invalid/image.jpg"
            );

            statement.setString(
                4,
                "https://www.amazon.com.br/dp/" + ASIN
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "Product insert returned no row"
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
                sold_percentage,
                rating,
                review_count,
                seller_name,
                delivery_provider,
                source
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                COLLECTED_AT
            );

            statement.setBigDecimal(
                3,
                new BigDecimal(
                    "99.90"
                )
            );

            statement.setBigDecimal(
                4,
                new BigDecimal(
                    "42.00"
                )
            );

            statement.setDouble(
                5,
                4.6
            );

            statement.setLong(
                6,
                58363L
            );

            statement.setString(
                7,
                "Amazon.com.br"
            );

            statement.setString(
                8,
                "Amazon.com.br"
            );

            statement.setString(
                9,
                SOURCE
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "OfferSnapshot insert returned no row"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void deleteTestData(
        ApplicationConfig config,
        long snapshotId,
        long productId
    ) {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            if (snapshotId > 0) {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_job
                             WHERE offer_snapshot_id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        snapshotId
                    );

                    statement.executeUpdate();
                }

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM deal_evaluation
                             WHERE offer_snapshot_id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        snapshotId
                    );

                    statement.executeUpdate();
                }

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM offer_snapshot
                             WHERE id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        snapshotId
                    );

                    statement.executeUpdate();
                }
            }

            if (productId > 0) {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM product
                             WHERE id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        productId
                    );

                    statement.executeUpdate();
                }
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean enriched lookup test data",
                exception
            );
        }
    }
}
