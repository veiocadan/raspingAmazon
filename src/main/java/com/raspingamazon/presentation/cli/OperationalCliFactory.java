package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.GetDealEvaluationDetailUseCase;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.application.operation.observability.alert.GetOperationalAlertsUseCase;
import com.raspingamazon.application.operation.orchestration.job.ListProcessingJobsUseCase;
import com.raspingamazon.application.operation.orchestration.run.GetProcessingRunDetailUseCase;
import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.publication.GetPublicationDetailUseCase;
import com.raspingamazon.application.operation.publication.ListPublicationsUseCase;
import com.raspingamazon.application.scheduling.ChangeProcessingScheduleIntervalUseCase;
import com.raspingamazon.application.scheduling.GetProcessingScheduleUseCase;
import com.raspingamazon.application.scheduling.PauseProcessingScheduleUseCase;
import com.raspingamazon.application.scheduling.ResumeProcessingScheduleUseCase;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory da interface operacional.
 *
 * <p>Conecta os casos de uso da camada de aplicação aos handlers da
 * camada de apresentação.</p>
 *
 * <p>Esta classe não conhece JDBC, Connection, adapters concretos ou
 * configuração de infraestrutura.</p>
 */
public final class OperationalCliFactory {

    private OperationalCliFactory() {
    }

    /**
     * Assinatura histórica preservada.
     *
     * <p>Nesta variante o recurso schedules não é registrado. Isso
     * preserva consumidores e testes anteriores que compõem apenas a
     * interface observacional histórica.</p>
     */
    public static OperationalCli create(
        ListDealEvaluationsUseCase listDealEvaluationsUseCase,
        GetDealEvaluationDetailUseCase getDealEvaluationDetailUseCase,
        ListProcessingRunsUseCase listProcessingRunsUseCase,
        GetProcessingRunDetailUseCase getProcessingRunDetailUseCase,
        ListProcessingJobsUseCase listProcessingJobsUseCase,
        ListPublicationsUseCase listPublicationsUseCase,
        GetPublicationDetailUseCase getPublicationDetailUseCase,
        GetOperationalAlertsUseCase getOperationalAlertsUseCase,
        PrintWriter out,
        PrintWriter err
    ) {

        Map<String, CliCommandHandler> handlers =
            createBaseHandlers(
                listDealEvaluationsUseCase,
                getDealEvaluationDetailUseCase,
                listProcessingRunsUseCase,
                getProcessingRunDetailUseCase,
                listProcessingJobsUseCase,
                listPublicationsUseCase,
                getPublicationDetailUseCase,
                getOperationalAlertsUseCase
            );

        return new OperationalCli(
            handlers,
            requireWriter(
                out,
                "out"
            ),
            requireWriter(
                err,
                "err"
            )
        );
    }

