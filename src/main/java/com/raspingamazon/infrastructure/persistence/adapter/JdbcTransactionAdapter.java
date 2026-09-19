package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.infrastructure.persistence.TransactionOperationException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implementação JDBC da fronteira transacional da aplicação.
 *
 * <p>A mesma Connection deve ser compartilhada pelos repositories
 * usados dentro da operação.</p>
 *
 * <p>O adapter controla:</p>
 *
 * <ul>
 *     <li>desativação temporária de autoCommit;</li>
 *     <li>commit em sucesso;</li>
 *     <li>rollback em falha;</li>
 *     <li>restauração do estado anterior da conexão.</li>
 * </ul>
 */
public final class JdbcTransactionAdapter
        implements TransactionPort {

    private final Connection connection;

    public JdbcTransactionAdapter(
            Connection connection
    ) {
        this.connection =
                Objects.requireNonNull(
                        connection,
                        "connection must not be null"
                );
    }

    @Override
    public <T> T execute(
            Supplier<T> operation
    ) {
        Objects.requireNonNull(
                operation,
                "operation must not be null"
        );

        boolean originalAutoCommit;

        try {
            originalAutoCommit =
                    connection.getAutoCommit();

        } catch (SQLException exception) {

            throw new TransactionOperationException(
                    "Failed to read JDBC autoCommit state",
                    exception
            );
        }

        try {
            /*
             * Toda a unidade de trabalho executada pelo Supplier
             * usará a mesma Connection compartilhada pelos adapters.
             */
            if (originalAutoCommit) {
                connection.setAutoCommit(
                        false
                );
            }

            T result =
                    operation.get();

            connection.commit();

            return result;

        } catch (RuntimeException exception) {

            rollbackAfterFailure(
                    exception
            );

            throw exception;

        } catch (SQLException exception) {

            rollbackAfterFailure(
                    exception
            );

            throw new TransactionOperationException(
                    "Failed to commit JDBC transaction",
                    exception
            );

        } finally {

            restoreAutoCommit(
                    originalAutoCommit
            );
        }
    }

    /**
     * Executa rollback preservando a exceção original.
     *
     * <p>Se o rollback também falhar, essa segunda falha é adicionada
     * como suppressed para que a causa principal não seja perdida.</p>
     */
    private void rollbackAfterFailure(
            Throwable originalFailure
    ) {
        try {
            connection.rollback();

        } catch (SQLException rollbackFailure) {

            originalFailure.addSuppressed(
                    rollbackFailure
            );
        }
    }

    /**
     * Restaura o estado de autoCommit encontrado antes da transação.
     */
    private void restoreAutoCommit(
            boolean originalAutoCommit
    ) {
        try {
            if (connection.getAutoCommit()
                    != originalAutoCommit) {

                connection.setAutoCommit(
                        originalAutoCommit
                );
            }

        } catch (SQLException exception) {

            throw new TransactionOperationException(
                    "Failed to restore JDBC autoCommit state",
                    exception
            );
        }
    }
}