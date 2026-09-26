package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.GetDealEvaluationDetailUseCase;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.application.operation.observability.alert.GetOperationalAlertsUseCase;
import com.raspingamazon.application.operation.orchestration.job.ListProcessingJobsUseCase;
import com.raspingamazon.application.operation.orchestration.run.GetProcessingRunDetailUseCase;
import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.publication.GetPublicationDetailUseCase;
import com.raspingamazon.application.operation.publication.ListPublicationsUseCase;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory da interface operacional.
 *
 * <p>Conecta os casos de uso da camada de aplicacao aos handlers
 * da camada de apresentacao.</p>
 *
 * <p>Esta classe nao conhece JDBC, Connection, adapters concretos
 * ou configuracao de infraestrutura.</p>
 */
public final class OperationalCliFactory {

    private OperationalCliFactory() {
    }

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

        Objects.requireNonNull(
            out,
            "out must not be null"
        );

        Objects.requireNonNull(
            err,
            "err must not be null"
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

        return new OperationalCli(
            handlers,
            out,
            err
        );
    }
}
