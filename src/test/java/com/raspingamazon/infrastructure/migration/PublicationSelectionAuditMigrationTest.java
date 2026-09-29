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
 * Verifica o contrato estrutural introduzido pela migration V25.
 *
 * <p>Este teste valida somente o schema. Persistência e leitura
 * da auditoria serão testadas separadamente na próxima etapa.</p>
 */
class PublicationSelectionAuditMigrationTest {

    @Test
    void shouldCreatePublicationSelectionAuditSchema()
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

            assertMigrationVersionTwentyFiveApplied(
                connection
            );

            assertTableExists(
                connection,
                "publication_selection_profile"
            );

            assertTableExists(
                connection,
                "publication_quota_profile"
            );

            assertTableExists(
                connection,
                "publication_selection_run"
            );

            assertTableExists(
                connection,
                "publication_selection_decision"
            );

            assertSelectionProfileColumns(
                connection
            );

            assertQuotaProfileColumns(
                connection
            );

            assertSelectionRunColumns(
                connection
            );

            assertSelectionDecisionColumns(
                connection
            );

            assertSelectionProfileConstraints(
                connection
            );

            assertQuotaProfileConstraints(
                connection
            );

            assertSelectionRunConstraints(
                connection
            );

            assertSelectionDecisionConstraints(
                connection
            );

            assertRequiredIndexes(
                connection
            );
        }
    }

    private void assertMigrationVersionTwentyFiveApplied(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '25'
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

    private void assertSelectionProfileColumns(
        Connection connection
    ) throws Exception {

        assertColumns(
            connection,
            "publication_selection_profile",
            Set.of(
                "id",
                "channel",
                "destination",
                "version",
                "hard_cooldown_seconds",
                "preferred_cooldown_seconds",
                "active",
                "created_at"
            )
        );
    }

    private void assertQuotaProfileColumns(
        Connection connection
    ) throws Exception {

        assertColumns(
            connection,
            "publication_quota_profile",
            Set.of(
                "id",
                "channel",
                "destination",
                "version",
                "max_publications_per_day",
                "quota_zone",
                "active",
                "created_at"
            )
        );
    }

    private void assertSelectionRunColumns(
        Connection connection
    ) throws Exception {

        assertColumns(
            connection,
            "publication_selection_run",
            Set.of(
                "id",
                "channel",
                "destination",
                "decided_at",
                "selection_profile_version",
                "hard_cooldown_seconds",
                "preferred_cooldown_seconds",
                "quota_profile_version",
                "quota_date",
                "max_publications_per_day",
                "occupied_slots",
                "created_at"
            )
        );
    }

    private void assertSelectionDecisionColumns(
        Connection connection
    ) throws Exception {

        assertColumns(
            connection,
            "publication_selection_decision",
            Set.of(
                "id",
                "selection_run_id",
                "deal_evaluation_id",
                "asin",
                "score",
                "decision_status",
                "recency",
                "priority_position",
                "last_successful_publication_at",
                "successful_publication_count",
                "created_at"
            )
        );
    }

    private void assertColumns(
        Connection connection,
        String tableName,
        Set<String> expectedColumns
    ) throws Exception {

        String sql =
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = ?
            """;

        Set<String> actualColumns =
            new HashSet<>();

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

                while (resultSet.next()) {

                    actualColumns.add(
                        resultSet.getString(
                            "column_name"
                        )
                    );
                }
            }
        }

        assertEquals(
            expectedColumns,
            actualColumns
        );
    }

    private void assertSelectionProfileConstraints(
        Connection connection
    ) throws Exception {

        Set<String> constraints =
            loadConstraints(
                connection,
                "publication_selection_profile"
            );

        assertTrue(
            constraints.contains(
                "uq_publication_selection_profile_scope_version"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_profile_hard_non_negative"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_profile_preferred_valid"
            )
        );
    }

    private void assertQuotaProfileConstraints(
        Connection connection
    ) throws Exception {

        Set<String> constraints =
            loadConstraints(
                connection,
                "publication_quota_profile"
            );

        assertTrue(
            constraints.contains(
                "uq_publication_quota_profile_scope_version"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_quota_profile_max_positive"
            )
        );
    }

    private void assertSelectionRunConstraints(
        Connection connection
    ) throws Exception {

        Set<String> constraints =
            loadConstraints(
                connection,
                "publication_selection_run"
            );

        assertTrue(
            constraints.contains(
                "fk_publication_selection_run_selection_profile"
            )
        );

        assertTrue(
            constraints.contains(
                "fk_publication_selection_run_quota_profile"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_run_hard_non_negative"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_run_preferred_valid"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_run_max_positive"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_run_occupied_non_negative"
            )
        );
    }

    private void assertSelectionDecisionConstraints(
        Connection connection
    ) throws Exception {

        Set<String> constraints =
            loadConstraints(
                connection,
                "publication_selection_decision"
            );

        assertTrue(
            constraints.contains(
                "fk_publication_selection_decision_run"
            )
        );

        assertTrue(
            constraints.contains(
                "fk_publication_selection_decision_evaluation"
            )
        );

        assertTrue(
            constraints.contains(
                "uq_publication_selection_decision_run_evaluation"
            )
        );

        assertTrue(
            constraints.contains(
                "uq_publication_selection_decision_run_position"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_decision_status"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_decision_recency"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_decision_position_positive"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_decision_history"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_publication_selection_decision_status_recency_position"
            )
        );
    }

    private Set<String> loadConstraints(
        Connection connection,
        String tableName
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
              AND rel.relname = ?
            """;

        Set<String> constraints =
            new HashSet<>();

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

                while (resultSet.next()) {

                    constraints.add(
                        resultSet.getString(
                            "conname"
                        )
                    );
                }
            }
        }

        return constraints;
    }

    private void assertRequiredIndexes(
        Connection connection
    ) throws Exception {

        Set<String> indexes =
            loadIndexes(
                connection
            );

        assertTrue(
            indexes.contains(
                "uq_publication_selection_profile_single_active_scope"
            )
        );

        assertTrue(
            indexes.contains(
                "uq_publication_quota_profile_single_active_scope"
            )
        );

        assertTrue(
            indexes.contains(
                "idx_publication_selection_run_scope_decided"
            )
        );

        assertTrue(
            indexes.contains(
                "idx_publication_selection_decision_run_status"
            )
        );

        assertTrue(
            indexes.contains(
                "idx_publication_selection_decision_evaluation_run"
            )
        );

        assertTrue(
            indexes.contains(
                "idx_publication_attempt_success_scope"
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
