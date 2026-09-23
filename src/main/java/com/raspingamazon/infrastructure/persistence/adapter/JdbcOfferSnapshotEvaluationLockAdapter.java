package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLockPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Lock PostgreSQL utilizado para serializar avaliações concorrentes
 * de um mesmo OfferSnapshot.
 *
 * <p>SELECT ... FOR UPDATE mantém o lock até commit ou rollback da
 * transação corrente.</p>
 */
public final class JdbcOfferSnapshotEvaluationLockAdapter
    implements OfferSnapshotEvaluationLockPort {

    private final Connection connection;

    public JdbcOfferSnapshotEvaluationLockAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public void lockById(
        long offerSnapshotId
    ) {

        if (offerSnapshotId <= 0) {
            throw new IllegalArgumentException(
                "offerSnapshotId must be positive"
            );
        }

        String sql =
            """
            SELECT id
            FROM offer_snapshot
            WHERE id = ?
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                offerSnapshotId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                        "OfferSnapshot not found: "
                            + offerSnapshotId
                    );
                }
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Could not lock OfferSnapshot for evaluation: "
                    + offerSnapshotId,
                exception
            );
        }
    }
}
