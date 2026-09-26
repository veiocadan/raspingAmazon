package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.publication.GetPublicationDetailUseCase;
import com.raspingamazon.application.operation.publication.ListPublicationsUseCase;
import com.raspingamazon.application.operation.publication.PublicationCursor;
import com.raspingamazon.application.operation.publication.PublicationDetail;
import com.raspingamazon.application.operation.publication.PublicationPage;
import com.raspingamazon.application.operation.publication.PublicationSearchCriteria;
import com.raspingamazon.application.operation.publication.PublicationSummary;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.io.PrintWriter;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Comandos operacionais relacionados a Publication.
 *
 * <p>Esta camada somente converte argumentos da linha de comando
 * em contratos da aplicação e renderiza os read models.</p>
 *
 * <p>Ela não gera publicações, não altera status e não executa
 * aprovação ou publicação em canais.</p>
 */
public final class PublicationsCliCommand
    implements CliCommandHandler {

    private static final String HEADER =
        String.join(
            "\t",
            "PUBLICATION_ID",
            "EVALUATION_ID",
            "PRODUCT_ID",
            "ASIN",
            "STATUS",
            "TEMPLATE_VERSION",
            "COMMERCIAL_PRESENTATION_VERSION",
            "AFFILIATE_LINK_VERSION",
            "CREATED_AT",
            "TITLE"
        );

    private final ListPublicationsUseCase
        listPublicationsUseCase;

    private final GetPublicationDetailUseCase
        getPublicationDetailUseCase;

    public PublicationsCliCommand(
        ListPublicationsUseCase listPublicationsUseCase,
        GetPublicationDetailUseCase getPublicationDetailUseCase
    ) {

        this.listPublicationsUseCase =
            Objects.requireNonNull(
                listPublicationsUseCase,
                "listPublicationsUseCase must not be null"
            );

        this.getPublicationDetailUseCase =
            Objects.requireNonNull(
                getPublicationDetailUseCase,
                "getPublicationDetailUseCase must not be null"
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
                "publications requires an action"
            );
        }

        String action =
            arguments.getFirst();

        if (isHelp(
            action
        )) {

            if (arguments.size() != 1) {
                throw new CliUsageException(
                    "publications help does not accept additional arguments"
                );
            }

            PublicationsCliUsage.print(
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
                    "unknown publications action: "
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

            PublicationsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        ParsedListArguments parsed =
            parseListArguments(
                arguments
            );

        PublicationSearchCriteria criteria =
            createCriteria(
                parsed
            );

        PublicationPage page =
            listPublicationsUseCase.execute(
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

            PublicationsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        if (arguments.size() != 1) {
            throw new CliUsageException(
                "publications show requires exactly one publication-id"
            );
        }

        long publicationId =
            CliValueParser.positiveLong(
                "publication-id",
                arguments.getFirst()
            );

        Optional<PublicationDetail> result =
            getPublicationDetailUseCase.execute(
                publicationId
            );

        if (result.isEmpty()) {

            err.println(
                "Publication not found: "
                    + publicationId
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

        PublicationStatus status =
            null;

        Asin asin =
            null;

        Long evaluationId =
            null;

        OffsetDateTime createdFrom =
            null;

        OffsetDateTime createdUntil =
            null;

        OffsetDateTime afterAt =
            null;

        Long afterId =
            null;

        int limit =
            PublicationSearchCriteria.DEFAULT_LIMIT;

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

                case "--status" ->
                    status =
                        parseStatus(
                            value
                        );

                case "--asin" ->
                    asin =
                        parseAsin(
                            value
                        );

                case "--evaluation-id" ->
                    evaluationId =
                        CliValueParser.positiveLong(
                            option,
                            value
                        );

                case "--from" ->
                    createdFrom =
                        CliValueParser.offsetDateTime(
                            option,
                            value
                        );

                case "--until" ->
                    createdUntil =
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
                        "unknown publications list option: "
                            + option
                    );
            }
        }

        validateCursorPair(
            afterAt,
            afterId
        );

        return new ParsedListArguments(
            status,
            asin,
            evaluationId,
            createdFrom,
            createdUntil,
            afterAt,
            afterId,
            limit
        );
    }

    private PublicationSearchCriteria createCriteria(
        ParsedListArguments parsed
    ) {

        PublicationCursor cursor =
            null;

        if (parsed.afterAt() != null) {

            cursor =
                new PublicationCursor(
                    parsed.afterAt(),
                    parsed.afterId()
                );
        }

        try {

            return new PublicationSearchCriteria(
                parsed.status(),
                parsed.asin(),
                parsed.evaluationId(),
                parsed.createdFrom(),
                parsed.createdUntil(),
                cursor,
                parsed.limit()
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                exception.getMessage()
            );
        }
    }

    private PublicationStatus parseStatus(
        String value
    ) {

        try {

            return PublicationStatus.valueOf(
                value.toUpperCase(
                    Locale.ROOT
                )
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                "--status must be one of "
                    + "CREATED, READY, PUBLISHED or FAILED"
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
        PublicationPage page,
        PrintWriter out
    ) {

        out.println(
            HEADER
        );

        for (PublicationSummary summary
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
                    .createdAt()
                    + "\t"
                    + page.nextCursor()
                    .publicationId()
            );
        }

        out.flush();
    }

    private String renderSummary(
        PublicationSummary summary
    ) {

        return String.join(
            "\t",
            Long.toString(
                summary.publicationId()
            ),
            Long.toString(
                summary.dealEvaluationId()
            ),
            Long.toString(
                summary.productId()
            ),
            summary.asin()
                .value(),
            CliText.text(
                summary.status()
            ),
            CliText.text(
                summary.templateVersion()
            ),
            CliText.text(
                summary.commercialPresentationVersion()
            ),
            CliText.text(
                summary.affiliateLinkVersion()
            ),
            summary.createdAt()
                .toString(),
            CliText.text(
                summary.title()
            )
        );
    }

    private void renderDetail(
        PublicationDetail detail,
        PrintWriter out
    ) {

        PublicationSummary summary =
            detail.summary();

        out.println(
            "PUBLICATION_ID\t"
                + summary.publicationId()
        );

        out.println(
            "DEAL_EVALUATION_ID\t"
                + summary.dealEvaluationId()
        );

        out.println(
            "PRODUCT_ID\t"
                + summary.productId()
        );

        out.println(
            "ASIN\t"
                + summary.asin()
                .value()
        );

        out.println(
            "TITLE\t"
                + CliText.text(
                summary.title()
            )
        );

        out.println(
            "STATUS\t"
                + CliText.text(
                summary.status()
            )
        );

        out.println(
            "TEMPLATE_VERSION\t"
                + CliText.text(
                summary.templateVersion()
            )
        );

        out.println(
            "COMMERCIAL_PRESENTATION_VERSION\t"
                + CliText.text(
                summary.commercialPresentationVersion()
            )
        );

        out.println(
            "AFFILIATE_LINK_VERSION\t"
                + CliText.text(
                summary.affiliateLinkVersion()
            )
        );

        out.println(
            "CREATED_AT\t"
                + summary.createdAt()
        );

        out.println(
            "AFFILIATE_URL\t"
                + CliText.text(
                detail.affiliateUrl()
            )
        );

        out.println();

        out.println(
            "GENERATED_TEXT_BEGIN"
        );

        out.println(
            detail.generatedText()
        );

        out.println(
            "GENERATED_TEXT_END"
        );

        out.flush();
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
        PublicationStatus status,
        Asin asin,
        Long evaluationId,
        OffsetDateTime createdFrom,
        OffsetDateTime createdUntil,
        OffsetDateTime afterAt,
        Long afterId,
        int limit
    ) {
    }
}
