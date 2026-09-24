package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.evaluation.DealEvaluationDetail;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSummary;
import com.raspingamazon.application.operation.evaluation.OperationalEvaluationRuleResult;
import com.raspingamazon.application.operation.evaluation.OperationalMomentumAudit;
import com.raspingamazon.application.operation.evaluation.OperationalScoreFactorResult;
import com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalDetailQueryPort;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

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
 * Consulta JDBC do detalhe auditável de uma DealEvaluation.
 *
 * <p>A leitura é dividida em consultas específicas para evitar o
 * produto cartesiano que ocorreria ao juntar simultaneamente regras
 * e fatores de score.</p>
 */
public final class JdbcDealEvaluationOperationalDetailQueryAdapter
    implements DealEvaluationOperationalDetailQueryPort {

    private final Connection connection;

    public JdbcDealEvaluationOperationalDetailQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Optional<DealEvaluationDetail> findById(
        long evaluationId
    ) {

        if (evaluationId <= 0L) {
            throw new IllegalArgumentException(
                "evaluationId must be positive"
            );
        }

        try {

            Optional<BaseDetail> baseDetail =
                findBaseDetail(
                    evaluationId
                );

            if (baseDetail.isEmpty()) {
                return Optional.empty();
            }

            BaseDetail base =
                baseDetail.orElseThrow();

            List<OperationalEvaluationRuleResult> ruleResults =
                findRuleResults(
                    evaluationId
                );

            List<OperationalScoreFactorResult> scoreFactors =
                findScoreFactors(
                    evaluationId
                );

            OperationalMomentumAudit momentumAudit =
                findMomentumAudit(
                    evaluationId
                ).orElse(
                    null
                );

            return Optional.of(
                new DealEvaluationDetail(
                    base.summary(),
                    base.productUrl(),
                    base.basisPrice(),
                    base.previousPrice(),
                    base.soldPercentage(),
                    base.rating(),
                    base.reviewCount(),
                    base.sellerName(),
                    base.deliveryProvider(),
                    base.source(),
                    base.eligibilityPolicyVersion(),
                    base.filterProfileVersion(),
                    base.scoreVersion(),
                    ruleResults,
                    scoreFactors,
                    base.momentumVersion(),
                    momentumAudit
                )
            );

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                "Could not load operational detail "
                    + "for DealEvaluation "
                    + evaluationId,
                exception
            );
        }
    }

    private Optional<BaseDetail> findBaseDetail(
        long evaluationId
    ) throws SQLException {

        String sql =
            """
            SELECT
                de.id AS evaluation_id,
                os.id AS offer_snapshot_id,
                p.id AS product_id,
                p.asin,
                p.title,
                p.product_url,
                os.current_price,
                os.basis_price,
                os.previous_price,
                os.sold_percentage,
                os.rating,
                os.review_count,
                os.seller_name,
                os.delivery_provider,
                os.source,
                os.collected_at,
                de.eligible,
                de.rejection_reason,
                de.eligibility_policy_version,
                de.filter_profile_version,
                de.score,
                de.score_version,
                de.momentum,
                de.momentum_version,
                de.evaluated_at
            FROM deal_evaluation de
            INNER JOIN offer_snapshot os
                ON os.id = de.offer_snapshot_id
            INNER JOIN product p
                ON p.id = os.product_id
            WHERE de.id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                BaseDetail detail =
                    readBaseDetail(
                        resultSet
                    );

                if (resultSet.next()) {
                    throw new IllegalStateException(
                        "More than one base row found "
                            + "for DealEvaluation "
                            + evaluationId
                    );
                }

                return Optional.of(
                    detail
                );
            }
        }
    }

    private BaseDetail readBaseDetail(
        ResultSet resultSet
    ) throws SQLException {

        DealEvaluationSummary summary =
            new DealEvaluationSummary(
                resultSet.getLong(
                    "evaluation_id"
                ),
                resultSet.getLong(
                    "offer_snapshot_id"
                ),
                resultSet.getLong(
                    "product_id"
                ),
                new Asin(
                    resultSet.getString(
                        "asin"
                    )
                ),
                resultSet.getString(
                    "title"
                ),
                new Money(
                    resultSet.getBigDecimal(
                        "current_price"
                    )
                ),
                resultSet.getBoolean(
                    "eligible"
                ),
                readRejectionReason(
                    resultSet
                ),
                resultSet.getBigDecimal(
                    "score"
                ),
                resultSet.getBigDecimal(
                    "momentum"
                ),
                resultSet.getObject(
                    "collected_at",
                    OffsetDateTime.class
                ),
                resultSet.getObject(
                    "evaluated_at",
                    OffsetDateTime.class
                )
            );

        return new BaseDetail(
            summary,
            resultSet.getString(
                "product_url"
            ),
            readNullableMoney(
                resultSet,
                "basis_price"
            ),
            readNullableMoney(
                resultSet,
                "previous_price"
            ),
            resultSet.getBigDecimal(
                "sold_percentage"
            ),
            readNullableDouble(
                resultSet,
                "rating"
            ),
            readNullableLong(
                resultSet,
                "review_count"
            ),
            resultSet.getString(
                "seller_name"
            ),
            resultSet.getString(
                "delivery_provider"
            ),
            resultSet.getString(
                "source"
            ),
            resultSet.getString(
                "eligibility_policy_version"
            ),
            resultSet.getString(
                "filter_profile_version"
            ),
            resultSet.getString(
                "score_version"
            ),
            resultSet.getString(
                "momentum_version"
            )
        );
    }

    private List<OperationalEvaluationRuleResult> findRuleResults(
        long evaluationId
    ) throws SQLException {

        String sql =
            """
            SELECT
                rule_order,
                rule_code,
                passed,
                observed_value,
                threshold_value,
                reason_code
            FROM deal_evaluation_rule_result
            WHERE deal_evaluation_id = ?
            ORDER BY rule_order ASC
            """;

        List<OperationalEvaluationRuleResult> results =
            new ArrayList<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    results.add(
                        new OperationalEvaluationRuleResult(
                            resultSet.getInt(
                                "rule_order"
                            ),
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
                            resultSet.getString(
                                "reason_code"
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

    private List<OperationalScoreFactorResult> findScoreFactors(
        long evaluationId
    ) throws SQLException {

        String sql =
            """
            SELECT
                factor_order,
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

        List<OperationalScoreFactorResult> factors =
            new ArrayList<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    factors.add(
                        new OperationalScoreFactorResult(
                            resultSet.getInt(
                                "factor_order"
                            ),
                            resultSet.getString(
                                "factor_code"
                            ),
                            resultSet.getString(
                                "status"
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
            factors
        );
    }

    private Optional<OperationalMomentumAudit> findMomentumAudit(
        long evaluationId
    ) throws SQLException {

        String sql =
            """
            SELECT
                id,
                calculation_version,
                status,
                unavailable_reason,
                previous_offer_snapshot_id,
                elapsed_seconds,
                sold_percentage_delta,
                current_price_delta,
                current_price_delta_percentage,
                cash_discount_delta,
                momentum,
                created_at
            FROM deal_evaluation_momentum_audit
            WHERE deal_evaluation_id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                OperationalMomentumAudit audit =
                    new OperationalMomentumAudit(
                        resultSet.getLong(
                            "id"
                        ),
                        resultSet.getString(
                            "calculation_version"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getString(
                            "unavailable_reason"
                        ),
                        readNullableLong(
                            resultSet,
                            "previous_offer_snapshot_id"
                        ),
                        readNullableLong(
                            resultSet,
                            "elapsed_seconds"
                        ),
                        resultSet.getBigDecimal(
                            "sold_percentage_delta"
                        ),
                        resultSet.getBigDecimal(
                            "current_price_delta"
                        ),
                        resultSet.getBigDecimal(
                            "current_price_delta_percentage"
                        ),
                        resultSet.getBigDecimal(
                            "cash_discount_delta"
                        ),
                        resultSet.getBigDecimal(
                            "momentum"
                        ),
                        resultSet.getObject(
                            "created_at",
                            OffsetDateTime.class
                        )
                    );

                if (resultSet.next()) {
                    throw new IllegalStateException(
                        "More than one momentum audit found "
                            + "for DealEvaluation "
                            + evaluationId
                    );
                }

                return Optional.of(
                    audit
                );
            }
        }
    }

    private RejectionReason readRejectionReason(
        ResultSet resultSet
    ) throws SQLException {

        String persistedValue =
            resultSet.getString(
                "rejection_reason"
            );

        if (persistedValue == null) {
            return null;
        }

        try {

            return RejectionReason.valueOf(
                persistedValue
            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                "Unknown persisted rejection reason: "
                    + persistedValue,
                exception
            );
        }
    }

    private Money readNullableMoney(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        BigDecimal amount =
            resultSet.getBigDecimal(
                column
            );

        if (amount == null) {
            return null;
        }

        return new Money(
            amount
        );
    }

    private Double readNullableDouble(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        double value =
            resultSet.getDouble(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private Long readNullableLong(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        long value =
            resultSet.getLong(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private record BaseDetail(
        DealEvaluationSummary summary,
        String productUrl,
        Money basisPrice,
        Money previousPrice,
        BigDecimal soldPercentage,
        Double rating,
        Long reviewCount,
        String sellerName,
        String deliveryProvider,
        String source,
        String eligibilityPolicyVersion,
        String filterProfileVersion,
        String scoreVersion,
        String momentumVersion
    ) {
    }
}
