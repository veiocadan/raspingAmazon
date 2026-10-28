package com.raspingamazon.infrastructure.security;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova operacional executada EXPLICITAMENTE pelo gate C2-E-D-D-B.
 *
 * <p>O nome termina em Probe para nao ser incluida na suite Maven padrao.
 * Nao executa migracoes, DDL ou escritas; o Flyway realiza apenas validate().
 * Nunca imprime configuracoes, senhas ou detalhes de excecoes do driver.</p>
 */
final class CredentialSeparationJdbcFlywayProbe {

    private static final String RUNTIME = "rasping_runtime";
    private static final String MIGRATOR = "rasping_migrator";
    private static final String DATABASE = "rasping_amazon";

    private static ApplicationConfig config() {
        ApplicationConfig result = EnvironmentConfigProvider.load();
        assertEquals(DATABASE, result.databaseName());
        assertEquals(RUNTIME, result.databaseUser());
        assertEquals(MIGRATOR, result.databaseMigrationUser());
        return result;
    }

    private static String url(ApplicationConfig config) {
        return "jdbc:postgresql://"
                + config.databaseHost() + ":" + config.databasePort()
                + "/" + config.databaseName();
    }

    private static int scalarCount(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                throw new AssertionError("C2-E-D-D-B: consulta de catalogo sem resultado");
            }
            return results.getInt(1);
        }
    }

    private static String scalarString(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                throw new AssertionError("C2-E-D-D-B: consulta de identidade sem resultado");
            }
            return results.getString(1);
        }
    }

    @Test
    void jdbcShouldUseRealRuntimeWithExactPrivileges() {
        ApplicationConfig config = config();
        try (Connection connection = DatabaseConnection.open(config)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            assertEquals(RUNTIME, scalarString(connection, "SELECT current_user"));
            assertEquals(DATABASE, scalarString(connection, "SELECT current_database()"));

            String relationQuery = """
                    SELECT COUNT(*) FILTER (WHERE has_table_privilege(current_user, c.oid, '%s'))
                    FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
                    """;
            assertEquals(29, scalarCount(connection, """
                    SELECT COUNT(*) FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
                    """));
            assertEquals(27, scalarCount(connection, relationQuery.formatted("SELECT")));
            assertEquals(25, scalarCount(connection, relationQuery.formatted("INSERT")));
            assertEquals(12, scalarCount(connection, relationQuery.formatted("UPDATE")));
            assertEquals(0, scalarCount(connection, relationQuery.formatted("DELETE")));
            assertEquals(0, scalarCount(connection, relationQuery.formatted("TRUNCATE")));

            String sequenceQuery = """
                    SELECT COUNT(*) FILTER (WHERE has_sequence_privilege(current_user, c.oid, '%s'))
                    FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public' AND c.relkind = 'S'
                    """;
            assertEquals(26, scalarCount(connection, """
                    SELECT COUNT(*) FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public' AND c.relkind = 'S'
                    """));
            assertEquals(23, scalarCount(connection, sequenceQuery.formatted("USAGE")));
            assertEquals(0, scalarCount(connection, sequenceQuery.formatted("UPDATE")));

            assertEquals(0, scalarCount(connection, """
                    SELECT COUNT(*) FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public'
                      AND c.relname IN ('flyway_schema_history','configuration_version')
                      AND has_table_privilege(current_user, c.oid, 'SELECT')
                    """));
            assertEquals(0, scalarCount(connection,
                    "SELECT COUNT(*) FROM public.processing_job WHERE 1 = 0"));
            connection.rollback();
        } catch (SQLException failure) {
            throw new AssertionError("C2-E-D-D-B: acesso JDBC runtime reprovado (detalhes omitidos)");
        }
        System.out.println("=== C2-E-D-D-B JDBC RUNTIME PASS - identidade e ACLs reais ===");
    }

    @Test
    void migrationConnectionShouldUseRealMigrator() {
        ApplicationConfig config = config();
        try (Connection connection = DriverManager.getConnection(
                url(config),
                config.databaseMigrationUser(),
                config.databaseMigrationPassword())) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);

            assertEquals(MIGRATOR, scalarString(connection, "SELECT current_user"));
            assertEquals(MIGRATOR, scalarString(connection, """
                    SELECT pg_catalog.pg_get_userbyid(n.nspowner)
                    FROM pg_catalog.pg_namespace n
                    WHERE n.nspname = 'public'
                    """));
            assertEquals(29, scalarCount(connection, """
                    SELECT COUNT(*) FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
                      AND pg_catalog.pg_get_userbyid(c.relowner) = current_user
                    """));
            assertEquals(26, scalarCount(connection, """
                    SELECT COUNT(*) FROM pg_catalog.pg_class c
                    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public' AND c.relkind = 'S'
                      AND pg_catalog.pg_get_userbyid(c.relowner) = current_user
                    """));
            assertEquals(1, scalarCount(connection, """
                    SELECT CASE WHEN has_table_privilege(
                        current_user, 'public.flyway_schema_history', 'SELECT')
                        THEN 1 ELSE 0 END
                    """));
            assertEquals(1, scalarCount(connection, """
                    SELECT CASE WHEN has_schema_privilege(
                        current_user, 'public', 'CREATE') THEN 1 ELSE 0 END
                    """));
            connection.rollback();
        } catch (SQLException failure) {
            throw new AssertionError("C2-E-D-D-B: acesso JDBC migrator reprovado (detalhes omitidos)");
        }
        System.out.println("=== C2-E-D-D-B JDBC MIGRATOR PASS - identidade e ownership reais ===");
    }

    @Test
    void flywayShouldValidateWithMigratorWithoutMigrating() {
        ApplicationConfig config = config();
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(
                            url(config),
                            config.databaseMigrationUser(),
                            config.databaseMigrationPassword())
                    .load();
            flyway.validate(); // Somente validacao. NAO chamar migrate(), repair() ou clean().
        } catch (FlywayException failure) {
            throw new AssertionError("C2-E-D-D-B: Flyway validate reprovado (detalhes omitidos)");
        }
        System.out.println("=== C2-E-D-D-B FLYWAY VALIDATE PASS - sem executar migrations ===");
    }
}
