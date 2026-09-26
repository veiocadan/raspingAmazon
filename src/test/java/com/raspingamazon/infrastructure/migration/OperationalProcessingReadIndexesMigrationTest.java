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

class OperationalProcessingReadIndexesMigrationTest {

    @Test
    void shouldCreateOperationalProcessingIndexes()
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

            assertMigrationVersionEighteenApplied(
                connection
            );

            assertIndex(
                connection,
                "idx_processing_run_operational_order",
                "processing_run",
                List.of(
                    "requested_at DESC",
                    "id DESC"
                )
            );

            assertIndex(
                connection,
                "idx_processing_job_operational_order",
                "processing_job",
                List.of(
                    "created_at DESC",
                    "id DESC"
                )
            );
        }
    }

    private void assertMigrationVersionEighteenApplied(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '18'
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
        List<String> expectedFragments
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

                String definition =
                    resultSet.getString(
                        "indexdef"
                    );

                for (String fragment
                    : expectedFragments) {

                    assertTrue(
                        definition.contains(
                            fragment
                        ),
                        () ->
                            "Index "
                                + indexName
                                + " should contain '"
                                + fragment
                                + "' but definition was: "
                                + definition
                    );
                }

                assertFalse(
                    resultSet.next()
                );
            }
        }
    }
}
