package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.application.operation.observability.SafeOperationalError;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Entrada diagnostica independente do daemon. O unico efeito externo e
 * consultas SQL na transacao READ ONLY do usuario rasping_runtime.
 * Nao constroi ContinuousProcessingBootstrap nem inicia threads.
 */
public final class ContinuousProcessingReadinessMain {

    private ContinuousProcessingReadinessMain() {
    }

    public static void main(String[] args) {
        PrintWriter output = new PrintWriter(System.out, true);
        int code = run(
            output,
            DaemonStartupPolicyGate.defaultPolicyPath(),
            () -> {
                DriverManager.setLoginTimeout(8);
                return DatabaseConnection.open(EnvironmentConfigProvider.load());
            }
        );
        output.flush();
        if (code != 0) {
            System.exit(code);
        }
    }

    @FunctionalInterface
    interface ConnectionOpener {
        Connection open() throws SQLException;
    }

    static int run(PrintWriter output, Path policyPath, ConnectionOpener opener) {
        try {
            DaemonStartupPolicyGate.requirePreparationOnly(policyPath);
            try (Connection connection = opener.open()) {
                ContinuousProcessingReadinessProbe.verify(connection);
            }
            output.println("READINESS_JDBC_READ_ONLY_PASS");
            return 0;
        } catch (SQLException | RuntimeException exception) {
            output.println("Readiness failed: " + SafeOperationalError.describe(exception));
            return 1;
        }
    }
}
