package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integração da fronteira transacional JDBC.
 *
 * <p>O objetivo é provar o comportamento real contra PostgreSQL
 * tanto quando o adapter é proprietário da transação quanto quando
 * participa de uma transação externa.</p>
 */
@PostgresIntegrationTest
class JdbcTransactionAdapterTest {

    @Test
    void shouldCommitOwnedTransactionOnSuccess()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String asin =
            "B0TXCOMIT1";

        long productId =
            0;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            assertTrue(
                connection.getAutoCommit()
            );

            JdbcTransactionAdapter adapter =
                new JdbcTransactionAdapter(
                    connection
                );

            Long generatedId =
                adapter.execute(
                    () -> {

                        try {
                            return insertProduct(
                                connection,
                                asin,
                                "Produto commit"
                            );

                        } catch (Exception exception) {
                            throw new RuntimeException(
                                exception
                            );
                        }
                    }
                );

            productId =
                generatedId;

            /*
             * O adapter é proprietário da transação porque a Connection
             * chegou com autoCommit=true.
             *
             * Portanto deve restaurar o estado original depois do commit.
             */
            assertTrue(
                connection.getAutoCommit()
            );

            /*
             * A linha precisa permanecer no banco depois do commit.
             */
            assertTrue(
                productExists(
                    connection,
                    asin
                )
            );

        } finally {

            deleteProductById(
                config,
                productId
            );
        }
    }

    @Test
    void shouldRollbackOwnedTransactionOnFailure()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String asin =
            "B0TXROLL01";

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            assertTrue(
                connection.getAutoCommit()
            );

            JdbcTransactionAdapter adapter =
                new JdbcTransactionAdapter(
                    connection
                );

            assertThrows(
                RuntimeException.class,
                () -> adapter.execute(
                    () -> {

                        try {
                            insertProduct(
                                connection,
                                asin,
                                "Produto rollback"
                            );

                        } catch (Exception exception) {
                            throw new RuntimeException(
                                exception
                            );
                        }

                        throw new RuntimeException(
                            "controlled failure"
                        );
                    }
                )
            );

            /*
             * O estado original da conexão deve ser restaurado.
             */
            assertTrue(
                connection.getAutoCommit()
            );

            /*
             * Como o adapter era proprietário da transação,
             * a falha deve causar rollback total da unidade de trabalho.
             */
            assertFalse(
                productExists(
                    connection,
                    asin
                )
            );
        }
    }

    @Test
    void shouldNotCommitCallerOwnedTransactionOnSuccess()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String asin =
            "B0TXNEST01";

        try {
            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                /*
                 * O chamador abre e passa a ser proprietário
                 * da transação externa.
                 */
                connection.setAutoCommit(
                    false
                );

                JdbcTransactionAdapter adapter =
                    new JdbcTransactionAdapter(
                        connection
                    );

                adapter.execute(
                    () -> {

                        try {
                            insertProduct(
                                connection,
                                asin,
                                "Produto nested success"
                            );

                            return "ok";

                        } catch (Exception exception) {
                            throw new RuntimeException(
                                exception
                            );
                        }
                    }
                );

                /*
                 * O adapter não pode alterar a propriedade
                 * da transação externa.
                 */
                assertFalse(
                    connection.getAutoCommit()
                );

                /*
                 * Dentro da mesma transação a gravação é visível.
                 */
                assertTrue(
                    productExists(
                        connection,
                        asin
                    )
                );

                /*
                 * Esta é a prova principal:
                 *
                 * se o adapter tivesse executado commit indevidamente,
                 * este rollback do chamador não conseguiria remover
                 * a linha criada dentro de adapter.execute().
                 */
                connection.rollback();

                assertFalse(
                    productExists(
                        connection,
                        asin
                    )
                );

                connection.setAutoCommit(
                    true
                );
            }

        } finally {

            deleteProductByAsin(
                config,
                asin
            );
        }
    }

    @Test
    void shouldRollbackOnlyNestedWorkWhenCallerOwnsTransaction()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String outerAsin =
            "B0TXOUT001";

        String innerAsin =
            "B0TXIN0001";

        try {
            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                connection.setAutoCommit(
                    false
                );

                /*
                 * Este INSERT pertence ao chamador e acontece antes
                 * da fronteira interna do JdbcTransactionAdapter.
                 */
                insertProduct(
                    connection,
                    outerAsin,
                    "Produto externo"
                );

                JdbcTransactionAdapter adapter =
                    new JdbcTransactionAdapter(
                        connection
                    );

                assertThrows(
                    RuntimeException.class,
                    () -> adapter.execute(
                        () -> {

                            try {
                                insertProduct(
                                    connection,
                                    innerAsin,
                                    "Produto interno"
                                );

                            } catch (Exception exception) {
                                throw new RuntimeException(
                                    exception
                                );
                            }

                            throw new RuntimeException(
                                "controlled nested failure"
                            );
                        }
                    )
                );

                /*
                 * A transação externa continua aberta.
                 */
                assertFalse(
                    connection.getAutoCommit()
                );

                /*
                 * O trabalho feito antes do Savepoint pertence ao chamador
                 * e precisa ser preservado.
                 */
                assertTrue(
                    productExists(
                        connection,
                        outerAsin
                    )
                );

                /*
                 * O trabalho executado depois do Savepoint precisa
                 * ter sido desfeito.
                 */
                assertFalse(
                    productExists(
                        connection,
                        innerAsin
                    )
                );

                /*
                 * O chamador continua sendo o responsável por decidir
                 * o destino da transação externa.
                 */
                connection.rollback();

                assertFalse(
                    productExists(
                        connection,
                        outerAsin
                    )
                );

                assertFalse(
                    productExists(
                        connection,
                        innerAsin
                    )
                );

                connection.setAutoCommit(
                    true
                );
            }

        } finally {

            deleteProductByAsin(
                config,
                outerAsin
            );

            deleteProductByAsin(
                config,
                innerAsin
            );
        }
    }

    /**
     * Insere um Product diretamente via JDBC.
     *
     * <p>Usamos SQL direto neste teste porque o objetivo é testar
     * exclusivamente a fronteira transacional, não ProductRepository.</p>
     */
    private long insertProduct(
        Connection connection,
        String asin,
        String title
    ) throws Exception {

        String sql =
            """
            INSERT INTO product (
                asin,
                title,
                image_url,
                product_url
            )
            VALUES (?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                asin
            );

            statement.setString(
                2,
                title
            );

            statement.setObject(
                3,
                null
            );

            statement.setString(
                4,
                "https://example.invalid/" + asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    /**
     * Verifica diretamente no banco se o ASIN existe usando
     * a Connection recebida.
     */
    private boolean productExists(
        Connection connection,
        String asin
    ) throws Exception {

        String sql =
            """
            SELECT EXISTS (
                SELECT 1
                FROM product
                WHERE asin = ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                asin
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getBoolean(
                    1
                );
            }
        }
    }

    /**
     * Limpeza defensiva de um produto pelo ID.
     */
    private void deleteProductById(
        ApplicationConfig config,
        long productId
    ) throws Exception {

        if (productId <= 0) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );

             PreparedStatement statement =
                 connection.prepareStatement(
                     "DELETE FROM product WHERE id = ?"
                 )) {

            statement.setLong(
                1,
                productId
            );

            statement.executeUpdate();
        }
    }

    /**
     * Limpeza defensiva usada pelos testes de transação externa.
     *
     * <p>Em um teste correto nenhuma linha deve sobrar. Este método existe
     * apenas para evitar resíduos caso uma regressão execute commit
     * indevidamente antes da asserção falhar.</p>
     */
    private void deleteProductByAsin(
        ApplicationConfig config,
        String asin
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );

             PreparedStatement statement =
                 connection.prepareStatement(
                     "DELETE FROM product WHERE asin = ?"
                 )) {

            statement.setString(
                1,
                asin
            );

            statement.executeUpdate();
        }
    }
}
