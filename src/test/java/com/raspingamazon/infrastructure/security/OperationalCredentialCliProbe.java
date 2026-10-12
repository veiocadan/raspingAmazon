package com.raspingamazon.infrastructure.security;

import com.raspingamazon.infrastructure.bootstrap.OperationalCliBootstrap;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.presentation.cli.CliExitCode;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C2-E-D-D-C-A: teste EXPLICITO do caminho operacional real da CLI.
 *
 * <p>Executado apenas por -Dtest=OperationalCredentialCliProbe.
 * Nunca inicia o daemon, executa migrations ou modifica o banco.
 * Saidas de listagem ficam em memoria e NAO sao publicadas nos logs.</p>
 */
final class OperationalCredentialCliProbe {

    @Test
    void shouldResolveDistinctRuntimeAndMigrationIdentities() {
        ApplicationConfig config = EnvironmentConfigProvider.load();
        assertEquals("rasping_amazon", config.databaseName());
        assertEquals("127.0.0.1", config.databaseHost());
        assertEquals("rasping_runtime", config.databaseUser());
        assertEquals("rasping_migrator", config.databaseMigrationUser());
        assertNotEquals(config.databaseUser(), config.databaseMigrationUser());
        System.out.println("=== C2-E-D-D-C-A CONFIG PASS - identidades independentes ===");
    }

    @Test
    void shouldListRunsUsingRealOperationalComposition() {
        assertReadOnlyList("runs", "RUN_ID\t");
        System.out.println("=== C2-E-D-D-C-A RUNS PASS - CLI operacional autenticou como runtime ===");
    }

    @Test
    void shouldListJobsUsingRealOperationalComposition() {
        assertReadOnlyList("jobs", "JOB_ID\t");
        System.out.println("=== C2-E-D-D-C-A JOBS PASS - CLI operacional autenticou como runtime ===");
    }

    private static void assertReadOnlyList(String resource, String header) {
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();

        CliExitCode code = OperationalCliBootstrap.run(
                new String[]{resource, "list", "--limit", "1"},
                new PrintWriter(output, true),
                new PrintWriter(errors, true)
        );

        // Nao reproduzir erros nem linhas do banco: podem conter dados operacionais.
        assertEquals(CliExitCode.SUCCESS, code,
                "C2-E-D-D-C-A: comando operacional nao foi bem-sucedido");
        assertTrue(output.toString().startsWith(header),
                "C2-E-D-D-C-A: cabecalho de listagem inesperado");
        assertTrue(errors.toString().isBlank(),
                "C2-E-D-D-C-A: comando emitiu erro operacional");
    }
}
