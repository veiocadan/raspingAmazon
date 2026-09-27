package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.scheduling.ChangeProcessingScheduleIntervalUseCase;
import com.raspingamazon.application.scheduling.GetProcessingScheduleUseCase;
import com.raspingamazon.application.scheduling.PauseProcessingScheduleUseCase;
import com.raspingamazon.application.scheduling.ProcessingSchedule;
import com.raspingamazon.application.scheduling.ProcessingScheduleNotFoundException;
import com.raspingamazon.application.scheduling.ResumeProcessingScheduleUseCase;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Comandos operacionais do scheduler.
 *
 * <p>A apresentação apenas converte argumentos e chama casos de uso.
 * Ela não conhece JDBC, SQL, locks, leases PostgreSQL ou regras de
 * cálculo da próxima janela.</p>
 */
public final class SchedulesCliCommand
    implements CliCommandHandler {

    private final GetProcessingScheduleUseCase
        getProcessingScheduleUseCase;

    private final PauseProcessingScheduleUseCase
        pauseProcessingScheduleUseCase;

    private final ResumeProcessingScheduleUseCase
        resumeProcessingScheduleUseCase;

    private final ChangeProcessingScheduleIntervalUseCase
        changeProcessingScheduleIntervalUseCase;

    public SchedulesCliCommand(
        GetProcessingScheduleUseCase getProcessingScheduleUseCase,
        PauseProcessingScheduleUseCase pauseProcessingScheduleUseCase,
        ResumeProcessingScheduleUseCase resumeProcessingScheduleUseCase,
        ChangeProcessingScheduleIntervalUseCase
            changeProcessingScheduleIntervalUseCase
    ) {

        this.getProcessingScheduleUseCase =
            Objects.requireNonNull(
                getProcessingScheduleUseCase,
                "getProcessingScheduleUseCase must not be null"
            );

        this.pauseProcessingScheduleUseCase =
            Objects.requireNonNull(
                pauseProcessingScheduleUseCase,
                "pauseProcessingScheduleUseCase must not be null"
            );

        this.resumeProcessingScheduleUseCase =
            Objects.requireNonNull(
                resumeProcessingScheduleUseCase,
                "resumeProcessingScheduleUseCase must not be null"
            );

        this.changeProcessingScheduleIntervalUseCase =
            Objects.requireNonNull(
                changeProcessingScheduleIntervalUseCase,
                "changeProcessingScheduleIntervalUseCase must not be null"
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
                "schedules requires an action"
            );
        }

        String action =
            arguments.getFirst();

        if (isHelp(
            action
        )) {

            if (arguments.size() != 1) {

                throw new CliUsageException(
                    "schedules help does not accept additional arguments"
                );
            }

            SchedulesCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        return switch (action) {

            case "status" ->
                executeStatus(
                    arguments.subList(
                        1,
                        arguments.size()
                    ),
                    out,
                    err
                );

            case "pause" ->
                executePause(
                    arguments.subList(
                        1,
                        arguments.size()
                    ),
                    out,
                    err
                );

            case "resume" ->
                executeResume(
                    arguments.subList(
                        1,
                        arguments.size()
                    ),
                    out,
                    err
                );

            case "interval" ->
                executeInterval(
                    arguments.subList(
                        1,
                        arguments.size()
                    ),
                    out,
                    err
                );

            default ->
                throw new CliUsageException(
                    "unknown schedules action: "
                        + action
                );
        };
    }

    private CliExitCode executeStatus(
        List<String> arguments,
        PrintWriter out,
        PrintWriter err
    ) {

        if (arguments.size() == 1
            && isHelp(
            arguments.getFirst()
        )) {

            SchedulesCliUsage.print(
                out
            );

            return CliExitCode.SUCCESS;
        }

        requireExactlyOneScheduleKey(
            arguments,
            "schedules status"
        );

        String scheduleKey =
            arguments.getFirst();

        Optional<ProcessingSchedule> schedule =
            getProcessingScheduleUseCase.execute(
                scheduleKey
            );

        if (schedule.isEmpty()) {

            return notFound(
                scheduleKey,
                err
            );
        }

        render(
            schedule.orElseThrow(),
            out
        );

        return CliExitCode.SUCCESS;
    }

    private CliExitCode executePause(
        List<String> arguments,
        PrintWriter out,
        PrintWriter err
    ) {

        requireExactlyOneScheduleKey(
            arguments,
            "schedules pause"
        );

        String scheduleKey =
            arguments.getFirst();

        try {

            ProcessingSchedule schedule =
                pauseProcessingScheduleUseCase.execute(
                    scheduleKey
                );

            render(
                schedule,
                out
            );

            return CliExitCode.SUCCESS;

        } catch (ProcessingScheduleNotFoundException exception) {

            return notFound(
                scheduleKey,
                err
            );
        }
    }

    private CliExitCode executeResume(
        List<String> arguments,
        PrintWriter out,
        PrintWriter err
    ) {

        requireExactlyOneScheduleKey(
            arguments,
            "schedules resume"
        );

        String scheduleKey =
            arguments.getFirst();

        try {

            ProcessingSchedule schedule =
                resumeProcessingScheduleUseCase.execute(
                    scheduleKey
                );

            render(
                schedule,
                out
            );

            return CliExitCode.SUCCESS;

        } catch (ProcessingScheduleNotFoundException exception) {

            return notFound(
                scheduleKey,
                err
            );
        }
    }

    private CliExitCode executeInterval(
        List<String> arguments,
        PrintWriter out,
        PrintWriter err
    ) {

        if (arguments.size() != 2) {

            throw new CliUsageException(
                "schedules interval requires "
                    + "<schedule-key> <duration>"
            );
        }

        String scheduleKey =
            arguments.get(
                0
            );

        Duration interval =
            CliValueParser.positiveDuration(
                "duration",
                arguments.get(
                    1
                )
            );

        try {

            ProcessingSchedule schedule =
                changeProcessingScheduleIntervalUseCase.execute(
                    scheduleKey,
                    interval
                );

            render(
                schedule,
                out
            );

            return CliExitCode.SUCCESS;

        } catch (ProcessingScheduleNotFoundException exception) {

            return notFound(
                scheduleKey,
                err
            );
        }
    }

    private void requireExactlyOneScheduleKey(
        List<String> arguments,
        String command
    ) {

        if (arguments.size() != 1) {

            throw new CliUsageException(
                command
                    + " requires exactly one schedule-key"
            );
        }
    }

    private CliExitCode notFound(
        String scheduleKey,
        PrintWriter err
    ) {

        err.println(
            "Schedule not found: "
                + scheduleKey
        );

        err.flush();

        return CliExitCode.NOT_FOUND;
    }

    /**
     * Renderiza o estado persistido completo do agendamento.
     *
     * <p>A saída é deliberadamente factual. Nenhum valor operacional é
     * recalculado pela apresentação.</p>
     */
    private void render(
        ProcessingSchedule schedule,
        PrintWriter out
    ) {

        printField(
            out,
            "SCHEDULE_KEY",
            CliText.text(
                schedule.scheduleKey()
            )
        );

        printField(
            out,
            "SOURCE_URI",
            schedule.source()
                .toString()
        );

        printField(
            out,
            "ENABLED",
            Boolean.toString(
                schedule.enabled()
            )
        );

        printField(
            out,
            "INTERVAL",
            schedule.interval()
                .toString()
        );

        printField(
            out,
            "NEXT_RUN_AT",
            schedule.nextRunAt()
                .toString()
        );

        printField(
            out,
            "LEASED",
            Boolean.toString(
                schedule.leased()
            )
        );

        printField(
            out,
            "LEASE_OWNER",
            CliText.text(
                schedule.leaseOwner()
            )
        );

        printField(
            out,
            "LEASE_EXPIRES_AT",
            dateTime(
                schedule.leaseExpiresAt()
            )
        );

        printField(
            out,
            "LAST_SCHEDULED_FOR",
            dateTime(
                schedule.lastScheduledFor()
            )
        );

        printField(
            out,
            "LAST_PROCESSING_RUN_ID",
            longValue(
                schedule.lastProcessingRunId()
            )
        );

        printField(
            out,
            "CREATED_AT",
            schedule.createdAt()
                .toString()
        );

        printField(
            out,
            "UPDATED_AT",
            schedule.updatedAt()
                .toString()
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

    private String longValue(
        Long value
    ) {

        if (value == null) {
            return "-";
        }

        return Long.toString(
            value
        );
    }

    private boolean isHelp(
        String value
    ) {

        return "help".equals(
            value
        )
            || "--help".equals(
            value
        )
            || "-h".equals(
            value
        );
    }
}
