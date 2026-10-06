package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitReservation;
import com.raspingamazon.application.publication.ratelimit.port.PublicationRateLimitReservationPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Rate limiter persistente baseado em PostgreSQL.
 *
 * <p>A integração mantém somente o primeiro instante em que uma nova
 * chamada pode ser admitida.</p>
 *
 * <p>Quando o slot está disponível:</p>
 *
 * <pre>
 * SELECT ... FOR UPDATE
 *          ↓
 * requestedAt >= nextAllowedAt
 *          ↓
 * chamada admitida agora
 *          ↓
 * nextAllowedAt = requestedAt + minimumInterval
 *          ↓
 * UPDATE
 * </pre>
 *
 * <p>Quando o slot ainda não está disponível:</p>
 *
 * <pre>
 * SELECT ... FOR UPDATE
 *          ↓
 * requestedAt < nextAllowedAt
 *          ↓
 * devolver nextAllowedAt ao chamador
 *          ↓
 * NÃO atualizar o estado
 * </pre>
 *
 * <p>Portanto um horário futuro não é reservado antecipadamente.</p>
 *
 * <p>A FASE 20-G acrescenta feedback do provider:</p>
 *
 * <pre>
 * HTTP 429 + Retry-After
 *          ↓
 * extendNotBefore(...)
 *          ↓
 * nextAllowedAt =
 *     max(
 *         persistido,
 *         Retry-After,
 *         observedAt
 *     )
 * </pre>
 *
 * <p>Esse piso também não pertence exclusivamente à outbox que
 * recebeu o 429. Ele protege toda a integração física compartilhada
 * por múltiplos workers e instâncias.</p>
 *
 * <p>O bloqueio ocorre somente durante a decisão persistente.
 * Nenhuma chamada HTTP e nenhuma espera temporal ocorrem dentro da
 * transação.</p>
 */
