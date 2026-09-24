package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.domain.filter.FilterProfile;
import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC da fonte de FilterProfile.
 */
public final class FilterProfileJdbcRepository
    implements FilterProfileProvider {

    private final Connection connection;

    public FilterProfileJdbcRepository(
        Connection connection
    ) {
        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public FilterProfile activeProfile() {

        String sql = """
            SELECT
                version,
                min_cash_discount_percentage,
                min_basis_discount_percentage,
                min_rating,
                min_review_count
            FROM filter_profile
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
                    "No active commercial filter profile was found"
                );
            }

            FilterProfile profile =
                mapFilterProfile(
                    resultSet
                );

            if (resultSet.next()) {
                throw new IllegalStateException(
                    "More than one active commercial filter profile was found"
                );
            }

            return profile;

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to load active commercial filter profile",
                exception
            );
        }
    }

    private FilterProfile mapFilterProfile(
        ResultSet resultSet
    ) throws SQLException {
        return new FilterProfile(
            resultSet.getString(
                "version"
            ),
            percentageOrNull(
                resultSet.getBigDecimal(
                    "min_cash_discount_percentage"
                )
            ),
            percentageOrNull(
                resultSet.getBigDecimal(
                    "min_basis_discount_percentage"
                )
            ),
            resultSet.getBigDecimal(
                "min_rating"
            ),
            resultSet.getLong(
                "min_review_count"
            )
        );
    }

    private Percentage percentageOrNull(
        BigDecimal value
    ) {
        return value == null
            ? null
            : new Percentage(value);
    }
}
