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
 * Verifica o contrato estrutural introduzido pela migration V27.
 */
class PublicationAttemptOutboxMigrationTest {

    @Test
    void shouldLinkPublicationAttemptToPublicationOutbox()
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

            assertMigrationVersionTwentySevenApplied(
                connection
            );

            assertOutboxColumnExists(
                connection
            );

            assertConstraints(
                connection
            );

            assertIndexes(
                connection
            );
        }
    }

    private void assertMigrationVersionTwentySevenApplied(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '27'
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

    private void assertOutboxColumnExists(
        Connection connection
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
              AND column_name = 'publication_outbox_id'
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
                "publication_outbox_id",
                resultSet.getString(
                    "column_name"
                )
            );

            assertEquals(
                "YES",
                resultSet.getString(
                    "is_nullable"
                )
            );

            assertEquals(
                "bigint",
                resultSet.getString(
                    "data_type"
                )
            );
        }
    }

    private void assertConstraints(
        Connection connection
    ) throws Exception {

        Set<String> constraints =
            loadConstraints(
                connection
            );

        assertTrue(
            constraints.contains(
                "fk_publication_attempt_outbox"
            )
        );

        assertTrue(
            constraints.contains(
                "uq_publication_attempt_outbox_attempt"
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

    private void assertIndexes(
        Connection connection
    ) throws Exception {

        Set<String> indexes =
            loadIndexes(
                connection
            );

        assertTrue(
            indexes.contains(
                "idx_publication_attempt_outbox"
            )
        );

        /*
         * PostgreSQL também cria automaticamente um índice para
         * a constraint UNIQUE.
         */
        assertTrue(
            indexes.contains(
                "uq_publication_attempt_outbox_attempt"
            )
        );
    }

    private Set<String> loadIndexes(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename = 'publication_attempt'
            """;

        Set<String> indexes =
            new HashSet<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );
             ResultSet resultSet =
                 statement.executeQuery()) {

            while (resultSet.next()) {

                indexes.add(
                    resultSet.getString(
                        "indexname"
                    )
                );
            }
        }

        return indexes;
    }
}