public final class JdbcPublicationRateLimitReservationAdapter
    implements PublicationRateLimitReservationPort {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationRateLimitReservationAdapter(
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
    public PublicationRateLimitReservation reserve(
        String integrationKey,
        Duration minimumInterval,
        OffsetDateTime requestedAt
    ) {

        String validatedIntegrationKey =
            requireText(
                integrationKey,
                "integrationKey"
            );

        Duration validatedMinimumInterval =
            requirePositiveDuration(
                minimumInterval,
                "minimumInterval"
            );

        Objects.requireNonNull(
            requestedAt,
            "requestedAt must not be null"
        );

        return transactionAdapter.execute(
            () ->
                reserveInsideTransaction(
                    validatedIntegrationKey,
                    validatedMinimumInterval,
                    requestedAt
                )
        );
    }

    @Override
    public OffsetDateTime extendNotBefore(
        String integrationKey,
        OffsetDateTime notBefore,
        OffsetDateTime observedAt
    ) {

        String validatedIntegrationKey =
            requireText(
                integrationKey,
                "integrationKey"
            );

        Objects.requireNonNull(
            notBefore,
            "notBefore must not be null"
        );

        Objects.requireNonNull(
            observedAt,
            "observedAt must not be null"
        );

        return transactionAdapter.execute(
            () ->
                extendNotBeforeInsideTransaction(
                    validatedIntegrationKey,
                    notBefore,
                    observedAt
                )
        );
    }

    private PublicationRateLimitReservation reserveInsideTransaction(
        String integrationKey,
        Duration minimumInterval,
        OffsetDateTime requestedAt
    ) {

        try {

            ensureStateExists(
                integrationKey,
                requestedAt
            );

            OffsetDateTime persistedNextAllowedAt =
                lockNextAllowedAt(
                    integrationKey
                );

            /*
             * O provider ainda não pode ser chamado.
             *
             * Não alteramos publication_rate_limit_state.
             *
             * O instante futuro NÃO fica reservado para este
             * chamador. Ele representa somente o momento em que
             * uma nova tentativa de admissão pode acontecer.
             */
            if (persistedNextAllowedAt.isAfter(
                requestedAt
            )) {

                return new PublicationRateLimitReservation(
                    integrationKey,
                    requestedAt,
                    persistedNextAllowedAt,
                    persistedNextAllowedAt
                );
            }

            /*
             * O slot atual está livre.
             *
             * Esta execução o adquire e avança atomicamente o
             * primeiro instante disponível.
             */
            OffsetDateTime allowedAt =
                requestedAt;

            OffsetDateTime nextAllowedAt =
                allowedAt.plus(
                    minimumInterval
                );

            updateNextAllowedAt(
                integrationKey,
                nextAllowedAt,
                requestedAt
            );

            return new PublicationRateLimitReservation(
                integrationKey,
                requestedAt,
                allowedAt,
                nextAllowedAt
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to reserve publication rate-limit slot "
                    + "for integration "
                    + integrationKey,
                exception
            );
        }
    }

    private OffsetDateTime extendNotBeforeInsideTransaction(
        String integrationKey,
        OffsetDateTime notBefore,
        OffsetDateTime observedAt
    ) {

        try {

            /*
             * A linha normalmente já existe porque a admissão
             * preventiva ocorreu antes do provider. Ainda assim,
             * mantemos a operação robusta para callers isolados.
             */
            ensureStateExists(
                integrationKey,
                observedAt
            );

            OffsetDateTime persistedNextAllowedAt =
                lockNextAllowedAt(
                    integrationKey
                );

            OffsetDateTime effectiveFloor =
                maxInstant(
                    notBefore,
                    observedAt
                );

            /*
             * Retry-After nunca reduz proteção já persistida.
             */
            if (!effectiveFloor.isAfter(
                persistedNextAllowedAt
            )) {

                return persistedNextAllowedAt;
            }

            updateNextAllowedAt(
                integrationKey,
                effectiveFloor,
                observedAt
            );

            return effectiveFloor;

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to extend publication rate-limit floor "
                    + "for integration "
                    + integrationKey,
                exception
            );
        }
    }

    /**
     * Garante a existência da linha da integração.
     *
     * <p>Quando dois workers encontram uma integração ainda sem
     * estado, a chave primária e ON CONFLICT garantem a criação
     * idempotente.</p>
     */
    private void ensureStateExists(
        String integrationKey,
        OffsetDateTime requestedAt
    ) throws SQLException {

        String sql =
            """
            INSERT INTO publication_rate_limit_state (
                integration_key,
                next_allowed_at,
                updated_at
            )
            VALUES (?, ?, ?)
            ON CONFLICT (integration_key)
            DO NOTHING
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                integrationKey
            );

            statement.setObject(
                2,
                requestedAt
            );

            statement.setObject(
                3,
                requestedAt
            );

            statement.executeUpdate();
        }
    }

    private OffsetDateTime lockNextAllowedAt(
        String integrationKey
    ) throws SQLException {

        String sql =
            """
            SELECT next_allowed_at
            FROM publication_rate_limit_state
            WHERE integration_key = ?
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                integrationKey
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication rate-limit state disappeared "
                            + "for integration "
                            + integrationKey
                    );
                }

                OffsetDateTime nextAllowedAt =
                    resultSet.getObject(
                        "next_allowed_at",
                        OffsetDateTime.class
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one publication rate-limit state "
                            + "exists for integration "
                            + integrationKey
                    );
                }

                return Objects.requireNonNull(
                    nextAllowedAt,
                    "Persisted nextAllowedAt must not be null"
                );
            }
        }
    }

    private void updateNextAllowedAt(
        String integrationKey,
        OffsetDateTime nextAllowedAt,
        OffsetDateTime updatedAt
    ) throws SQLException {

        String sql =
            """
            UPDATE publication_rate_limit_state
            SET
                next_allowed_at = ?,
                updated_at = ?
            WHERE integration_key = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                nextAllowedAt
            );

            statement.setObject(
                2,
                updatedAt
            );

            statement.setString(
                3,
                integrationKey
            );

            int updated =
                statement.executeUpdate();

            if (updated != 1) {

                throw new IllegalStateException(
                    "Publication rate-limit update affected "
                        + updated
                        + " rows for integration "
                        + integrationKey
                );
            }
        }
    }

    private static OffsetDateTime maxInstant(
        OffsetDateTime left,
        OffsetDateTime right
    ) {

        if (left.toInstant()
            .isAfter(
                right.toInstant()
            )) {

            return left;
        }

        return right;
    }

    private static Duration requirePositiveDuration(
        Duration value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                fieldName + " must be positive"
            );
        }

        return value;
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
