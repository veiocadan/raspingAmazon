package com.raspingamazon.infrastructure.migration;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica o índice da FASE 14 destinado à paginação operacional
 * de DealEvaluation.
 */
class OperationalDealEvaluationReadMigrationTest {

    @Test
    void shouldCreateOperationalDealEvaluationOrderIndex()
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

            assertMigrationVersionSeventeenApplied(
                connection
            );

            assertOperationalOrderIndex(
                connection
            );
        }
    }

    private void assertMigrationVersionSeventeenApplied(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '17'
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

            assertFalse(
                resultSet.next()
            );
        }
    }

    private void assertOperationalOrderIndex(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT
                tablename,
                indexdef
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
                "idx_deal_evaluation_operational_order"
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next(),
                    "Operational DealEvaluation index was not found"
                );

                assertEquals(
                    "deal_evaluation",
                    resultSet.getString(
                        "tablename"
                    )
                );

                String indexDefinition =
                    resultSet.getString(
                        "indexdef"
                    );

                assertTrue(
                    indexDefinition.contains(
                        "evaluated_at DESC"
                    ),
                    () ->
                        "Expected evaluated_at DESC in index: "
                            + indexDefinition
                );

                assertTrue(
                    indexDefinition.contains(
                        "id DESC"
                    ),
                    () ->
                        "Expected id DESC in index: "
                            + indexDefinition
                );

                assertFalse(
                    resultSet.next(),
                    "Expected exactly one operational order index"
                );
            }
        }
    }
}
