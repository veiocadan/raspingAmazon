package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.DealEvaluationCursor;
import com.raspingamazon.application.operation.evaluation.DealEvaluationDetail;
import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSearchCriteria;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSummary;
import com.raspingamazon.application.operation.evaluation.GetDealEvaluationDetailUseCase;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.application.operation.evaluation.OperationalEvaluationRuleResult;
import com.raspingamazon.application.operation.evaluation.OperationalMomentumAudit;
import com.raspingamazon.application.operation.evaluation.OperationalScoreFactorResult;
import com.raspingamazon.domain.product.Asin;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Comandos operacionais relacionados às avaliações de ofertas.
 *
 * <p>Esta classe converte argumentos do terminal em critérios
 * da camada de aplicação e renderiza os read models retornados.</p>
 *
 * <p>Ela não executa SQL, não recalcula score, não recalcula
 * momentum e não decide elegibilidade.</p>
 */
public final class EvaluationsCliCommand
    implements CliCommandHandler {

    private static final String HEADER =
        String.join(
            "\t",
            "EVALUATION_ID",
            "OFFER_SNAPSHOT_ID",
            "PRODUCT_ID",
            "ASIN",
            "ELIGIBLE",
            "SCORE",
            "MOMENTUM",
            "PRICE",
            "REJECTION",
            "COLLECTED_AT",
            "EVALUATED_AT",
            "TITLE"
        );

    private final ListDealEvaluationsUseCase
        listDealEvaluationsUseCase;

    private final GetDealEvaluationDetailUseCase
        getDealEvaluationDetailUseCase;

    public EvaluationsCliCommand(
        ListDealEvaluationsUseCase listDealEvaluationsUseCase,
        GetDealEvaluationDetailUseCase getDealEvaluationDetailUseCase
    ) {

        this.listDealEvaluationsUseCase =
            Objects.requireNonNull(
                listDealEvaluationsUseCase,
                "listDealEvaluationsUseCase must not be null"
            );

        this.getDealEvaluationDetailUseCase =
            Objects.requireNonNull(
                getDealEvaluationDetailUseCase,
                "getDealEvaluationDetailUseCase must not be null"
            );
    }

    @Override
    public CliExitCode execute(
        List<String> arguments,
        PrintWriter out,
        PrintWriter err
    ) {

        Objects.requireNonNull(
            arguments,
            "arguments must not be null"
        );

        Objects.requireNonNull(
            out,
            "out must not be null"
        );

        Objects.requireNonNull(
            err,
            "err must not be null"
        );

        if (arguments.isEmpty()) {
            throw new CliUsageException(
                "evaluations requires an action"
            );
        }

        String action =
            arguments.getFirst();

        if (isHelp(
            action
        )) {

            if (arguments.size() != 1) {
                throw new CliUsageException(
                    "evaluations help does not accept additional arguments"
                );
            }

            EvaluationsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        return switch (action) {

            case "list" ->
                executeList(
                    arguments.subList(
                        1,
                        arguments.size()
                    ),
                    out
                );

            case "show" ->
                executeShow(
                    arguments.subList(
                        1,
                        arguments.size()
                    ),
                    out,
                    err
                );

            default ->
                throw new CliUsageException(
                    "unknown evaluations action: "
                        + action
                );
        };
    }

    private CliExitCode executeList(
        List<String> arguments,
        PrintWriter out
    ) {

        if (arguments.size() == 1
            && isHelp(
            arguments.getFirst()
        )) {

            EvaluationsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        ParsedListArguments parsed =
            parseListArguments(
                arguments
            );

        DealEvaluationSearchCriteria criteria =
            createCriteria(
                parsed
            );

        DealEvaluationPage page =
            listDealEvaluationsUseCase.execute(
                criteria
            );

        renderPage(
            page,
            out
        );

        return CliExitCode.SUCCESS;
    }

    private CliExitCode executeShow(
        List<String> arguments,
        PrintWriter out,
        PrintWriter err
    ) {

        if (arguments.size() == 1
            && isHelp(
            arguments.getFirst()
        )) {

            EvaluationsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        if (arguments.size() != 1) {
            throw new CliUsageException(
                "evaluations show requires exactly one evaluation-id"
            );
        }

        long evaluationId =
            CliValueParser.positiveLong(
                "evaluation-id",
                arguments.getFirst()
            );

        Optional<DealEvaluationDetail> result =
            getDealEvaluationDetailUseCase.execute(
                evaluationId
            );

        if (result.isEmpty()) {

            err.println(
                "Evaluation not found: "
                    + evaluationId
            );

            err.flush();

            return CliExitCode.NOT_FOUND;
        }

        renderDetail(
            result.orElseThrow(),
            out
        );

        return CliExitCode.SUCCESS;
    }

    private ParsedListArguments parseListArguments(
        List<String> arguments
    ) {

        Boolean eligible =
            null;

        Asin asin =
            null;

        BigDecimal minScore =
            null;

        BigDecimal maxScore =
            null;

        OffsetDateTime evaluatedFrom =
            null;

        OffsetDateTime evaluatedUntil =
            null;

        OffsetDateTime afterAt =
            null;

        Long afterId =
            null;

        int limit =
            DealEvaluationSearchCriteria.DEFAULT_LIMIT;

        Set<String> seenOptions =
            new HashSet<>();

        for (int index = 0;
             index < arguments.size();
             index++) {

            String option =
                arguments.get(
                    index
                );

            if (!option.startsWith(
                "--"
            )) {

                throw new CliUsageException(
                    "unexpected argument: "
                        + option
                );
            }

            if (!seenOptions.add(
                option
            )) {

                throw new CliUsageException(
                    "duplicate option: "
                        + option
                );
            }

            String value =
                requireOptionValue(
                    arguments,
                    index,
                    option
                );

            index++;

            switch (option) {

                case "--eligible" ->
                    eligible =
                        CliValueParser.strictBoolean(
                            option,
                            value
                        );

                case "--asin" ->
                    asin =
                        parseAsin(
                            value
                        );

                case "--min-score" ->
                    minScore =
                        CliValueParser.decimal(
                            option,
                            value
                        );

                case "--max-score" ->
                    maxScore =
                        CliValueParser.decimal(
                            option,
                            value
                        );

                case "--from" ->
                    evaluatedFrom =
                        CliValueParser.offsetDateTime(
                            option,
                            value
                        );

                case "--until" ->
                    evaluatedUntil =
                        CliValueParser.offsetDateTime(
                            option,
                            value
                        );

                case "--after-at" ->
                    afterAt =
                        CliValueParser.offsetDateTime(
                            option,
                            value
                        );

                case "--after-id" ->
                    afterId =
                        CliValueParser.positiveLong(
                            option,
                            value
                        );

                case "--limit" ->
                    limit =
                        CliValueParser.positiveInt(
                            option,
                            value
                        );

                default ->
                    throw new CliUsageException(
                        "unknown evaluations list option: "
                            + option
                    );
            }
        }

        validateCursorPair(
            afterAt,
            afterId
        );

        return new ParsedListArguments(
            eligible,
            asin,
            minScore,
            maxScore,
            evaluatedFrom,
            evaluatedUntil,
            afterAt,
            afterId,
            limit
        );
    }

    private DealEvaluationSearchCriteria createCriteria(
        ParsedListArguments parsed
    ) {

        DealEvaluationCursor cursor =
            null;

        if (parsed.afterAt() != null) {

            cursor =
                new DealEvaluationCursor(
                    parsed.afterAt(),
                    parsed.afterId()
                );
        }

        try {

            return new DealEvaluationSearchCriteria(
                parsed.eligible(),
                parsed.asin(),
                parsed.minScore(),
                parsed.maxScore(),
                parsed.evaluatedFrom(),
                parsed.evaluatedUntil(),
                cursor,
                parsed.limit()
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                exception.getMessage()
            );
        }
    }

    private Asin parseAsin(
        String value
    ) {

        try {

            return new Asin(
                value
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                exception.getMessage()
            );
        }
    }

    private String requireOptionValue(
        List<String> arguments,
        int optionIndex,
        String option
    ) {

        int valueIndex =
            optionIndex + 1;

        if (valueIndex >= arguments.size()) {
            throw new CliUsageException(
                "missing value for "
                    + option
            );
        }

        String value =
            arguments.get(
                valueIndex
            );

        if (value.startsWith(
            "--"
        )) {

            throw new CliUsageException(
                "missing value for "
                    + option
            );
        }

        return value;
    }

    private void validateCursorPair(
        OffsetDateTime afterAt,
        Long afterId
    ) {

        if ((afterAt == null)
            != (afterId == null)) {

            throw new CliUsageException(
                "--after-at and --after-id must be provided together"
            );
        }
    }

    private void renderPage(
        DealEvaluationPage page,
        PrintWriter out
    ) {

        out.println(
            HEADER
        );

        for (DealEvaluationSummary summary
            : page.items()) {

            out.println(
                renderSummary(
                    summary
                )
            );
        }

        if (page.nextCursor() != null) {

            out.println();

            out.println(
                "NEXT_CURSOR\t"
                    + page.nextCursor()
                    .evaluatedAt()
                    + "\t"
                    + page.nextCursor()
                    .evaluationId()
            );
        }

        out.flush();
    }

    private String renderSummary(
        DealEvaluationSummary summary
    ) {

        return String.join(
            "\t",
            Long.toString(
                summary.evaluationId()
            ),
            Long.toString(
                summary.offerSnapshotId()
            ),
            Long.toString(
                summary.productId()
            ),
            summary.asin()
                .value(),
            Boolean.toString(
                summary.eligible()
            ),
            CliText.decimal(
                summary.score()
            ),
            CliText.decimal(
                summary.momentum()
            ),
            CliText.money(
                summary.currentPrice()
            ),
            CliText.enumName(
                summary.rejectionReason()
            ),
            summary.collectedAt()
                .toString(),
            summary.evaluatedAt()
                .toString(),
            CliText.text(
                summary.title()
            )
        );
    }

    private void renderDetail(
        DealEvaluationDetail detail,
        PrintWriter out
    ) {

        DealEvaluationSummary summary =
            detail.summary();

        out.println(
            "EVALUATION"
        );

        printField(
            out,
            "EVALUATION_ID",
            Long.toString(
                summary.evaluationId()
            )
        );

        printField(
            out,
            "OFFER_SNAPSHOT_ID",
            Long.toString(
                summary.offerSnapshotId()
            )
        );

        printField(
            out,
            "PRODUCT_ID",
            Long.toString(
                summary.productId()
            )
        );

        printField(
            out,
            "ASIN",
            summary.asin()
                .value()
        );

        printField(
            out,
            "TITLE",
            CliText.text(
                summary.title()
            )
        );

        printField(
            out,
            "PRODUCT_URL",
            CliText.text(
                detail.productUrl()
            )
        );

        printField(
            out,
            "CURRENT_PRICE",
            CliText.money(
                summary.currentPrice()
            )
        );

        printField(
            out,
            "BASIS_PRICE",
            CliText.money(
                detail.basisPrice()
            )
        );

        printField(
            out,
            "PREVIOUS_PRICE",
            CliText.money(
                detail.previousPrice()
            )
        );

        printField(
            out,
            "SOLD_PERCENTAGE",
            CliText.decimal(
                detail.soldPercentage()
            )
        );

        printField(
            out,
            "RATING",
            nullableDouble(
                detail.rating()
            )
        );

        printField(
            out,
            "REVIEW_COUNT",
            nullableLong(
                detail.reviewCount()
            )
        );

        printField(
            out,
            "SELLER_NAME",
            CliText.text(
                detail.sellerName()
            )
        );

        printField(
            out,
            "DELIVERY_PROVIDER",
            CliText.text(
                detail.deliveryProvider()
            )
        );

        printField(
            out,
            "SOURCE",
            CliText.text(
                detail.source()
            )
        );

        printField(
            out,
            "ELIGIBLE",
            Boolean.toString(
                summary.eligible()
            )
        );

        printField(
            out,
            "REJECTION",
            CliText.enumName(
                summary.rejectionReason()
            )
        );

        printField(
            out,
            "SCORE",
            CliText.decimal(
                summary.score()
            )
        );

        printField(
            out,
            "MOMENTUM",
            CliText.decimal(
                summary.momentum()
            )
        );

        printField(
            out,
            "COLLECTED_AT",
            summary.collectedAt()
                .toString()
        );

        printField(
            out,
            "EVALUATED_AT",
            summary.evaluatedAt()
                .toString()
        );

        out.println();

        out.println(
            "VERSIONS"
        );

        printField(
            out,
            "ELIGIBILITY_POLICY_VERSION",
            CliText.text(
                detail.eligibilityPolicyVersion()
            )
        );

        printField(
            out,
            "FILTER_PROFILE_VERSION",
            CliText.text(
                detail.filterProfileVersion()
            )
        );

        printField(
            out,
            "SCORE_VERSION",
            CliText.text(
                detail.scoreVersion()
            )
        );

        printField(
            out,
            "MOMENTUM_VERSION",
            CliText.text(
                detail.momentumVersion()
            )
        );

        out.println();

        renderRuleResults(
            detail.ruleResults(),
            out
        );

        out.println();

        renderScoreFactors(
            detail.scoreFactors(),
            out
        );

        out.println();

        renderMomentumAudit(
            detail.momentumAudit(),
            out
        );

        out.flush();
    }

    private void renderRuleResults(
        List<OperationalEvaluationRuleResult> results,
        PrintWriter out
    ) {

        out.println(
            "RULE_RESULTS"
        );

        out.println(
            String.join(
                "\t",
                "ORDER",
                "RULE_CODE",
                "PASSED",
                "OBSERVED_VALUE",
                "THRESHOLD_VALUE",
                "REASON_CODE"
            )
        );

        for (OperationalEvaluationRuleResult result
            : results) {

            out.println(
                String.join(
                    "\t",
                    Integer.toString(
                        result.ruleOrder()
                    ),
                    CliText.text(
                        result.ruleCode()
                    ),
                    Boolean.toString(
                        result.passed()
                    ),
                    CliText.text(
                        result.observedValue()
                    ),
                    CliText.text(
                        result.thresholdValue()
                    ),
                    CliText.text(
                        result.reasonCode()
                    )
                )
            );
        }
    }

    private void renderScoreFactors(
        List<OperationalScoreFactorResult> factors,
        PrintWriter out
    ) {

        out.println(
            "SCORE_FACTORS"
        );

        out.println(
            String.join(
                "\t",
                "ORDER",
                "FACTOR_CODE",
                "STATUS",
                "RAW_VALUE",
                "NORMALIZED_VALUE",
                "WEIGHT",
                "CONTRIBUTION"
            )
        );

        for (OperationalScoreFactorResult factor
            : factors) {

            out.println(
                String.join(
                    "\t",
                    Integer.toString(
                        factor.factorOrder()
                    ),
                    CliText.text(
                        factor.factorCode()
                    ),
                    CliText.text(
                        factor.status()
                    ),
                    CliText.decimal(
                        factor.rawValue()
                    ),
                    CliText.decimal(
                        factor.normalizedValue()
                    ),
                    CliText.decimal(
                        factor.weight()
                    ),
                    CliText.decimal(
                        factor.contribution()
                    )
                )
            );
        }
    }

    private void renderMomentumAudit(
        OperationalMomentumAudit audit,
        PrintWriter out
    ) {

        out.println(
            "MOMENTUM_AUDIT"
        );

        if (audit == null) {

            printField(
                out,
                "PRESENT",
                "false"
            );

            return;
        }

        printField(
            out,
            "PRESENT",
            "true"
        );

        printField(
            out,
            "AUDIT_ID",
            Long.toString(
                audit.auditId()
            )
        );

        printField(
            out,
            "CALCULATION_VERSION",
            CliText.text(
                audit.calculationVersion()
            )
        );

        printField(
            out,
            "STATUS",
            CliText.text(
                audit.status()
            )
        );

        printField(
            out,
            "UNAVAILABLE_REASON",
            CliText.text(
                audit.unavailableReason()
            )
        );

        printField(
            out,
            "PREVIOUS_OFFER_SNAPSHOT_ID",
            nullableLong(
                audit.previousOfferSnapshotId()
            )
        );

        printField(
            out,
            "ELAPSED_SECONDS",
            nullableLong(
                audit.elapsedSeconds()
            )
        );

        printField(
            out,
            "SOLD_PERCENTAGE_DELTA",
            CliText.decimal(
                audit.soldPercentageDelta()
            )
        );

        printField(
            out,
            "CURRENT_PRICE_DELTA",
            CliText.decimal(
                audit.currentPriceDelta()
            )
        );

        printField(
            out,
            "CURRENT_PRICE_DELTA_PERCENTAGE",
            CliText.decimal(
                audit.currentPriceDeltaPercentage()
            )
        );

        printField(
            out,
            "CASH_DISCOUNT_DELTA",
            CliText.decimal(
                audit.cashDiscountDelta()
            )
        );

        printField(
            out,
            "MOMENTUM",
            CliText.decimal(
                audit.momentum()
            )
        );

        printField(
            out,
            "CREATED_AT",
            audit.createdAt()
                .toString()
        );
    }

    private void printField(
        PrintWriter out,
        String name,
        String value
    ) {

        out.println(
            name
                + "\t"
                + value
        );
    }

    private String nullableLong(
        Long value
    ) {

        if (value == null) {
            return "-";
        }

        return Long.toString(
            value
        );
    }

    private String nullableDouble(
        Double value
    ) {

        if (value == null) {
            return "-";
        }

        return Double.toString(
            value
        );
    }

    private boolean isHelp(
        String argument
    ) {

        return "help".equals(
            argument
        )
            || "--help".equals(
            argument
        )
            || "-h".equals(
            argument
        );
    }

    private record ParsedListArguments(
        Boolean eligible,
        Asin asin,
        BigDecimal minScore,
        BigDecimal maxScore,
        OffsetDateTime evaluatedFrom,
        OffsetDateTime evaluatedUntil,
        OffsetDateTime afterAt,
        Long afterId,
        int limit
    ) {
    }
}
