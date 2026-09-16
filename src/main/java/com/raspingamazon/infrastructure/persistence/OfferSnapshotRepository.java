package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.deal.OfferSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Repository JDBC responsável pela persistência de OfferSnapshot.
 *
 * <p>Este componente somente persiste o estado definido pelo domínio.
 * Regras comerciais não devem ser implementadas aqui.</p>
 *
 * <p>As condições de pagamento possuem persistência própria por meio
 * de OfferPaymentConditionRepository.</p>
 */
public final class OfferSnapshotRepository {

    private final Connection connection;

    public OfferSnapshotRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Persiste um OfferSnapshot e retorna o identificador gerado.
     *
     * <p>O desconto não é mais persistido diretamente no snapshot.
     * A semântica comercial do desconto pertence a PaymentCondition
     * e é persistida por OfferPaymentConditionRepository.</p>
     */
    public long insert(OfferSnapshot snapshot) throws SQLException {

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
                RETURNING id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

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
                statement.setObject(4, null);
            } else {
                statement.setBigDecimal(
                        4,
                        snapshot.basisPrice().amount()
                );
            }

            if (snapshot.previousPrice() == null) {
                statement.setObject(5, null);
            } else {
                statement.setBigDecimal(
                        5,
                        snapshot.previousPrice().amount()
                );
            }

            if (snapshot.soldPercentage() == null) {
                statement.setObject(6, null);
            } else {
                statement.setBigDecimal(
                        6,
                        snapshot.soldPercentage().value()
                );
            }

            if (snapshot.rating() == null) {
                statement.setObject(7, null);
            } else {
                statement.setDouble(
                        7,
                        snapshot.rating()
                );
            }

            if (snapshot.reviewCount() == null) {
                statement.setObject(8, null);
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

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                            "Failed to obtain generated offer_snapshot id"
                    );
                }

                return resultSet.getLong("id");
            }
        }
    }
}
