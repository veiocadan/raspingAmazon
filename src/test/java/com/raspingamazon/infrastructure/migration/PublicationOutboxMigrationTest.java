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
 * Verifica o contrato estrutural introduzido pela migration V26.
 */
class PublicationOutboxMigrationTest {

    @Test
    void shouldCreatePublicationOutboxSchema()
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

            assertMigrationVersionTwentySixApplied(
                connection
            );

            assertTableExists(
                connection,
                "publication_outbox"
            );

            assertColumns(
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

    private void assertMigrationVersionTwentySixApplied(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '26'
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

    private void assertTableExists(
        Connection connection,
        String tableName
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS table_count
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                tableName
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    1L,
                    resultSet.getLong(
                        "table_count"
                    )
                );
            }
        }
    }

    private void assertColumns(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'publication_outbox'
            """;

        Set<String> actualColumns =
            new HashSet<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );
             ResultSet resultSet =
                 statement.executeQuery()) {

            while (resultSet.next()) {

                actualColumns.add(
                    resultSet.getString(
                        "column_name"
                    )
                );
            }
        }

        assertEquals(
            Set.of(
                "id",
                "publication_id",
                "selection_run_id",
                "selection_position",
                "channel",
                "destination",
                "content",
                "quota_profile_version",
                "quota_date",
                "status",
                "available_at",
                "locked_at",
                "locked_by",
                "created_at",
                "updated_at",
                "finished_at"
            ),
            actualColumns
        );
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
                "fk_publication_outbox_publication"
            )
        );

        assertTrue(
            constraints.contains(
                "fk_publication_outbox_selection_run"
            )
        );

        assertTrue(
            constraints.contains(
                "fk_publication_outbox_quota_profile"
            )
        );

        assertTrue(
            constraints.contains(
                "uq_publication_outbox_delivery_identity"
            )
        );

        assertTrue(
            constraints.contains(
                "uq_publication_outbox_selection_position"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_outbox_selection_position"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_outbox_channel"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_outbox_destination"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_outbox_content"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_outbox_quota_profile_version"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_outbox_status"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_outbox_lock_state"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_outbox_finished_state"
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
              AND rel.relname = 'publication_outbox'
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
                "idx_publication_outbox_quota_scope"
            )
        );

        assertTrue(
            indexes.contains(
                "idx_publication_outbox_claim"
            )
        );

        assertTrue(
            indexes.contains(
                "idx_publication_outbox_processing_lease"
            )
        );

        assertTrue(
            indexes.contains(
                "idx_publication_outbox_selection_run"
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
              AND tablename = 'publication_outbox'
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
