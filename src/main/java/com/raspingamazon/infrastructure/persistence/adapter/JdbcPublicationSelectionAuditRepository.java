package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.selection.port.PublicationSelectionAuditRepository;
import com.raspingamazon.domain.publication.selection.PublicationQuotaSnapshot;
import com.raspingamazon.domain.publication.selection.PublicationSelectionCandidate;
import com.raspingamazon.domain.publication.selection.PublicationSelectionDecision;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionResult;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implementação JDBC da auditoria de seleção operacional.
 *
 * <p>O adapter persiste exatamente o resultado já produzido
 * pelo domínio. Ele não recalcula:</p>
 *
 * <ul>
 *     <li>score;</li>
 *     <li>histórico de publicação;</li>
 *     <li>recência;</li>
 *     <li>posição;</li>
 *     <li>status da decisão.</li>
 * </ul>
 *
 * <p>Antes da persistência, o adapter valida somente vínculos
 * estruturais:</p>
 *
 * <ul>
 *     <li>perfil temporal persistido;</li>
 *     <li>perfil de quota persistido;</li>
 *     <li>data operacional da quota;</li>
 *     <li>DealEvaluation, ASIN e score auditados.</li>
 * </ul>
 *
 * <p>A run e todas as decisões são gravadas dentro da mesma
 * fronteira transacional.</p>
 */
