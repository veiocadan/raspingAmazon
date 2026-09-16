package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductRepositoryTest {

    @Test
    void shouldPersistAndQueryProduct() throws Exception {
        ApplicationConfig config = EnvironmentConfigProvider.load();

        String asin = "B000TEST02";

        try (Connection connection = DatabaseConnection.open(config)) {
            ProductRepository repository = new ProductRepository(connection);

            long productId = repository.insert(
                    asin,
                    "Produto de integração",
                    null,
                    "https://example.invalid/produto"
            );

            assertTrue(productId > 0);
            assertTrue(repository.existsByAsin(asin));

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM product WHERE id = ?"
            )) {
                statement.setLong(1, productId);
                statement.executeUpdate();
            }
        }
    }
}
