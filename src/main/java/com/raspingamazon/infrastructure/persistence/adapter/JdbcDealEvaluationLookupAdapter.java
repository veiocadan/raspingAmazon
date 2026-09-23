package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.port.DealEvaluationLookupPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Consulta JDBC utilizada para detectar DealEvaluation já
 * persistida para um OfferSnapshot.
 */
public final class JdbcDealEvaluationLookupAdapter
    implements DealEvaluationLookupPort {

    private final Connection connection;

    public JdbcDealEvaluationLookupAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public OptionalLong findEvaluationIdByOfferSnapshotId(
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
            FROM deal_evaluation
            WHERE offer_snapshot_id = ?
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
                    return OptionalLong.empty();
                }

                long evaluationId =
                    resultSet.getLong(
                        "id"
                    );

                /*
                 * A V12 garante unicidade no banco.
                 *
                 * Esta verificação continua útil como defesa contra
                 * schema incorreto ou migration não aplicada.
                 */
                if (resultSet.next()) {
                    throw new IllegalStateException(
                        "More than one DealEvaluation found for OfferSnapshot "
                            + offerSnapshotId
                    );
                }

                return OptionalLong.of(
                    evaluationId
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Could not find DealEvaluation for OfferSnapshot "
                    + offerSnapshotId,
                exception
            );
        }
    }
}
