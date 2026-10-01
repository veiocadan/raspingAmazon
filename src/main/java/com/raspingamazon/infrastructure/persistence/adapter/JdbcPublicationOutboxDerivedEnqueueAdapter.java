package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.outbox.PublicationOutboxDerivedEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxDerivedEnqueuePort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Implementação PostgreSQL do enqueue de entregas derivadas.
 *
 * <p>Uma entrega derivada não executa seleção comercial e não
 * reserva uma segunda quota. Ela reutiliza uma entrada primária
 * já persistida na publication_outbox.</p>
 *
 * <p>Do source são copiados:</p>
 *
 * <ul>
 *     <li>publication_id;</li>
 *     <li>selection_run_id;</li>
 *     <li>selection_position;</li>
 *     <li>content;</li>
 *     <li>available_at.</li>
 * </ul>
 *
 * <p>O chamador pode definir somente channel e destination da
 * nova entrega.</p>
 *
 * <p>A entrada source precisa representar uma reserva real de
 * quota. Dessa forma o adapter não permite criar cadeias de
 * "derivado de derivado".</p>
 */
public final class JdbcPublicationOutboxDerivedEnqueueAdapter
    implements PublicationOutboxDerivedEnqueuePort {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationOutboxDerivedEnqueueAdapter(
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
        PublicationOutboxDerivedEnqueueRequest request
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
        PublicationOutboxDerivedEnqueueRequest request
    ) {

        try {

            SourceOutbox source =
                loadSourceOutbox(
                    request.sourceOutboxId()
                );

            validateSourceIsPrimary(
                source
            );

            validateDifferentDeliveryIdentity(
                source,
                request
            );

            /*
             * Fast path idempotente.
             *
             * Uma repetição do fan-out não cria uma segunda
             * entrega lógica.
             */
            OptionalLong existingBeforeInsert =
                findExistingOutboxId(
                    source.publicationId(),
                    request.channel(),
                    request.destination()
                );

            if (existingBeforeInsert.isPresent()) {

                return PublicationOutboxEnqueueResult
                    .alreadyEnqueued(
                        existingBeforeInsert.getAsLong()
                    );
            }

            /*
             * Uma nova entrega derivada deve ser criada enquanto
             * a entrega principal ainda aguarda processamento.
             *
             * Uma chamada idempotente posterior continua permitida,
             * pois o fast path acima retorna a linha derivada já
             * existente independentemente do estado atual do source.
             */
            validateSourcePendingForNewDerivedDelivery(
                source
            );

            Long insertedId =
                insertDerivedOutbox(
                    source,
                    request
                );

            if (insertedId != null) {

                return PublicationOutboxEnqueueResult
                    .enqueued(
                        insertedId
                    );
            }

            /*
             * Autoridade final de idempotência:
             *
             * publication + channel + destination.
             *
             * ON CONFLICT pode ter perdido uma corrida concorrente;
             * nesse caso consultamos novamente a identidade.
             */
            OptionalLong existingAfterInsert =
                findExistingOutboxId(
                    source.publicationId(),
                    request.channel(),
                    request.destination()
                );

            if (existingAfterInsert.isPresent()) {

                return PublicationOutboxEnqueueResult
                    .alreadyEnqueued(
                        existingAfterInsert.getAsLong()
                    );
            }

            throw new IllegalStateException(
                "Derived publication outbox insert returned no row "
                    + "and no existing delivery identity was found"
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to enqueue derived publication outbox",
                exception
            );
        }
    }

    /**
     * Carrega a origem dentro da mesma transação.
     *
     * <p>FOR SHARE impede alterações incompatíveis na linha enquanto
     * sua identidade está sendo utilizada para criar o derivado.</p>
     */
    private SourceOutbox loadSourceOutbox(
        long sourceOutboxId
    ) throws SQLException {

        String sql =
            """
            SELECT
                publication_id,
                selection_run_id,
                selection_position,
                channel,
                destination,
                content,
                quota_profile_version,
                quota_date,
                status,
                available_at
            FROM publication_outbox
            WHERE id = ?
            FOR SHARE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                sourceOutboxId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalArgumentException(
                        "Source publication outbox "
                            + sourceOutboxId
                            + " was not found"
                    );
                }

                SourceOutbox source =
                    new SourceOutbox(
                        sourceOutboxId,
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
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "available_at",
                            OffsetDateTime.class
                        )
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one source publication outbox "
                            + "was found for id "
                            + sourceOutboxId
                    );
                }

                return source;
            }
        }
    }

    private void validateSourceIsPrimary(
        SourceOutbox source
    ) {

        if (source.quotaProfileVersion() == null
            || source.quotaDate() == null) {

            throw new IllegalStateException(
                "Source publication outbox "
                    + source.id()
                    + " must reserve quota before creating "
                    + "a derived delivery"
            );
        }
    }

    private void validateDifferentDeliveryIdentity(
        SourceOutbox source,
        PublicationOutboxDerivedEnqueueRequest request
    ) {

        if (source.channel()
            .equals(
                request.channel()
            )
            && source.destination()
            .equals(
                request.destination()
            )) {

            throw new IllegalArgumentException(
                "Derived delivery must use a different "
                    + "channel or destination from source"
            );
        }
    }

    private void validateSourcePendingForNewDerivedDelivery(
        SourceOutbox source
    ) {

        if (!"PENDING".equals(
            source.status()
        )) {

            throw new IllegalStateException(
                "Source publication outbox "
                    + source.id()
                    + " must be PENDING when creating "
                    + "a new derived delivery"
            );
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

                long id =
                    resultSet.getLong(
                        "id"
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one publication outbox "
                            + "was found for delivery identity"
                    );
                }

                return OptionalLong.of(
                    id
                );
            }
        }
    }

    private Long insertDerivedOutbox(
        SourceOutbox source,
        PublicationOutboxDerivedEnqueueRequest request
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
                NULL,
                NULL,
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
                source.publicationId()
            );

            statement.setLong(
                2,
                source.selectionRunId()
            );

            statement.setInt(
                3,
                source.selectionPosition()
            );

            statement.setString(
                4,
                request.channel()
            );

            statement.setString(
                5,
                request.destination()
            );

            statement.setString(
                6,
                source.content()
            );

            statement.setObject(
                7,
                source.availableAt()
            );

            statement.setObject(
                8,
                request.enqueuedAt()
            );

            statement.setObject(
                9,
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

    private record SourceOutbox(
        long id,
        long publicationId,
        long selectionRunId,
        int selectionPosition,
        String channel,
        String destination,
        String content,
        String quotaProfileVersion,
        LocalDate quotaDate,
        String status,
        OffsetDateTime availableAt
    ) {
    }
}
