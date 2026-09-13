package com.raspingamazon.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ProductRepository {

    private final Connection connection;

    public ProductRepository(Connection connection) {
        this.connection = connection;
    }

    public long insert(
            String asin,
            String title,
            String imageUrl,
            String productUrl
    ) throws SQLException {
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

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, asin);
            statement.setString(2, title);
            statement.setString(3, imageUrl);
            statement.setString(4, productUrl);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Product insert did not return an id.");
                }

                return resultSet.getLong("id");
            }
        }
    }

    public boolean existsByAsin(String asin) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM product
                    WHERE asin = ?
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, asin);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Product existence query returned no result.");
                }

                return resultSet.getBoolean(1);
            }
        }
    }
}
