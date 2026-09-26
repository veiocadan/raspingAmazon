package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.orchestration.run.GetProcessingRunDetailUseCase;
import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunCursor;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunDetail;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunJobMetrics;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPipelineMetrics;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSearchCriteria;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSummary;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;

import java.io.PrintWriter;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Comandos operacionais relacionados a ProcessingRun.
 *
 * <p>Esta classe converte argumentos do terminal em critérios da
 * camada de aplicação e renderiza read models persistidos.</p>
 *
 * <p>Ela não inicia runs, não cria jobs, não executa workers e não
 * altera o estado do pipeline.</p>
 *
 * <p>O detalhe operacional exposto por show representa fatos
 * persistidos. Nenhuma regra comercial, score, momentum,
 * elegibilidade ou publicação é recalculada pela apresentação.</p>
 */
public final class RunsCliCommand
    implements CliCommandHandler {

    private static final String HEADER =
        String.join(
            "\t",
            "RUN_ID",
            "RUN_KEY",
            "STATUS",
            "REQUESTED_AT",
            "STARTED_AT",
            "COMPLETED_AT",
            "CREATED_AT",
            "UPDATED_AT",
            "SOURCE_URI",
            "LAST_ERROR_CODE",
            "LAST_ERROR_MESSAGE"
        );

    private final ListProcessingRunsUseCase
        listProcessingRunsUseCase;

    private final GetProcessingRunDetailUseCase
        getProcessingRunDetailUseCase;

    public RunsCliCommand(
        ListProcessingRunsUseCase listProcessingRunsUseCase,
        GetProcessingRunDetailUseCase getProcessingRunDetailUseCase
    ) {

        this.listProcessingRunsUseCase =
            Objects.requireNonNull(
                listProcessingRunsUseCase,
                "listProcessingRunsUseCase must not be null"
            );

        this.getProcessingRunDetailUseCase =
            Objects.requireNonNull(
                getProcessingRunDetailUseCase,
                "getProcessingRunDetailUseCase must not be null"
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
                "runs requires an action"
            );
        }

        String action =
            arguments.getFirst();

        if (isHelp(
            action
        )) {

            if (arguments.size() != 1) {
                throw new CliUsageException(
                    "runs help does not accept additional arguments"
                );
            }

            RunsCliUsage.print(
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
                    "unknown runs action: "
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

            RunsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        ParsedListArguments parsed =
            parseListArguments(
                arguments
            );

        ProcessingRunSearchCriteria criteria =
            createCriteria(
                parsed
            );

        ProcessingRunPage page =
            listProcessingRunsUseCase.execute(
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

            RunsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        if (arguments.size() != 1) {
            throw new CliUsageException(
                "runs show requires exactly one run-id"
            );
        }

        long runId =
            CliValueParser.positiveLong(
                "run-id",
                arguments.getFirst()
            );

        Optional<ProcessingRunDetail> result =
            getProcessingRunDetailUseCase.execute(
                runId
            );

        if (result.isEmpty()) {

            err.println(
                "Run not found: "
                    + runId
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

        ProcessingRunStatus status =
            null;

        OffsetDateTime requestedFrom =
            null;

        OffsetDateTime requestedUntil =
            null;

        OffsetDateTime afterAt =
            null;

        Long afterId =
            null;

        int limit =
            ProcessingRunSearchCriteria.DEFAULT_LIMIT;

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

                case "--from" ->
                    requestedFrom =
                        CliValueParser.offsetDateTime(
                            option,
                            value
                        );

                case "--until" ->
                    requestedUntil =
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
                        "unknown runs list option: "
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
            requestedFrom,
            requestedUntil,
            afterAt,
            afterId,
            limit
        );
    }

    private ProcessingRunSearchCriteria createCriteria(
        ParsedListArguments parsed
    ) {

        ProcessingRunCursor cursor =
            null;

        if (parsed.afterAt() != null) {

            cursor =
                new ProcessingRunCursor(
                    parsed.afterAt(),
                    parsed.afterId()
                );
        }

        try {

            return new ProcessingRunSearchCriteria(
                parsed.status(),
                parsed.requestedFrom(),
                parsed.requestedUntil(),
                cursor,
                parsed.limit()
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                exception.getMessage()
            );
        }
    }

    private ProcessingRunStatus parseStatus(
        String value
    ) {

        try {

            return ProcessingRunStatus.valueOf(
                value.toUpperCase(
                    Locale.ROOT
                )
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                "--status must be one of "
                    + "PENDING, RUNNING, COMPLETED or FAILED"
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
        ProcessingRunPage page,
        PrintWriter out
    ) {

        out.println(
            HEADER
        );

        for (ProcessingRunSummary summary
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
                    .requestedAt()
                    + "\t"
                    + page.nextCursor()
                    .runId()
            );
        }

        out.flush();
    }

    private String renderSummary(
        ProcessingRunSummary summary
    ) {

        return String.join(
            "\t",
            Long.toString(
                summary.runId()
            ),
            CliText.text(
                summary.runKey()
            ),
            CliText.enumName(
                summary.status()
            ),
            summary.requestedAt()
                .toString(),
            dateTime(
                summary.startedAt()
            ),
            dateTime(
                summary.completedAt()
            ),
            summary.createdAt()
                .toString(),
            summary.updatedAt()
                .toString(),
            CliText.text(
                summary.sourceUri()
            ),
            CliText.text(
                summary.lastErrorCode()
            ),
            CliText.text(
                summary.lastErrorMessage()
            )
        );
    }

    private void renderDetail(
        ProcessingRunDetail detail,
        PrintWriter out
    ) {

        ProcessingRunSummary summary =
            detail.summary();

        ProcessingRunPipelineMetrics pipeline =
            detail.pipeline();

        ProcessingRunJobMetrics jobs =
            detail.jobs();

        out.println(
            "RUN"
        );

        printField(
            out,
            "RUN_ID",
            Long.toString(
                summary.runId()
            )
        );

        printField(
            out,
            "RUN_KEY",
            CliText.text(
                summary.runKey()
            )
        );

        printField(
            out,
            "STATUS",
            CliText.enumName(
                summary.status()
            )
        );

        printField(
            out,
            "REQUESTED_AT",
            summary.requestedAt()
                .toString()
        );

        printField(
            out,
            "STARTED_AT",
            dateTime(
                summary.startedAt()
            )
        );

        printField(
            out,
            "COMPLETED_AT",
            dateTime(
                summary.completedAt()
            )
        );

        printField(
            out,
            "CREATED_AT",
            summary.createdAt()
                .toString()
        );

        printField(
            out,
            "UPDATED_AT",
            summary.updatedAt()
                .toString()
        );

        printField(
            out,
            "SOURCE_URI",
            CliText.text(
                summary.sourceUri()
            )
        );

        printField(
            out,
            "LAST_ERROR_CODE",
            CliText.text(
                summary.lastErrorCode()
            )
        );

        printField(
            out,
            "LAST_ERROR_MESSAGE",
            CliText.text(
                summary.lastErrorMessage()
            )
        );

        out.println();

        out.println(
            "PIPELINE"
        );

        printField(
            out,
            "COLLECTED_CANDIDATES",
            Long.toString(
                pipeline.collectedCandidates()
            )
        );

        printField(
            out,
            "ENRICHED_CANDIDATES",
            Long.toString(
                pipeline.enrichedCandidates()
            )
        );

        printField(
            out,
            "PENDING_ENRICHMENT_CANDIDATES",
            Long.toString(
                pipeline.pendingEnrichmentCandidates()
            )
        );

        printField(
            out,
            "EVALUATIONS",
            Long.toString(
                pipeline.evaluations()
            )
        );

        printField(
            out,
            "ELIGIBLE_EVALUATIONS",
            Long.toString(
                pipeline.eligibleEvaluations()
            )
        );

        printField(
            out,
            "REJECTED_EVALUATIONS",
            Long.toString(
                pipeline.rejectedEvaluations()
            )
        );

        printField(
            out,
            "PUBLICATIONS_GENERATED",
            Long.toString(
                pipeline.publicationsGenerated()
            )
        );

        out.println();

        out.println(
            "JOBS"
        );

        printField(
            out,
            "TOTAL_JOBS",
            Long.toString(
                jobs.totalJobs()
            )
        );

        printField(
            out,
            "PENDING_JOBS",
            Long.toString(
                jobs.pendingJobs()
            )
        );

        printField(
            out,
            "RUNNING_JOBS",
            Long.toString(
                jobs.runningJobs()
            )
        );

        printField(
            out,
            "RETRY_WAIT_JOBS",
            Long.toString(
                jobs.retryWaitJobs()
            )
        );

        printField(
            out,
            "SUCCEEDED_JOBS",
            Long.toString(
                jobs.succeededJobs()
            )
        );

        printField(
            out,
            "DEAD_JOBS",
            Long.toString(
                jobs.deadJobs()
            )
        );

        printField(
            out,
            "TOTAL_ATTEMPTS",
            Long.toString(
                jobs.totalAttempts()
            )
        );

        printField(
            out,
            "RETRY_ATTEMPTS",
            Long.toString(
                jobs.retryAttempts()
            )
        );

        out.flush();
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

    private String dateTime(
        OffsetDateTime value
    ) {

        if (value == null) {
            return "-";
        }

        return value.toString();
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
        ProcessingRunStatus status,
        OffsetDateTime requestedFrom,
        OffsetDateTime requestedUntil,
        OffsetDateTime afterAt,
        Long afterId,
        int limit
    ) {
    }
}