    /**
     * Composição completa da interface operacional da FASE 17.
     */
    public static OperationalCli create(
        ListDealEvaluationsUseCase listDealEvaluationsUseCase,
        GetDealEvaluationDetailUseCase getDealEvaluationDetailUseCase,
        ListProcessingRunsUseCase listProcessingRunsUseCase,
        GetProcessingRunDetailUseCase getProcessingRunDetailUseCase,
        ListProcessingJobsUseCase listProcessingJobsUseCase,
        ListPublicationsUseCase listPublicationsUseCase,
        GetPublicationDetailUseCase getPublicationDetailUseCase,
        GetOperationalAlertsUseCase getOperationalAlertsUseCase,
        GetProcessingScheduleUseCase getProcessingScheduleUseCase,
        PauseProcessingScheduleUseCase pauseProcessingScheduleUseCase,
        ResumeProcessingScheduleUseCase resumeProcessingScheduleUseCase,
        ChangeProcessingScheduleIntervalUseCase
            changeProcessingScheduleIntervalUseCase,
        PrintWriter out,
        PrintWriter err
    ) {

        Map<String, CliCommandHandler> handlers =
            createBaseHandlers(
                listDealEvaluationsUseCase,
                getDealEvaluationDetailUseCase,
                listProcessingRunsUseCase,
                getProcessingRunDetailUseCase,
                listProcessingJobsUseCase,
                listPublicationsUseCase,
                getPublicationDetailUseCase,
                getOperationalAlertsUseCase
            );

        handlers.put(
            "schedules",
            new SchedulesCliCommand(
                Objects.requireNonNull(
                    getProcessingScheduleUseCase,
                    "getProcessingScheduleUseCase must not be null"
                ),
                Objects.requireNonNull(
                    pauseProcessingScheduleUseCase,
                    "pauseProcessingScheduleUseCase must not be null"
                ),
                Objects.requireNonNull(
                    resumeProcessingScheduleUseCase,
                    "resumeProcessingScheduleUseCase must not be null"
                ),
                Objects.requireNonNull(
                    changeProcessingScheduleIntervalUseCase,
                    "changeProcessingScheduleIntervalUseCase must not be null"
                )
            )
        );

        return new OperationalCli(
            handlers,
            requireWriter(
                out,
                "out"
            ),
            requireWriter(
                err,
                "err"
            )
        );
    }

    private static Map<String, CliCommandHandler> createBaseHandlers(
        ListDealEvaluationsUseCase listDealEvaluationsUseCase,
        GetDealEvaluationDetailUseCase getDealEvaluationDetailUseCase,
        ListProcessingRunsUseCase listProcessingRunsUseCase,
        GetProcessingRunDetailUseCase getProcessingRunDetailUseCase,
        ListProcessingJobsUseCase listProcessingJobsUseCase,
        ListPublicationsUseCase listPublicationsUseCase,
        GetPublicationDetailUseCase getPublicationDetailUseCase,
        GetOperationalAlertsUseCase getOperationalAlertsUseCase
    ) {

        Objects.requireNonNull(
            listDealEvaluationsUseCase,
            "listDealEvaluationsUseCase must not be null"
        );

        Objects.requireNonNull(
            getDealEvaluationDetailUseCase,
            "getDealEvaluationDetailUseCase must not be null"
        );

        Objects.requireNonNull(
            listProcessingRunsUseCase,
            "listProcessingRunsUseCase must not be null"
        );

        Objects.requireNonNull(
            getProcessingRunDetailUseCase,
            "getProcessingRunDetailUseCase must not be null"
        );

        Objects.requireNonNull(
            listProcessingJobsUseCase,
            "listProcessingJobsUseCase must not be null"
        );

        Objects.requireNonNull(
            listPublicationsUseCase,
            "listPublicationsUseCase must not be null"
        );

        Objects.requireNonNull(
            getPublicationDetailUseCase,
            "getPublicationDetailUseCase must not be null"
        );

        Objects.requireNonNull(
            getOperationalAlertsUseCase,
            "getOperationalAlertsUseCase must not be null"
        );

        Map<String, CliCommandHandler> handlers =
            new LinkedHashMap<>();

        handlers.put(
            "evaluations",
            new EvaluationsCliCommand(
                listDealEvaluationsUseCase,
                getDealEvaluationDetailUseCase
            )
        );

        handlers.put(
            "runs",
            new RunsCliCommand(
                listProcessingRunsUseCase,
                getProcessingRunDetailUseCase
            )
        );

        handlers.put(
            "jobs",
            new JobsCliCommand(
                listProcessingJobsUseCase
            )
        );

        handlers.put(
            "publications",
            new PublicationsCliCommand(
                listPublicationsUseCase,
                getPublicationDetailUseCase
            )
        );

        handlers.put(
            "alerts",
            new AlertsCliCommand(
                getOperationalAlertsUseCase
            )
        );

        return handlers;
    }

    private static PrintWriter requireWriter(
        PrintWriter writer,
        String name
    ) {

        return Objects.requireNonNull(
            writer,
            name + " must not be null"
        );
    }
}
