package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Implementação PostgreSQL do enqueue da outbox.
 *
 * <p>A criação da linha e a reserva da quota são a mesma
 * operação transacional.</p>
 *
 * <p>Fluxo:</p>
 *
 * <pre>
 * carregar Publication + SelectionRun + SelectionDecision
 *                  ↓
 * verificar idempotência
 *                  ↓
 * bloquear profile ativo de quota FOR UPDATE
 *                  ↓
 * verificar novamente idempotência
 *                  ↓
 * validar READY + SELECTED
 *                  ↓
 * validar que a seleção ainda pertence ao dia/perfil atual
 *                  ↓
 * contar publication_outbox no escopo
 *                  ↓
 * quota disponível?
 *      ├── não → QUOTA_EXHAUSTED
 *      └── sim → INSERT publication_outbox
 * </pre>
 *
 * <p>O bloqueio do profile ativo serializa as reservas do mesmo
 * channel + destination. A FASE 18.9C provará o comportamento
 * com duas conexões concorrentes.</p>
 */
public final class JdbcPublicationOutboxEnqueueAdapter
    implements PublicationOutboxEnqueuePort {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationOutboxEnqueueAdapter(
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
    public PublicationOutboxEnqueueResult enqueue(
        PublicationOutboxEnqueueRequest request
    ) {

        Objects.requireNonNull(
            request,
            "request must not be null"
        );

        return transactionAdapter.execute(
            () ->
                enqueueInsideTransaction(
                    request
                )
        );
    }

    private PublicationOutboxEnqueueResult enqueueInsideTransaction(
        PublicationOutboxEnqueueRequest request
    ) {

        try {

            SelectionContext context =
                loadSelectionContext(
                    request
                );

            /*
             * Fast path idempotente.
             *
             * Caso a linha já exista, nenhuma nova quota precisa
             * ser consultada ou reservada.
             */
            OptionalLong existingBeforeLock =
                findExistingOutboxId(
                    request.publicationId(),
                    context.channel(),
                    context.destination()
                );

            if (existingBeforeLock.isPresent()) {

                return PublicationOutboxEnqueueResult
                    .alreadyEnqueued(
                        existingBeforeLock.getAsLong()
                    );
            }

            ActiveQuotaProfile activeProfile =
                lockActiveQuotaProfile(
                    context.channel(),
                    context.destination()
                );

            /*
             * Repetimos a verificação depois do lock.
             *
             * Duas transações podem ter observado ausência antes
             * de uma delas adquirir o bloqueio.
             */
            OptionalLong existingAfterLock =
                findExistingOutboxId(
                    request.publicationId(),
                    context.channel(),
                    context.destination()
                );

            if (existingAfterLock.isPresent()) {

                return PublicationOutboxEnqueueResult
                    .alreadyEnqueued(
                        existingAfterLock.getAsLong()
                    );
            }

            validateNewReservationPreconditions(
                context
            );

            if (selectionIsStale(
                context,
                activeProfile,
                request
            )) {

                return PublicationOutboxEnqueueResult
                    .staleSelectionResult();
            }

            long occupiedSlots =
                countOccupiedSlots(
                    context.channel(),
                    context.destination(),
                    context.quotaDate()
                );

            if (occupiedSlots
                >= activeProfile.maxPublicationsPerDay()) {

                return PublicationOutboxEnqueueResult
                    .quotaExhaustedResult();
            }

            Long insertedId =
                insertOutbox(
                    context,
                    request
                );

            if (insertedId != null) {

                return PublicationOutboxEnqueueResult
                    .enqueued(
                        insertedId
                    );
            }

            /*
             * A constraint UNIQUE
             * publication + channel + destination
             * permanece como autoridade final de idempotência.
             */
            OptionalLong existingAfterInsert =
                findExistingOutboxId(
                    request.publicationId(),
                    context.channel(),
                    context.destination()
                );

            if (existingAfterInsert.isPresent()) {

                return PublicationOutboxEnqueueResult
                    .alreadyEnqueued(
                        existingAfterInsert.getAsLong()
                    );
            }

            throw new IllegalStateException(
                "Publication outbox insert returned no row "
                    + "and no existing delivery identity was found"
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to enqueue publication outbox",
                exception
            );
        }
    }

    private SelectionContext loadSelectionContext(
        PublicationOutboxEnqueueRequest request
    ) throws SQLException {

        String sql =
            """
            SELECT
                publication.status AS publication_status,
                publication.generated_text,
                run.channel,
                run.destination,
                run.quota_profile_version,
                run.quota_date,
                run.max_publications_per_day,
                decision.decision_status,
                decision.priority_position
            FROM publication
            JOIN publication_selection_run AS run
              ON run.id = ?
            LEFT JOIN publication_selection_decision AS decision
              ON decision.selection_run_id = run.id
             AND decision.deal_evaluation_id =
                 publication.deal_evaluation_id
            WHERE publication.id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                request.selectionRunId()
            );

            statement.setLong(
                2,
                request.publicationId()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalArgumentException(
                        "Publication "
                            + request.publicationId()
                            + " or selection run "
                            + request.selectionRunId()
                            + " was not found"
                    );
                }

                String decisionStatus =
                    resultSet.getString(
                        "decision_status"
                    );

                Integer selectionPosition =
                    resultSet.getObject(
                        "priority_position",
                        Integer.class
                    );

                if (decisionStatus == null) {

                    throw new IllegalStateException(
                        "Publication "
                            + request.publicationId()
                            + " does not belong to selection run "
                            + request.selectionRunId()
                    );
                }

                return new SelectionContext(
                    request.publicationId(),
                    request.selectionRunId(),
                    resultSet.getString(
                        "publication_status"
                    ),
                    resultSet.getString(
                        "generated_text"
                    ),
                    resultSet.getString(
                        "channel"
                    ),
                    resultSet.getString(
                        "destination"
                    ),
                    resultSet.getString(
                        "quota_profile_version"
                    ),
                    resultSet.getObject(
                        "quota_date",
                        LocalDate.class
                    ),
                    resultSet.getInt(
                        "max_publications_per_day"
                    ),
                    decisionStatus,
                    selectionPosition
                );
            }
        }
    }

    private OptionalLong findExistingOutboxId(
        long publicationId,
        String channel,
        String destination
    ) throws SQLException {

        String sql =
            """
            SELECT id
            FROM publication_outbox
            WHERE publication_id = ?
              AND channel = ?
              AND destination = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                publicationId
            );

            statement.setString(
                2,
                channel
            );

            statement.setString(
                3,
                destination
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return OptionalLong.empty();
                }

                return OptionalLong.of(
                    resultSet.getLong(
                        "id"
                    )
                );
            }
        }
    }

    private ActiveQuotaProfile lockActiveQuotaProfile(
        String channel,
        String destination
    ) throws SQLException {

        String sql =
            """
            SELECT
                version,
                max_publications_per_day,
                quota_zone
            FROM publication_quota_profile
            WHERE channel = ?
              AND destination = ?
              AND active = true
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                channel
            );

            statement.setString(
                2,
                destination
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "No active publication quota profile "
                            + "was found for channel "
                            + channel
                            + " and destination "
                            + destination
                    );
                }

                String quotaZoneValue =
                    resultSet.getString(
                        "quota_zone"
                    );

                ZoneId quotaZone;

                try {

                    quotaZone =
                        ZoneId.of(
                            quotaZoneValue
                        );

                } catch (DateTimeException exception) {

                    throw new IllegalStateException(
                        "Persisted publication quota profile "
                            + "contains invalid quota zone: "
                            + quotaZoneValue,
                        exception
                    );
                }

                ActiveQuotaProfile profile =
                    new ActiveQuotaProfile(
                        resultSet.getString(
                            "version"
                        ),
                        resultSet.getInt(
                            "max_publications_per_day"
                        ),
                        quotaZone
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one active publication quota "
                            + "profile was found for channel "
                            + channel
                            + " and destination "
                            + destination
                    );
                }

                return profile;
            }
        }
    }

    private void validateNewReservationPreconditions(
        SelectionContext context
    ) {

        if (!PublicationStatus.READY
            .name()
            .equals(
                context.publicationStatus()
            )) {

            throw new IllegalStateException(
                "Publication "
                    + context.publicationId()
                    + " must be READY before outbox enqueue"
            );
        }

        if (!"SELECTED".equals(
            context.decisionStatus()
        )) {

            throw new IllegalStateException(
                "Publication "
                    + context.publicationId()
                    + " was not SELECTED by selection run "
                    + context.selectionRunId()
            );
        }

        if (context.selectionPosition() == null
            || context.selectionPosition() <= 0) {

            throw new IllegalStateException(
                "Selected publication must have "
                    + "a positive selection position"
            );
        }

        if (context.content() == null
            || context.content().isBlank()) {

            throw new IllegalStateException(
                "Publication content must not be blank"
            );
        }
    }

    private boolean selectionIsStale(
        SelectionContext context,
        ActiveQuotaProfile activeProfile,
        PublicationOutboxEnqueueRequest request
    ) {

        if (!context.quotaProfileVersion()
            .equals(
                activeProfile.version()
            )) {

            return true;
        }

        if (context.maxPublicationsPerDay()
            != activeProfile.maxPublicationsPerDay()) {

            return true;
        }

        LocalDate enqueueQuotaDate =
            request.enqueuedAt()
                .toInstant()
                .atZone(
                    activeProfile.quotaZone()
                )
                .toLocalDate();

        if (!context.quotaDate()
            .equals(
                enqueueQuotaDate
            )) {

            return true;
        }

        LocalDate availabilityQuotaDate =
            request.availableAt()
                .toInstant()
                .atZone(
                    activeProfile.quotaZone()
                )
                .toLocalDate();

        return !context.quotaDate()
            .equals(
                availabilityQuotaDate
            );
    }

    private long countOccupiedSlots(
        String channel,
        String destination,
        LocalDate quotaDate
    ) throws SQLException {

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
                channel
            );

            statement.setString(
                2,
                destination
            );

            statement.setObject(
                3,
                quotaDate
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox quota count "
                            + "returned no row"
                    );
                }

                return resultSet.getLong(
                    "occupied_slots"
                );
            }
        }
    }

    private Long insertOutbox(
        SelectionContext context,
        PublicationOutboxEnqueueRequest request
    ) throws SQLException {

        String sql =
            """
            INSERT INTO publication_outbox (
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
                created_at,
                updated_at
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
                'PENDING',
                ?,
                ?,
                ?
            )
            ON CONFLICT ON CONSTRAINT
                uq_publication_outbox_delivery_identity
            DO NOTHING
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                context.publicationId()
            );

            statement.setLong(
                2,
                context.selectionRunId()
            );

            statement.setInt(
                3,
                context.selectionPosition()
            );

            statement.setString(
                4,
                context.channel()
            );

            statement.setString(
                5,
                context.destination()
            );

            statement.setString(
                6,
                context.content()
            );

            statement.setString(
                7,
                context.quotaProfileVersion()
            );

            statement.setObject(
                8,
                context.quotaDate()
            );

            statement.setObject(
                9,
                request.availableAt()
            );

            statement.setObject(
                10,
                request.enqueuedAt()
            );

            statement.setObject(
                11,
                request.enqueuedAt()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private record SelectionContext(
        long publicationId,
        long selectionRunId,
        String publicationStatus,
        String content,
        String channel,
        String destination,
        String quotaProfileVersion,
        LocalDate quotaDate,
        int maxPublicationsPerDay,
        String decisionStatus,
        Integer selectionPosition
    ) {
    }

    private record ActiveQuotaProfile(
        String version,
        int maxPublicationsPerDay,
        ZoneId quotaZone
    ) {
    }
}
