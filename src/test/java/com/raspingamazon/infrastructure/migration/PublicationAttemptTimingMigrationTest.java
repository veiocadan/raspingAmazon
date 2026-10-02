package com.raspingamazon.infrastructure.migration;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica o contrato estrutural introduzido pela migration V28.
 */
class PublicationAttemptTimingMigrationTest {

    @Test
    void shouldAddAttemptTimingAuditColumns()
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

            assertMigrationVersionTwentyEightApplied(
                connection
            );

            assertTimingColumn(
                connection,
                "started_at"
            );

            assertTimingColumn(
                connection,
                "finished_at"
            );

            assertTimingConstraint(
                connection
            );
        }
    }

    private void assertMigrationVersionTwentyEightApplied(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '28'
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

    private void assertTimingColumn(
        Connection connection,
        String columnName
    ) throws Exception {

        String sql =
            """
            SELECT
                column_name,
                is_nullable,
                data_type
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'publication_attempt'
              AND column_name = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                columnName
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    columnName,
                    resultSet.getString(
                        "column_name"
                    )
                );

                assertEquals(
                    "NO",
                    resultSet.getString(
                        "is_nullable"
                    )
                );

                assertEquals(
                    "timestamp with time zone",
                    resultSet.getString(
                        "data_type"
                    )
                );
            }
        }
    }

    private void assertTimingConstraint(
        Connection connection
    ) throws Exception {

        Set<String> constraints =
            loadConstraints(
                connection
            );

        assertTrue(
            constraints.contains(
                "ck_publication_attempt_timing"
            )
        );
    }

    private Set<String> loadConstraints(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class rel
              ON rel.oid = con.conrelid
            JOIN pg_namespace nsp
              ON nsp.oid = rel.relnamespace
            WHERE nsp.nspname = 'public'
              AND rel.relname = 'publication_attempt'
            """;

        Set<String> constraints =
            new HashSet<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );
             ResultSet resultSet =
                 statement.executeQuery()) {

            while (resultSet.next()) {

                constraints.add(
                    resultSet.getString(
                        "conname"
                    )
                );
            }
        }

        return constraints;
    }
}
