package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.scoring.ScoreProfileProvider;
import com.raspingamazon.domain.scoring.ScoreProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC da fonte de ScoreProfile.
 */
public final class ScoreProfileJdbcRepository
    implements ScoreProfileProvider {

    private final Connection connection;

    public ScoreProfileJdbcRepository(
        Connection connection
    ) {
        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public ScoreProfile activeProfile() {

        String sql = """
            SELECT
                version,
                sold_percentage_weight,
                cash_discount_weight,
                basis_discount_weight,
                rating_weight,
                review_count_weight,
                review_count_full_score_threshold
            FROM score_profile
            WHERE active = true
            """;

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql);
            ResultSet resultSet =
                statement.executeQuery()
        ) {
            if (!resultSet.next()) {
                throw new IllegalStateException(
                    "No active score profile was found"
                );
            }

            ScoreProfile profile =
                mapScoreProfile(
                    resultSet
                );

            if (resultSet.next()) {
                throw new IllegalStateException(
                    "More than one active score profile was found"
                );
            }

            return profile;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to load active score profile",
                exception
            );
        }
    }

    private ScoreProfile mapScoreProfile(
        ResultSet resultSet
    ) throws SQLException {
        return new ScoreProfile(
            resultSet.getString(
                "version"
            ),
            resultSet.getBigDecimal(
                "sold_percentage_weight"
            ),
            resultSet.getBigDecimal(
                "cash_discount_weight"
            ),
            resultSet.getBigDecimal(
                "basis_discount_weight"
            ),
            resultSet.getBigDecimal(
                "rating_weight"
            ),
            resultSet.getBigDecimal(
                "review_count_weight"
            ),
            resultSet.getLong(
                "review_count_full_score_threshold"
            )
        );
    }
}
