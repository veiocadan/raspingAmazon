package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.DealEvaluationCursor;
import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSearchCriteria;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSummary;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.domain.product.Asin;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Comandos operacionais relacionados às avaliações de ofertas.
 *
 * <p>Esta classe converte argumentos do terminal em critérios
 * da camada de aplicação e renderiza os read models retornados.</p>
 *
 * <p>Ela não executa SQL, não recalcula score e não decide
 * elegibilidade.</p>
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

    public EvaluationsCliCommand(
        ListDealEvaluationsUseCase listDealEvaluationsUseCase
    ) {

        this.listDealEvaluationsUseCase =
            Objects.requireNonNull(
                listDealEvaluationsUseCase,
                "listDealEvaluationsUseCase must not be null"
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

        if (!"list".equals(
            action
        )) {

            throw new CliUsageException(
                "unknown evaluations action: "
                    + action
            );
        }

        return executeList(
            arguments.subList(
                1,
                arguments.size()
            ),
            out
        );
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
