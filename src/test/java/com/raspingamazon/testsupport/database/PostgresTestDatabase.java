package com.raspingamazon.testsupport.database;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;

import java.util.HashSet;
import java.util.Set;

/**
 * Suporte central para preparação do PostgreSQL usado pelos
 * testes de integração.
 *
 * <p>A responsabilidade desta classe é garantir que as migrations
 * reais da aplicação tenham sido aplicadas antes de qualquer teste
 * que dependa do schema PostgreSQL.</p>
 *
 * <p>Cada banco é preparado no máximo uma vez por JVM de testes.
 * A identidade considerada é composta por host, porta, nome do banco
 * e usuário. Assim, uma mesma JVM pode trabalhar com bancos distintos
 * sem assumir incorretamente que todos já foram migrados.</p>
 *
 * <p>Se a suíte for executada em mais de uma JVM/fork, cada processo
 * poderá chamar Flyway novamente. Isso é seguro porque o Flyway
 * controla o versionamento das migrations aplicadas.</p>
 */
public final class PostgresTestDatabase {

    private static final Object MIGRATION_MONITOR =
        new Object();

    private static final Set<DatabaseIdentity> MIGRATED_DATABASES =
        new HashSet<>();

    private PostgresTestDatabase() {
    }

    public static void ensureMigrated() {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseIdentity databaseIdentity =
            DatabaseIdentity.from(
                config
            );

        synchronized (MIGRATION_MONITOR) {

            if (MIGRATED_DATABASES.contains(
                databaseIdentity
            )) {
                return;
            }

            DatabaseMigration.migrate(
                config
            );

            MIGRATED_DATABASES.add(
                databaseIdentity
            );
        }
    }

    private record DatabaseIdentity(
        String host,
        String port,
        String databaseName,
        String user
    ) {

        private static DatabaseIdentity from(
            ApplicationConfig config
        ) {

            return new DatabaseIdentity(
                config.databaseHost(),
                config.databasePort(),
                config.databaseName(),
                config.databaseUser()
            );
        }
    }
}
