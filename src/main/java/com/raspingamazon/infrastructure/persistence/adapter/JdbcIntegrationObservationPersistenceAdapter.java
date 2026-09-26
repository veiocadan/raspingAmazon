package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.observability.IntegrationObservation;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.observability.port.IntegrationObservationPersistencePort;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * Adapter JDBC responsável pela persistência das observações duráveis
 * de integração.
 *
 * <p>Cada chamada a save() insere exatamente uma observação. Contadores,
 * médias, taxas e alertas permanecem derivados dos fatos persistidos.</p>
 *
 * <p>O adapter utiliza JdbcTransactionAdapter para proteger a conexão:</p>
 *
 * <ul>
 *     <li>
 *         com autoCommit=true, a gravação possui uma transação própria;
 *     </li>
 *     <li>
 *         com autoCommit=false, a gravação utiliza savepoint e não
 *         executa commit da transação pertencente ao chamador;
 *     </li>
 *     <li>
 *         em falha, somente o trabalho da observação é desfeito quando
 *         existe uma transação externa.
 *     </li>
 * </ul>
 *
 * <p>O adapter não implementa política best-effort. Uma falha JDBC é
 * convertida para PersistenceOperationException. A fronteira que utiliza
 * observabilidade decidirá posteriormente se essa falha deve ser isolada
 * do fluxo funcional.</p>
 */
public final class JdbcIntegrationObservationPersistenceAdapter
    implements IntegrationObservationPersistencePort {

    private static final String INSERT_SQL =
        """
        INSERT INTO integration_observation (
            observed_at,
            integration,
            operation,
            outcome,
            duration_ms,
            processing_run_id,
            processing_job_id,
            job_type,
            deal_candidate_id,
            offer_snapshot_id,
            deal_evaluation_id,
            publication_id,
            asin,
            failure_origin,
            failure_type,
            error_code,
            http_status_code
        )
        VALUES (
            ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?,
            ?, ?
        )
        RETURNING id
        """;

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcIntegrationObservationPersistenceAdapter(
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
    public IntegrationObservation save(
        IntegrationObservation observation
    ) {

        Objects.requireNonNull(
            observation,
            "observation must not be null"
        );

        if (observation.id() != null) {

            throw new IllegalArgumentException(
                "New IntegrationObservation must not already have an id"
            );
        }

        return transactionAdapter.execute(
            () -> insert(
                observation
            )
        );
    }

    private IntegrationObservation insert(
        IntegrationObservation observation
    ) {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     INSERT_SQL
                 )) {

            bind(
                statement,
                observation
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Integration observation INSERT "
                            + "did not return a generated id"
                    );
                }

                long generatedId =
                    resultSet.getLong(
                        "id"
                    );

                return copyWithId(
                    observation,
                    generatedId
                );
            }

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                "Failed to persist integration observation for "
                    + observation.integration(),
                exception
            );
        }
    }

    private void bind(
        PreparedStatement statement,
        IntegrationObservation observation
    ) throws SQLException {

        OperationalLogContext context =
            observation.context();

        statement.setObject(
            1,
            observation.observedAt()
        );

        statement.setString(
            2,
            observation.integration()
        );

        statement.setString(
            3,
            observation.operation()
        );

        statement.setString(
            4,
            observation.outcome()
                .name()
        );

        statement.setLong(
            5,
            observation.durationMs()
        );

        setNullableLong(
            statement,
            6,
            context.runId()
        );

        setNullableLong(
            statement,
            7,
            context.jobId()
        );

        setNullableString(
            statement,
            8,
            context.jobType() == null
                ? null
                : context.jobType()
                .name()
        );

        setNullableLong(
            statement,
            9,
            context.candidateId()
        );

        setNullableLong(
            statement,
            10,
            context.snapshotId()
        );

        setNullableLong(
            statement,
            11,
            context.evaluationId()
        );

        setNullableLong(
            statement,
            12,
            context.publicationId()
        );

        setNullableString(
            statement,
            13,
            context.asin()
        );

        setNullableString(
            statement,
            14,
            observation.failureOrigin() == null
                ? null
                : observation.failureOrigin()
                .name()
        );

        setNullableString(
            statement,
            15,
            observation.failureType() == null
                ? null
                : observation.failureType()
                .name()
        );

        setNullableString(
            statement,
            16,
            observation.errorCode()
        );

        setNullableInteger(
            statement,
            17,
            observation.httpStatusCode()
        );
    }

    private IntegrationObservation copyWithId(
        IntegrationObservation observation,
        long id
    ) {

        return new IntegrationObservation(
            id,
            observation.observedAt(),
            observation.integration(),
            observation.operation(),
            observation.outcome(),
            observation.durationMs(),
            observation.context(),
            observation.failureOrigin(),
            observation.failureType(),
            observation.errorCode(),
            observation.httpStatusCode()
        );
    }

    private void setNullableLong(
        PreparedStatement statement,
        int parameterIndex,
        Long value
    ) throws SQLException {

        if (value == null) {

            statement.setNull(
                parameterIndex,
                Types.BIGINT
            );

            return;
        }

        statement.setLong(
            parameterIndex,
            value
        );
    }

    private void setNullableInteger(
        PreparedStatement statement,
        int parameterIndex,
        Integer value
    ) throws SQLException {

        if (value == null) {

            statement.setNull(
                parameterIndex,
                Types.INTEGER
            );

            return;
        }

        statement.setInt(
            parameterIndex,
            value
        );
    }

    private void setNullableString(
        PreparedStatement statement,
        int parameterIndex,
        String value
    ) throws SQLException {

        if (value == null) {

            statement.setNull(
                parameterIndex,
                Types.VARCHAR
            );

            return;
        }

        statement.setString(
            parameterIndex,
            value
        );
    }
}
