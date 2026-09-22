package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.orchestration.port.EnrichedOfferLookupPort;
import com.raspingamazon.application.parsing.contract.ParsedDeal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Implementação JDBC da consulta de enriquecimento já persistido.
 *
 * <p>A consulta utiliza Product como identidade durável do ASIN e
 * OfferSnapshot como identidade temporal da observação.</p>
 */
public final class JdbcEnrichedOfferLookupAdapter
    implements EnrichedOfferLookupPort {

    private final Connection connection;

    public JdbcEnrichedOfferLookupAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public OptionalLong findSnapshotId(
        DealCandidate candidate
    ) {

        Objects.requireNonNull(
            candidate,
            "candidate must not be null"
        );

        ParsedDeal parsedDeal =
            candidate.parsedDeal();

        String sql =
            """
            SELECT snapshot.id
            FROM offer_snapshot AS snapshot
            INNER JOIN product
                ON product.id = snapshot.product_id
            WHERE product.asin = ?
              AND snapshot.collected_at = ?
              AND snapshot.source = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                parsedDeal.asin()
            );

            statement.setObject(
                2,
                parsedDeal.collectedAt()
            );

            statement.setString(
                3,
                parsedDeal.source()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return OptionalLong.empty();
                }

                long snapshotId =
                    resultSet.getLong(
                        "id"
                    );

                if (resultSet.next()) {
                    throw new IllegalStateException(
                        "More than one OfferSnapshot found for candidate "
                            + candidate.id()
                    );
                }

                return OptionalLong.of(
                    snapshotId
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Could not find persisted OfferSnapshot for DealCandidate "
                    + candidate.id(),
                exception
            );
        }
    }
}
