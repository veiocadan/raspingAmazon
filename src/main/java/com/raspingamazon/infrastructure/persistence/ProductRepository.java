package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Repository JDBC responsável pela persistência de Product.
 *
 * <p>Product representa identidade durável. O mesmo ASIN pode aparecer
 * em várias coletas, portanto o repository oferece também operação
 * de upsert.</p>
 */
public final class ProductRepository {

    private final Connection connection;

    public ProductRepository(
            Connection connection
    ) {
        this.connection = connection;
    }

    /**
     * Insere um novo Product.
     *
     * <p>Este método continua disponível para testes e operações
     * explicitamente orientadas a insert.</p>
     */
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

            statement.setString(
                    3,
                    imageUrl
            );

            statement.setString(
                    4,
                    productUrl
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                            "Product insert did not return an id."
                    );
                }

                return resultSet.getLong(
                        "id"
                );
            }
        }
    }

    /**
     * Insere o Product quando o ASIN ainda não existe ou atualiza
     * os dados mutáveis conhecidos quando ele já existe.
     *
     * <p>O ASIN permanece como identidade natural do Product.</p>
     *
     * <p>O uso de ON CONFLICT também evita uma janela de corrida
     * existente em estratégias do tipo exists + insert.</p>
     */
    public Product upsert(
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
                ON CONFLICT (asin)
                DO UPDATE SET
                    title = EXCLUDED.title,
                    image_url = EXCLUDED.image_url,
                    product_url = EXCLUDED.product_url
                RETURNING
                    id,
                    asin,
                    title,
                    image_url,
                    product_url
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

            statement.setString(
                    3,
                    imageUrl
            );

            statement.setString(
                    4,
                    productUrl
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                            "Product upsert did not return a row."
                    );
                }

                return mapProduct(
                        resultSet
                );
            }
        }
    }

    public boolean existsByAsin(
            String asin
    ) throws SQLException {

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

                if (!resultSet.next()) {
                    throw new SQLException(
                            "Product existence query returned no result."
                    );
                }

                return resultSet.getBoolean(
                        1
                );
            }
        }
    }

    /**
     * Converte uma linha JDBC em entidade de domínio.
     */
    private Product mapProduct(
            ResultSet resultSet
    ) throws SQLException {

        return new Product(
                resultSet.getLong(
                        "id"
                ),

                new Asin(
                        resultSet.getString(
                                "asin"
                        )
                ),

                resultSet.getString(
                        "title"
                ),

                resultSet.getString(
                        "image_url"
                ),

                resultSet.getString(
                        "product_url"
                )
        );
    }
}