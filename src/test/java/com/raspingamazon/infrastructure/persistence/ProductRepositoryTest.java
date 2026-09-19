package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.product.Product;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de integração JDBC de ProductRepository.
 */
class ProductRepositoryTest {

    @Test
    void shouldInsertProduct() throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();

        String asin =
                "B000TEST01";

        long productId =
                0;

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            ProductRepository repository =
                    new ProductRepository(
                            connection
                    );

            productId =
                    repository.insert(
                            asin,
                            "Produto de teste",
                            null,
                            "https://example.invalid/product"
                    );

            assertTrue(
                    productId > 0
            );

            assertTrue(
                    repository.existsByAsin(
                            asin
                    )
            );

        } finally {

            deleteProduct(
                    config,
                    productId
            );
        }
    }

    @Test
    void shouldUpsertSameAsinWithoutCreatingDuplicateProduct()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();

        String asin =
                "B000TEST02";

        long productId =
                0;

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            ProductRepository repository =
                    new ProductRepository(
                            connection
                    );

            Product first =
                    repository.upsert(
                            asin,
                            "Título inicial",
                            null,
                            "https://example.invalid/product-initial"
                    );

            productId =
                    first.id();

            Product updated =
                    repository.upsert(
                            asin,
                            "Título atualizado",
                            "https://example.invalid/image.jpg",
                            "https://example.invalid/product-updated"
                    );

            /*
             * O mesmo ASIN continua apontando para a mesma
             * identidade persistente.
             */
            assertEquals(
                    first.id(),
                    updated.id()
            );

            assertEquals(
                    asin,
                    updated.asin().value()
            );

            assertEquals(
                    "Título atualizado",
                    updated.title()
            );

            assertEquals(
                    "https://example.invalid/image.jpg",
                    updated.imageUrl()
            );

            assertEquals(
                    "https://example.invalid/product-updated",
                    updated.productUrl()
            );

            /*
             * Confirma no banco que existe somente uma linha
             * para esse ASIN.
             */
            String sql = """
                    SELECT COUNT(*)
                    FROM product
                    WHERE asin = ?
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 sql
                         )) {

                statement.setString(
                        1,
                        asin
                );

                try (var resultSet =
                             statement.executeQuery()) {

                    assertTrue(
                            resultSet.next()
                    );

                    assertEquals(
                            1L,
                            resultSet.getLong(
                                    1
                            )
                    );
                }
            }

        } finally {

            deleteProduct(
                    config,
                    productId
            );
        }
    }

    /**
     * Remove o produto criado pelo teste sem produzir saída
     * desnecessária no console.
     */
    private void deleteProduct(
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