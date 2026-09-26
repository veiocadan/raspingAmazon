package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa concorrência real na criação idempotente de OfferSnapshot.
 *
 * <p>Duas conexões independentes tentam registrar exatamente a mesma
 * observação. A constraint UNIQUE e o ON CONFLICT do PostgreSQL devem
 * garantir que somente uma delas crie a linha.</p>
 */
@PostgresIntegrationTest
class OfferSnapshotConcurrencyTest {

    private static final String ASIN =
            "B0CONCUR01";

    private static final String SOURCE =
            "amazon-deals-concurrency-test";

    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse(
                    "2026-09-18T22:00:00Z"
            );

    @Test
    void shouldCreateOnlyOneSnapshotUnderConcurrentAttempts()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();


        long productId =
                0;

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        2
                );

        try {
            /*
             * Primeiro criamos a identidade durável do Product.
             *
             * O teste de concorrência desta classe é especificamente
             * sobre OfferSnapshot, portanto o Product é preparado antes.
             */
            Product product;

            try (Connection connection =
                         DatabaseConnection.open(
                                 config
                         )) {

                ProductRepository productRepository =
                        new ProductRepository(
                                connection
                        );

                product =
                        productRepository.upsert(
                                ASIN,
                                "Produto do teste concorrente",
                                null,
                                "https://example.invalid/" + ASIN
                        );

                productId =
                        product.id();
            }

            /*
             * As duas tarefas devem estar prontas antes que qualquer
             * uma tente executar o INSERT.
             *
             * readyLatch confirma que ambas chegaram à barreira.
             * startLatch libera ambas praticamente no mesmo instante.
             */
            CountDownLatch readyLatch =
                    new CountDownLatch(
                            2
                    );

            CountDownLatch startLatch =
                    new CountDownLatch(
                            1
                    );

            Future<OfferSnapshotRepository.InsertResult> firstFuture =
                    executor.submit(
                            () -> insertConcurrently(
                                    config,
                                    product,
                                    readyLatch,
                                    startLatch
                            )
                    );

            Future<OfferSnapshotRepository.InsertResult> secondFuture =
                    executor.submit(
                            () -> insertConcurrently(
                                    config,
                                    product,
                                    readyLatch,
                                    startLatch
                            )
                    );

            /*
             * Evita um teste pendurado indefinidamente caso uma tarefa
             * nem sequer consiga alcançar a barreira.
             */
            assertTrue(
                    readyLatch.await(
                            5,
                            TimeUnit.SECONDS
                    ),
                    "Both concurrent tasks should become ready"
            );

            /*
             * Libera as duas conexões para competir pela mesma
             * identidade persistente.
             */
            startLatch.countDown();

            OfferSnapshotRepository.InsertResult first =
                    firstFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            OfferSnapshotRepository.InsertResult second =
                    secondFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            /*
             * Exatamente uma tentativa precisa criar a linha.
             *
             * Não importa qual thread venceu.
             */
            assertNotEquals(
                    first.created(),
                    second.created()
            );

            assertTrue(
                    first.created()
                            || second.created()
            );

            assertFalse(
                    first.created()
                            && second.created()
            );

            /*
             * As duas operações devem convergir para a mesma
             * identidade persistente.
             */
            assertEquals(
                    first.id(),
                    second.id()
            );

            /*
             * Confirma o resultado diretamente no PostgreSQL.
             */
            try (Connection connection =
                         DatabaseConnection.open(
                                 config
                         )) {

                assertEquals(
                        1L,
                        countSnapshots(
                                connection,
                                productId
                        )
                );
            }

        } finally {

            executor.shutdownNow();

            executor.awaitTermination(
                    5,
                    TimeUnit.SECONDS
            );

            cleanup(
                    config,
                    productId
            );
        }
    }

    /**
     * Executa uma das tentativas concorrentes.
     *
     * <p>Cada tarefa recebe sua própria Connection. Isso é essencial:
     * duas operações na mesma Connection não representariam duas
     * transações concorrentes reais.</p>
     */
    private OfferSnapshotRepository.InsertResult insertConcurrently(
            ApplicationConfig config,
            Product product,
            CountDownLatch readyLatch,
            CountDownLatch startLatch
    ) throws Exception {

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            /*
             * Cada thread controla sua própria transação.
             */
            connection.setAutoCommit(
                    false
            );

            try {
                OfferSnapshotRepository repository =
                        new OfferSnapshotRepository(
                                connection
                        );

                OfferSnapshot snapshot =
                        createSnapshot(
                                product
                        );

                /*
                 * Esta tarefa está pronta para disputar o INSERT.
                 */
                readyLatch.countDown();

                if (!startLatch.await(
                        5,
                        TimeUnit.SECONDS
                )) {

                    throw new IllegalStateException(
                            "Timed out waiting for concurrent start"
                    );
                }

                OfferSnapshotRepository.InsertResult result =
                        repository.insertIdempotent(
                                snapshot
                        );

                /*
                 * O commit libera definitivamente o conflito para a
                 * outra transação.
                 */
                connection.commit();

                return result;

            } catch (Exception exception) {

                connection.rollback();

                throw exception;

            } finally {

                connection.setAutoCommit(
                        true
                );
            }
        }
    }

    private OfferSnapshot createSnapshot(
            Product product
    ) {

        return new OfferSnapshot(
                null,
                product,
                COLLECTED_AT,
                Money.of(
                        "161.40"
                ),
                Money.of(
                        "299.00"
                ),
                null,
                Percentage.of(
                        "89"
                ),
                4.6,
                58363L,
                "Amazon.com.br",
                "Amazon.com.br",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                SOURCE,
                List.of()
        );
    }

    private long countSnapshots(
            Connection connection,
            long productId
    ) throws Exception {

        String sql = """
                SELECT COUNT(*)
                FROM offer_snapshot
                WHERE product_id = ?
                  AND collected_at = ?
                  AND source = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            statement.setLong(
                    1,
                    productId
            );

            statement.setObject(
                    2,
                    COLLECTED_AT
            );

            statement.setString(
                    3,
                    SOURCE
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                return resultSet.getLong(
                        1
                );
            }
        }
    }

    /**
     * Remove somente os registros pertencentes a este teste.
     */
    private void cleanup(
            ApplicationConfig config,
            long productId
    ) throws Exception {

        if (productId <= 0) {
            return;
        }

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 """
                                 DELETE FROM offer_snapshot
                                 WHERE product_id = ?
                                   AND source = ?
                                 """
                         )) {

                statement.setLong(
                        1,
                        productId
                );

                statement.setString(
                        2,
                        SOURCE
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 """
                                 DELETE FROM product
                                 WHERE id = ?
                                 """
                         )) {

                statement.setLong(
                        1,
                        productId
                );

                statement.executeUpdate();
            }
        }
    }
}