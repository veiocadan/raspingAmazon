package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.observability.IntegrationObservation;
import com.raspingamazon.application.observability.IntegrationObservationOutcome;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcIntegrationObservationPersistenceAdapterTest {

    private static final OffsetDateTime OBSERVED_AT =
        OffsetDateTime.parse(
            "2026-09-26T13:30:00Z"
        );

    @Test
    void shouldPersistObservationAndReturnGeneratedId()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long observationId =
            0L;

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                assertTrue(
                    connection.getAutoCommit()
                );

                JdbcIntegrationObservationPersistenceAdapter adapter =
                    new JdbcIntegrationObservationPersistenceAdapter(
                        connection
                    );

                OperationalLogContext context =
                    new OperationalLogContext(
                        null,
                        null,
                        ProcessingJobType.COLLECT_DEALS,
                        null,
                        null,
                        null,
                        null,
                        "B0OBSERVE01",
                        "amazon-deals-http"
                    );

                IntegrationObservation persisted =
                    adapter.save(
                        new IntegrationObservation(
                            null,
                            OBSERVED_AT,
                            "amazon-deals-http",
                            "GET",
                            IntegrationObservationOutcome.FAILURE,
                            450L,
                            context,
                            OperationalFailureOrigin.EXTERNAL,
                            ProcessingFailureType.TRANSIENT,
                            "COLLECTION_HTTP_503",
                            503
                        )
                    );

                assertNotNull(
                    persisted.id()
                );

                assertTrue(
                    persisted.id() > 0L
                );

                observationId =
                    persisted.id();

                /*
                 * O adapter era proprietário da pequena transação de
                 * observabilidade e deve restaurar autoCommit.
                 */
                assertTrue(
                    connection.getAutoCommit()
                );

                assertPersistedObservation(
                    connection,
                    persisted.id()
                );
            }

        } finally {

            deleteObservation(
                config,
                observationId
            );
        }
    }

    @Test
    void shouldParticipateInCallerTransactionWithoutCommittingIt()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long observationId =
            0L;

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                connection.setAutoCommit(
                    false
                );

                JdbcIntegrationObservationPersistenceAdapter adapter =
                    new JdbcIntegrationObservationPersistenceAdapter(
                        connection
                    );

                IntegrationObservation persisted =
                    adapter.save(
                        successfulObservation()
                    );

                observationId =
                    persisted.id();

                /*
                 * A conexão continua pertencendo à transação externa.
                 */
                assertFalse(
                    connection.getAutoCommit()
                );

                /*
                 * O INSERT é visível dentro da própria transação.
                 */
                assertTrue(
                    observationExists(
                        connection,
                        observationId
                    )
                );

                /*
                 * Esta é a prova de que save() não executou commit da
                 * transação pertencente ao chamador.
                 */
                connection.rollback();

                assertFalse(
                    observationExists(
                        connection,
                        observationId
                    )
                );

                connection.setAutoCommit(
                    true
                );
            }

        } finally {

            /*
             * Limpeza defensiva caso uma regressão tenha executado
             * commit indevidamente antes da asserção.
             */
            deleteObservation(
                config,
                observationId
            );
        }
    }

    @Test
    void shouldRollbackOnlyFailedObservationInsideCallerTransaction()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String outerAsin =
            "B0OBSOUT01";

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                connection.setAutoCommit(
                    false
                );

                /*
                 * Trabalho funcional anterior à observabilidade.
                 *
                 * Se o adapter de observabilidade fizer rollback total
                 * por engano, esta linha desaparecerá.
                 */
                insertProduct(
                    connection,
                    outerAsin
                );

                assertTrue(
                    productExists(
                        connection,
                        outerAsin
                    )
                );

                JdbcIntegrationObservationPersistenceAdapter adapter =
                    new JdbcIntegrationObservationPersistenceAdapter(
                        connection
                    );

                /*
                 * O modelo aceita um runId positivo, mas o PostgreSQL
                 * rejeitará o FK inexistente.
                 *
                 * Isso provoca uma falha JDBC real dentro do savepoint.
                 */
                OperationalLogContext invalidContext =
                    new OperationalLogContext(
                        Long.MAX_VALUE,
                        null,
                        ProcessingJobType.COLLECT_DEALS,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "amazon-deals-http"
                    );

                IntegrationObservation invalidObservation =
                    new IntegrationObservation(
                        null,
                        OBSERVED_AT,
                        "amazon-deals-http",
                        "GET",
                        IntegrationObservationOutcome.SUCCESS,
                        10L,
                        invalidContext,
                        null,
                        null,
                        null,
                        200
                    );

                assertThrows(
                    PersistenceOperationException.class,
                    () -> adapter.save(
                        invalidObservation
                    )
                );

                /*
                 * A transação externa continua aberta e utilizável.
                 */
                assertFalse(
                    connection.getAutoCommit()
                );

                /*
                 * Principal garantia da 16.5B:
                 *
                 * a falha da observabilidade foi desfeita até o
                 * savepoint sem destruir o trabalho funcional que
                 * já existia na transação.
                 */
                assertTrue(
                    productExists(
                        connection,
                        outerAsin
                    )
                );

                /*
                 * Também prova que a conexão PostgreSQL não ficou no
                 * estado "current transaction is aborted".
                 */
                assertTrue(
                    databaseIsUsable(
                        connection
                    )
                );

                connection.rollback();

                assertFalse(
                    productExists(
                        connection,
                        outerAsin
                    )
                );

                connection.setAutoCommit(
                    true
                );
            }

        } finally {

            deleteProductByAsin(
                config,
                outerAsin
            );
        }
    }

    @Test
    void shouldRejectObservationThatAlreadyHasId()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcIntegrationObservationPersistenceAdapter adapter =
                new JdbcIntegrationObservationPersistenceAdapter(
                    connection
                );

            IntegrationObservation alreadyPersisted =
                new IntegrationObservation(
                    10L,
                    OBSERVED_AT,
                    "amazon-deals-http",
                    "GET",
                    IntegrationObservationOutcome.SUCCESS,
                    25L,
                    OperationalLogContext.empty(),
                    null,
                    null,
                    null,
                    200
                );

            assertThrows(
                IllegalArgumentException.class,
                () -> adapter.save(
                    alreadyPersisted
                )
            );
        }
    }

    private IntegrationObservation successfulObservation() {

        return new IntegrationObservation(
            null,
            OBSERVED_AT,
            "amazon-product-page",
            "GET",
            IntegrationObservationOutcome.SUCCESS,
            125L,
            OperationalLogContext.empty(),
            null,
            null,
            null,
            200
        );
    }

    private void assertPersistedObservation(
        Connection connection,
        long observationId
    ) throws Exception {

        String sql =
            """
            SELECT
                observed_at,
                integration,
                operation,
                outcome,
                duration_ms,
                job_type,
                asin,
                failure_origin,
                failure_type,
                error_code,
                http_status_code
            FROM integration_observation
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                observationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    OBSERVED_AT,
                    resultSet.getObject(
                        "observed_at",
                        OffsetDateTime.class
                    )
                );

                assertEquals(
                    "amazon-deals-http",
                    resultSet.getString(
                        "integration"
                    )
                );

                assertEquals(
                    "GET",
                    resultSet.getString(
                        "operation"
                    )
                );

                assertEquals(
                    "FAILURE",
                    resultSet.getString(
                        "outcome"
                    )
                );

                assertEquals(
                    450L,
                    resultSet.getLong(
                        "duration_ms"
                    )
                );

                assertEquals(
                    "COLLECT_DEALS",
                    resultSet.getString(
                        "job_type"
                    )
                );

                assertEquals(
                    "B0OBSERVE01",
                    resultSet.getString(
                        "asin"
                    )
                );

                assertEquals(
                    "EXTERNAL",
                    resultSet.getString(
                        "failure_origin"
                    )
                );

                assertEquals(
                    "TRANSIENT",
                    resultSet.getString(
                        "failure_type"
                    )
                );

                assertEquals(
                    "COLLECTION_HTTP_503",
                    resultSet.getString(
                        "error_code"
                    )
                );

                assertEquals(
                    503,
                    resultSet.getInt(
                        "http_status_code"
                    )
                );

                assertFalse(
                    resultSet.next()
                );
            }
        }
    }

    private boolean observationExists(
        Connection connection,
        long observationId
    ) throws Exception {

        String sql =
            """
            SELECT EXISTS (
                SELECT 1
                FROM integration_observation
                WHERE id = ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                observationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getBoolean(
                    1
                );
            }
        }
    }

    private void insertProduct(
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
            VALUES (?, ?, ?, ?)
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
                "Produto transação externa"
            );

            statement.setObject(
                3,
                null
            );

            statement.setString(
                4,
                "https://example.invalid/" + asin
            );

            statement.executeUpdate();
        }
    }

    private boolean productExists(
        Connection connection,
        String asin
    ) throws Exception {

        String sql =
            """
            SELECT EXISTS (
                SELECT 1
                FROM product
                WHERE asin = ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getBoolean(
                    1
                );
            }
        }
    }

    private boolean databaseIsUsable(
        Connection connection
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     "SELECT 1"
                 );

             ResultSet resultSet =
                 statement.executeQuery()) {

            assertTrue(
                resultSet.next()
            );

            return resultSet.getInt(
                1
            ) == 1;
        }
    }

    private void deleteObservation(
        ApplicationConfig config,
        long observationId
    ) throws Exception {

        if (observationId <= 0L) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );

             PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM integration_observation
                     WHERE id = ?
                     """
                 )) {

            statement.setLong(
                1,
                observationId
            );

            statement.executeUpdate();
        }
    }

    private void deleteProductByAsin(
        ApplicationConfig config,
        String asin
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );

             PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM product
                     WHERE asin = ?
                     """
                 )) {

            statement.setString(
                1,
                asin
            );

            statement.executeUpdate();
        }
    }
}
