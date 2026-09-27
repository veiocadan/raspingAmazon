package com.raspingamazon.infrastructure.observability;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.observability.BestEffortIntegrationObservationRecorder;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.observability.port.IntegrationObservationPersistencePort;
import com.raspingamazon.application.observability.port.IntegrationObservationRecorder;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.operation.orchestration.run.GetProcessingRunDetailUseCase;
import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunDetail;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunIntegrationMetrics;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.failure.DefaultProcessingFailureClassifier;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;
import com.raspingamazon.infrastructure.collection.HttpCollectionCollector;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.http.JavaHttpTransport;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcIntegrationObservationPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingRunOperationalDetailQueryAdapter;
import com.raspingamazon.presentation.cli.CliExitCode;
import com.raspingamazon.presentation.cli.RunsCliCommand;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prova real da observabilidade operacional introduzida na FASE 16.
 *
 * <p>Este teste é deliberadamente uma probe externa e, por terminar em
 * {@code IT}, não pertence à descoberta padrão da suíte hermética do
 * Surefire. Ele deve ser executado explicitamente.</p>
 *
 * <p>A prova usa:</p>
 *
 * <ol>
 *     <li>a fonte real {@code https://www.amazon.com.br/deals};</li>
 *     <li>o transporte HTTP real da aplicação;</li>
 *     <li>o recorder e o adapter JDBC reais da observabilidade;</li>
 *     <li>o PostgreSQL real configurado para os testes;</li>
 *     <li>o read model operacional real de ProcessingRun.</li>
 * </ol>
 *
 * <p>Uma segunda conexão JDBC lê a observação depois da coleta. Dessa
 * forma, a prova não depende apenas da visibilidade da mesma transação:
 * a observação precisa ter sido efetivamente persistida antes de ser
 * agregada por run.</p>
 *
 * <p>Ao final, os dados diagnósticos inseridos pela probe são removidos.
 * O relatório textual em {@code target/diagnostics} permanece como
 * evidência local da execução.</p>
 */
@PostgresIntegrationTest
class Phase16ObservabilityRealSourceIT {

    private static final URI DEALS_SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final String INTEGRATION =
        "amazon-deals-http";

    private static final String OPERATION =
        "GET";

    private static final int MAX_ATTEMPTS =
        3;

    private static final Duration REQUEST_TIMEOUT =
        Duration.ofSeconds(
            30
        );

    private static final Duration FIRST_RETRY_DELAY =
        Duration.ofSeconds(
            2
        );

    private static final Duration SECOND_RETRY_DELAY =
        Duration.ofSeconds(
            5
        );

    private static final Path DIAGNOSTIC_FILE =
        Path.of(
            "target",
            "diagnostics",
            "phase16-observability-real-source.txt"
        );

    private final ProcessingFailureClassifier failureClassifier =
        new DefaultProcessingFailureClassifier();

    @Test
    void shouldPersistAndReadRealAmazonCollectionObservabilityByRun()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Clock clock =
            Clock.systemUTC();

        long runId =
            -1L;

        try (Connection writeConnection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertRunningProbeRun(
                    writeConnection,
                    clock
                );

            HttpCollectionCollector collector =
                createObservedCollector(
                    writeConnection,
                    clock
                );

            CollectionResult result =
                collectRealSourceWithRetry(
                    collector,
                    runId
                );

            assertNotNull(
                result,
                "Real Amazon collection result must not be null"
            );

            assertNotNull(
                result.content(),
                "Real Amazon collection content must not be null"
            );

            assertFalse(
                result.content().isBlank(),
                "Real Amazon collection content must not be blank"
            );

            markRunCompleted(
                writeConnection,
                runId,
                clock
            );

            /*
             * A leitura usa outra conexão de propósito.
             *
             * Se a observação estivesse somente visível na conexão de
             * escrita, esta parte da prova falharia.
             */
            try (Connection readConnection =
                     DatabaseConnection.open(
                         config
                     )) {

                ProcessingRunDetail detail =
                    loadRunDetail(
                        readConnection,
                        runId
                    );

                ProcessingRunIntegrationMetrics metrics =
                    findCollectionMetrics(
                        detail
                    );

                SuccessfulObservationEvidence evidence =
                    loadLatestSuccessfulObservation(
                        readConnection,
                        runId
                    );

                assertOperationalEvidence(
                    runId,
                    metrics,
                    evidence
                );

                String runsShowOutput =
                    renderRunsShow(
                        readConnection,
                        runId
                    );

                writeSuccessDiagnostic(
                    runId,
                    result,
                    detail,
                    metrics,
                    evidence,
                    runsShowOutput
                );

                printSuccess(
                    runId,
                    result,
                    metrics,
                    evidence,
                    runsShowOutput
                );
            }

        } finally {

            if (runId > 0L) {

                cleanupProbeDataBestEffort(
                    config,
                    runId
                );
            }
        }
    }

    private HttpCollectionCollector createObservedCollector(
        Connection connection,
        Clock clock
    ) {

        HttpClient httpClient =
            HttpClient.newBuilder()
                .connectTimeout(
                    REQUEST_TIMEOUT
                )
                .followRedirects(
                    HttpClient.Redirect.NORMAL
                )
                .build();

        JavaHttpTransport httpTransport =
            new JavaHttpTransport(
                httpClient,
                REQUEST_TIMEOUT
            );

        IntegrationObservationPersistencePort persistencePort =
            new JdbcIntegrationObservationPersistenceAdapter(
                connection
            );

        StructuredOperationalLogPort operationalLog =
            new JsonStructuredOperationalLogAdapter(
                new PrintWriter(
                    System.err,
                    true,
                    StandardCharsets.UTF_8
                ),
                clock
            );

        IntegrationObservationRecorder recorder =
            new BestEffortIntegrationObservationRecorder(
                persistencePort,
                failureClassifier,
                operationalLog
            );

        return new HttpCollectionCollector(
            httpTransport,
            clock,
            INTEGRATION,
            recorder,
            failureClassifier
        );
    }

    private CollectionResult collectRealSourceWithRetry(
        HttpCollectionCollector collector,
        long runId
    ) throws Exception {

        CollectionRequest request =
            new CollectionRequest(
                DEALS_SOURCE
            );

        OperationalLogContext context =
            new OperationalLogContext(
                runId,
                null,
                ProcessingJobType.COLLECT_DEALS,
                null,
                null,
                null,
                null,
                null,
                null
            );

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            try {

                return collector.collect(
                    request,
                    context
                );

            } catch (CollectionException exception) {

                FailureClassification classification =
                    failureClassifier.classify(
                        exception
                    );

                if (!classification.retryable()) {

                    printFailure(
                        attempt,
                        classification,
                        exception
                    );

                    throw exception;
                }

                if (attempt >= MAX_ATTEMPTS) {

                    printInconclusive(
                        attempt,
                        classification,
                        exception
                    );

                    assumeTrue(
                        false,
                        "Phase 16 real observability probe inconclusive "
                            + "after "
                            + MAX_ATTEMPTS
                            + " transient Amazon failures: "
                            + classification.code()
                    );

                    return null;
                }

                Duration retryDelay =
                    retryDelayForAttempt(
                        attempt
                    );

                printRetry(
                    attempt,
                    classification,
                    exception,
                    retryDelay
                );

                Thread.sleep(
                    retryDelay.toMillis()
                );
            }
        }

        throw new IllegalStateException(
            "Real source collection retry loop ended unexpectedly"
        );
    }

    private Duration retryDelayForAttempt(
        int completedAttempt
    ) {

        if (completedAttempt == 1) {
            return FIRST_RETRY_DELAY;
        }

        return SECOND_RETRY_DELAY;
    }

    private ProcessingRunDetail loadRunDetail(
        Connection connection,
        long runId
    ) {

        JdbcProcessingRunOperationalDetailQueryAdapter queryAdapter =
            new JdbcProcessingRunOperationalDetailQueryAdapter(
                connection
            );

        Optional<ProcessingRunDetail> detail =
            queryAdapter.findById(
                runId
            );

        assertTrue(
            detail.isPresent(),
            "ProcessingRun operational detail must exist"
        );

        return detail.orElseThrow();
    }


    private String renderRunsShow(
        Connection connection,
        long runId
    ) {

        JdbcProcessingRunOperationalDetailQueryAdapter detailAdapter =
            new JdbcProcessingRunOperationalDetailQueryAdapter(
                connection
            );

        RunsCliCommand command =
            new RunsCliCommand(
                new ListProcessingRunsUseCase(
                    criteria ->
                        new ProcessingRunPage(
                            List.of(),
                            null
                        )
                ),
                new GetProcessingRunDetailUseCase(
                    detailAdapter
                )
            );

        ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        PrintWriter out =
            new PrintWriter(
                stdout,
                true,
                StandardCharsets.UTF_8
            );

        PrintWriter err =
            new PrintWriter(
                stderr,
                true,
                StandardCharsets.UTF_8
            );

        CliExitCode exitCode =
            command.execute(
                List.of(
                    "show",
                    Long.toString(
                        runId
                    )
                ),
                out,
                err
            );

        out.flush();
        err.flush();

        String output =
            stdout.toString(
                StandardCharsets.UTF_8
            );

        String errorOutput =
            stderr.toString(
                StandardCharsets.UTF_8
            );

        assertEquals(
            CliExitCode.SUCCESS,
            exitCode,
            "runs show must complete successfully for the probe run"
        );

        assertTrue(
            errorOutput.isEmpty(),
            "runs show must not write errors for the probe run: "
                + errorOutput
        );

        assertTrue(
            output.contains(
                "INTEGRATIONS"
            ),
            "runs show must expose the INTEGRATIONS section"
        );

        assertTrue(
            output.contains(
                INTEGRATION
            ),
            "runs show must expose the observed integration"
        );

        assertTrue(
            output.contains(
                Long.toString(
                    runId
                )
            ),
            "runs show must expose the correlated run id"
        );

        return output;
    }

    private ProcessingRunIntegrationMetrics findCollectionMetrics(
        ProcessingRunDetail detail
    ) {

        return detail.integrations()
            .stream()
            .filter(
                metrics -> INTEGRATION.equals(
                    metrics.integration()
                )
            )
            .findFirst()
            .orElseThrow(
                () -> new AssertionError(
                    "Run detail must expose "
                        + INTEGRATION
                )
            );
    }

    private void assertOperationalEvidence(
        long runId,
        ProcessingRunIntegrationMetrics metrics,
        SuccessfulObservationEvidence evidence
    ) {

        assertEquals(
            INTEGRATION,
            metrics.integration()
        );

        assertTrue(
            metrics.observations() >= 1L,
            "At least one integration observation must be aggregated"
        );

        assertEquals(
            1L,
            metrics.successes(),
            "The retry loop stops after the first successful collection"
        );

        assertEquals(
            metrics.observations(),
            metrics.successes()
                + metrics.failures()
        );

        assertEquals(
            metrics.failures(),
            metrics.externalFailures()
                + metrics.internalFailures()
        );

        assertTrue(
            metrics.averageDurationMs().signum() >= 0,
            "Average integration duration must not be negative"
        );

        assertTrue(
            metrics.maximumDurationMs() >= 0L,
            "Maximum integration duration must not be negative"
        );

        assertEquals(
            runId,
            evidence.runId()
        );

        assertEquals(
            ProcessingJobType.COLLECT_DEALS.name(),
            evidence.jobType()
        );

        assertEquals(
            INTEGRATION,
            evidence.integration()
        );

        assertEquals(
            OPERATION,
            evidence.operation()
        );

        assertEquals(
            "SUCCESS",
            evidence.outcome()
        );

        assertTrue(
            evidence.durationMs() >= 0L,
            "Persisted integration duration must not be negative"
        );

        assertNotNull(
            evidence.httpStatusCode(),
            "Successful HTTP observation must preserve status code"
        );

        assertTrue(
            evidence.httpStatusCode() >= 200
                && evidence.httpStatusCode() < 300,
            "Successful HTTP observation must have a 2xx status code"
        );
    }

    private long insertRunningProbeRun(
        Connection connection,
        Clock clock
    ) throws Exception {

        OffsetDateTime now =
            OffsetDateTime.now(
                clock
            );

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at,
                started_at,
                completed_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                "phase16-real-observability-"
                    + UUID.randomUUID()
            );

            statement.setString(
                2,
                DEALS_SOURCE.toString()
            );

            statement.setString(
                3,
                ProcessingRunStatus.RUNNING.name()
            );

            statement.setObject(
                4,
                now
            );

            statement.setObject(
                5,
                now
            );

            statement.setObject(
                6,
                now
            );

            statement.setObject(
                7,
                now
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next(),
                    "Probe ProcessingRun insert must return an id"
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void markRunCompleted(
        Connection connection,
        long runId,
        Clock clock
    ) throws Exception {

        OffsetDateTime completedAt =
            OffsetDateTime.now(
                clock
            );

        String sql =
            """
            UPDATE processing_run
               SET status = ?,
                   completed_at = ?,
                   updated_at = ?
             WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                ProcessingRunStatus.COMPLETED.name()
            );

            statement.setObject(
                2,
                completedAt
            );

            statement.setObject(
                3,
                completedAt
            );

            statement.setLong(
                4,
                runId
            );

            assertEquals(
                1,
                statement.executeUpdate(),
                "Probe ProcessingRun must be completed exactly once"
            );
        }
    }

    private SuccessfulObservationEvidence
        loadLatestSuccessfulObservation(
            Connection connection,
            long runId
        ) throws Exception {

        String sql =
            """
            SELECT
                processing_run_id,
                job_type,
                integration,
                operation,
                outcome,
                duration_ms,
                http_status_code
            FROM integration_observation
            WHERE processing_run_id = ?
              AND integration = ?
              AND outcome = 'SUCCESS'
            ORDER BY id DESC
            LIMIT 1
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                runId
            );

            statement.setString(
                2,
                INTEGRATION
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next(),
                    "A committed successful integration observation "
                        + "must be visible from another connection"
                );

                return new SuccessfulObservationEvidence(
                    resultSet.getLong(
                        "processing_run_id"
                    ),
                    resultSet.getString(
                        "job_type"
                    ),
                    resultSet.getString(
                        "integration"
                    ),
                    resultSet.getString(
                        "operation"
                    ),
                    resultSet.getString(
                        "outcome"
                    ),
                    resultSet.getLong(
                        "duration_ms"
                    ),
                    (Integer) resultSet.getObject(
                        "http_status_code"
                    )
                );
            }
        }
    }

    private void writeSuccessDiagnostic(
        long runId,
        CollectionResult result,
        ProcessingRunDetail detail,
        ProcessingRunIntegrationMetrics metrics,
        SuccessfulObservationEvidence evidence,
        String runsShowOutput
    ) throws Exception {

        Files.createDirectories(
            DIAGNOSTIC_FILE.getParent()
        );

        String report =
            """
            PHASE 16 REAL OBSERVABILITY PROBE
            =================================
            Status: SUCCESS
            Run id: %s
            Run status: %s
            Source: %s
            Collected at: %s
            Content length: %s

            Integration: %s
            Observations: %s
            Successes: %s
            Failures: %s
            External failures: %s
            Internal failures: %s
            Average duration ms: %s
            Maximum duration ms: %s

            Latest successful observation
            -----------------------------
            Job type: %s
            Operation: %s
            Outcome: %s
            HTTP status: %s
            Duration ms: %s

            Cross-connection persistence visibility: true
            runs show integration visibility: true

            runs show output
            ----------------
            %s

            Diagnostic database rows are cleaned after the probe.
            """.formatted(
                runId,
                detail.summary().status(),
                result.source(),
                result.collectedAt(),
                result.content().length(),
                metrics.integration(),
                metrics.observations(),
                metrics.successes(),
                metrics.failures(),
                metrics.externalFailures(),
                metrics.internalFailures(),
                metrics.averageDurationMs().toPlainString(),
                metrics.maximumDurationMs(),
                evidence.jobType(),
                evidence.operation(),
                evidence.outcome(),
                evidence.httpStatusCode(),
                evidence.durationMs(),
                runsShowOutput
            );

        Files.writeString(
            DIAGNOSTIC_FILE,
            report,
            StandardCharsets.UTF_8
        );
    }

    private void cleanupProbeDataBestEffort(
        ApplicationConfig config,
        long runId
    ) {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            deleteObservations(
                connection,
                runId
            );

            deleteRun(
                connection,
                runId
            );

        } catch (Exception cleanupFailure) {

            System.err.println(
                "Phase 16 real observability probe cleanup failed for run "
                    + runId
                    + ": "
                    + cleanupFailure.getMessage()
            );
        }
    }

    private void deleteObservations(
        Connection connection,
        long runId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     "DELETE FROM integration_observation "
                         + "WHERE processing_run_id = ?"
                 )) {

            statement.setLong(
                1,
                runId
            );

            statement.executeUpdate();
        }
    }

    private void deleteRun(
        Connection connection,
        long runId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     "DELETE FROM processing_run WHERE id = ?"
                 )) {

            statement.setLong(
                1,
                runId
            );

            statement.executeUpdate();
        }
    }

    private void printRetry(
        int attempt,
        FailureClassification classification,
        CollectionException exception,
        Duration delay
    ) {

        System.out.println();
        System.out.println(
            "=== PHASE 16 REAL OBSERVABILITY PROBE ==="
        );
        System.out.println(
            "Status: RETRY"
        );
        System.out.println(
            "Attempt: "
                + attempt
                + "/"
                + MAX_ATTEMPTS
        );
        System.out.println(
            "Failure type: "
                + classification.type()
        );
        System.out.println(
            "Failure code: "
                + classification.code()
        );
        System.out.println(
            "HTTP status: "
                + exception.httpStatusCode()
        );
        System.out.println(
            "Retry in: "
                + delay.toSeconds()
                + " seconds"
        );
        System.out.println(
            "========================================="
        );
        System.out.println();
    }

    private void printFailure(
        int attempt,
        FailureClassification classification,
        CollectionException exception
    ) {

        System.out.println();
        System.out.println(
            "=== PHASE 16 REAL OBSERVABILITY PROBE ==="
        );
        System.out.println(
            "Status: FAILURE"
        );
        System.out.println(
            "Attempt: "
                + attempt
                + "/"
                + MAX_ATTEMPTS
        );
        System.out.println(
            "Failure type: "
                + classification.type()
        );
        System.out.println(
            "Failure code: "
                + classification.code()
        );
        System.out.println(
            "HTTP status: "
                + exception.httpStatusCode()
        );
        System.out.println(
            "========================================="
        );
        System.out.println();
    }

    private void printInconclusive(
        int attempt,
        FailureClassification classification,
        CollectionException exception
    ) {

        System.out.println();
        System.out.println(
            "=== PHASE 16 REAL OBSERVABILITY PROBE ==="
        );
        System.out.println(
            "Status: INCONCLUSIVE"
        );
        System.out.println(
            "Attempt: "
                + attempt
                + "/"
                + MAX_ATTEMPTS
        );
        System.out.println(
            "Failure type: "
                + classification.type()
        );
        System.out.println(
            "Failure code: "
                + classification.code()
        );
        System.out.println(
            "HTTP status: "
                + exception.httpStatusCode()
        );
        System.out.println(
            "========================================="
        );
        System.out.println();
    }

    private void printSuccess(
        long runId,
        CollectionResult result,
        ProcessingRunIntegrationMetrics metrics,
        SuccessfulObservationEvidence evidence,
        String runsShowOutput
    ) {

        System.out.println();
        System.out.println(
            "=== PHASE 16 REAL OBSERVABILITY PROBE ==="
        );
        System.out.println(
            "Status: SUCCESS"
        );
        System.out.println(
            "Run id: "
                + runId
        );
        System.out.println(
            "Source: "
                + result.source()
        );
        System.out.println(
            "Content length: "
                + result.content().length()
        );
        System.out.println(
            "Integration: "
                + metrics.integration()
        );
        System.out.println(
            "Observations: "
                + metrics.observations()
        );
        System.out.println(
            "Successes: "
                + metrics.successes()
        );
        System.out.println(
            "Failures: "
                + metrics.failures()
        );
        System.out.println(
            "Average duration ms: "
                + metrics.averageDurationMs().toPlainString()
        );
        System.out.println(
            "Maximum duration ms: "
                + metrics.maximumDurationMs()
        );
        System.out.println(
            "Latest HTTP status: "
                + evidence.httpStatusCode()
        );
        System.out.println(
            "Cross-connection persistence visibility: true"
        );
        System.out.println(
            "runs show integration visibility: true"
        );
        System.out.println();
        System.out.println(
            "--- runs show proof ---"
        );
        System.out.print(
            runsShowOutput
        );
        if (!runsShowOutput.endsWith(
            System.lineSeparator()
        )) {
            System.out.println();
        }
        System.out.println(
            "--- end runs show proof ---"
        );
        System.out.println(
            "Diagnostic artifact: "
                + DIAGNOSTIC_FILE.toAbsolutePath()
        );
        System.out.println(
            "========================================="
        );
        System.out.println();
    }

    private record SuccessfulObservationEvidence(
        long runId,
        String jobType,
        String integration,
        String operation,
        String outcome,
        long durationMs,
        Integer httpStatusCode
    ) {
    }
}
