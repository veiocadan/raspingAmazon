package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.deal.OfferSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Repository JDBC responsável pela persistência de OfferSnapshot.
 *
 * <p>A identidade de uma observação é:</p>
 *
 * <pre>
 * product_id
 * + collected_at
 * + source
 * </pre>
 *
 * <p>Isso permite reprocessar a mesma coleta sem criar histórico
 * duplicado.</p>
 */
public final class OfferSnapshotRepository {

    private final Connection connection;

    public OfferSnapshotRepository(
            Connection connection
    ) {
        this.connection =
                Objects.requireNonNull(
                        connection,
                        "connection must not be null"
                );
    }

    /**
     * Mantém o método insert tradicional para consumidores existentes.
     */
    public long insert(
            OfferSnapshot snapshot
    ) throws SQLException {

        InsertResult result =
                insertIdempotent(
                        snapshot
                );

        return result.id();
    }

    /**
     * Persiste o snapshot de forma idempotente.
     *
     * <p>Quando a observação já existir, nenhuma coluna histórica é
     * sobrescrita. Retornamos apenas o id já existente.</p>
     */
    public InsertResult insertIdempotent(
            OfferSnapshot snapshot
    ) throws SQLException {

        Objects.requireNonNull(
                snapshot,
                "snapshot must not be null"
        );

        String sql = """
                INSERT INTO offer_snapshot (
                    product_id,
                    collected_at,
                    current_price,
                    basis_price,
                    previous_price,
                    sold_percentage,
                    rating,
                    review_count,
                    seller_name,
                    delivery_provider,
                    source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (
                    product_id,
                    collected_at,
                    source
                )
                DO NOTHING
                RETURNING id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            bindSnapshot(
                    statement,
                    snapshot
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {

                    return new InsertResult(
                            resultSet.getLong(
                                    "id"
                            ),
                            true
                    );
                }
            }
        }

        /*
         * Se o INSERT não retornou linha, a constraint encontrou
         * exatamente a mesma identidade lógica.
         */
        return findExistingIdentity(
                snapshot
        );
    }

    /**
     * Localiza o snapshot que provocou ON CONFLICT.
     */
    private InsertResult findExistingIdentity(
            OfferSnapshot snapshot
    ) throws SQLException {

        String sql = """
                SELECT id
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
                    snapshot.product().id()
            );

            statement.setObject(
                    2,
                    snapshot.collectedAt()
            );

            statement.setString(
                    3,
                    snapshot.source()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                            "OfferSnapshot conflict occurred but existing "
                                    + "snapshot could not be located"
                    );
                }

                return new InsertResult(
                        resultSet.getLong(
                                "id"
                        ),
                        false
                );
            }
        }
    }

    /**
     * Vincula o estado do domínio ao PreparedStatement.
     */
    private void bindSnapshot(
            PreparedStatement statement,
            OfferSnapshot snapshot
    ) throws SQLException {

        statement.setLong(
                1,
                snapshot.product().id()
        );

        statement.setObject(
                2,
                snapshot.collectedAt()
        );

        statement.setBigDecimal(
                3,
                snapshot.currentPrice().amount()
        );

        if (snapshot.basisPrice() == null) {
            statement.setObject(
                    4,
                    null
            );
        } else {
            statement.setBigDecimal(
                    4,
                    snapshot.basisPrice().amount()
            );
        }

        if (snapshot.previousPrice() == null) {
            statement.setObject(
                    5,
                    null
            );
        } else {
            statement.setBigDecimal(
                    5,
                    snapshot.previousPrice().amount()
            );
        }

        if (snapshot.soldPercentage() == null) {
            statement.setObject(
                    6,
                    null
            );
        } else {
            statement.setBigDecimal(
                    6,
                    snapshot.soldPercentage().value()
            );
        }

        if (snapshot.rating() == null) {
            statement.setObject(
                    7,
                    null
            );
        } else {
            statement.setDouble(
                    7,
                    snapshot.rating()
            );
        }

        if (snapshot.reviewCount() == null) {
            statement.setObject(
                    8,
                    null
            );
        } else {
            statement.setLong(
                    8,
                    snapshot.reviewCount()
            );
        }

        statement.setString(
                9,
                snapshot.sellerName()
        );

        statement.setString(
                10,
                snapshot.deliveryProvider()
        );

        statement.setString(
                11,
                snapshot.source()
        );
    }

    /**
     * Resultado interno da operação idempotente.
     */
    public record InsertResult(
            long id,
            boolean created
    ) {

        public InsertResult {

            if (id <= 0) {
                throw new IllegalArgumentException(
                        "OfferSnapshot id must be positive"
                );
            }
        }
    }
}