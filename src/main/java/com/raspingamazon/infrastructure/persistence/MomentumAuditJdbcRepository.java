package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.momentum.MomentumAuditRepository;
import com.raspingamazon.domain.momentum.MomentumAudit;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC da persistência auditável de momentum.
 *
 * <p>O repository persiste exatamente os fatos já produzidos pelo
 * domínio. Ele não recalcula evolução nem momentum.</p>
 *
 * <p>Antes do INSERT, também valida que o snapshot atual informado
 * pela auditoria é realmente o snapshot associado à DealEvaluation.</p>
 */
public final class MomentumAuditJdbcRepository
    implements MomentumAuditRepository {

    private final Connection connection;

    public MomentumAuditJdbcRepository(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public MomentumAudit save(
        MomentumAudit audit
    ) {

        Objects.requireNonNull(
            audit,
            "audit must not be null"
        );

        if (audit.id() != null) {
            throw new IllegalArgumentException(
                "Only new MomentumAudit instances can be persisted"
            );
        }

        try {

            validateEvaluationSnapshotLink(
                audit
            );

            long auditId =
                insertAudit(
                    audit
                );

            return audit.withId(
                auditId
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to persist MomentumAudit",
                exception
            );
        }
    }

    /**
     * Garante que o snapshot atual representado pela auditoria
     * corresponde exatamente ao snapshot da DealEvaluation.
     *
     * <p>A tabela de auditoria não armazena current_offer_snapshot_id
     * porque essa informação já existe em deal_evaluation.</p>
     *
     * <p>Esta validação evita que uma comparação histórica de um
     * snapshot seja ligada por engano à avaliação de outro snapshot.</p>
     */
    private void validateEvaluationSnapshotLink(
        MomentumAudit audit
    ) throws SQLException {

        String sql = """
            SELECT offer_snapshot_id
            FROM deal_evaluation
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                audit.dealEvaluationId()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalArgumentException(
                        "DealEvaluation not found for MomentumAudit: "
                            + audit.dealEvaluationId()
                    );
                }

                long persistedSnapshotId =
                    resultSet.getLong(
                        "offer_snapshot_id"
                    );

                if (persistedSnapshotId
                    != audit.currentOfferSnapshotId()) {

                    throw new IllegalArgumentException(
                        "MomentumAudit current snapshot does not match DealEvaluation snapshot"
                    );
                }
            }
        }
    }

    private long insertAudit(
        MomentumAudit audit
    ) throws SQLException {

        String sql = """
            INSERT INTO deal_evaluation_momentum_audit (
                deal_evaluation_id,
                calculation_version,
                status,
                unavailable_reason,
                previous_offer_snapshot_id,
                elapsed_seconds,
                sold_percentage_delta,
                current_price_delta,
                current_price_delta_percentage,
                cash_discount_delta,
                momentum
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                audit.dealEvaluationId()
            );

            statement.setString(
                2,
                audit.calculationVersion()
            );

            statement.setString(
                3,
                audit.isAvailable()
                    ? "AVAILABLE"
                    : "UNAVAILABLE"
            );

            if (audit.unavailableReason() == null) {

                statement.setObject(
                    4,
                    null
                );

            } else {

                statement.setString(
                    4,
                    audit.unavailableReason()
                        .name()
                );
            }

            setNullableLong(
                statement,
                5,
                audit.previousOfferSnapshotId()
            );

            setNullableLong(
                statement,
                6,
                audit.elapsedSeconds()
            );

            setNullableBigDecimal(
                statement,
                7,
                audit.soldPercentageDelta()
            );

            setNullableBigDecimal(
                statement,
                8,
                audit.currentPriceDelta()
            );

            setNullableBigDecimal(
                statement,
                9,
                audit.currentPriceDeltaPercentage()
            );

            setNullableBigDecimal(
                statement,
                10,
                audit.cashDiscountDelta()
            );

            setNullableBigDecimal(
                statement,
                11,
                audit.momentum()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new SQLException(
                        "MomentumAudit insert did not return an id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private static void setNullableLong(
        PreparedStatement statement,
        int index,
        Long value
    ) throws SQLException {

        if (value == null) {

            statement.setObject(
                index,
                null
            );

        } else {

            statement.setLong(
                index,
                value
            );
        }
    }

    private static void setNullableBigDecimal(
        PreparedStatement statement,
        int index,
        BigDecimal value
    ) throws SQLException {

        if (value == null) {

            statement.setObject(
                index,
                null
            );

        } else {

            statement.setBigDecimal(
                index,
                value
            );
        }
    }
}
