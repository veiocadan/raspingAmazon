package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.scheduling.port.PublicationCadenceReservationQueryPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação PostgreSQL da leitura do último slot de cadência
 * efetivamente reservado pela outbox primária.
 *
 * <p>A existência da linha de outbox com quota representa uma
 * posição ocupada independentemente do status atual da entrega.</p>
 *
 * <p>Portanto esta consulta deliberadamente não filtra por:</p>
 *
 * <pre>
 * PENDING
 * PROCESSING
 * SUCCEEDED
 * FAILED_TRANSIENT
 * FAILED_PERMANENT
 * </pre>
 *
 * <p>O filtro:</p>
 *
 * <pre>
 * quota_profile_version IS NOT NULL
 * </pre>
 *
 * <p>distingue reservas primárias das entregas derivadas
 * introduzidas pelo fan-out da FASE 19.</p>
 */
public final class JdbcPublicationCadenceReservationQueryAdapter
    implements PublicationCadenceReservationQueryPort {

    private final Connection connection;

    public JdbcPublicationCadenceReservationQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Optional<OffsetDateTime> findLastReservedAvailableAt(
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
            SELECT
                MAX(available_at) AS last_reserved_available_at
            FROM publication_outbox
            WHERE channel = ?
              AND destination = ?
              AND quota_date = ?
              AND quota_profile_version IS NOT NULL
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
                        "Publication cadence reservation query "
                            + "returned no aggregate row"
                    );
                }

                OffsetDateTime lastReservedAvailableAt =
                    resultSet.getObject(
                        "last_reserved_available_at",
                        OffsetDateTime.class
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication cadence reservation query "
                            + "returned more than one aggregate row"
                    );
                }

                return Optional.ofNullable(
                    lastReservedAvailableAt
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load last reserved publication "
                    + "availableAt",
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

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