public final class JdbcPublicationSelectionAuditRepository
    implements PublicationSelectionAuditRepository {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationSelectionAuditRepository(
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
    public long save(
        PublicationSelectionResult result
    ) {

        Objects.requireNonNull(
            result,
            "result must not be null"
        );

        return transactionAdapter.execute(
            () ->
                persist(
                    result
                )
        );
    }

    private long persist(
        PublicationSelectionResult result
    ) {

        try {

            validatePersistedProfiles(
                result
            );

            validateEvaluationLinks(
                result.decisions()
            );

            long selectionRunId =
                insertSelectionRun(
                    result
                );

            insertDecisions(
                selectionRunId,
                result.decisions()
            );

            return selectionRunId;

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to persist publication selection audit",
                exception
            );
        }
    }

    private void validatePersistedProfiles(
        PublicationSelectionResult result
    ) throws SQLException {

        validateSelectionProfile(
            result
        );

        validateQuotaProfile(
            result
        );
    }

    private void validateSelectionProfile(
        PublicationSelectionResult result
    ) throws SQLException {

        PublicationSelectionProfile profile =
            result.selectionProfile();

        PublicationQuotaSnapshot quota =
            result.quotaSnapshot();

        String sql =
            """
            SELECT
                hard_cooldown_seconds,
                preferred_cooldown_seconds
            FROM publication_selection_profile
            WHERE channel = ?
              AND destination = ?
              AND version = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                quota.channel()
            );

            statement.setString(
                2,
                quota.destination()
            );

            statement.setString(
                3,
                profile.version()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalArgumentException(
                        "Publication selection profile not found "
                            + "for audit scope and version"
                    );
                }

                long persistedHardCooldownSeconds =
                    resultSet.getLong(
                        "hard_cooldown_seconds"
                    );

                long persistedPreferredCooldownSeconds =
                    resultSet.getLong(
                        "preferred_cooldown_seconds"
                    );

                if (persistedHardCooldownSeconds
                    != profile.hardCooldown()
                    .getSeconds()) {

                    throw new IllegalArgumentException(
                        "Publication selection hard cooldown "
                            + "does not match persisted profile"
                    );
                }

                if (persistedPreferredCooldownSeconds
                    != profile.preferredCooldown()
                    .getSeconds()) {

                    throw new IllegalArgumentException(
                        "Publication selection preferred cooldown "
                            + "does not match persisted profile"
                    );
                }

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one publication selection profile "
                            + "matched the audit scope and version"
                    );
                }
            }
        }
    }

    private void validateQuotaProfile(
        PublicationSelectionResult result
    ) throws SQLException {

        PublicationQuotaSnapshot quota =
            result.quotaSnapshot();

        String sql =
            """
            SELECT
                max_publications_per_day,
                quota_zone
            FROM publication_quota_profile
            WHERE channel = ?
              AND destination = ?
              AND version = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                quota.channel()
            );

            statement.setString(
                2,
                quota.destination()
            );

            statement.setString(
                3,
                quota.quotaProfileVersion()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalArgumentException(
                        "Publication quota profile not found "
                            + "for audit scope and version"
                    );
                }

                int persistedMaximum =
                    resultSet.getInt(
                        "max_publications_per_day"
                    );

                String persistedQuotaZone =
                    resultSet.getString(
                        "quota_zone"
                    );

                if (persistedMaximum
                    != quota.maxPublicationsPerDay()) {

                    throw new IllegalArgumentException(
                        "Publication quota maximum does not match "
                            + "persisted profile"
                    );
                }

                ZoneId quotaZone;

                try {

                    quotaZone =
                        ZoneId.of(
                            persistedQuotaZone
                        );

                } catch (DateTimeException exception) {

                    throw new IllegalStateException(
                        "Persisted publication quota profile "
                            + "contains invalid quota zone: "
                            + persistedQuotaZone,
                        exception
                    );
                }

                if (!result.decidedAt()
                    .atZone(
                        quotaZone
                    )
                    .toLocalDate()
                    .equals(
                        quota.quotaDate()
                    )) {

                    throw new IllegalArgumentException(
                        "Publication quota date does not match "
                            + "decidedAt and persisted quota zone"
                    );
                }

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one publication quota profile "
                            + "matched the audit scope and version"
                    );
                }
            }
        }
    }

    private void validateEvaluationLinks(
        List<PublicationSelectionDecision> decisions
    ) throws SQLException {

        if (decisions.isEmpty()) {
            return;
        }

        Long[] evaluationIds =
            decisions.stream()
                .map(
                    decision ->
                        decision.candidate()
                            .dealEvaluationId()
                )
                .toArray(
                    Long[]::new
                );

        String sql =
            """
            SELECT
                evaluation.id AS deal_evaluation_id,
                product.asin AS asin,
                evaluation.score AS score
            FROM deal_evaluation AS evaluation
            JOIN offer_snapshot AS snapshot
              ON snapshot.id =
                 evaluation.offer_snapshot_id
            JOIN product
              ON product.id =
                 snapshot.product_id
            WHERE evaluation.id = ANY (?)
            """;

        Map<Long, EvaluationReference> references =
            new HashMap<>();

        Array idArray =
            connection.createArrayOf(
                "bigint",
                evaluationIds
            );

        try {

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         sql
                     )) {

                statement.setArray(
                    1,
                    idArray
                );

                try (ResultSet resultSet =
                         statement.executeQuery()) {

                    while (resultSet.next()) {

                        long evaluationId =
                            resultSet.getLong(
                                "deal_evaluation_id"
                            );

                        references.put(
                            evaluationId,
                            new EvaluationReference(
                                resultSet.getString(
                                    "asin"
                                ),
                                resultSet.getBigDecimal(
                                    "score"
                                )
                            )
                        );
                    }
                }
            }

        } finally {

            idArray.free();
        }

        for (PublicationSelectionDecision decision : decisions) {

            PublicationSelectionCandidate candidate =
                decision.candidate();

            EvaluationReference reference =
                references.get(
                    candidate.dealEvaluationId()
                );

            if (reference == null) {

                throw new IllegalArgumentException(
                    "DealEvaluation not found for publication "
                        + "selection audit: "
                        + candidate.dealEvaluationId()
                );
            }

            if (!candidate.asin()
                .value()
                .equals(
                    reference.asin()
                )) {

                throw new IllegalArgumentException(
                    "Publication selection candidate ASIN "
                        + "does not match DealEvaluation ASIN"
                );
            }

            if (reference.score() == null
                || candidate.score()
                .compareTo(
                    reference.score()
                ) != 0) {

                throw new IllegalArgumentException(
                    "Publication selection candidate score "
                        + "does not match DealEvaluation score"
                );
            }
        }
    }

    private long insertSelectionRun(
        PublicationSelectionResult result
    ) throws SQLException {

        PublicationSelectionProfile profile =
            result.selectionProfile();

        PublicationQuotaSnapshot quota =
            result.quotaSnapshot();

        String sql =
            """
            INSERT INTO publication_selection_run (
                channel,
                destination,
                decided_at,
                selection_profile_version,
                hard_cooldown_seconds,
                preferred_cooldown_seconds,
                quota_profile_version,
                quota_date,
                max_publications_per_day,
                occupied_slots
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
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                quota.channel()
            );

            statement.setString(
                2,
                quota.destination()
            );

            statement.setObject(
                3,
                OffsetDateTime.ofInstant(
                    result.decidedAt(),
                    ZoneOffset.UTC
                )
            );

            statement.setString(
                4,
                profile.version()
            );

            statement.setLong(
                5,
                profile.hardCooldown()
                    .getSeconds()
            );

            statement.setLong(
                6,
                profile.preferredCooldown()
                    .getSeconds()
            );

            statement.setString(
                7,
                quota.quotaProfileVersion()
            );

            statement.setObject(
                8,
                quota.quotaDate()
            );

            statement.setInt(
                9,
                quota.maxPublicationsPerDay()
            );

            statement.setLong(
                10,
                quota.occupiedSlots()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new SQLException(
                        "Publication selection run insert "
                            + "did not return an id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void insertDecisions(
        long selectionRunId,
        List<PublicationSelectionDecision> decisions
    ) throws SQLException {

        if (decisions.isEmpty()) {
            return;
        }

        String sql =
            """
            INSERT INTO publication_selection_decision (
                selection_run_id,
                deal_evaluation_id,
                asin,
                score,
                decision_status,
                recency,
                priority_position,
                last_successful_publication_at,
                successful_publication_count
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            for (PublicationSelectionDecision decision : decisions) {

                PublicationSelectionCandidate candidate =
                    decision.candidate();

                statement.setLong(
                    1,
                    selectionRunId
                );

                statement.setLong(
                    2,
                    candidate.dealEvaluationId()
                );

                statement.setString(
                    3,
                    candidate.asin()
                        .value()
                );

                statement.setBigDecimal(
                    4,
                    candidate.score()
                );

                statement.setString(
                    5,
                    decision.status()
                        .name()
                );

                statement.setString(
                    6,
                    decision.recency()
                        .name()
                );

                if (decision.priorityPosition() == null) {

                    statement.setObject(
                        7,
                        null
                    );

                } else {

                    statement.setInt(
                        7,
                        decision.priorityPosition()
                    );
                }

                if (candidate.neverSuccessfullyPublished()) {

                    statement.setObject(
                        8,
                        null
                    );

                    statement.setLong(
                        9,
                        0L
                    );

                } else {

                    SuccessfulPublicationHistory history =
                        candidate.successfulHistory()
                            .orElseThrow();

                    statement.setObject(
                        8,
                        OffsetDateTime.ofInstant(
                            history.lastSuccessfulPublicationAt(),
                            ZoneOffset.UTC
                        )
                    );

                    statement.setLong(
                        9,
                        history.successfulPublicationCount()
                    );
                }

                statement.addBatch();
            }

            int[] updateCounts =
                statement.executeBatch();

            if (updateCounts.length
                != decisions.size()) {

                throw new SQLException(
                    "Publication selection decision batch "
                        + "returned unexpected result count"
                );
            }

            for (int updateCount : updateCounts) {

                if (updateCount
                    == Statement.EXECUTE_FAILED) {

                    throw new SQLException(
                        "Publication selection decision "
                            + "batch insert failed"
                    );
                }
            }
        }
    }

    private record EvaluationReference(
        String asin,
        BigDecimal score
    ) {
    }
}
