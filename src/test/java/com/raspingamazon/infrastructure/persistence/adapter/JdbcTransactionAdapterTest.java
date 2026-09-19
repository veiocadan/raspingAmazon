package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integração da fronteira transacional JDBC.
 *
 * <p>O objetivo aqui é provar comportamento real no PostgreSQL:</p>
 *
 * <ul>
 *     <li>commit em sucesso;</li>
 *     <li>rollback em falha;</li>
 *     <li>restauração do autoCommit original.</li>
 * </ul>
 */
class JdbcTransactionAdapterTest {

    @Test
    void shouldCommitTransactionOnSuccess()
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
             * O adapter deve restaurar o estado original
             * da Connection após o commit.
             */
            assertTrue(
                    connection.getAutoCommit()
            );

            /*
             * A linha precisa continuar visível após a transação.
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
    void shouldRollbackTransactionOnFailure()
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

                                /*
                                 * Falha controlada depois da gravação.
                                 *
                                 * Se a fronteira transacional estiver
                                 * correta, o INSERT acima será desfeito.
                                 */
                                throw new RuntimeException(
                                        "controlled failure"
                                );
                            }
                    )
            );

            /*
             * O estado original da conexão precisa ser restaurado.
             */
            assertTrue(
                    connection.getAutoCommit()
            );

            /*
             * Esta é a prova principal:
             * a linha inserida antes da falha não pode permanecer.
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
    void shouldRestoreOriginalAutoCommitState()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            /*
             * Simulamos um chamador que já controla transação
             * externamente.
             */
            connection.setAutoCommit(
                    false
            );

            JdbcTransactionAdapter adapter =
                    new JdbcTransactionAdapter(
                            connection
                    );

            adapter.execute(
                    () -> "ok"
            );

            /*
             * O adapter não deve obrigar autoCommit=true.
             * Deve restaurar exatamente o estado encontrado.
             */
            assertFalse(
                    connection.getAutoCommit()
            );

            /*
             * Encerramos explicitamente qualquer estado pendente
             * antes de fechar a Connection do teste.
             */
            connection.rollback();

            connection.setAutoCommit(
                    true
            );
        }
    }

    /**
     * Insere um Product diretamente via JDBC.
     *
     * <p>Usamos SQL direto neste teste porque o objetivo é testar
     * exclusivamente a transação, não ProductRepository.</p>
     */
    private long insertProduct(
            Connection connection,
            String asin,
            String title
    ) throws Exception {

        String sql = """
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
     * Verifica diretamente no banco se o ASIN existe.
     */
    private boolean productExists(
            Connection connection,
            String asin
    ) throws Exception {

        String sql = """
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
     * Limpeza defensiva do teste de commit.
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
}