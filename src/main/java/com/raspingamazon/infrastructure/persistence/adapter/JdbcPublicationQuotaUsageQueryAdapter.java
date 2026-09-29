package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.selection.port.PublicationQuotaUsageQueryPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Consulta a ocupação persistente da quota de publicação.
 *
 * <p>A autoridade é publication_outbox.</p>
 *
 * <p>Todas as linhas contam como vaga reservada,
 * independentemente do status atual:</p>
 *
 * <ul>
 *     <li>PENDING;</li>
 *     <li>PROCESSING;</li>
 *     <li>SUCCEEDED;</li>
 *     <li>FAILED_TRANSIENT;</li>
 *     <li>FAILED_PERMANENT.</li>
 * </ul>
 *
 * <p>Uma falha não libera automaticamente a vaga para outro
 * candidato no mesmo dia.</p>
 */
public final class JdbcPublicationQuotaUsageQueryAdapter
    implements PublicationQuotaUsageQueryPort {

    private final Connection connection;

    public JdbcPublicationQuotaUsageQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public long occupiedSlots(
        String channel,
        String destination,
        LocalDate quotaDate
    ) {

        String validatedChannel =
            requireText(
                channel,
                "channel"
            );

        String validatedDestination =
            requireText(
                destination,
                "destination"
            );

        Objects.requireNonNull(
            quotaDate,
            "quotaDate must not be null"
        );

        String sql =
            """
            SELECT COUNT(*) AS occupied_slots
            FROM publication_outbox
            WHERE channel = ?
              AND destination = ?
              AND quota_date = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                validatedChannel
            );

            statement.setString(
                2,
                validatedDestination
            );

            statement.setObject(
                3,
                quotaDate
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "Publication quota usage query returned no row"
                    );
                }

                return resultSet.getLong(
                    "occupied_slots"
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load publication quota usage",
                exception
            );
        }
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }
}
