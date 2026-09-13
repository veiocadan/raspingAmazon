package com.raspingamazon.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductRepositoryTest {

    @Test
    void shouldPersistAndQueryProduct() throws Exception {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String database = System.getenv("DB_NAME");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        String asin = "B000TEST02";

        try (Connection connection = DatabaseConnection.open(
                host,
                port,
                database,
                username,
                password
        )) {
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
