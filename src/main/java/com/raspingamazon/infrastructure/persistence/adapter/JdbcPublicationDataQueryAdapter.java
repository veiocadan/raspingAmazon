package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLoadPort;
import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.application.publication.port.PublicationDataQueryPort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.scoring.ScoreFactorCode;
import com.raspingamazon.domain.scoring.ScoreFactorResult;
import com.raspingamazon.domain.scoring.ScoreFactorStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação JDBC da leitura dos dados que alimentam
 * a geração de uma publicação.
 *
 * <p>Este adapter reconstrói a DealEvaluation persistida,
 * incluindo seus resultados individuais de regras e fatores
 * de score.</p>
 *
 * <p>A reconstrução do OfferSnapshot é deliberadamente
 * delegada a OfferSnapshotEvaluationLoadPort. Assim esta
 * classe não duplica a lógica já existente para:</p>
 *
 * <ul>
 *     <li>Product;</li>
 *     <li>OfferSnapshot;</li>
 *     <li>evidências normalizadas de vendedor e entrega;</li>
 *     <li>PaymentCondition;</li>
 *     <li>PaymentMethod.</li>
 * </ul>
 */
public final class JdbcPublicationDataQueryAdapter
    implements PublicationDataQueryPort {

    private final Connection connection;

    private final OfferSnapshotEvaluationLoadPort
        offerSnapshotLoadPort;

    public JdbcPublicationDataQueryAdapter(
        Connection connection,
        OfferSnapshotEvaluationLoadPort offerSnapshotLoadPort
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.offerSnapshotLoadPort =
            Objects.requireNonNull(
                offerSnapshotLoadPort,
                "offerSnapshotLoadPort must not be null"
            );
    }

    @Override
    public Optional<PublicationData> findByDealEvaluationId(
        long dealEvaluationId
    ) {

        if (dealEvaluationId <= 0L) {
            throw new IllegalArgumentException(
                "dealEvaluationId must be positive"
            );
        }

        try {

            Optional<EvaluationRow> evaluationRow =
                findEvaluationRow(
                    dealEvaluationId
                );

            if (evaluationRow.isEmpty()) {
                return Optional.empty();
            }

            EvaluationRow row =
                evaluationRow.get();

            OfferSnapshot offerSnapshot =
                offerSnapshotLoadPort.findById(
                        row.offerSnapshotId()
                    )
                    .orElseThrow(
                        () -> new IllegalStateException(
                            "OfferSnapshot not found for DealEvaluation "
                                + dealEvaluationId
                                + ": "
                                + row.offerSnapshotId()
                        )
                    );

            List<EvaluationRuleResult> ruleResults =
                loadRuleResults(
                    dealEvaluationId
                );

            List<ScoreFactorResult> scoreFactors =
                loadScoreFactors(
                    dealEvaluationId
                );

            DealEvaluation evaluation =
                new DealEvaluation(
                    row.id(),
                    offerSnapshot,
                    row.eligible(),
                    row.rejectionReason(),
                    row.eligibilityPolicyVersion(),
                    row.filterProfileVersion(),
                    ruleResults,
                    row.score(),
                    row.scoreVersion(),
                    scoreFactors,
                    row.momentum(),
                    row.momentumVersion(),
                    row.evaluatedAt()
                );

            return Optional.of(
                new PublicationData(
                    evaluation
                )
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Could not load publication data for DealEvaluation "
                    + dealEvaluationId,
                exception
            );
        }
    }

    /**
     * Carrega somente os campos agregados da DealEvaluation.
     *
     * <p>OfferSnapshot, regras e fatores possuem ciclos de
     * reconstrução próprios e são carregados separadamente.</p>
     */
    private Optional<EvaluationRow> findEvaluationRow(
        long dealEvaluationId
    ) throws SQLException {

        String sql =
            """
            SELECT
                id,
                offer_snapshot_id,
                eligible,
                rejection_reason,
                eligibility_policy_version,
                filter_profile_version,
                score,
                score_version,
                momentum,
                momentum_version,
                evaluated_at
            FROM deal_evaluation
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                dealEvaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    new EvaluationRow(
                        resultSet.getLong(
                            "id"
                        ),
                        resultSet.getLong(
                            "offer_snapshot_id"
                        ),
                        resultSet.getBoolean(
                            "eligible"
                        ),
                        toRejectionReason(
                            resultSet.getString(
                                "rejection_reason"
                            )
                        ),
                        resultSet.getString(
                            "eligibility_policy_version"
                        ),
                        resultSet.getString(
                            "filter_profile_version"
                        ),
                        resultSet.getBigDecimal(
                            "score"
                        ),
                        resultSet.getString(
                            "score_version"
                        ),
                        resultSet.getBigDecimal(
                            "momentum"
                        ),
                        resultSet.getString(
                            "momentum_version"
                        ),
                        resultSet.getObject(
                            "evaluated_at",
                            OffsetDateTime.class
                        )
                    )
                );
            }
        }
    }

    /**
     * Reconstrói a explicação detalhada das regras exatamente
     * na ordem em que foi persistida.
     */
    private List<EvaluationRuleResult> loadRuleResults(
        long dealEvaluationId
    ) throws SQLException {

        String sql =
            """
            SELECT
                rule_code,
                passed,
                observed_value,
                threshold_value,
                reason_code
            FROM deal_evaluation_rule_result
            WHERE deal_evaluation_id = ?
            ORDER BY rule_order ASC
            """;

        List<EvaluationRuleResult> results =
            new ArrayList<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                dealEvaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    results.add(
                        new EvaluationRuleResult(
                            resultSet.getString(
                                "rule_code"
                            ),
                            resultSet.getBoolean(
                                "passed"
                            ),
                            resultSet.getString(
                                "observed_value"
                            ),
                            resultSet.getString(
                                "threshold_value"
                            ),
                            toRejectionReason(
                                resultSet.getString(
                                    "reason_code"
                                )
                            )
                        )
                    );
                }
            }
        }

        return List.copyOf(
            results
        );
    }

    /**
     * Reconstrói a decomposição auditável do score exatamente
     * na ordem em que foi persistida.
     *
     * <p>Uma avaliação sem score naturalmente retornará uma
     * lista vazia.</p>
     */
    private List<ScoreFactorResult> loadScoreFactors(
        long dealEvaluationId
    ) throws SQLException {

        String sql =
            """
            SELECT
                factor_code,
                status,
                raw_value,
                normalized_value,
                weight,
                contribution
            FROM deal_evaluation_score_factor
            WHERE deal_evaluation_id = ?
            ORDER BY factor_order ASC
            """;

        List<ScoreFactorResult> results =
            new ArrayList<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                dealEvaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    results.add(
                        new ScoreFactorResult(
                            ScoreFactorCode.valueOf(
                                resultSet.getString(
                                    "factor_code"
                                )
                            ),
                            ScoreFactorStatus.valueOf(
                                resultSet.getString(
                                    "status"
                                )
                            ),
                            resultSet.getBigDecimal(
                                "raw_value"
                            ),
                            resultSet.getBigDecimal(
                                "normalized_value"
                            ),
                            resultSet.getBigDecimal(
                                "weight"
                            ),
                            resultSet.getBigDecimal(
                                "contribution"
                            )
                        )
                    );
                }
            }
        }

        return List.copyOf(
            results
        );
    }

    private RejectionReason toRejectionReason(
        String value
    ) {

        if (value == null) {
            return null;
        }

        return RejectionReason.valueOf(
            value
        );
    }

    private record EvaluationRow(
        long id,
        long offerSnapshotId,
        boolean eligible,
        RejectionReason rejectionReason,
        String eligibilityPolicyVersion,
        String filterProfileVersion,
        BigDecimal score,
        String scoreVersion,
        BigDecimal momentum,
        String momentumVersion,
        OffsetDateTime evaluatedAt
    ) {
    }
}
