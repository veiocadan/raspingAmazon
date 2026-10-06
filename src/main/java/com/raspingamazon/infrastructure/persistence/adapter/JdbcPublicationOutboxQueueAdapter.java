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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação PostgreSQL do consumo da publication_outbox.
 *
 * <p>O claim utiliza FOR UPDATE SKIP LOCKED no mesmo padrão da
 * fila durável de processamento.</p>
 *
 * <p>A recuperação de lease da FASE 20 é fail-closed:</p>
 *
 * <ul>
 *     <li>
 *         PROCESSING expirado sem STARTED volta para PENDING;
 *     </li>
 *     <li>
 *         PROCESSING expirado com STARTED termina como
 *         DELIVERY_UNKNOWN, juntamente com a tentativa.
 *     </li>
 * </ul>
 *
 * <p>Recovery e completion bloqueiam primeiro a mesma linha de outbox.
 * Isso serializa as duas decisões e impede que uma conclusão conhecida
 * e uma recuperação ambígua avancem em paralelo.</p>
 */
public final class JdbcPublicationOutboxQueueAdapter
    implements PublicationOutboxQueuePort {

    static final String UNKNOWN_AFTER_LEASE_EXPIRY_ERROR =
        "PUBLICATION_DELIVERY_OUTCOME_UNKNOWN_AFTER_LEASE_EXPIRY";

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationOutboxQueueAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.transactionAdapter =
            new JdbcTransactionAdapter(
                connection
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

        OffsetDateTime validatedLockedBefore =
            Objects.requireNonNull(
                lockedBefore,
                "lockedBefore must not be null"
            );

        OffsetDateTime validatedRecoveredAt =
            Objects.requireNonNull(
                recoveredAt,
                "recoveredAt must not be null"
            );

        if (validatedRecoveredAt.isBefore(
            validatedLockedBefore
        )) {

            throw new IllegalArgumentException(
                "recoveredAt must not be before lockedBefore"
            );
        }

        return transactionAdapter.execute(
            () ->
                recoverExpiredLeasesInsideTransaction(
                    validatedLockedBefore,
                    validatedRecoveredAt
                )
        );
    }

    private int recoverExpiredLeasesInsideTransaction(
        OffsetDateTime lockedBefore,
        OffsetDateTime recoveredAt
    ) {

        try {

            List<Long> expiredOutboxIds =
                lockExpiredOutboxes(
                    lockedBefore
                );

            for (long outboxId : expiredOutboxIds) {

                Optional<Long> startedAttemptId =
                    findStartedAttemptForUpdate(
                        outboxId
                    );

                if (startedAttemptId.isPresent()) {

                    markAttemptDeliveryUnknown(
                        startedAttemptId.orElseThrow(),
                        recoveredAt
                    );

                    markOutboxDeliveryUnknown(
                        outboxId,
                        recoveredAt
                    );

                } else {

                    returnOutboxToPending(
                        outboxId,
                        recoveredAt
                    );
                }
            }

            return expiredOutboxIds.size();

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to recover expired publication outbox leases",
                exception
            );
        }
    }

    /**
     * Bloqueia primeiro a outbox.
     *
     * <p>Completion utiliza a mesma ordem de lock. O SKIP LOCKED evita
     * que o recovery espere por uma conclusão ainda em andamento.</p>
     */
    private List<Long> lockExpiredOutboxes(
        OffsetDateTime lockedBefore
    ) throws SQLException {

        String sql =
            """
            SELECT id
            FROM publication_outbox
            WHERE status = 'PROCESSING'
              AND locked_at <= ?
            ORDER BY
                locked_at ASC,
                id ASC
            FOR UPDATE SKIP LOCKED
            """;

        List<Long> ids =
            new ArrayList<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                lockedBefore
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    ids.add(
                        resultSet.getLong(
                            "id"
                        )
                    );
                }
            }
        }

        return ids;
    }

    /**
     * Procura a tentativa física ainda aberta da outbox.
     *
     * <p>V35 garante no máximo um STARTED ativo por outbox. A checagem
     * de multiplicidade permanece aqui como defesa adicional.</p>
     */
    private Optional<Long> findStartedAttemptForUpdate(
        long outboxId
    ) throws SQLException {

        String sql =
            """
            SELECT id
            FROM publication_attempt
            WHERE publication_outbox_id = ?
              AND status = 'STARTED'
            ORDER BY id ASC
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    return Optional.empty();
                }

                long attemptId =
                    resultSet.getLong(
                        "id"
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox item "
                            + outboxId
                            + " contains more than one STARTED attempt"
                    );
                }

                return Optional.of(
                    attemptId
                );
            }
        }
    }

    private void markAttemptDeliveryUnknown(
        long attemptId,
        OffsetDateTime recoveredAt
    ) throws SQLException {

        String sql =
            """
            UPDATE publication_attempt
            SET
                status = 'DELIVERY_UNKNOWN',
                error_code = ?,
                finished_at = ?
            WHERE id = ?
              AND status = 'STARTED'
              AND finished_at IS NULL
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                UNKNOWN_AFTER_LEASE_EXPIRY_ERROR
            );

            statement.setObject(
                2,
                recoveredAt
            );

            statement.setLong(
                3,
                attemptId
            );

            int updated =
                statement.executeUpdate();

            if (updated != 1) {

                throw new IllegalStateException(
                    "Publication attempt "
                        + attemptId
                        + " could not be moved from STARTED "
                        + "to DELIVERY_UNKNOWN"
                );
            }
        }
    }

    private void markOutboxDeliveryUnknown(
        long outboxId,
        OffsetDateTime recoveredAt
    ) throws SQLException {

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'DELIVERY_UNKNOWN',
                locked_at = NULL,
                locked_by = NULL,
                updated_at = ?,
                finished_at = ?
            WHERE id = ?
              AND status = 'PROCESSING'
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
                recoveredAt
            );

            statement.setLong(
                3,
                outboxId
            );

            int updated =
                statement.executeUpdate();

            if (updated != 1) {

                throw new IllegalStateException(
                    "Publication outbox item "
                        + outboxId
                        + " could not be moved from PROCESSING "
                        + "to DELIVERY_UNKNOWN"
                );
            }
        }
    }

    private void returnOutboxToPending(
        long outboxId,
        OffsetDateTime recoveredAt
    ) throws SQLException {

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'PENDING',
                locked_at = NULL,
                locked_by = NULL,
                updated_at = ?,
                finished_at = NULL
            WHERE id = ?
              AND status = 'PROCESSING'
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                recoveredAt
            );

            statement.setLong(
                2,
                outboxId
            );

            int updated =
                statement.executeUpdate();

            if (updated != 1) {

                throw new IllegalStateException(
                    "Publication outbox item "
                        + outboxId
                        + " could not be returned from PROCESSING "
                        + "to PENDING"
                );
            }
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
