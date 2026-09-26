package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.orchestration.job.ListProcessingJobsUseCase;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobCursor;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobPage;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobSearchCriteria;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobSummary;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;

import java.io.PrintWriter;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Comandos operacionais relacionados a ProcessingJob.
 *
 * <p>Esta classe apenas converte argumentos da CLI em critérios
 * da camada de aplicação e renderiza fatos persistidos da fila.</p>
 *
 * <p>Ela não reivindica jobs, não executa retry, não executa workers
 * e não altera qualquer estado da orquestração.</p>
 */
public final class JobsCliCommand
    implements CliCommandHandler {

    private static final String HEADER =
        String.join(
            "\t",
            "JOB_ID",
            "TYPE",
            "STATUS",
            "RUN_ID",
            "CANDIDATE_ID",
            "SNAPSHOT_ID",
            "ATTEMPT_COUNT",
            "MAX_ATTEMPTS",
            "REMAINING_ATTEMPTS",
            "AVAILABLE_AT",
            "LOCKED_AT",
            "LOCKED_BY",
            "LAST_FAILURE_TYPE",
            "LAST_ERROR_CODE",
            "LAST_ERROR_MESSAGE",
            "CREATED_AT",
            "UPDATED_AT",
            "FINISHED_AT",
            "IDEMPOTENCY_KEY"
        );

    private final ListProcessingJobsUseCase
        listProcessingJobsUseCase;

    public JobsCliCommand(
        ListProcessingJobsUseCase listProcessingJobsUseCase
    ) {

        this.listProcessingJobsUseCase =
            Objects.requireNonNull(
                listProcessingJobsUseCase,
                "listProcessingJobsUseCase must not be null"
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
                "jobs requires an action"
            );
        }

        String action =
            arguments.getFirst();

        if (isHelp(
            action
        )) {

            if (arguments.size() != 1) {
                throw new CliUsageException(
                    "jobs help does not accept additional arguments"
                );
            }

            JobsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        if (!"list".equals(
            action
        )) {

            throw new CliUsageException(
                "unknown jobs action: "
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

            JobsCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        ParsedListArguments parsed =
            parseListArguments(
                arguments
            );

        ProcessingJobSearchCriteria criteria =
            createCriteria(
                parsed
            );

        ProcessingJobPage page =
            listProcessingJobsUseCase.execute(
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

        ProcessingJobType type =
            null;

        ProcessingJobStatus status =
            null;

        ProcessingFailureType failureType =
            null;

        Long processingRunId =
            null;

        Long dealCandidateId =
            null;

        Long offerSnapshotId =
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
            ProcessingJobSearchCriteria.DEFAULT_LIMIT;

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

                case "--type" ->
                    type =
                        parseType(
                            value
                        );

                case "--status" ->
                    status =
                        parseStatus(
                            value
                        );

                case "--failure-type" ->
                    failureType =
                        parseFailureType(
                            value
                        );

                case "--run-id" ->
                    processingRunId =
                        CliValueParser.positiveLong(
                            option,
                            value
                        );

                case "--candidate-id" ->
                    dealCandidateId =
                        CliValueParser.positiveLong(
                            option,
                            value
                        );

                case "--snapshot-id" ->
                    offerSnapshotId =
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
                        "unknown jobs list option: "
                            + option
                    );
            }
        }

        validateCursorPair(
            afterAt,
            afterId
        );

        return new ParsedListArguments(
            type,
            status,
            failureType,
            processingRunId,
            dealCandidateId,
            offerSnapshotId,
            createdFrom,
            createdUntil,
            afterAt,
            afterId,
            limit
        );
    }

    private ProcessingJobSearchCriteria createCriteria(
        ParsedListArguments parsed
    ) {

        ProcessingJobCursor cursor =
            null;

        if (parsed.afterAt() != null) {

            cursor =
                new ProcessingJobCursor(
                    parsed.afterAt(),
                    parsed.afterId()
                );
        }

        try {

            return new ProcessingJobSearchCriteria(
                parsed.type(),
                parsed.status(),
                parsed.failureType(),
                parsed.processingRunId(),
                parsed.dealCandidateId(),
                parsed.offerSnapshotId(),
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

    private ProcessingJobType parseType(
        String value
    ) {

        try {

            return ProcessingJobType.valueOf(
                value.toUpperCase(
                    Locale.ROOT
                )
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                "--type must be one of "
                    + "COLLECT_DEALS, ENRICH_DEAL or EVALUATE_DEAL"
            );
        }
    }

    private ProcessingJobStatus parseStatus(
        String value
    ) {

        try {

            return ProcessingJobStatus.valueOf(
                value.toUpperCase(
                    Locale.ROOT
                )
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                "--status must be one of "
                    + "PENDING, RUNNING, RETRY_WAIT, SUCCEEDED or DEAD"
            );
        }
    }

    private ProcessingFailureType parseFailureType(
        String value
    ) {

        try {

            return ProcessingFailureType.valueOf(
                value.toUpperCase(
                    Locale.ROOT
                )
            );

        } catch (IllegalArgumentException exception) {

            throw new CliUsageException(
                "--failure-type must be TRANSIENT or PERMANENT"
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
        ProcessingJobPage page,
        PrintWriter out
    ) {

        out.println(
            HEADER
        );

        for (ProcessingJobSummary summary
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
                    .jobId()
            );
        }

        out.flush();
    }

    private String renderSummary(
        ProcessingJobSummary summary
    ) {

        return String.join(
            "\t",
            Long.toString(
                summary.jobId()
            ),
            CliText.enumName(
                summary.type()
            ),
            CliText.enumName(
                summary.status()
            ),
            nullableLong(
                summary.processingRunId()
            ),
            nullableLong(
                summary.dealCandidateId()
            ),
            nullableLong(
                summary.offerSnapshotId()
            ),
            Integer.toString(
                summary.attemptCount()
            ),
            Integer.toString(
                summary.maxAttempts()
            ),
            Integer.toString(
                summary.remainingAttempts()
            ),
            summary.availableAt()
                .toString(),
            dateTime(
                summary.lockedAt()
            ),
            CliText.text(
                summary.lockedBy()
            ),
            CliText.enumName(
                summary.lastFailureType()
            ),
            CliText.text(
                summary.lastErrorCode()
            ),
            CliText.text(
                summary.lastErrorMessage()
            ),
            summary.createdAt()
                .toString(),
            summary.updatedAt()
                .toString(),
            dateTime(
                summary.finishedAt()
            ),
            CliText.text(
                summary.idempotencyKey()
            )
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
        ProcessingJobType type,
        ProcessingJobStatus status,
        ProcessingFailureType failureType,
        Long processingRunId,
        Long dealCandidateId,
        Long offerSnapshotId,
        OffsetDateTime createdFrom,
        OffsetDateTime createdUntil,
        OffsetDateTime afterAt,
        Long afterId,
        int limit
    ) {
    }
}
