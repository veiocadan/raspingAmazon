package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.orchestration.port.DealCandidateRepositoryPort;
import com.raspingamazon.application.parsing.contract.ParsedDeal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação JDBC da persistência de DealCandidate.
 *
 * <p>A identidade idempotente de um candidato dentro de uma
 * ProcessingRun é:</p>
 *
 * <pre>
 * processing_run_id + asin + source
 * </pre>
 *
 * <p>collectedAt permanece sendo um fato da observação original,
 * mas não participa da identidade. Assim, uma reentrada da mesma
 * etapa com outro timestamp não cria um segundo candidato lógico.</p>
 */
public final class JdbcDealCandidateRepositoryAdapter
    implements DealCandidateRepositoryPort {

    private final Connection connection;

    public JdbcDealCandidateRepositoryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public DealCandidate save(
        DealCandidate candidate
    ) {

        Objects.requireNonNull(
            candidate,
            "candidate must not be null"
        );

        if (candidate.persisted()) {
            throw new IllegalArgumentException(
                "New DealCandidate must not already have an id"
            );
        }

        ParsedDeal deal =
            candidate.parsedDeal();

        String sql =
            """
            INSERT INTO deal_candidate (
                processing_run_id,
                asin,
                product_url,
                title,
                image_url,
                current_price,
                basis_price,
                previous_price,
                sold_percentage,
                rating,
                review_count,
                collected_at,
                source
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            ON CONFLICT (
                processing_run_id,
                asin,
                source
            )
            DO UPDATE
            SET asin =
                EXCLUDED.asin
            RETURNING
                id,
                processing_run_id,
                asin,
                product_url,
                title,
                image_url,
                current_price,
                basis_price,
                previous_price,
                sold_percentage,
                rating,
                review_count,
                collected_at,
                source
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                candidate.processingRunId()
            );

            statement.setString(
                2,
                deal.asin()
            );

            statement.setString(
                3,
                deal.productUrl()
            );

            statement.setString(
                4,
                deal.title()
            );

            statement.setString(
                5,
                deal.imageUrl()
            );

            statement.setBigDecimal(
                6,
                deal.currentPrice()
            );

            statement.setBigDecimal(
                7,
                deal.basisPrice()
            );

            statement.setBigDecimal(
                8,
                deal.previousPrice()
            );

            statement.setBigDecimal(
                9,
                deal.soldPercentage()
            );

            setNullableDouble(
                statement,
                10,
                deal.rating()
            );

            setNullableLong(
                statement,
                11,
                deal.reviewCount()
            );

            statement.setObject(
                12,
                deal.collectedAt()
            );

            statement.setString(
                13,
                deal.source()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "DealCandidate insert returned no row"
                    );
                }

                return readCandidate(
                    resultSet
                );
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "save DealCandidate",
                exception
            );
        }
    }

    @Override
    public Optional<DealCandidate> findById(
        long id
    ) {

        requirePositiveId(
            id
        );

        String sql =
            """
            SELECT
                id,
                processing_run_id,
                asin,
                product_url,
                title,
                image_url,
                current_price,
                basis_price,
                previous_price,
                sold_percentage,
                rating,
                review_count,
                collected_at,
                source
            FROM deal_candidate
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                id
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    readCandidate(
                        resultSet
                    )
                );
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "find DealCandidate",
                exception
            );
        }
    }

    private DealCandidate readCandidate(
        ResultSet resultSet
    ) throws SQLException {

        ParsedDeal parsedDeal =
            new ParsedDeal(
                resultSet.getString(
                    "asin"
                ),
                resultSet.getString(
                    "product_url"
                ),
                resultSet.getString(
                    "title"
                ),
                resultSet.getString(
                    "image_url"
                ),
                resultSet.getBigDecimal(
                    "current_price"
                ),
                resultSet.getBigDecimal(
                    "basis_price"
                ),
                resultSet.getBigDecimal(
                    "previous_price"
                ),
                resultSet.getBigDecimal(
                    "sold_percentage"
                ),
                getNullableDouble(
                    resultSet,
                    "rating"
                ),
                getNullableLong(
                    resultSet,
                    "review_count"
                ),
                resultSet.getObject(
                    "collected_at",
                    OffsetDateTime.class
                ),
                resultSet.getString(
                    "source"
                )
            );

        return new DealCandidate(
            resultSet.getLong(
                "id"
            ),
            resultSet.getLong(
                "processing_run_id"
            ),
            parsedDeal
        );
    }

    private Double getNullableDouble(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        double value =
            resultSet.getDouble(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private Long getNullableLong(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        long value =
            resultSet.getLong(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private void setNullableDouble(
        PreparedStatement statement,
        int index,
        Double value
    ) throws SQLException {

        if (value == null) {

            statement.setObject(
                index,
                null
            );

            return;
        }

        statement.setDouble(
            index,
            value
        );
    }

    private void setNullableLong(
        PreparedStatement statement,
        int index,
        Long value
    ) throws SQLException {

        if (value == null) {

            statement.setObject(
                index,
                null
            );

            return;
        }

        statement.setLong(
            index,
            value
        );
    }

    private void requirePositiveId(
        long id
    ) {

        if (id <= 0) {
            throw new IllegalArgumentException(
                "DealCandidate id must be positive"
            );
        }
    }

    private IllegalStateException persistenceFailure(
        String operation,
        SQLException exception
    ) {

        return new IllegalStateException(
            "Could not "
                + operation,
            exception
        );
    }
}
