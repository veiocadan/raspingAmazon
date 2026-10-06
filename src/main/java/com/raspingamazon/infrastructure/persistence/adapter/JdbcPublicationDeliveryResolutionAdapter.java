package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionDecision;
import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionRequest;
import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionResult;
import com.raspingamazon.application.publication.outbox.resolution.port.PublicationDeliveryResolutionPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação PostgreSQL da resolução operacional de
 * publication_outbox em DELIVERY_UNKNOWN.
 *
 * <p>A tentativa externa original permanece imutável. Esta classe
 * altera somente:</p>
 *
 * <ul>
 *     <li>publication_delivery_resolution_event;</li>
 *     <li>publication_outbox.</li>
 * </ul>
 *
 * <p>Decisões:</p>
 *
 * <pre>
 * DELIVERY_UNKNOWN
 *      |
 *      +-- CONFIRMED_DELIVERED
 *      |       -> SUCCEEDED
 *      |       -> preserva finished_at original
 *      |
 *      +-- CONFIRMED_NOT_DELIVERED
 *      |       -> PENDING
 *      |       -> available_at = resolvedAt
 *      |       -> finished_at = NULL
 *      |
 *      +-- REMAINS_UNKNOWN
 *              -> DELIVERY_UNKNOWN
 *              -> nenhum retry
 * </pre>
 *
 * <p>A operação é transacional e idempotente por requestKey.</p>
 */
