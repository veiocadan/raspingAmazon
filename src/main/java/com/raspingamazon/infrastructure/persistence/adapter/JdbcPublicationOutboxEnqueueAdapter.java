package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Implementação PostgreSQL do enqueue da outbox.
 *
 * <p>A criação da linha e a reserva da quota são a mesma
 * operação transacional.</p>
 *
 * <p>No fluxo gerenciado por cadência, a mesma transação também
 * revalida o slot de availableAt.</p>
 *
 * <p>Fluxo:</p>
 *
 * <pre>
 * carregar Publication + SelectionRun + SelectionDecision
 *                  |
 *                  v
 * verificar idempotência
 *                  |
 *                  v
 * bloquear profile ativo de quota FOR UPDATE
 *                  |
 *                  v
 * verificar novamente idempotência
 *                  |
 *                  v
 * validar READY + SELECTED
 *                  |
 *                  v
 * revalidar quota/date
 *                  |
 *                  v
 * se cadence-managed:
 *     carregar profile ativo de cadência FOR UPDATE
 *     validar versão
 *     validar janela/grade
 *     validar distância do último availableAt
 *                  |
 *                  v
 * contar quota ocupada
 *                  |
 *                  v
 * quota disponível?
 *      | não
 *      +----> QUOTA_EXHAUSTED
 *      |
 *      | sim
 *      v
 * INSERT publication_outbox
 * </pre>
 *
 * <p>O bloqueio do profile ativo de quota serializa as reservas
 * do mesmo channel + destination.</p>
 *
 * <p>Isso significa que duas execuções concorrentes podem até
 * calcular o mesmo slot antes de entrar nesta transação, mas apenas
 * a primeira poderá confirmá-lo. A segunda observará o novo
 * availableAt persistido e retornará STALE_SELECTION.</p>
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
             * Uma Publication já reservada para este canal/destino
             * não precisa recalcular quota ou cadência.
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

            /*
             * Este lock continua sendo a autoridade de serialização
             * das reservas do mesmo escopo operacional.
             */
            ActiveQuotaProfile activeQuotaProfile =
                lockActiveQuotaProfile(
                    context.channel(),
                    context.destination()
                );

            /*
             * Outra transação pode ter inserido a identidade lógica
             * enquanto esta execução aguardava o lock.
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

            /*
             * Primeiro revalidamos a fotografia de quota da
             * SelectionRun.
             */
            if (selectionIsStale(
                context,
                activeQuotaProfile,
                request
            )) {

                return PublicationOutboxEnqueueResult
                    .staleSelectionResult();
            }

            /*
             * Caminhos anteriores à introdução da cadência continuam
             * possíveis para compatibilidade dos registros/testes
             * existentes.
             *
             * O novo fluxo automático sempre será cadence-managed.
             */
            if (request.cadenceManaged()) {

                ActiveCadenceProfile activeCadenceProfile =
                    lockActiveCadenceProfile(
                        context.channel(),
                        context.destination()
                    );

                if (cadenceReservationIsStale(
                    context,
                    activeQuotaProfile,
                    activeCadenceProfile,
                    request
                )) {

                    return PublicationOutboxEnqueueResult
                        .staleSelectionResult();
                }
            }

            long occupiedSlots =
                countOccupiedSlots(
                    context.channel(),
                    context.destination(),
                    context.quotaDate()
                );

            if (occupiedSlots
                >= activeQuotaProfile.maxPublicationsPerDay()) {

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
             * A constraint de identidade de entrega permanece como
             * autoridade final de idempotência.
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

                final ZoneId quotaZone;

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

    private ActiveCadenceProfile lockActiveCadenceProfile(
        String channel,
        String destination
    ) throws SQLException {

        String sql =
            """
            SELECT
                version,
                interval_seconds,
                window_start,
                window_end,
                cadence_zone
            FROM publication_cadence_profile
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
                        "No active publication cadence profile "
                            + "was found for channel "
                            + channel
                            + " and destination "
                            + destination
                    );
                }

                String cadenceZoneValue =
                    resultSet.getString(
                        "cadence_zone"
                    );

                final ZoneId cadenceZone;

                try {

                    cadenceZone =
                        ZoneId.of(
                            cadenceZoneValue
                        );

                } catch (DateTimeException exception) {

                    throw new IllegalStateException(
                        "Persisted publication cadence profile "
                            + "contains invalid cadence zone: "
                            + cadenceZoneValue,
                        exception
                    );
                }

                long intervalSeconds =
                    resultSet.getLong(
                        "interval_seconds"
                    );

                LocalTime windowStart =
                    resultSet.getObject(
                        "window_start",
                        LocalTime.class
                    );

                LocalTime windowEnd =
                    resultSet.getObject(
                        "window_end",
                        LocalTime.class
                    );

                if (intervalSeconds <= 0L) {

                    throw new IllegalStateException(
                        "Persisted publication cadence interval "
                            + "must be positive"
                    );
                }

                if (windowStart == null
                    || windowEnd == null
                    || !windowEnd.isAfter(
                    windowStart
                )) {

                    throw new IllegalStateException(
                        "Persisted publication cadence window "
                            + "must remain inside one operational day"
                    );
                }

                ActiveCadenceProfile profile =
                    new ActiveCadenceProfile(
                        resultSet.getString(
                            "version"
                        ),
                        intervalSeconds,
                        windowStart,
                        windowEnd,
                        cadenceZone
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one active publication cadence "
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

    private boolean cadenceReservationIsStale(
        SelectionContext context,
        ActiveQuotaProfile activeQuotaProfile,
        ActiveCadenceProfile activeCadenceProfile,
        PublicationOutboxEnqueueRequest request
    ) throws SQLException {

        /*
         * O chamador precisa ter planejado usando exatamente a
         * configuração ainda ativa.
         */
        if (!request.cadenceProfileVersion()
            .equals(
                activeCadenceProfile.version()
            )) {

            return true;
        }

        /*
         * Quota e cadência foram modeladas como uma única política
         * operacional e precisam compartilhar a mesma zona.
         */
        if (!activeQuotaProfile.quotaZone()
            .equals(
                activeCadenceProfile.cadenceZone()
            )) {

            throw new IllegalStateException(
                "Active publication quota and cadence profiles "
                    + "use different operational zones"
            );
        }

        Instant availableInstant =
            request.availableAt()
                .toInstant();

        /*
         * Uma reserva gerenciada por cadência nunca deve nascer
         * retroativamente.
         */
        if (availableInstant.isBefore(
            request.enqueuedAt()
                .toInstant()
        )) {

            return true;
        }

        LocalDate availableDate =
            availableInstant
                .atZone(
                    activeCadenceProfile.cadenceZone()
                )
                .toLocalDate();

        if (!context.quotaDate()
            .equals(
                availableDate
            )) {

            return true;
        }

        LocalTime availableLocalTime =
            availableInstant
                .atZone(
                    activeCadenceProfile.cadenceZone()
                )
                .toLocalTime();

        if (availableLocalTime.isBefore(
            activeCadenceProfile.windowStart()
        )
            || availableLocalTime.isAfter(
            activeCadenceProfile.windowEnd()
        )) {

            return true;
        }

        /*
         * Também validamos que o horário está na grade da política.
         *
         * Exemplo:
         *
         * start = 08:00
         * interval = 2h
         *
         * válidos:
         *
         * 08:00
         * 10:00
         * 12:00
         *
         * inválido:
         *
         * 11:00
         */
        Instant windowStartInstant =
            context.quotaDate()
                .atTime(
                    activeCadenceProfile.windowStart()
                )
                .atZone(
                    activeCadenceProfile.cadenceZone()
                )
                .toInstant();

        Duration elapsed =
            Duration.between(
                windowStartInstant,
                availableInstant
            );

        if (elapsed.isNegative()
            || elapsed.getNano() != 0) {

            return true;
        }

        if (elapsed.getSeconds()
            % activeCadenceProfile.intervalSeconds()
            != 0L) {

            return true;
        }

        Optional<OffsetDateTime> lastReserved =
            findLastReservedAvailableAt(
                context.channel(),
                context.destination(),
                context.quotaDate()
            );

        if (lastReserved.isEmpty()) {

            return false;
        }

        Instant earliestAllowed =
            lastReserved.orElseThrow()
                .toInstant()
                .plusSeconds(
                    activeCadenceProfile.intervalSeconds()
                );

        /*
         * Esta é a proteção concorrente principal.
         *
         * Se outra execução reservou um slot enquanto este request
         * aguardava o lock de quota, o horário previamente calculado
         * deixa de ser válido.
         */
        return availableInstant.isBefore(
            earliestAllowed
        );
    }

    private Optional<OffsetDateTime> findLastReservedAvailableAt(
        String channel,
        String destination,
        LocalDate quotaDate
    ) throws SQLException {

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
                        "Publication cadence reservation query "
                            + "returned no aggregate row"
                    );
                }

                OffsetDateTime value =
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
                    value
                );
            }
        }
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
                cadence_profile_version,
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

            if (request.cadenceProfileVersion() == null) {

                statement.setNull(
                    8,
                    Types.VARCHAR
                );

            } else {

                statement.setString(
                    8,
                    request.cadenceProfileVersion()
                );
            }

            statement.setObject(
                9,
                context.quotaDate()
            );

            statement.setObject(
                10,
                request.availableAt()
            );

            statement.setObject(
                11,
                request.enqueuedAt()
            );

            statement.setObject(
                12,
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

    private record ActiveCadenceProfile(
        String version,
        long intervalSeconds,
        LocalTime windowStart,
        LocalTime windowEnd,
        ZoneId cadenceZone
    ) {
    }
}
