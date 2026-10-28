package com.raspingamazon.infrastructure.bootstrap;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Verificacao JDBC exclusivamente consultiva do ambiente de preparacao.
 * Nao utiliza o bootstrap produtivo, composicoes, worker ou recovery.
 */
final class ContinuousProcessingReadinessProbe {

    private ContinuousProcessingReadinessProbe() {
    }

    static void verify(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        // Exigir modo read-only antes de abrir qualquer transacao.
        connection.setReadOnly(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
        try {
            // PostgreSQL deve CONFIRMAR o atributo no servidor, nao apenas
            // o estado local da API JDBC.
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(8);
                try (ResultSet result = statement.executeQuery(
                    "SELECT current_setting('transaction_read_only'), "
                        + "current_user, current_database()")) {
                    if (!result.next()
                        || !"on".equals(result.getString(1))
                        || !"rasping_runtime".equals(result.getString(2))
                        || !"rasping_amazon".equals(result.getString(3))
                        || result.next()) {
                        throw rejected();
                    }
                }
            }

            // Igualdade integral de identidade, pausa, intervalo, lease
            // e historico. Rejeita agendamentos extras, mesmo desativados.
            String scheduleSql = """
                SELECT COUNT(*),
                       COUNT(*) FILTER (
                           WHERE schedule_key = 'amazon-deals'
                             AND source_uri = 'https://www.amazon.com.br/deals'
                             AND enabled = FALSE
                             AND interval_ms = 3600000
                             AND next_run_at IS NOT NULL
                             AND lease_owner IS NULL
                             AND lease_expires_at IS NULL
                             AND last_scheduled_for IS NULL
                             AND last_processing_run_id IS NULL
                       )
                  FROM processing_schedule
                """;
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(8);
                try (ResultSet result = statement.executeQuery(scheduleSql)) {
                    if (!result.next() || result.getLong(1) != 1
                        || result.getLong(2) != 1 || result.next()) {
                        throw rejected();
                    }
                }
            }

            if (count(connection, "SELECT COUNT(*) FROM processing_job") != 0
                || count(connection, "SELECT COUNT(*) FROM processing_run") != 0
                || count(connection, "SELECT COUNT(*) FROM publication_outbox") != 0) {
                throw rejected();
            }
        } finally {
            // Nunca realizar COMMIT. Descartar a transacao de leitura.
            connection.rollback();
        }
    }

    private static long count(Connection connection, String sql)
        throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(8);
            try (ResultSet result = statement.executeQuery(sql)) {
                if (!result.next()) {
                    throw rejected();
                }
                long value = result.getLong(1);
                if (result.wasNull() || value < 0 || result.next()) {
                    throw rejected();
                }
                return value;
            }
        }
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("Readiness prerequisites not satisfied");
    }
}
