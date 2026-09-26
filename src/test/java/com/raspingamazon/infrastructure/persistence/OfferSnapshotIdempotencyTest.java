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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa a política de idempotência de OfferSnapshot diretamente
 * contra o PostgreSQL.
 *
 * <p>A identidade lógica de uma observação é:</p>
 *
 * <pre>
 * product_id
 * + collected_at
 * + source
 * </pre>
 *
 * <p>O teste também garante que uma coleta posterior continua
 * produzindo histórico normalmente.</p>
 */
@PostgresIntegrationTest
class OfferSnapshotIdempotencyTest {

    private static final String ASIN =
            "B0IDEMP001";

    private static final String SOURCE =
            "amazon-deals";

    @Test
    void shouldReuseExistingSnapshotForSameCollectionIdentity()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();

        /*
         * Garante que V6 esteja aplicada antes de exercitar
         * a constraint de idempotência.
         */

        long productId =
                0;

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            ProductRepository productRepository =
                    new ProductRepository(
                            connection
                    );

            Product product =
                    productRepository.upsert(
                            ASIN,
                            "Produto de teste de idempotência",
                            null,
                            "https://example.invalid/" + ASIN
                    );

            productId =
                    product.id();

            OfferSnapshot snapshot =
                    createSnapshot(
                            product,
                            OffsetDateTime.parse(
                                    "2026-09-18T20:00:00Z"
                            )
                    );

            OfferSnapshotRepository repository =
                    new OfferSnapshotRepository(
                            connection
                    );

            /*
             * Primeira tentativa:
             * a observação ainda não existe.
             */
            OfferSnapshotRepository.InsertResult first =
                    repository.insertIdempotent(
                            snapshot
                    );

            /*
             * Segunda tentativa:
             * exatamente a mesma identidade lógica.
             */
            OfferSnapshotRepository.InsertResult second =
                    repository.insertIdempotent(
                            snapshot
                    );

            assertTrue(
                    first.created()
            );

            assertFalse(
                    second.created()
            );

            /*
             * As duas chamadas devem apontar para exatamente
             * a mesma linha persistida.
             */
            assertEquals(
                    first.id(),
                    second.id()
            );

            /*
             * E o PostgreSQL precisa conter somente uma linha
             * para essa identidade.
             */
            assertEquals(
                    1L,
                    countSnapshots(
                            connection,
                            productId
                    )
            );

        } finally {

            cleanup(
                    config,
                    productId
            );
        }
    }

    @Test
    void shouldPreserveHistoryWhenCollectedAtChanges()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();


        long productId =
                0;

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            ProductRepository productRepository =
                    new ProductRepository(
                            connection
                    );

            Product product =
                    productRepository.upsert(
                            ASIN,
                            "Produto de teste de histórico",
                            null,
                            "https://example.invalid/" + ASIN
                    );

            productId =
                    product.id();

            OfferSnapshot firstObservation =
                    createSnapshot(
                            product,
                            OffsetDateTime.parse(
                                    "2026-09-18T20:00:00Z"
                            )
                    );

            OfferSnapshot secondObservation =
                    createSnapshot(
                            product,
                            OffsetDateTime.parse(
                                    "2026-09-18T21:00:00Z"
                            )
                    );

            OfferSnapshotRepository repository =
                    new OfferSnapshotRepository(
                            connection
                    );

            OfferSnapshotRepository.InsertResult first =
                    repository.insertIdempotent(
                            firstObservation
                    );

            OfferSnapshotRepository.InsertResult second =
                    repository.insertIdempotent(
                            secondObservation
                    );

            /*
             * Os dois eventos são observações legítimas e distintas.
             */
            assertTrue(
                    first.created()
            );

            assertTrue(
                    second.created()
            );

            assertNotEquals(
                    first.id(),
                    second.id()
            );

            /*
             * O histórico precisa possuir as duas observações.
             */
            assertEquals(
                    2L,
                    countSnapshots(
                            connection,
                            productId
                    )
            );

        } finally {

            cleanup(
                    config,
                    productId
            );
        }
    }

    /**
     * Cria um snapshot controlado para os testes.
     */
    private OfferSnapshot createSnapshot(
            Product product,
            OffsetDateTime collectedAt
    ) {

        return new OfferSnapshot(
                null,
                product,
                collectedAt,
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

    /**
     * Conta todos os snapshots pertencentes ao produto do teste.
     */
    private long countSnapshots(
            Connection connection,
            long productId
    ) throws Exception {

        String sql = """
                SELECT COUNT(*)
                FROM offer_snapshot
                WHERE product_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            statement.setLong(
                    1,
                    productId
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
     * Remove os dados produzidos pelo teste respeitando a ordem
     * das foreign keys.
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
                                 """
                         )) {

                statement.setLong(
                        1,
                        productId
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