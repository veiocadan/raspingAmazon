package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação PostgreSQL do consumo da publication_outbox.
 *
 * <p>O claim utiliza FOR UPDATE SKIP LOCKED no mesmo padrão da
 * fila durável de processamento.</p>
 */
public final class JdbcPublicationOutboxQueueAdapter
    implements PublicationOutboxQueuePort {

    private final Connection connection;

    public JdbcPublicationOutboxQueueAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Optional<PublicationOutboxItem> claimNext(
        String workerId,
        OffsetDateTime claimedAt
    ) {

        String validatedWorkerId =
            requireText(
                workerId,
                "workerId"
            );

        Objects.requireNonNull(
            claimedAt,
            "claimedAt must not be null"
        );

        String sql =
            """
            WITH next_outbox AS (
                SELECT id
                FROM publication_outbox
                WHERE status = 'PENDING'
                  AND available_at <= ?
                ORDER BY
                    available_at ASC,
                    selection_run_id ASC,
                    selection_position ASC,
                    id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE publication_outbox AS outbox
            SET
                status = 'PROCESSING',
                locked_at = ?,
                locked_by = ?,
                updated_at = ?
            FROM next_outbox
            WHERE outbox.id = next_outbox.id
            RETURNING
                outbox.id,
                outbox.publication_id,
                outbox.selection_run_id,
                outbox.selection_position,
                outbox.channel,
                outbox.destination,
                outbox.content,
                outbox.quota_profile_version,
                outbox.quota_date,
                outbox.status,
                outbox.available_at,
                outbox.locked_at,
                outbox.locked_by,
                outbox.created_at,
                outbox.updated_at,
                outbox.finished_at
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                claimedAt
            );

            statement.setObject(
                2,
                claimedAt
            );

            statement.setString(
                3,
                validatedWorkerId
            );

            statement.setObject(
                4,
                claimedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    return Optional.empty();
                }

                return Optional.of(
                    readItem(
                        resultSet
                    )
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to claim publication outbox item",
                exception
            );
        }
    }

    /**
     * Devolve para PENDING uma entrada já reivindicada quando nenhuma
     * chamada ao provider ocorreu.
     *
     * <p>Essa é a transição utilizada pelo rate limiter.</p>
     */
    @Override
    public PublicationOutboxItem deferClaimed(
        long outboxId,
        String workerId,
        OffsetDateTime availableAt,
        OffsetDateTime deferredAt
    ) {

        if (outboxId <= 0L) {

            throw new IllegalArgumentException(
                "outboxId must be positive"
            );
        }

        String validatedWorkerId =
            requireText(
                workerId,
                "workerId"
            );

        Objects.requireNonNull(
            availableAt,
            "availableAt must not be null"
        );

        Objects.requireNonNull(
            deferredAt,
            "deferredAt must not be null"
        );

        if (!availableAt.isAfter(
            deferredAt
        )) {

            throw new IllegalArgumentException(
                "availableAt must be after deferredAt"
            );
        }

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'PENDING',
                available_at = ?,
                locked_at = NULL,
                locked_by = NULL,
                updated_at = ?,
                finished_at = NULL
            WHERE id = ?
              AND status = 'PROCESSING'
              AND locked_by = ?
            RETURNING
                id,
                publication_id,
                selection_run_id,
                selection_position,
                channel,
                destination,
                content,
                quota_profile_version,
                quota_date,
                status,
                available_at,
                locked_at,
                locked_by,
                created_at,
                updated_at,
                finished_at
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                availableAt
            );

            statement.setObject(
                2,
                deferredAt
            );

            statement.setLong(
                3,
                outboxId
            );

            statement.setString(
                4,
                validatedWorkerId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox item "
                            + outboxId
                            + " could not be deferred by worker "
                            + validatedWorkerId
                    );
                }

                PublicationOutboxItem deferred =
                    readItem(
                        resultSet
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox defer returned more "
                            + "than one row for id "
                            + outboxId
                    );
                }

                return deferred;
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to defer publication outbox item "
                    + outboxId,
                exception
            );
        }
    }

    @Override
    public int recoverExpiredLeases(
        OffsetDateTime lockedBefore,
        OffsetDateTime recoveredAt
    ) {

        Objects.requireNonNull(
            lockedBefore,
            "lockedBefore must not be null"
        );

        Objects.requireNonNull(
            recoveredAt,
            "recoveredAt must not be null"
        );

        if (recoveredAt.isBefore(
            lockedBefore
        )) {

            throw new IllegalArgumentException(
                "recoveredAt must not be before lockedBefore"
            );
        }

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'PENDING',
                locked_at = NULL,
                locked_by = NULL,
                updated_at = ?
            WHERE status = 'PROCESSING'
              AND locked_at <= ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                recoveredAt
            );

            statement.setObject(
                2,
                lockedBefore
            );

            return statement.executeUpdate();

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to recover expired publication outbox leases",
                exception
            );
        }
    }

    private PublicationOutboxItem readItem(
        ResultSet resultSet
    ) throws SQLException {

        return new PublicationOutboxItem(
            resultSet.getLong(
                "id"
            ),
            resultSet.getLong(
                "publication_id"
            ),
            resultSet.getLong(
                "selection_run_id"
            ),
            resultSet.getInt(
                "selection_position"
            ),
            resultSet.getString(
                "channel"
            ),
            resultSet.getString(
                "destination"
            ),
            resultSet.getString(
                "content"
            ),
            resultSet.getString(
                "quota_profile_version"
            ),
            resultSet.getObject(
                "quota_date",
                LocalDate.class
            ),
            PublicationOutboxStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getObject(
                "available_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "locked_at",
                OffsetDateTime.class
            ),
            resultSet.getString(
                "locked_by"
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "finished_at",
                OffsetDateTime.class
            )
        );
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
