package com.raspingamazon.infrastructure.migration;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica o contrato estrutural introduzido pela V35.
 */
class PublicationDeliveryUnknownMigrationTest {

    @Test
    void shouldInstallDeliveryUnknownPersistenceContract()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            assertMigrationApplied(
                connection
            );

            assertConstraintContains(
                connection,
                "publication_outbox",
                "ck_publication_outbox_status",
                "DELIVERY_UNKNOWN"
            );

            assertConstraintContains(
                connection,
                "publication_attempt",
                "ck_publication_attempt_status",
                "STARTED"
            );

            assertConstraintContains(
                connection,
                "publication_attempt",
                "ck_publication_attempt_status",
                "DELIVERY_UNKNOWN"
            );

            assertConstraintContains(
                connection,
                "publication_attempt",
                "ck_publication_attempt_timing",
                "STARTED"
            );

            assertIndexExists(
                connection,
                "uq_publication_attempt_active_started"
            );

            assertIndexExists(
                connection,
                "idx_publication_attempt_started_outbox"
            );
        }
    }

    private void assertMigrationApplied(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '35'
              AND success = true
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );
             ResultSet resultSet =
                 statement.executeQuery()) {

            assertTrue(
                resultSet.next()
            );

            assertEquals(
                1L,
                resultSet.getLong(
                    "migration_count"
                )
            );
        }
    }

    private void assertConstraintContains(
        Connection connection,
        String tableName,
        String constraintName,
        String expectedFragment
    ) throws Exception {

        String sql =
            """
            SELECT pg_get_constraintdef(
                con.oid
            ) AS constraint_definition
            FROM pg_constraint con
            JOIN pg_class rel
              ON rel.oid = con.conrelid
            JOIN pg_namespace nsp
              ON nsp.oid = rel.relnamespace
            WHERE nsp.nspname = 'public'
              AND rel.relname = ?
              AND con.conname = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                tableName
            );

            statement.setString(
                2,
                constraintName
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                String definition =
                    resultSet.getString(
                        "constraint_definition"
                    );

                assertTrue(
                    definition.contains(
                        expectedFragment
                    ),
                    () ->
                        "Constraint "
                            + constraintName
                            + " must contain "
                            + expectedFragment
                            + ", but was: "
                            + definition
                );
            }
        }
    }

    private void assertIndexExists(
        Connection connection,
        String indexName
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS index_count
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                indexName
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    1L,
                    resultSet.getLong(
                        "index_count"
                    )
                );
            }
        }
    }
}
