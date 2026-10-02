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
 * Verifica o contrato estrutural introduzido pela migration V29.
 *
 * <p>A outbox passa a suportar múltiplas entregas derivadas da
 * mesma posição selecionada, mantendo exatamente uma reserva
 * real de quota por posição.</p>
 */
class PublicationOutboxFanoutMigrationTest {

    @Test
    void shouldSupportQuotaFreeDerivedDeliveries()
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

            assertMigrationVersionTwentyNineApplied(
                connection
            );

            assertQuotaColumnsAreNullable(
                connection
            );

            assertQuotaPairConstraintExists(
                connection
            );

            assertSelectionPositionConstraintIncludesDeliveryIdentity(
                connection
            );

            assertPrimaryQuotaReservationIndexExists(
                connection
            );
        }
    }

    private void assertMigrationVersionTwentyNineApplied(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '29'
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

    private void assertQuotaColumnsAreNullable(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT
                column_name,
                is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'publication_outbox'
              AND column_name IN (
                  'quota_profile_version',
                  'quota_date'
              )
            ORDER BY column_name
            """;

        int found =
            0;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );
             ResultSet resultSet =
                 statement.executeQuery()) {

            while (resultSet.next()) {

                found++;

                assertEquals(
                    "YES",
                    resultSet.getString(
                        "is_nullable"
                    ),
                    resultSet.getString(
                        "column_name"
                    )
                );
            }
        }

        assertEquals(
            2,
            found
        );
    }

    private void assertQuotaPairConstraintExists(
        Connection connection
    ) throws Exception {

        String definition =
            loadConstraintDefinition(
                connection,
                "ck_publication_outbox_quota_reservation_pair"
            );

        assertTrue(
            definition.contains(
                "quota_profile_version IS NOT NULL"
            )
        );

        assertTrue(
            definition.contains(
                "quota_date IS NOT NULL"
            )
        );

        assertTrue(
            definition.contains(
                "quota_profile_version IS NULL"
            )
        );

        assertTrue(
            definition.contains(
                "quota_date IS NULL"
            )
        );
    }

    private void assertSelectionPositionConstraintIncludesDeliveryIdentity(
        Connection connection
    ) throws Exception {

        String definition =
            loadConstraintDefinition(
                connection,
                "uq_publication_outbox_selection_position"
            );

        assertTrue(
            definition.contains(
                "selection_run_id"
            )
        );

        assertTrue(
            definition.contains(
                "selection_position"
            )
        );

        assertTrue(
            definition.contains(
                "channel"
            )
        );

        assertTrue(
            definition.contains(
                "destination"
            )
        );
    }

    private void assertPrimaryQuotaReservationIndexExists(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename = 'publication_outbox'
              AND indexname =
                  'uq_publication_outbox_quota_reservation_position'
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

            String indexDefinition =
                resultSet.getString(
                    "indexdef"
                );

            assertTrue(
                indexDefinition.contains(
                    "UNIQUE"
                )
            );

            assertTrue(
                indexDefinition.contains(
                    "selection_run_id"
                )
            );

            assertTrue(
                indexDefinition.contains(
                    "selection_position"
                )
            );

            assertTrue(
                indexDefinition.contains(
                    "quota_profile_version IS NOT NULL"
                )
            );

            assertFalse(
                resultSet.next()
            );
        }
    }

    private String loadConstraintDefinition(
        Connection connection,
        String constraintName
    ) throws Exception {

        String sql =
            """
            SELECT pg_get_constraintdef(con.oid) AS definition
            FROM pg_constraint con
            JOIN pg_class rel
              ON rel.oid = con.conrelid
            JOIN pg_namespace nsp
              ON nsp.oid = rel.relnamespace
            WHERE nsp.nspname = 'public'
              AND rel.relname = 'publication_outbox'
              AND con.conname = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                constraintName
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next(),
                    "Constraint not found: "
                        + constraintName
                );

                String definition =
                    resultSet.getString(
                        "definition"
                    );

                assertFalse(
                    resultSet.next(),
                    "Duplicate constraint: "
                        + constraintName
                );

                return definition;
            }
        }
    }
}
