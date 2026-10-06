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
 * Verifica o contrato final das colunas de timing originalmente
 * introduzidas pela V28 e posteriormente evoluídas pela V35.
 *
 * <p>started_at continua obrigatório.</p>
 *
 * <p>finished_at passa a aceitar NULL exclusivamente porque uma
 * tentativa STARTED ainda não possui resultado externo final.</p>
 */
class PublicationAttemptTimingMigrationTest {

    @Test
    void shouldPreserveAttemptTimingAuditContract()
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

            assertMigrationVersionThirtyFiveApplied(
                connection
            );

            assertTimingColumn(
                connection,
                "started_at",
                "NO"
            );

            assertTimingColumn(
                connection,
                "finished_at",
                "YES"
            );

            assertTimingConstraint(
                connection
            );
        }
    }

    private void assertMigrationVersionTwentyEightApplied(
        Connection connection
    ) throws Exception {

        assertMigrationApplied(
            connection,
            "28"
        );
    }

    private void assertMigrationVersionThirtyFiveApplied(
        Connection connection
    ) throws Exception {

        assertMigrationApplied(
            connection,
            "35"
        );
    }

    private void assertMigrationApplied(
        Connection connection,
        String version
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = ?
              AND success = true
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                version
            );

            try (ResultSet resultSet =
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
    }

    private void assertTimingColumn(
        Connection connection,
        String columnName,
        String expectedNullable
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
                    expectedNullable,
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
