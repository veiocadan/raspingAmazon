package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.history.OfferHistoryStatus;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class OfferHistoryStatusJdbcRepositoryTest {

    private static final Asin ASIN =
        new Asin(
            "B0HISTG101"
        );

    @Test
    void shouldReturnHistoricalStatusAndOnlyTreatPublishedAsAlreadyPublished()
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

                Product product =
                    createProduct(
                        connection
                    );

                OffsetDateTime firstCollectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T10:00:00-03:00"
                    );

                OffsetDateTime secondCollectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T13:00:00-03:00"
                    );

                OfferSnapshotRepository snapshotRepository =
                    new OfferSnapshotRepository(
                        connection
                    );

                long firstSnapshotId =
                    snapshotRepository.insert(
                        snapshot(
                            product,
                            firstCollectedAt,
                            "100.00",
                            "62"
                        )
                    );

                long secondSnapshotId =
                    snapshotRepository.insert(
                        snapshot(
                            product,
                            secondCollectedAt,
                            "90.00",
                            "68"
                        )
                    );

                DealEvaluation firstEvaluation =
                    persistEvaluation(
                        connection,
                        persistedSnapshot(
                            firstSnapshotId,
                            product,
                            firstCollectedAt,
                            "100.00",
                            "62"
                        )
                    );

                /*
                 * READY ainda não significa publicação concluída.
                 */
                long publicationId =
                    insertPublication(
                        connection,
                        firstEvaluation.id(),
                        "READY"
                    );

                OfferHistoryStatusJdbcRepository repository =
                    new OfferHistoryStatusJdbcRepository(
                        connection
                    );

                OfferHistoryStatus readyStatus =
                    repository.findByAsin(
                        ASIN
                    ).orElseThrow();

                assertEquals(
                    2L,
                    readyStatus.snapshotCount()
                );

                assertTrue(
                    readyStatus.recurring()
                );

                assertEquals(
                    firstCollectedAt.toInstant(),
                    readyStatus.firstDetectedAt()
                        .toInstant()
                );

                assertEquals(
                    secondCollectedAt.toInstant(),
                    readyStatus.lastUpdatedAt()
                        .toInstant()
                );

                /*
                 * READY não conta como "já publicada".
                 */
                assertFalse(
                    readyStatus.publishedBefore()
                );

                OffsetDateTime referenceTime =
                    OffsetDateTime.parse(
                        "2026-09-20T16:00:00-03:00"
                    );

                assertEquals(
                    Duration.ofHours(
                        6
                    ),
                    readyStatus
                        .timeSinceFirstDetection(
                            referenceTime
                        )
                        .orElseThrow()
                );

                assertEquals(
                    Duration.ofHours(
                        3
                    ),
                    readyStatus
                        .timeSinceLastUpdate(
                            referenceTime
                        )
                        .orElseThrow()
                );

                /*
                 * A mesma Publication passa agora ao estado
                 * semanticamente conclusivo.
                 */
                updatePublicationStatus(
                    connection,
                    publicationId,
                    "PUBLISHED"
                );

                OfferHistoryStatus publishedStatus =
                    repository.findByAsin(
                        ASIN
                    ).orElseThrow();

                assertTrue(
                    publishedStatus.publishedBefore()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldReturnEmptyForUnknownAsin()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();


        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            OfferHistoryStatusJdbcRepository repository =
                new OfferHistoryStatusJdbcRepository(
                    connection
                );

            Optional<OfferHistoryStatus> result =
                repository.findByAsin(
                    new Asin(
                        "B0HISTG199"
                    )
                );

            assertTrue(
                result.isEmpty()
            );
        }
    }

    private Product createProduct(
        Connection connection
    ) throws Exception {

        ProductRepository repository =
            new ProductRepository(
                connection
            );

        long productId =
            repository.insert(
                ASIN.value(),
                "Produto histórico operacional",
                null,
                "https://example.invalid/history-status"
            );

        return new Product(
            productId,
            ASIN,
            "Produto histórico operacional",
            null,
            "https://example.invalid/history-status"
        );
    }

    private OfferSnapshot snapshot(
        Product product,
        OffsetDateTime collectedAt,
        String currentPrice,
        String soldPercentage
    ) {

        return new OfferSnapshot(
            null,
            product,
            collectedAt,
            Money.of(
                currentPrice
            ),
            null,
            null,
            Percentage.of(
                soldPercentage
            ),
            4.8,
            2000L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "history-status-test",
            List.of()
        );
    }

    private OfferSnapshot persistedSnapshot(
        long id,
        Product product,
        OffsetDateTime collectedAt,
        String currentPrice,
        String soldPercentage
    ) {

        return new OfferSnapshot(
            id,
            product,
            collectedAt,
            Money.of(
                currentPrice
            ),
            null,
            null,
            Percentage.of(
                soldPercentage
            ),
            4.8,
            2000L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "history-status-test",
            List.of()
        );
    }

    private DealEvaluation persistEvaluation(
        Connection connection,
        OfferSnapshot snapshot
    ) {

        DealEvaluation evaluation =
            new DealEvaluation(
                null,
                snapshot,
                true,
                null,
                "AMAZON_SELLER_DELIVERY_V1",
                null,
                List.of(
                    EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    ),
                    EvaluationRuleResult.passed(
                        "DELIVERY_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    )
                ),
                null,
                null,
                null,
                null,
                OffsetDateTime.parse(
                    "2026-09-20T13:01:00-03:00"
                )
            );

        return new DealEvaluationJdbcRepository(
            connection
        ).save(
            evaluation
        );
    }

    private long insertPublication(
        Connection connection,
        long dealEvaluationId,
        String status
    ) throws Exception {

        String sql = """
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
                dealEvaluationId
            );

            statement.setString(
                2,
                "TEST_TEMPLATE_V1"
            );

            statement.setString(
                3,
                "TEST_COMMERCIAL_PRESENTATION_V1"
            );

            statement.setString(
                4,
                "TEST_AFFILIATE_LINK_V1"
            );

            statement.setString(
                5,
                "Publicação controlada pelo teste"
            );

            statement.setString(
                6,
                "https://example.invalid/affiliate"
            );

            statement.setString(
                7,
                status
            );

            statement.setObject(
                8,
                OffsetDateTime.parse(
                    "2026-09-20T14:00:00-03:00"
                )
            );

            try (var resultSet =
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

    private void updatePublicationStatus(
        Connection connection,
        long publicationId,
        String status
    ) throws Exception {

        String sql = """
            UPDATE publication
            SET status = ?
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                status
            );

            statement.setLong(
                2,
                publicationId
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }
}
