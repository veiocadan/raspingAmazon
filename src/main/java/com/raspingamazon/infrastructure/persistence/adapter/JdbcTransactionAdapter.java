package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.infrastructure.persistence.TransactionOperationException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implementação JDBC da fronteira transacional da aplicação.
 *
 * <p>A mesma Connection deve ser compartilhada pelos repositories
 * usados dentro da operação.</p>
 *
 * <p>O adapter suporta dois modos de execução:</p>
 *
 * <ul>
 *     <li>
 *         quando {@code autoCommit=true}, o adapter é proprietário
 *         da transação e controla begin, commit, rollback e restauração;
 *     </li>
 *     <li>
 *         quando {@code autoCommit=false}, existe uma transação externa
 *         pertencente ao chamador. Nesse caso o adapter utiliza um
 *         {@link Savepoint}, nunca executa commit da transação externa
 *         e, em caso de falha, desfaz somente o trabalho executado
 *         dentro da sua própria fronteira.
 *     </li>
 * </ul>
 *
 * <p>Falhas secundárias de rollback, liberação de savepoint ou restauração
 * de estado são adicionadas como suppressed à falha principal sempre que
 * houver uma exceção original a preservar.</p>
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

        boolean originalAutoCommit =
            readAutoCommit();

        if (originalAutoCommit) {
            return executeOwnedTransaction(
                operation
            );
        }

        return executeInsideExistingTransaction(
            operation
        );
    }

    /**
     * Lê o estado transacional atual da conexão.
     */
    private boolean readAutoCommit() {

        try {
            return connection.getAutoCommit();

        } catch (SQLException exception) {

            throw new TransactionOperationException(
                "Failed to read JDBC autoCommit state",
                exception
            );
        }
    }

    /**
     * Executa uma unidade de trabalho quando o adapter é proprietário
     * da transação.
     *
     * <p>Nesse modo a Connection chegou com autoCommit=true. O adapter:</p>
     *
     * <ol>
     *     <li>desativa autoCommit;</li>
     *     <li>executa a operação;</li>
     *     <li>faz commit em sucesso;</li>
     *     <li>faz rollback em falha;</li>
     *     <li>restaura autoCommit=true.</li>
     * </ol>
     */
    private <T> T executeOwnedTransaction(
        Supplier<T> operation
    ) {

        try {
            connection.setAutoCommit(
                false
            );

        } catch (SQLException exception) {

            throw new TransactionOperationException(
                "Failed to begin JDBC transaction",
                exception
            );
        }

        Throwable primaryFailure =
            null;

        try {
            T result =
                operation.get();

            connection.commit();

            return result;

        } catch (RuntimeException | Error exception) {

            primaryFailure =
                exception;

            rollbackWholeTransaction(
                exception
            );

            throw exception;

        } catch (SQLException exception) {

            TransactionOperationException wrapped =
                new TransactionOperationException(
                    "Failed to commit JDBC transaction",
                    exception
                );

            primaryFailure =
                wrapped;

            rollbackWholeTransaction(
                wrapped
            );

            throw wrapped;

        } finally {

            restoreAutoCommit(
                true,
                primaryFailure
            );
        }
    }

    /**
     * Executa uma unidade de trabalho dentro de uma transação já aberta
     * pelo chamador.
     *
     * <p>O adapter não é proprietário dessa transação e, portanto,
     * nunca pode executar {@link Connection#commit()} ou rollback total.</p>
     *
     * <p>Um Savepoint delimita apenas o trabalho executado pelo adapter.</p>
     */
    private <T> T executeInsideExistingTransaction(
        Supplier<T> operation
    ) {

        Savepoint savepoint =
            createSavepoint();

        try {
            T result =
                operation.get();

            releaseSavepointOnSuccess(
                savepoint
            );

            return result;

        } catch (RuntimeException | Error exception) {

            rollbackToSavepoint(
                savepoint,
                exception
            );

            throw exception;
        }
    }

    /**
     * Cria o Savepoint usado quando a Connection já pertence
     * a uma transação externa.
     */
    private Savepoint createSavepoint() {

        try {
            return connection.setSavepoint();

        } catch (SQLException exception) {

            throw new TransactionOperationException(
                "Failed to create JDBC savepoint",
                exception
            );
        }
    }

    /**
     * Libera o Savepoint quando a operação interna terminou com sucesso.
     *
     * <p>Falhar ao liberar o Savepoint significa que a fronteira interna
     * não conseguiu terminar normalmente. A falha é transformada em
     * TransactionOperationException.</p>
     */
    private void releaseSavepointOnSuccess(
        Savepoint savepoint
    ) {

        try {
            connection.releaseSavepoint(
                savepoint
            );

        } catch (SQLException exception) {

            throw new TransactionOperationException(
                "Failed to release JDBC savepoint",
                exception
            );
        }
    }

    /**
     * Executa rollback completo quando o adapter é proprietário
     * da transação.
     *
     * <p>Se o rollback também falhar, a falha secundária é adicionada
     * como suppressed para preservar a causa principal.</p>
     */
    private void rollbackWholeTransaction(
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
     * Desfaz somente o trabalho posterior ao Savepoint.
     *
     * <p>A transação externa continua aberta e sob responsabilidade
     * do chamador.</p>
     */
    private void rollbackToSavepoint(
        Savepoint savepoint,
        Throwable originalFailure
    ) {

        boolean rollbackSucceeded =
            false;

        try {
            connection.rollback(
                savepoint
            );

            rollbackSucceeded =
                true;

        } catch (SQLException rollbackFailure) {

            originalFailure.addSuppressed(
                rollbackFailure
            );
        }

        if (rollbackSucceeded) {
            releaseSavepointAfterRollback(
                savepoint,
                originalFailure
            );
        }
    }

    /**
     * Libera o Savepoint depois de um rollback bem-sucedido.
     *
     * <p>Uma eventual falha de liberação não substitui a exceção
     * que provocou o rollback.</p>
     */
    private void releaseSavepointAfterRollback(
        Savepoint savepoint,
        Throwable originalFailure
    ) {

        try {
            connection.releaseSavepoint(
                savepoint
            );

        } catch (SQLException releaseFailure) {

            originalFailure.addSuppressed(
                releaseFailure
            );
        }
    }

    /**
     * Restaura o estado original de autoCommit quando o adapter
     * era proprietário da transação.
     *
     * <p>Quando já existe uma falha principal, uma eventual falha
     * de restauração é adicionada como suppressed em vez de ocultar
     * a causa original.</p>
     */
    private void restoreAutoCommit(
        boolean originalAutoCommit,
        Throwable primaryFailure
    ) {

        try {
            if (connection.getAutoCommit()
                != originalAutoCommit) {

                connection.setAutoCommit(
                    originalAutoCommit
                );
            }

        } catch (SQLException exception) {

            TransactionOperationException restoreFailure =
                new TransactionOperationException(
                    "Failed to restore JDBC autoCommit state",
                    exception
                );

            if (primaryFailure != null) {
                primaryFailure.addSuppressed(
                    restoreFailure
                );

                return;
            }

            throw restoreFailure;
        }
    }
}
