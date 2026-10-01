package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOutboxCadenceSchemaTest {

    @Test
    void shouldExposeCadenceAuditColumnForeignKeyAndUniqueSlotIndex()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            assertCadenceColumnExists(
                connection
            );

            assertCadenceForeignKeyExists(
                connection
            );

            assertCadenceSlotUniqueIndexExists(
                connection
            );
        }
    }

    private void assertCadenceColumnExists(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'publication_outbox'
              AND column_name = 'cadence_profile_version'
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );
             ResultSet resultSet =
                 statement.executeQuery()) {

            resultSet.next();

            assertEquals(
                1,
                resultSet.getInt(
                    1
                )
            );
        }
    }

    private void assertCadenceForeignKeyExists(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM pg_constraint
            WHERE conname =
                'fk_publication_outbox_cadence_profile'
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );
             ResultSet resultSet =
                 statement.executeQuery()) {

            resultSet.next();

            assertEquals(
                1,
                resultSet.getInt(
                    1
                )
            );
        }
    }

    private void assertCadenceSlotUniqueIndexExists(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = current_schema()
              AND tablename = 'publication_outbox'
              AND indexname =
                  'uq_publication_outbox_primary_cadence_slot'
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
                    )
                    .toLowerCase();

            assertTrue(
                indexDefinition.contains(
                    "unique"
                )
            );

            assertTrue(
                indexDefinition.contains(
                    "available_at"
                )
            );

            assertTrue(
                indexDefinition.contains(
                    "cadence_profile_version"
                )
            );

            assertTrue(
                indexDefinition.contains(
                    "quota_profile_version"
                )
            );
        }
    }
}
