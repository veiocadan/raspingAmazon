package com.raspingamazon.infrastructure.migration;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica os índices introduzidos pela FASE 11 para suportar
 * as consultas históricas.
 *
 * <p>Este teste valida somente o contrato estrutural da migration.
 * A semântica das consultas permanece coberta pelos testes dos
 * repositories.</p>
 */
class HistoricalReadIndexesMigrationTest {

    @Test
    void shouldCreateIndexesForHistoricalReadPaths()
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

            assertMigrationVersionTenApplied(
                connection
            );

            assertIndex(
                connection,
                "idx_offer_snapshot_history",
                "offer_snapshot",
                List.of(
                    "product_id",
                    "collected_at DESC",
                    "id DESC"
                )
            );

            assertIndex(
                connection,
                "idx_deal_evaluation_offer_snapshot",
                "deal_evaluation",
                List.of(
                    "offer_snapshot_id"
                )
            );

            assertIndex(
                connection,
                "idx_publication_evaluation_status",
                "publication",
                List.of(
                    "deal_evaluation_id",
                    "status"
                )
            );
        }
    }

    private void assertMigrationVersionTenApplied(
        Connection connection
    ) throws Exception {

        String sql = """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '10'
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

    private void assertIndex(
        Connection connection,
        String indexName,
        String expectedTable,
        List<String> expectedDefinitionFragments
    ) throws Exception {

        String sql = """
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
                indexName
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next(),
                    "Expected index not found: "
                        + indexName
                );

                assertEquals(
                    expectedTable,
                    resultSet.getString(
                        "tablename"
                    )
                );

                String indexDefinition =
                    resultSet.getString(
                        "indexdef"
                    );

                assertTrue(
                    indexDefinition != null
                        && !indexDefinition.isBlank()
                );

                for (String fragment
                    : expectedDefinitionFragments) {

                    assertTrue(
                        indexDefinition.contains(
                            fragment
                        ),
                        () ->
                            "Index "
                                + indexName
                                + " should contain '"
                                + fragment
                                + "' but definition was: "
                                + indexDefinition
                    );
                }

                assertFalse(
                    resultSet.next(),
                    "Expected exactly one index named "
                        + indexName
                );
            }
        }
    }
}
