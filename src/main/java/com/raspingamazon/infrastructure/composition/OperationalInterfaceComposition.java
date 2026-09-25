package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.operation.evaluation.GetDealEvaluationDetailUseCase;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.application.operation.orchestration.job.ListProcessingJobsUseCase;
import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.publication.GetPublicationDetailUseCase;
import com.raspingamazon.application.operation.publication.ListPublicationsUseCase;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcDealEvaluationOperationalDetailQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcDealEvaluationOperationalQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobOperationalQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingRunOperationalQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOperationalDetailQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOperationalQueryAdapter;

import java.sql.Connection;
import java.sql.SQLException;
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
 *
 * <p>Nenhuma regra comercial, decisão de publicação, retry,
 * scheduler ou transição de estado é implementada aqui.</p>
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

    private final ListProcessingJobsUseCase
        listProcessingJobsUseCase;

    private final ListPublicationsUseCase
        listPublicationsUseCase;

    private final GetPublicationDetailUseCase
        getPublicationDetailUseCase;

    private boolean closed;

    private OperationalInterfaceComposition(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
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

        this.listProcessingRunsUseCase =
            new ListProcessingRunsUseCase(
                processingRunQueryAdapter
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
    }

    /**
     * Abre a composição utilizando a configuração padrão
     * proveniente do ambiente.
     */
    public static OperationalInterfaceComposition open() {

        return open(
            EnvironmentConfigProvider.load()
        );
    }

    /**
     * Abre a composição utilizando configuração explicitamente
     * fornecida.
     *
     * <p>A Connection criada pertence à composição e será encerrada
     * por close().</p>
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
     * Variante package-private destinada a testes estruturais
     * e outros composition roots do mesmo pacote.
     *
     * <p>A Connection recebida passa a pertencer à composição.</p>
     */
    static OperationalInterfaceComposition fromOwnedConnection(
        Connection connection
    ) {

        return new OperationalInterfaceComposition(
            connection
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

    public ListProcessingJobsUseCase listProcessingJobs() {

        return listProcessingJobsUseCase;
    }

    public ListPublicationsUseCase listPublications() {

        return listPublicationsUseCase;
    }

    public GetPublicationDetailUseCase getPublicationDetail() {

        return getPublicationDetailUseCase;
    }

    /**
     * Encerra a Connection pertencente a esta composição.
     *
     * <p>Chamadas repetidas são seguras.</p>
     */
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
