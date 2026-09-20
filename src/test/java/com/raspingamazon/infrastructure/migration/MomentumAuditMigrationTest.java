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
 * Verifica o contrato estrutural introduzido pela migration V9.
 *
 * <p>Este teste não testa o repository de momentum. Essa
 * responsabilidade será implementada na FASE 11-E2.</p>
 *
 * <p>Aqui verificamos somente que o schema auditável necessário
 * para a próxima etapa realmente existe após a execução do
 * Flyway.</p>
 */
class MomentumAuditMigrationTest {

    @Test
    void shouldCreateMomentumAuditSchema() throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        /*
         * Garante que todas as migrations conhecidas pela aplicação
         * estejam aplicadas.
         */
        DatabaseMigration.migrate(
            config
        );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            assertMigrationVersionNineApplied(
                connection
            );

            assertMomentumAuditTableExists(
                connection
            );

            assertMomentumAuditColumns(
                connection
            );

            assertMomentumAuditConstraints(
                connection
            );
        }
    }

    private void assertMigrationVersionNineApplied(
        Connection connection
    ) throws Exception {

        String sql = """
            SELECT COUNT(*) AS migration_count
            FROM flyway_schema_history
            WHERE version = '9'
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

    private void assertMomentumAuditTableExists(
        Connection connection
    ) throws Exception {

        String sql = """
            SELECT COUNT(*) AS table_count
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name = 'deal_evaluation_momentum_audit'
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
                    "table_count"
                )
            );
        }
    }

    private void assertMomentumAuditColumns(
        Connection connection
    ) throws Exception {

        String sql = """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'deal_evaluation_momentum_audit'
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

        Set<String> expectedColumns =
            Set.of(
                "id",
                "deal_evaluation_id",
                "calculation_version",
                "status",
                "unavailable_reason",
                "previous_offer_snapshot_id",
                "elapsed_seconds",
                "sold_percentage_delta",
                "current_price_delta",
                "current_price_delta_percentage",
                "cash_discount_delta",
                "momentum",
                "created_at"
            );

        assertEquals(
            expectedColumns,
            actualColumns
        );
    }

    private void assertMomentumAuditConstraints(
        Connection connection
    ) throws Exception {

        String sql = """
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class rel
              ON rel.oid = con.conrelid
            JOIN pg_namespace nsp
              ON nsp.oid = rel.relnamespace
            WHERE nsp.nspname = 'public'
              AND rel.relname = 'deal_evaluation_momentum_audit'
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

        assertTrue(
            constraints.contains(
                "fk_momentum_audit_evaluation"
            )
        );

        assertTrue(
            constraints.contains(
                "fk_momentum_audit_previous_snapshot"
            )
        );

        assertTrue(
            constraints.contains(
                "uq_momentum_audit_evaluation"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_momentum_audit_status"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_momentum_audit_elapsed_positive"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_momentum_audit_previous_context"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_momentum_audit_no_previous_no_deltas"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_momentum_audit_previous_has_price_delta"
            )
        );

        assertTrue(
            constraints.contains(
                "ck_momentum_audit_availability"
            )
        );
    }
}
