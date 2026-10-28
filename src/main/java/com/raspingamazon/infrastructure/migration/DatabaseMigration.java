package com.raspingamazon.infrastructure.migration;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import org.flywaydb.core.Flyway;

/**
 * Execucao das migracoes com a identidade exclusiva do Flyway.
 *
 * <p>Os parametros de rede seguem ApplicationConfig. Apenas usuario e senha
 * sao selecionados do contrato de migracao. Durante a transicao, o construtor
 * legado da configuracao preserva o comportamento anterior.</p>
 */
public final class DatabaseMigration {

    private DatabaseMigration() {
    }

    public static void migrate(ApplicationConfig config) {
        String jdbcUrl = "jdbc:postgresql://"
                + config.databaseHost()
                + ":"
                + config.databasePort()
                + "/"
                + config.databaseName();

        Flyway flyway = Flyway.configure()
                .dataSource(
                        jdbcUrl,
                        config.databaseMigrationUser(),
                        config.databaseMigrationPassword()
                )
                .load();

        flyway.migrate();
    }
}