public final class JdbcPublicationDeliveryResolutionAdapter
    implements PublicationDeliveryResolutionPort {

    private final Connection connection;

    private final JdbcTransactionAdapter transaction;

    public JdbcPublicationDeliveryResolutionAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.transaction =
            new JdbcTransactionAdapter(
                connection
            );
    }

    @Override
    public PublicationDeliveryResolutionResult resolve(
        PublicationDeliveryResolutionRequest request,
        OffsetDateTime resolvedAt
    ) {

        PublicationDeliveryResolutionRequest validatedRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        OffsetDateTime validatedResolvedAt =
            Objects.requireNonNull(
                resolvedAt,
                "resolvedAt must not be null"
            );

        return transaction.execute(
            () ->
                resolveInsideTransaction(
                    validatedRequest,
                    validatedResolvedAt
                )
        );
    }

    private PublicationDeliveryResolutionResult resolveInsideTransaction(
        PublicationDeliveryResolutionRequest request,
        OffsetDateTime resolvedAt
    ) {

        LockedOutbox outbox =
            lockOutbox(
                request.publicationOutboxId()
            );

        Optional<ResolutionEvent> existingEvent =
            findEventByRequestKey(
                request.requestKey()
            );

        if (existingEvent.isPresent()) {

            return replayExistingEvent(
                request,
                existingEvent.orElseThrow()
            );
        }

        requireDeliveryUnknown(
            outbox
        );

        Optional<Long> insertedEventId =
            insertResolutionEvent(
                request,
                resolvedAt,
                outbox
            );

        if (insertedEventId.isEmpty()) {

            /*
             * Outra transação venceu a UNIQUE(request_key).
             *
             * PostgreSQL somente decide o conflito depois que a
             * transação vencedora fica visível. Lemos então o evento
             * persistido e tratamos replay ou colisão.
             */
            ResolutionEvent concurrentEvent =
                findEventByRequestKey(
                    request.requestKey()
                ).orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Publication delivery resolution requestKey "
                                + "conflict was detected but no persisted "
                                + "event could be read"
                        )
                );

            return replayExistingEvent(
                request,
                concurrentEvent
            );
        }

        applyDecision(
            outbox,
            request.decision(),
            resolvedAt
        );

        return new PublicationDeliveryResolutionResult(
            insertedEventId.orElseThrow(),
            request.requestKey(),
            request.publicationOutboxId(),
            request.decision(),
            request.decision()
                .resultingStatus(),
            true
        );
    }

    private PublicationDeliveryResolutionResult replayExistingEvent(
        PublicationDeliveryResolutionRequest request,
        ResolutionEvent event
    ) {

        validateEventMatch(
            request,
            event
        );

        return new PublicationDeliveryResolutionResult(
            event.id(),
            event.requestKey(),
            event.publicationOutboxId(),
            event.decision(),
            event.resultingStatus(),
            false
        );
    }

    private LockedOutbox lockOutbox(
        long outboxId
    ) {

        String sql =
            """
            SELECT
                id,
                status,
                finished_at
            FROM publication_outbox
            WHERE id = ?
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

                    throw new IllegalStateException(
                        "Publication outbox item "
                            + outboxId
                            + " does not exist"
                    );
                }

                return new LockedOutbox(
                    resultSet.getLong(
                        "id"
                    ),
                    PublicationOutboxStatus.valueOf(
                        resultSet.getString(
                            "status"
                        )
                    ),
                    resultSet.getObject(
                        "finished_at",
                        OffsetDateTime.class
                    )
                );
            }

        } catch (SQLException exception) {

            throw persistenceFailure(
                "lock publication outbox for delivery resolution",
                exception
            );
        }
    }

    private Optional<ResolutionEvent> findEventByRequestKey(
        String requestKey
    ) {

        String sql =
            """
            SELECT
                id,
                publication_outbox_id,
                request_key,
                decision,
                resulting_status,
                decided_by,
                evidence,
                provider_reference,
                resolved_at
            FROM publication_delivery_resolution_event
            WHERE request_key = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                requestKey
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    new ResolutionEvent(
                        resultSet.getLong(
                            "id"
                        ),
                        resultSet.getLong(
                            "publication_outbox_id"
                        ),
                        resultSet.getString(
                            "request_key"
                        ),
                        PublicationDeliveryResolutionDecision.valueOf(
                            resultSet.getString(
                                "decision"
                            )
                        ),
                        PublicationOutboxStatus.valueOf(
                            resultSet.getString(
                                "resulting_status"
                            )
                        ),
                        resultSet.getString(
                            "decided_by"
                        ),
                        resultSet.getString(
                            "evidence"
                        ),
                        resultSet.getString(
                            "provider_reference"
                        ),
                        resultSet.getObject(
                            "resolved_at",
                            OffsetDateTime.class
                        )
                    )
                );
            }

        } catch (SQLException exception) {

            throw persistenceFailure(
                "read publication delivery resolution event",
                exception
            );
        }
    }

    private Optional<Long> insertResolutionEvent(
        PublicationDeliveryResolutionRequest request,
        OffsetDateTime resolvedAt,
        LockedOutbox outbox
    ) {

        String sql =
            """
            INSERT INTO publication_delivery_resolution_event (
                publication_outbox_id,
                request_key,
                decision,
                resulting_status,
                decided_by,
                evidence,
                provider_reference,
                resolved_at,
                previous_status,
                previous_finished_at
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
                ?
            )
            ON CONFLICT (
                request_key
            )
            DO NOTHING
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outbox.id()
            );

            statement.setString(
                2,
                request.requestKey()
            );

            statement.setString(
                3,
                request.decision()
                    .name()
            );

            statement.setString(
                4,
                request.decision()
                    .resultingStatus()
                    .name()
            );

            statement.setString(
                5,
                request.decidedBy()
            );

            statement.setString(
                6,
                request.evidence()
            );

            statement.setString(
                7,
                request.providerReference()
            );

            statement.setObject(
                8,
                resolvedAt
            );

            statement.setString(
                9,
                outbox.status()
                    .name()
            );

            statement.setObject(
                10,
                outbox.finishedAt()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    resultSet.getLong(
                        "id"
                    )
                );
            }

        } catch (SQLException exception) {

            throw persistenceFailure(
                "insert publication delivery resolution event",
                exception
            );
        }
    }

    private void applyDecision(
        LockedOutbox outbox,
        PublicationDeliveryResolutionDecision decision,
        OffsetDateTime resolvedAt
    ) {

        switch (decision) {

            case CONFIRMED_DELIVERED ->
                confirmDelivered(
                    outbox,
                    resolvedAt
                );

            case CONFIRMED_NOT_DELIVERED ->
                confirmNotDelivered(
                    outbox,
                    resolvedAt
                );

            case REMAINS_UNKNOWN ->
                keepUnknown(
                    outbox,
                    resolvedAt
                );
        }
    }

    /**
     * A confirmação posterior não altera quando a execução externa
     * terminou.
     *
     * <p>finished_at continua sendo o instante original em que a
     * tentativa ambígua terminou. resolved_at no evento registra
     * quando o conhecimento posterior foi adquirido.</p>
     */
    private void confirmDelivered(
        LockedOutbox outbox,
        OffsetDateTime resolvedAt
    ) {

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'SUCCEEDED',
                locked_at = NULL,
                locked_by = NULL,
                updated_at = ?
            WHERE id = ?
              AND status = 'DELIVERY_UNKNOWN'
              AND finished_at = ?
            """;

        executeExpectedSingleUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    resolvedAt
                );

                statement.setLong(
                    2,
                    outbox.id()
                );

                statement.setObject(
                    3,
                    outbox.finishedAt()
                );
            },
            "confirm publication delivery"
        );
    }

    private void confirmNotDelivered(
        LockedOutbox outbox,
        OffsetDateTime resolvedAt
    ) {

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
              AND status = 'DELIVERY_UNKNOWN'
              AND finished_at = ?
            """;

        executeExpectedSingleUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    resolvedAt
                );

                statement.setObject(
                    2,
                    resolvedAt
                );

                statement.setLong(
                    3,
                    outbox.id()
                );

                statement.setObject(
                    4,
                    outbox.finishedAt()
                );
            },
            "requeue publication after confirmed non-delivery"
        );
    }

    /**
     * A revisão foi auditada, porém a ambiguidade permanece.
     *
     * <p>Atualizamos somente updated_at para registrar que houve uma
     * ação operacional. finished_at original e DELIVERY_UNKNOWN
     * permanecem intactos.</p>
     */
    private void keepUnknown(
        LockedOutbox outbox,
        OffsetDateTime resolvedAt
    ) {

        String sql =
            """
            UPDATE publication_outbox
            SET
                updated_at = ?
            WHERE id = ?
              AND status = 'DELIVERY_UNKNOWN'
              AND finished_at = ?
            """;

        executeExpectedSingleUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    resolvedAt
                );

                statement.setLong(
                    2,
                    outbox.id()
                );

                statement.setObject(
                    3,
                    outbox.finishedAt()
                );
            },
            "keep publication delivery unknown"
        );
    }

    private void executeExpectedSingleUpdate(
        String sql,
        StatementBinder binder,
        String operation
    ) {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            binder.bind(
                statement
            );

            int updated =
                statement.executeUpdate();

            if (updated != 1) {

                throw new IllegalStateException(
                    "Could not "
                        + operation
                        + " for publication outbox "
                        + "because the persisted state changed"
                );
            }

        } catch (SQLException exception) {

            throw persistenceFailure(
                operation,
                exception
            );
        }
    }

    private void requireDeliveryUnknown(
        LockedOutbox outbox
    ) {

        if (outbox.status()
            != PublicationOutboxStatus.DELIVERY_UNKNOWN) {

            throw new IllegalStateException(
                "Publication outbox item "
                    + outbox.id()
                    + " cannot be resolved because status is "
                    + outbox.status()
            );
        }

        if (outbox.finishedAt()
            == null) {

            throw new IllegalStateException(
                "Publication outbox item "
                    + outbox.id()
                    + " is DELIVERY_UNKNOWN without finishedAt"
            );
        }
    }

    private void validateEventMatch(
        PublicationDeliveryResolutionRequest request,
        ResolutionEvent event
    ) {

        boolean sameDecision =
            event.publicationOutboxId()
                == request.publicationOutboxId()
            && event.requestKey()
                .equals(
                    request.requestKey()
                )
            && event.decision()
                == request.decision()
            && event.decidedBy()
                .equals(
                    request.decidedBy()
                )
            && event.evidence()
                .equals(
                    request.evidence()
                )
            && Objects.equals(
                event.providerReference(),
                request.providerReference()
            );

        if (!sameDecision) {

            throw new IllegalStateException(
                "Publication delivery resolution requestKey collision: "
                    + request.requestKey()
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

    @FunctionalInterface
    private interface StatementBinder {

        void bind(
            PreparedStatement statement
        ) throws SQLException;
    }

    private record LockedOutbox(
        long id,
        PublicationOutboxStatus status,
        OffsetDateTime finishedAt
    ) {
    }

    private record ResolutionEvent(
        long id,
        long publicationOutboxId,
        String requestKey,
        PublicationDeliveryResolutionDecision decision,
        PublicationOutboxStatus resultingStatus,
        String decidedBy,
        String evidence,
        String providerReference,
        OffsetDateTime resolvedAt
    ) {
    }
}
