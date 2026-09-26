package com.raspingamazon.testsupport.database;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Extensão JUnit responsável por preparar o schema PostgreSQL
 * antes da execução de uma classe de teste de integração.
 */
public final class PostgresSchemaExtension
        implements BeforeAllCallback {

    @Override
    public void beforeAll(
            ExtensionContext context
    ) {

        PostgresTestDatabase.ensureMigrated();
    }
}
