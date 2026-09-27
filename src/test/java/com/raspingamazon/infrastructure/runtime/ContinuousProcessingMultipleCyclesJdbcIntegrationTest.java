package com.raspingamazon.infrastructure.runtime;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.scheduling.EnsureProcessingScheduleUseCase;
import com.raspingamazon.infrastructure.bootstrap.ContinuousProcessingApplication;
import com.raspingamazon.infrastructure.composition.ContinuousProcessingComposition;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingScheduleAdapter;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class ContinuousProcessingMultipleCyclesJdbcIntegrationTest {

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    /*
     * Intervalo propositalmente curto apenas para a prova hermética.
     *
     * O contrato de produção continua aceitando a frequência
     * configurável/persistida normalmente.
     */
    private static final Duration SCHEDULE_INTERVAL =
        Duration.ofMillis(
            300
        );

    private static final Duration SCHEDULER_POLL_INTERVAL =
        Duration.ofMillis(
            25
        );

    private static final Duration SCHEDULER_LEASE_DURATION =
        Duration.ofSeconds(
            2
        );

    private static final Duration WORKER_IDLE_DELAY =
        Duration.ofMillis(
            10
        );

    private static final Duration RETRY_BASE_DELAY =
        Duration.ofMillis(
            100
        );

    private static final Duration RETRY_MAX_DELAY =
        Duration.ofSeconds(
            1
        );

    private static final Duration PROOF_TIMEOUT =
        Duration.ofSeconds(
            10
        );

    private static final int REQUIRED_COMPLETED_CYCLES =
        3;

    private static final int MAX_ATTEMPTS =
        3;

    @Test
    void shouldExecuteMultipleAutomaticCyclesWithoutManualIntervention()
        throws Exception {

        ApplicationConfig databaseConfig =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            "fase17-multiple-cycles-"
                + UUID.randomUUID();

        AtomicInteger collectionCalls =
            new AtomicInteger();

        AtomicInteger enrichmentCalls =
            new AtomicInteger();

        ContinuousProcessingApplication application =
            null;

        try {

            Clock clock =
                Clock.systemUTC();

            /*
             * -----------------------------------------------------
             * HERMETIC COLLECTION
             * -----------------------------------------------------
             *
             * A prova exercita a orquestração real, mas não depende
             * da disponibilidade externa da Amazon.
             */
            CollectionCollector collector =
                request -> {

                    collectionCalls.incrementAndGet();

                    return new CollectionResult(
                        "<html><body>empty-deals-test</body></html>",
                        OffsetDateTime.now(
                            clock
                        ),
                        request.source()
                            .toString()
                    );
                };

            /*
             * -----------------------------------------------------
             * HERMETIC PARSER
             * -----------------------------------------------------
             *
             * Zero ofertas é um resultado válido para esta prova.
             *
             * Isso permite demonstrar:
             *
             * ProcessingRun
             *      ↓
             * COLLECT_DEALS
             *      ↓
             * coleta
             *      ↓
             * parsing
             *      ↓
             * ProcessingRun COMPLETED
             *
             * sem transformar este teste em uma prova de enrichment.
             */
            DealsParser dealsParser =
                collectionResult ->
                    List.of();

            /*
             * Nenhuma oferta será parseada, então o enrichment jamais
             * deve ser chamado.
             */
            ProductEnrichmentClient enrichmentClient =
                deal -> {

                    enrichmentCalls.incrementAndGet();

                    throw new AssertionError(
                        "Enrichment must not be called "
                            + "when parser returns no deals"
                    );
                };

            /*
             * O objetivo desta prova não é validar formatação de logs.
             */
            StructuredOperationalLogPort operationalLog =
                event -> {
                };

            Connection schedulerConnection =
                DatabaseConnection.open(
                    databaseConfig
                );

            Connection workerConnection =
                null;

            try {

                workerConnection =
                    DatabaseConnection.open(
                        databaseConfig
                    );

                /*
                 * -------------------------------------------------
                 * INITIAL SCHEDULE
                 * -------------------------------------------------
                 */
                JdbcProcessingScheduleAdapter scheduleAdapter =
                    new JdbcProcessingScheduleAdapter(
                        schedulerConnection
                    );

                EnsureProcessingScheduleUseCase
                    ensureProcessingScheduleUseCase =
                    new EnsureProcessingScheduleUseCase(
                        scheduleAdapter,
                        clock
                    );

                ensureProcessingScheduleUseCase.execute(
                    scheduleKey,
                    SOURCE,
                    SCHEDULE_INTERVAL
                );

                /*
                 * -------------------------------------------------
                 * REAL CONTINUOUS COMPOSITION
                 * -------------------------------------------------
                 */
                ContinuousProcessingComposition.Settings settings =
                    new ContinuousProcessingComposition.Settings(
                        scheduleKey,
                        "scheduler-proof-"
                            + UUID.randomUUID(),
                        "worker-proof-"
                            + UUID.randomUUID(),
                        SCHEDULER_POLL_INTERVAL,
                        SCHEDULER_LEASE_DURATION,
                        WORKER_IDLE_DELAY,
                        MAX_ATTEMPTS,
                        MAX_ATTEMPTS,
                        MAX_ATTEMPTS,
                        RETRY_BASE_DELAY,
                        RETRY_MAX_DELAY
                    );

                ContinuousProcessingComposition composition =
                    new ContinuousProcessingComposition(
                        schedulerConnection,
                        workerConnection,
                        collector,
                        dealsParser,
                        enrichmentClient,
                        operationalLog,
                        clock,
                        new ThreadSleepProcessingSchedulerWaitStrategy(),
                        new ThreadSleepWorkerIdleWaitStrategy(),
                        settings
                    );

                ContinuousProcessingRuntime runtime =
                    new ContinuousProcessingRuntime(
                        composition.schedulerRunner(),
                        composition.workerRunner()
                    );

                /*
                 * ContinuousProcessingApplication assume ownership das
                 * duas Connections.
                 */
                application =
                    new ContinuousProcessingApplication(
                        runtime,
                        schedulerConnection,
                        workerConnection
                    );

                /*
                 * Ownership transferido.
                 *
                 * A partir daqui somente application.close() deve fechar
                 * as Connections.
                 */
                schedulerConnection =
                    null;

                workerConnection =
                    null;

                /*
                 * -------------------------------------------------
                 * START
                 * -------------------------------------------------
                 *
                 * Nenhuma chamada manual ao scheduler ou worker é feita
                 * depois deste ponto.
                 */
                application.start();

                awaitCompletedCycles(
                    databaseConfig,
                    scheduleKey,
                    application
                );

                /*
                 * -------------------------------------------------
                 * CONTROLLED SHUTDOWN
                 * -------------------------------------------------
                 */
                application.close();

                application =
                    null;

            } finally {

                closeConnectionBestEffort(
                    workerConnection
                );

                closeConnectionBestEffort(
                    schedulerConnection
                );
            }

            /*
             * -----------------------------------------------------
             * OPERATIONAL EVIDENCE
             * -----------------------------------------------------
             */
            long runs =
                countRuns(
                    databaseConfig,
                    scheduleKey
                );

            long distinctRuns =
                countDistinctRunKeys(
                    databaseConfig,
                    scheduleKey
                );

            long completedRuns =
                countCompletedRuns(
                    databaseConfig,
                    scheduleKey
                );

            long collectJobs =
                countCollectJobs(
                    databaseConfig,
                    scheduleKey
                );

            long succeededCollectJobs =
                countSucceededCollectJobs(
                    databaseConfig,
                    scheduleKey
                );

            assertTrue(
                completedRuns
                    >= REQUIRED_COMPLETED_CYCLES,
                "At least three automatic cycles must complete"
            );

            /*
             * Toda ProcessingRun criada pelo scheduler tem exatamente
             * um COLLECT_DEALS idempotente.
             */
            assertEquals(
                runs,
                collectJobs
            );

            /*
             * run_key deve permanecer único entre os ciclos.
             */
            assertEquals(
                runs,
                distinctRuns
            );

            /*
             * Pelo menos os ciclos cuja run terminou também tiveram
             * seu COLLECT_DEALS reconhecido como sucesso pelo worker.
             */
            assertTrue(
                succeededCollectJobs
                    >= REQUIRED_COMPLETED_CYCLES
            );

            /*
             * O collector foi chamado automaticamente pelo worker em
             * múltiplos ciclos distintos.
             */
            assertTrue(
                collectionCalls.get()
                    >= REQUIRED_COMPLETED_CYCLES
            );

            /*
             * O parser vazio impede corretamente a etapa seguinte.
             */
            assertEquals(
                0,
                enrichmentCalls.get()
            );

            /*
             * O teste terminou depois de shutdown controlado, portanto
             * não deve restar lease de scheduler ativo.
             */
            assertFalse(
                loadScheduleLeased(
                    databaseConfig,
                    scheduleKey
                )
            );

        } finally {

            if (application != null) {

                application.close();
            }

            cleanup(
                databaseConfig,
                scheduleKey
            );
        }
    }

    private void awaitCompletedCycles(
        ApplicationConfig databaseConfig,
        String scheduleKey,
        ContinuousProcessingApplication application
    ) throws Exception {

        long deadline =
            System.nanoTime()
                + PROOF_TIMEOUT.toNanos();

        while (System.nanoTime()
            < deadline) {

            long completed =
                countCompletedRuns(
                    databaseConfig,
                    scheduleKey
                );

            if (completed
                >= REQUIRED_COMPLETED_CYCLES) {

                return;
            }

            /*
             * Se o runtime parar antes da prova ser atingida, esperar
             * até o timeout apenas esconderia a falha.
             */
            if (!application.isRunning()) {

                throw new AssertionError(
                    "Continuous runtime stopped before completing "
                        + REQUIRED_COMPLETED_CYCLES
                        + " cycles"
                );
            }

            Thread.sleep(
                25
            );
        }

        throw new AssertionError(
            "Timed out waiting for "
                + REQUIRED_COMPLETED_CYCLES
                + " completed cycles"
        );
    }

    private boolean loadScheduleLeased(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            return new JdbcProcessingScheduleAdapter(
                connection
            )
                .findByKey(
                    scheduleKey
                )
                .orElseThrow()
                .leased();
        }
    }

    private long countRuns(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        return count(
            config,
            """
            SELECT COUNT(*)
            FROM processing_run
            WHERE run_key LIKE ?
            """,
            runKeyPattern(
                scheduleKey
            )
        );
    }

    private long countDistinctRunKeys(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        return count(
            config,
            """
            SELECT COUNT(DISTINCT run_key)
            FROM processing_run
            WHERE run_key LIKE ?
            """,
            runKeyPattern(
                scheduleKey
            )
        );
    }

    private long countCompletedRuns(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        return count(
            config,
            """
            SELECT COUNT(*)
            FROM processing_run
            WHERE run_key LIKE ?
              AND status = 'COMPLETED'
            """,
            runKeyPattern(
                scheduleKey
            )
        );
    }

    private long countCollectJobs(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        return count(
            config,
            """
            SELECT COUNT(*)
            FROM processing_job job
            JOIN processing_run run
              ON run.id = job.processing_run_id
            WHERE run.run_key LIKE ?
              AND job.job_type = 'COLLECT_DEALS'
            """,
            runKeyPattern(
                scheduleKey
            )
        );
    }

    private long countSucceededCollectJobs(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        return count(
            config,
            """
            SELECT COUNT(*)
            FROM processing_job job
            JOIN processing_run run
              ON run.id = job.processing_run_id
            WHERE run.run_key LIKE ?
              AND job.job_type = 'COLLECT_DEALS'
              AND job.status = 'SUCCEEDED'
            """,
            runKeyPattern(
                scheduleKey
            )
        );
    }

    private long count(
        ApplicationConfig config,
        String sql,
        String parameter
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );

             PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                parameter
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private String runKeyPattern(
        String scheduleKey
    ) {

        return "scheduled:"
            + scheduleKey
            + ":%";
    }

    private void cleanup(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        String runKeyPattern =
            runKeyPattern(
                scheduleKey
            );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            boolean originalAutoCommit =
                connection.getAutoCommit();

            connection.setAutoCommit(
                false
            );

            try {

                /*
                 * processing_schedule possui FK opcional para a última
                 * ProcessingRun, então removemos o schedule primeiro.
                 */
                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_schedule
                             WHERE schedule_key = ?
                             """
                         )) {

                    statement.setString(
                        1,
                        scheduleKey
                    );

                    statement.executeUpdate();
                }

                /*
                 * Os jobs referenciam processing_run.
                 */
                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_job job
                             USING processing_run run
                             WHERE job.processing_run_id = run.id
                               AND run.run_key LIKE ?
                             """
                         )) {

                    statement.setString(
                        1,
                        runKeyPattern
                    );

                    statement.executeUpdate();
                }

                /*
                 * O parser hermético retorna zero ofertas. Portanto não
                 * existem DealCandidates ou objetos posteriores
                 * pertencentes a estas runs.
                 */
                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_run
                             WHERE run_key LIKE ?
                             """
                         )) {

                    statement.setString(
                        1,
                        runKeyPattern
                    );

                    statement.executeUpdate();
                }

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();

                throw exception;

            } finally {

                connection.setAutoCommit(
                    originalAutoCommit
                );
            }
        }
    }

    private void closeConnectionBestEffort(
        Connection connection
    ) {

        if (connection == null) {
            return;
        }

        try {

            connection.close();

        } catch (Exception ignored) {

            /*
             * Utilizado somente na proteção de montagem do teste.
             */
        }
    }
}
