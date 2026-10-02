package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.PublicationStatus;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcSuccessfulPublicationHistoryQueryAdapterTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String DESTINATION =
        "@fase18_ofertas";

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2099-06-18T12:00:00-03:00"
        );

    @Test
    void shouldAggregateOnlySuccessfulAttemptsForRequestedScope()
        throws Exception {

        inTransaction(
            connection -> {

                PersistedContext matching =
                    insertContext(
                        connection,
                        "B0HIST1801",
                        "Produto histórico principal"
                    );

                PersistedContext differentScope =
                    insertContext(
                        connection,
                        "B0HIST1802",
                        "Produto outro escopo"
                    );

                PersistedContext failedOnly =
                    insertContext(
                        connection,
                        "B0HIST1803",
                        "Produto somente falha"
                    );

                long firstPublicationId =
                    insertPublication(
                        connection,
                        matching.evaluationId(),
                        PublicationStatus.PUBLISHED,
                        BASE_TIME.minusHours(
                            4
                        ),
                        "TEMPLATE_HISTORY_1"
                    );

                long secondPublicationId =
                    insertPublication(
                        connection,
                        matching.evaluationId(),
                        PublicationStatus.PUBLISHED,
                        BASE_TIME.minusHours(
                            2
                        ),
                        "TEMPLATE_HISTORY_2"
                    );

                insertAttempt(
                    connection,
                    firstPublicationId,
                    CHANNEL,
                    DESTINATION,
                    1,
                    "SUCCESS",
                    BASE_TIME.minusHours(
                        3
                    )
                );

                /*
                 * Uma segunda tentativa SUCCESS para a mesma
                 * Publication não pode inflar a quantidade de
                 * publicações distintas.
                 */
                insertAttempt(
                    connection,
                    firstPublicationId,
                    CHANNEL,
                    DESTINATION,
                    2,
                    "SUCCESS",
                    BASE_TIME.minusHours(
                        2
                    )
                );

                insertAttempt(
                    connection,
                    secondPublicationId,
                    CHANNEL,
                    DESTINATION,
                    1,
                    "SUCCESS",
                    BASE_TIME.minusHours(
                        1
                    )
                );

                /*
                 * Uma falha posterior não altera a data do último
                 * sucesso.
                 */
                insertAttempt(
                    connection,
                    secondPublicationId,
                    CHANNEL,
                    DESTINATION,
                    2,
                    "FAILED_TRANSIENT",
                    BASE_TIME
                );

                long differentScopePublicationId =
                    insertPublication(
                        connection,
                        differentScope.evaluationId(),
                        PublicationStatus.PUBLISHED,
                        BASE_TIME.minusMinutes(
                            30
                        ),
                        "TEMPLATE_OTHER_SCOPE"
                    );

                insertAttempt(
                    connection,
                    differentScopePublicationId,
                    CHANNEL,
                    "@outro_destino",
                    1,
                    "SUCCESS",
                    BASE_TIME.minusMinutes(
                        20
                    )
                );

                insertAttempt(
                    connection,
                    differentScopePublicationId,
                    "WHATSAPP",
                    DESTINATION,
                    2,
                    "SUCCESS",
                    BASE_TIME.minusMinutes(
                        10
                    )
                );

                long failedOnlyPublicationId =
                    insertPublication(
                        connection,
                        failedOnly.evaluationId(),
                        PublicationStatus.FAILED,
                        BASE_TIME.minusMinutes(
                            5
                        ),
                        "TEMPLATE_FAILED_ONLY"
                    );

                insertAttempt(
                    connection,
                    failedOnlyPublicationId,
                    CHANNEL,
                    DESTINATION,
                    1,
                    "FAILED_TRANSIENT",
                    BASE_TIME.minusMinutes(
                        1
                    )
                );

                JdbcSuccessfulPublicationHistoryQueryAdapter adapter =
                    new JdbcSuccessfulPublicationHistoryQueryAdapter(
                        connection
                    );

                Map<Asin, SuccessfulPublicationHistory> result =
                    adapter.findSuccessfulByAsins(
                        Set.of(
                            new Asin(
                                "B0HIST1801"
                            ),
                            new Asin(
                                "B0HIST1802"
                            ),
                            new Asin(
                                "B0HIST1803"
                            )
                        ),
                        CHANNEL,
                        DESTINATION
                    );

                assertEquals(
                    1,
                    result.size()
                );

                Asin expectedAsin =
                    new Asin(
                        "B0HIST1801"
                    );

                assertTrue(
                    result.containsKey(
                        expectedAsin
                    )
                );

                SuccessfulPublicationHistory history =
                    result.get(
                        expectedAsin
                    );

                assertEquals(
                    expectedAsin,
                    history.asin()
                );

                assertEquals(
                    BASE_TIME.minusHours(
                            1
                        )
                        .toInstant(),
                    history.lastSuccessfulPublicationAt()
                );

                assertEquals(
                    2L,
                    history.successfulPublicationCount()
                );

                assertFalse(
                    result.containsKey(
                        new Asin(
                            "B0HIST1802"
                        )
                    )
                );

                assertFalse(
                    result.containsKey(
                        new Asin(
                            "B0HIST1803"
                        )
                    )
                );
            }
        );
    }

    @Test
    void shouldReturnEmptyMapForEmptyAsinSet()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcSuccessfulPublicationHistoryQueryAdapter adapter =
                    new JdbcSuccessfulPublicationHistoryQueryAdapter(
                        connection
                    );

                Map<Asin, SuccessfulPublicationHistory> result =
                    adapter.findSuccessfulByAsins(
                        Set.of(),
                        CHANNEL,
                        DESTINATION
                    );

                assertTrue(
                    result.isEmpty()
                );
            }
        );
    }

    @Test
    void shouldRejectInvalidInput()
        throws Exception {

        assertThrows(
            NullPointerException.class,
            () ->
                new JdbcSuccessfulPublicationHistoryQueryAdapter(
                    null
                )
        );

        inTransaction(
            connection -> {

                JdbcSuccessfulPublicationHistoryQueryAdapter adapter =
                    new JdbcSuccessfulPublicationHistoryQueryAdapter(
                        connection
                    );

                assertThrows(
                    NullPointerException.class,
                    () ->
                        adapter.findSuccessfulByAsins(
                            null,
                            CHANNEL,
                            DESTINATION
                        )
                );

                assertThrows(
                    NullPointerException.class,
                    () ->
                        adapter.findSuccessfulByAsins(
                            Set.of(
                                new Asin(
                                    "B0HIST1804"
                                )
                            ),
                            null,
                            DESTINATION
                        )
                );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        adapter.findSuccessfulByAsins(
                            Set.of(
                                new Asin(
                                    "B0HIST1804"
                                )
                            ),
                            "   ",
                            DESTINATION
                        )
                );

                assertThrows(
                    NullPointerException.class,
                    () ->
                        adapter.findSuccessfulByAsins(
                            Set.of(
                                new Asin(
                                    "B0HIST1804"
                                )
                            ),
                            CHANNEL,
                            null
                        )
                );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        adapter.findSuccessfulByAsins(
                            Set.of(
                                new Asin(
                                    "B0HIST1804"
                                )
                            ),
                            CHANNEL,
                            ""
                        )
                );

                Set<Asin> setWithNull =
                    new HashSet<>();

                setWithNull.add(
                    new Asin(
                        "B0HIST1804"
                    )
                );

                setWithNull.add(
                    null
                );

                assertThrows(
                    NullPointerException.class,
                    () ->
                        adapter.findSuccessfulByAsins(
                            setWithNull,
                            CHANNEL,
                            DESTINATION
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
                BASE_TIME.minusHours(
                    6
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
                "TEST_PHASE_18_HISTORY"
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
                "TEST_PHASE_18_ELIGIBILITY"
            );

            statement.setString(
                5,
                "TEST_PHASE_18_FILTER"
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "80.0000"
                )
            );

            statement.setString(
                7,
                "TEST_PHASE_18_SCORE"
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
                BASE_TIME.minusHours(
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
                "COMMERCIAL_PRESENTATION_PHASE_18"
            );

            statement.setString(
                4,
                "AFFILIATE_LINK_PHASE_18"
            );

            statement.setString(
                5,
                "Texto de teste da FASE 18"
            );

            statement.setString(
                6,
                "https://www.amazon.com.br/dp/test?tag=phase18"
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

    private void insertAttempt(
        Connection connection,
        long publicationId,
        String channel,
        String target,
        int attemptNumber,
        String status,
        OffsetDateTime createdAt
    ) throws Exception {

        /*
         * Este fixture testa agregação de histórico, não duração.
         *
         * Antes da V28, apenas created_at era necessário.
         * A partir da V28, started_at e finished_at fazem parte
         * do contrato obrigatório de PublicationAttempt.
         *
         * Como este teste representa tentativas históricas
         * instantâneas, usamos o mesmo instante nos três campos.
         */
        String sql =
            """
            INSERT INTO publication_attempt (
                publication_id,
                channel,
                target,
                attempt_number,
                status,
                provider_reference,
                error_code,
                started_at,
                finished_at,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                publicationId
            );

            statement.setString(
                2,
                channel
            );

            statement.setString(
                3,
                target
            );

            statement.setInt(
                4,
                attemptNumber
            );

            statement.setString(
                5,
                status
            );

            statement.setObject(
                6,
                null
            );

            statement.setObject(
                7,
                null
            );

            statement.setObject(
                8,
                createdAt
            );

            statement.setObject(
                9,
                createdAt
            );

            statement.setObject(
                10,
                createdAt
            );

            statement.executeUpdate();
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
