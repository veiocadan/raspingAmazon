package com.raspingamazon.infrastructure.composition;

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
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.config.EnvironmentOperationalAlertPolicyProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcDealEvaluationOperationalDetailQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcDealEvaluationOperationalQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcOperationalAlertQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobOperationalQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingRunOperationalDetailQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingRunOperationalQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingScheduleAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOperationalDetailQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOperationalQueryAdapter;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;

/**
 * Composition root da interface operacional.
 *
 * <p>Esta composição pertence à infraestrutura e conecta os casos
 * de uso operacionais aos adapters JDBC concretos.</p>
 *
 * <p>A apresentação recebe somente casos de uso. Ela não precisa
 * conhecer SQL, JDBC, repositories concretos ou construção de
 * dependências.</p>
 *
 * <p>A composição é dona da Connection utilizada pelos adapters e
 * deve ser fechada ao final da execução do comando operacional.</p>
 */
public final class OperationalInterfaceComposition
    implements AutoCloseable {

    private final Connection connection;

    private final ListDealEvaluationsUseCase
        listDealEvaluationsUseCase;

    private final GetDealEvaluationDetailUseCase
        getDealEvaluationDetailUseCase;

    private final ListProcessingRunsUseCase
        listProcessingRunsUseCase;

    private final GetProcessingRunDetailUseCase
        getProcessingRunDetailUseCase;

    private final ListProcessingJobsUseCase
        listProcessingJobsUseCase;

    private final ListPublicationsUseCase
        listPublicationsUseCase;

    private final GetPublicationDetailUseCase
        getPublicationDetailUseCase;

    private final GetOperationalAlertsUseCase
        getOperationalAlertsUseCase;

    private final GetProcessingScheduleUseCase
        getProcessingScheduleUseCase;

    private final PauseProcessingScheduleUseCase
        pauseProcessingScheduleUseCase;

    private final ResumeProcessingScheduleUseCase
        resumeProcessingScheduleUseCase;

    private final ChangeProcessingScheduleIntervalUseCase
        changeProcessingScheduleIntervalUseCase;

    private boolean closed;

    private OperationalInterfaceComposition(
        Connection connection,
        Clock clock
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        Clock validatedClock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        /*
         * ---------------------------------------------------------
         * DEAL EVALUATION
         * ---------------------------------------------------------
         */

        JdbcDealEvaluationOperationalQueryAdapter
            dealEvaluationQueryAdapter =
            new JdbcDealEvaluationOperationalQueryAdapter(
                connection
            );

        JdbcDealEvaluationOperationalDetailQueryAdapter
            dealEvaluationDetailQueryAdapter =
            new JdbcDealEvaluationOperationalDetailQueryAdapter(
                connection
            );

        this.listDealEvaluationsUseCase =
            new ListDealEvaluationsUseCase(
                dealEvaluationQueryAdapter
            );

        this.getDealEvaluationDetailUseCase =
            new GetDealEvaluationDetailUseCase(
                dealEvaluationDetailQueryAdapter
            );

        /*
         * ---------------------------------------------------------
         * PROCESSING RUN
         * ---------------------------------------------------------
         */

        JdbcProcessingRunOperationalQueryAdapter
            processingRunQueryAdapter =
            new JdbcProcessingRunOperationalQueryAdapter(
                connection
            );

        JdbcProcessingRunOperationalDetailQueryAdapter
            processingRunDetailQueryAdapter =
            new JdbcProcessingRunOperationalDetailQueryAdapter(
                connection
            );

        this.listProcessingRunsUseCase =
            new ListProcessingRunsUseCase(
                processingRunQueryAdapter
            );

        this.getProcessingRunDetailUseCase =
            new GetProcessingRunDetailUseCase(
                processingRunDetailQueryAdapter
            );

        /*
         * ---------------------------------------------------------
         * PROCESSING JOB
         * ---------------------------------------------------------
         */

        JdbcProcessingJobOperationalQueryAdapter
            processingJobQueryAdapter =
            new JdbcProcessingJobOperationalQueryAdapter(
                connection
            );

        this.listProcessingJobsUseCase =
            new ListProcessingJobsUseCase(
                processingJobQueryAdapter
            );

        /*
         * ---------------------------------------------------------
         * PUBLICATION
         * ---------------------------------------------------------
         */

        JdbcPublicationOperationalQueryAdapter
            publicationQueryAdapter =
            new JdbcPublicationOperationalQueryAdapter(
                connection
            );

        JdbcPublicationOperationalDetailQueryAdapter
            publicationDetailQueryAdapter =
            new JdbcPublicationOperationalDetailQueryAdapter(
                connection
            );

        this.listPublicationsUseCase =
            new ListPublicationsUseCase(
                publicationQueryAdapter
            );

        this.getPublicationDetailUseCase =
            new GetPublicationDetailUseCase(
                publicationDetailQueryAdapter
            );

        /*
         * ---------------------------------------------------------
         * OPERATIONAL ALERTS
         * ---------------------------------------------------------
         */

        JdbcOperationalAlertQueryAdapter
            operationalAlertQueryAdapter =
            new JdbcOperationalAlertQueryAdapter(
                connection
            );

        EnvironmentOperationalAlertPolicyProvider
            operationalAlertPolicyProvider =
            new EnvironmentOperationalAlertPolicyProvider();

        this.getOperationalAlertsUseCase =
            new GetOperationalAlertsUseCase(
                operationalAlertQueryAdapter,
                operationalAlertPolicyProvider,
                validatedClock
            );

        /*
         * ---------------------------------------------------------
         * PROCESSING SCHEDULE
         * ---------------------------------------------------------
         *
         * O mesmo adapter fornece leitura e mutações operacionais.
         *
         * Nenhuma regra de scheduling é implementada nesta
         * composition root.
         */

        JdbcProcessingScheduleAdapter
            processingScheduleAdapter =
            new JdbcProcessingScheduleAdapter(
                connection
            );

        this.getProcessingScheduleUseCase =
            new GetProcessingScheduleUseCase(
                processingScheduleAdapter
            );

        this.pauseProcessingScheduleUseCase =
            new PauseProcessingScheduleUseCase(
                processingScheduleAdapter,
                validatedClock
            );

        this.resumeProcessingScheduleUseCase =
            new ResumeProcessingScheduleUseCase(
                processingScheduleAdapter,
                validatedClock
            );

        this.changeProcessingScheduleIntervalUseCase =
            new ChangeProcessingScheduleIntervalUseCase(
                processingScheduleAdapter,
                validatedClock
            );
    }

    /**
     * Abre a composição utilizando a configuração padrão proveniente
     * do ambiente.
     */
    public static OperationalInterfaceComposition open() {

        return open(
            EnvironmentConfigProvider.load()
        );
    }

    /**
     * Abre a composição utilizando configuração explicitamente
     * fornecida.
     */
    public static OperationalInterfaceComposition open(
        ApplicationConfig config
    ) {

        Objects.requireNonNull(
            config,
            "config must not be null"
        );

        try {

            Connection connection =
                DatabaseConnection.open(
                    config
                );

            return fromOwnedConnection(
                connection
            );

        } catch (SQLException exception) {

            throw new OperationalInterfaceCompositionException(
                "Could not open operational database connection",
                exception
            );
        }
    }

    /**
     * Variante package-private utilizada pelos testes estruturais.
     */
    static OperationalInterfaceComposition fromOwnedConnection(
        Connection connection
    ) {

        return fromOwnedConnection(
            connection,
            Clock.systemUTC()
        );
    }

    /**
     * Variante package-private com Clock explícito.
     *
     * <p>Permite provar comandos temporais sem depender do relógio
     * civil da máquina que executa a suíte.</p>
     */
    static OperationalInterfaceComposition fromOwnedConnection(
        Connection connection,
        Clock clock
    ) {

        return new OperationalInterfaceComposition(
            connection,
            clock
        );
    }

    public ListDealEvaluationsUseCase listDealEvaluations() {

        return listDealEvaluationsUseCase;
    }

    public GetDealEvaluationDetailUseCase getDealEvaluationDetail() {

        return getDealEvaluationDetailUseCase;
    }

    public ListProcessingRunsUseCase listProcessingRuns() {

        return listProcessingRunsUseCase;
    }

    public GetProcessingRunDetailUseCase getProcessingRunDetail() {

        return getProcessingRunDetailUseCase;
    }

    public ListProcessingJobsUseCase listProcessingJobs() {

        return listProcessingJobsUseCase;
    }

    public ListPublicationsUseCase listPublications() {

        return listPublicationsUseCase;
    }

    public GetPublicationDetailUseCase getPublicationDetail() {

        return getPublicationDetailUseCase;
    }

    public GetOperationalAlertsUseCase getOperationalAlerts() {

        return getOperationalAlertsUseCase;
    }

    public GetProcessingScheduleUseCase getProcessingSchedule() {

        return getProcessingScheduleUseCase;
    }

    public PauseProcessingScheduleUseCase pauseProcessingSchedule() {

        return pauseProcessingScheduleUseCase;
    }

    public ResumeProcessingScheduleUseCase resumeProcessingSchedule() {

        return resumeProcessingScheduleUseCase;
    }

    public ChangeProcessingScheduleIntervalUseCase
    changeProcessingScheduleInterval() {

        return changeProcessingScheduleIntervalUseCase;
    }

    @Override
    public void close() {

        if (closed) {
            return;
        }

        try {

            connection.close();

            closed =
                true;

        } catch (SQLException exception) {

            throw new OperationalInterfaceCompositionException(
                "Could not close operational database connection",
                exception
            );
        }
    }
}
