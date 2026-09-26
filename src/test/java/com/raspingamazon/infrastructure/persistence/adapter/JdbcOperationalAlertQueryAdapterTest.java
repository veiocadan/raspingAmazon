package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.observability.alert.OperationalAlert;
import com.raspingamazon.application.operation.observability.alert.OperationalAlertPolicy;
import com.raspingamazon.application.operation.observability.alert.OperationalAlertType;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcOperationalAlertQueryAdapterTest {

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2099-09-26T18:00:00Z"
        );

    @Test
    void shouldDetectRepeatedExternalFailuresInsideConfiguredWindow()
        throws Exception {

        inTransaction(
            connection -> {

                insertFailureObservation(
                    connection,
                    "alert-external-a",
                    EVALUATED_AT.minusMinutes(
                        1
                    ),
                    "EXTERNAL"
                );

                insertFailureObservation(
                    connection,
                    "alert-external-a",
                    EVALUATED_AT.minusMinutes(
                        5
                    ),
                    "EXTERNAL"
                );

                insertFailureObservation(
                    connection,
                    "alert-external-a",
                    EVALUATED_AT.minusMinutes(
                        14
                    ),
                    "EXTERNAL"
                );

                /*
                 * Fora da janela.
                 */
                insertFailureObservation(
                    connection,
                    "alert-external-a",
                    EVALUATED_AT.minusMinutes(
                        16
                    ),
                    "EXTERNAL"
                );

                /*
                 * Falha interna não participa do contador externo.
                 */
                insertFailureObservation(
                    connection,
                    "alert-external-a",
                    EVALUATED_AT.minusMinutes(
                        2
                    ),
                    "INTERNAL"
                );

                /*
                 * Segunda integração ainda abaixo do threshold.
                 */
                insertFailureObservation(
                    connection,
                    "alert-external-b",
                    EVALUATED_AT.minusMinutes(
                        2
                    ),
                    "EXTERNAL"
                );

                insertFailureObservation(
                    connection,
                    "alert-external-b",
                    EVALUATED_AT.minusMinutes(
                        3
                    ),
                    "EXTERNAL"
                );

                insertSuccessObservation(
                    connection,
                    "alert-external-a",
                    EVALUATED_AT.minusMinutes(
                        4
                    )
                );

                List<OperationalAlert> alerts =
                    adapter(
                        connection
                    ).findActiveAlerts(
                        policy(),
                        EVALUATED_AT
                    );

                List<OperationalAlert> repeatedFailures =
                    alerts.stream()
                        .filter(
                            alert ->
                                alert.type()
                                    == OperationalAlertType
                                    .REPEATED_EXTERNAL_FAILURES
                        )
                        .toList();

                assertEquals(
                    1,
                    repeatedFailures.size()
                );

                OperationalAlert alert =
                    repeatedFailures.getFirst();

                assertEquals(
                    "alert-external-a",
                    alert.integration()
                );

                assertEquals(
                    3L,
                    alert.observedCount()
                );

                assertNull(
                    alert.runId()
                );

                assertNull(
                    alert.referenceCount()
                );
            }
        );
    }

    @Test
    void shouldDetectDeadJobsAcrossCompleteRunLineage()
        throws Exception {

        inTransaction(
            connection -> {

                long runId =
                    insertRun(
                        connection,
                        "alert-dead-lineage-run",
                        ProcessingRunStatus.RUNNING,
                        EVALUATED_AT.minusHours(
                            2
                        )
                    );

                long candidateId =
                    insertCandidate(
                        connection,
                        runId,
                        "D000000001",
                        EVALUATED_AT.minusHours(
                            2
                        ),
                        null
                    );

                long productId =
                    insertProduct(
                        connection,
                        "D000000002"
                    );

                long snapshotId =
                    insertSnapshot(
                        connection,
                        productId,
                        EVALUATED_AT.minusHours(
                            1
                        )
                    );

                linkCandidateToSnapshot(
                    connection,
                    candidateId,
                    snapshotId
                );

                /*
                 * COLLECT_DEALS -> processing_run_id
                 */
                insertJob(
                    connection,
                    "COLLECT_DEALS",
                    "DEAD",
                    runId,
                    null,
                    null,
                    "alert-dead-collect"
                );

                /*
                 * ENRICH_DEAL -> deal_candidate_id
                 */
                insertJob(
                    connection,
                    "ENRICH_DEAL",
                    "DEAD",
                    null,
                    candidateId,
                    null,
                    "alert-dead-enrich"
                );

                /*
                 * EVALUATE_DEAL -> offer_snapshot_id
                 */
                insertJob(
                    connection,
                    "EVALUATE_DEAL",
                    "DEAD",
                    null,
                    null,
                    snapshotId,
                    "alert-dead-evaluate"
                );

                /*
                 * Não deve entrar no contador DEAD.
                 */
                insertJob(
                    connection,
                    "ENRICH_DEAL",
                    "SUCCEEDED",
                    null,
                    candidateId,
                    null,
                    "alert-success-enrich"
                );

                List<OperationalAlert> alerts =
                    adapter(
                        connection
                    ).findActiveAlerts(
                        policy(),
                        EVALUATED_AT
                    );

                OperationalAlert alert =
                    requireRunAlert(
                        alerts,
                        OperationalAlertType.DEAD_JOBS,
                        runId
                    );

                assertEquals(
                    3L,
                    alert.observedCount()
                );

                assertNull(
                    alert.integration()
                );

                assertNull(
                    alert.referenceCount()
                );
            }
        );
    }

    @Test
    void shouldAlertOnlyLatestCompletedRunWhenItHasZeroCandidates()
        throws Exception {

        inTransaction(
            connection -> {

                long olderRunId =
                    insertRun(
                        connection,
                        "alert-zero-older",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            3
                        )
                    );

                long latestRunId =
                    insertRun(
                        connection,
                        "alert-zero-latest",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            1
                        )
                    );

                /*
                 * Ambas têm zero candidates.
                 * Somente a run COMPLETED mais recente representa
                 * o estado atual da coleta.
                 */
                List<OperationalAlert> alerts =
                    adapter(
                        connection
                    ).findActiveAlerts(
                        policy(),
                        EVALUATED_AT
                    );

                OperationalAlert alert =
                    requireRunAlert(
                        alerts,
                        OperationalAlertType.ZERO_CANDIDATES,
                        latestRunId
                    );

                assertEquals(
                    0L,
                    alert.observedCount()
                );

                assertFalse(
                    hasRunAlert(
                        alerts,
                        OperationalAlertType.ZERO_CANDIDATES,
                        olderRunId
                    )
                );
            }
        );
    }

    @Test
    void shouldDetectSuspiciousDropUsingExactlyConfiguredPreviousRuns()
        throws Exception {

        inTransaction(
            connection -> {

                /*
                 * Run antiga fora do lookback.
                 *
                 * Se ela entrasse no baseline, a referência não seria 10.
                 */
                long outsideLookback =
                    insertRun(
                        connection,
                        "alert-drop-outside-lookback",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            5
                        )
                    );

                insertCandidates(
                    connection,
                    outsideLookback,
                    30,
                    EVALUATED_AT.minusHours(
                        5
                    ),
                    1000
                );

                long previousOne =
                    insertRun(
                        connection,
                        "alert-drop-history-1",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            4
                        )
                    );

                insertCandidates(
                    connection,
                    previousOne,
                    10,
                    EVALUATED_AT.minusHours(
                        4
                    ),
                    2000
                );

                long previousTwo =
                    insertRun(
                        connection,
                        "alert-drop-history-2",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            3
                        )
                    );

                insertCandidates(
                    connection,
                    previousTwo,
                    12,
                    EVALUATED_AT.minusHours(
                        3
                    ),
                    3000
                );

                long previousThree =
                    insertRun(
                        connection,
                        "alert-drop-history-3",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            2
                        )
                    );

                insertCandidates(
                    connection,
                    previousThree,
                    8,
                    EVALUATED_AT.minusHours(
                        2
                    ),
                    4000
                );

                /*
                 * Média das três anteriores:
                 *
                 * (10 + 12 + 8) / 3 = 10
                 *
                 * dropFraction = 0.50
                 *
                 * limite = 5
                 *
                 * observado = 4 -> alerta.
                 */
                long currentRun =
                    insertRun(
                        connection,
                        "alert-drop-current",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            1
                        )
                    );

                insertCandidates(
                    connection,
                    currentRun,
                    4,
                    EVALUATED_AT.minusHours(
                        1
                    ),
                    5000
                );

                List<OperationalAlert> alerts =
                    adapter(
                        connection
                    ).findActiveAlerts(
                        policy(),
                        EVALUATED_AT
                    );

                OperationalAlert alert =
                    requireRunAlert(
                        alerts,
                        OperationalAlertType
                            .SUSPICIOUS_COLLECTION_DROP,
                        currentRun
                    );

                assertEquals(
                    4L,
                    alert.observedCount()
                );

                assertEquals(
                    10L,
                    alert.referenceCount()
                );

                /*
                 * Como observedCount > 0, ZERO_CANDIDATES não deve
                 * existir para esta run.
                 */
                assertFalse(
                    hasRunAlert(
                        alerts,
                        OperationalAlertType.ZERO_CANDIDATES,
                        currentRun
                    )
                );
            }
        );
    }

    @Test
    void shouldNotDetectSuspiciousDropWithoutCompleteLookback()
        throws Exception {

        inTransaction(
            connection -> {

                long previousOne =
                    insertRun(
                        connection,
                        "alert-short-history-1",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            3
                        )
                    );

                insertCandidates(
                    connection,
                    previousOne,
                    10,
                    EVALUATED_AT.minusHours(
                        3
                    ),
                    6000
                );

                long previousTwo =
                    insertRun(
                        connection,
                        "alert-short-history-2",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            2
                        )
                    );

                insertCandidates(
                    connection,
                    previousTwo,
                    12,
                    EVALUATED_AT.minusHours(
                        2
                    ),
                    7000
                );

                long currentRun =
                    insertRun(
                        connection,
                        "alert-short-history-current",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            1
                        )
                    );

                insertCandidates(
                    connection,
                    currentRun,
                    1,
                    EVALUATED_AT.minusHours(
                        1
                    ),
                    8000
                );

                List<OperationalAlert> alerts =
                    adapter(
                        connection
                    ).findActiveAlerts(
                        policy(),
                        EVALUATED_AT
                    );

                assertFalse(
                    hasRunAlert(
                        alerts,
                        OperationalAlertType
                            .SUSPICIOUS_COLLECTION_DROP,
                        currentRun
                    )
                );
            }
        );
    }

    @Test
    void shouldNotDetectSuspiciousDropBelowMinimumBaseline()
        throws Exception {

        inTransaction(
            connection -> {

                long previousOne =
                    insertRun(
                        connection,
                        "alert-low-baseline-1",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            4
                        )
                    );

                insertCandidates(
                    connection,
                    previousOne,
                    2,
                    EVALUATED_AT.minusHours(
                        4
                    ),
                    9000
                );

                long previousTwo =
                    insertRun(
                        connection,
                        "alert-low-baseline-2",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            3
                        )
                    );

                insertCandidates(
                    connection,
                    previousTwo,
                    3,
                    EVALUATED_AT.minusHours(
                        3
                    ),
                    10000
                );

                long previousThree =
                    insertRun(
                        connection,
                        "alert-low-baseline-3",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            2
                        )
                    );

                insertCandidates(
                    connection,
                    previousThree,
                    4,
                    EVALUATED_AT.minusHours(
                        2
                    ),
                    11000
                );

                long currentRun =
                    insertRun(
                        connection,
                        "alert-low-baseline-current",
                        ProcessingRunStatus.COMPLETED,
                        EVALUATED_AT.minusHours(
                            1
                        )
                    );

                insertCandidates(
                    connection,
                    currentRun,
                    1,
                    EVALUATED_AT.minusHours(
                        1
                    ),
                    12000
                );

                /*
                 * Média histórica = 3.
                 *
                 * O policy exige baseline mínimo de 5.
                 */
                List<OperationalAlert> alerts =
                    adapter(
                        connection
                    ).findActiveAlerts(
                        policy(),
                        EVALUATED_AT
                    );

                assertFalse(
                    hasRunAlert(
                        alerts,
                        OperationalAlertType
                            .SUSPICIOUS_COLLECTION_DROP,
                        currentRun
                    )
                );
            }
        );
    }

    @Test
    void shouldRejectInvalidAdapterInput()
        throws Exception {

        assertThrows(
            NullPointerException.class,
            () -> new JdbcOperationalAlertQueryAdapter(
                null
            )
        );

        inTransaction(
            connection -> {

                JdbcOperationalAlertQueryAdapter adapter =
                    adapter(
                        connection
                    );

                assertThrows(
                    NullPointerException.class,
                    () -> adapter.findActiveAlerts(
                        null,
                        EVALUATED_AT
                    )
                );

                assertThrows(
                    NullPointerException.class,
                    () -> adapter.findActiveAlerts(
                        policy(),
                        null
                    )
                );
            }
        );
    }

    private JdbcOperationalAlertQueryAdapter adapter(
        Connection connection
    ) {

        return new JdbcOperationalAlertQueryAdapter(
            connection
        );
    }

    private OperationalAlertPolicy policy() {

        return new OperationalAlertPolicy(
            3,
            Duration.ofMinutes(
                15
            ),
            3,
            new BigDecimal(
                "0.50"
            ),
            5L
        );
    }

    private OperationalAlert requireRunAlert(
        List<OperationalAlert> alerts,
        OperationalAlertType type,
        long runId
    ) {

        List<OperationalAlert> matching =
            alerts.stream()
                .filter(
                    alert ->
                        alert.type() == type
                            && alert.runId() != null
                            && alert.runId() == runId
                )
                .toList();

        assertEquals(
            1,
            matching.size(),
            "Expected exactly one "
                + type
                + " alert for run "
                + runId
        );

        return matching.getFirst();
    }

    private boolean hasRunAlert(
        List<OperationalAlert> alerts,
        OperationalAlertType type,
        long runId
    ) {

        return alerts.stream()
            .anyMatch(
                alert ->
                    alert.type() == type
                        && alert.runId() != null
                        && alert.runId() == runId
            );
    }

    private void inTransaction(
        TransactionTest test
    ) throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                test.execute(
                    connection
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private long insertRun(
        Connection connection,
        String runKey,
        ProcessingRunStatus status,
        OffsetDateTime requestedAt
    ) throws Exception {

        OffsetDateTime startedAt =
            status == ProcessingRunStatus.PENDING
                ? null
                : requestedAt.plusMinutes(
                1
            );

        OffsetDateTime completedAt =
            status == ProcessingRunStatus.COMPLETED
                || status == ProcessingRunStatus.FAILED
                ? requestedAt.plusMinutes(
                2
            )
                : null;

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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                runKey
            );

            statement.setString(
                2,
                "https://www.amazon.com.br/deals"
            );

            statement.setString(
                3,
                status.name()
            );

            statement.setObject(
                4,
                requestedAt
            );

            statement.setObject(
                5,
                startedAt
            );

            statement.setObject(
                6,
                completedAt
            );

            statement.setObject(
                7,
                requestedAt
            );

            statement.setObject(
                8,
                completedAt == null
                    ? requestedAt
                    : completedAt
            );

            return returnedId(
                statement,
                "processing_run"
            );
        }
    }

    private long insertCandidate(
        Connection connection,
        long runId,
        String asin,
        OffsetDateTime collectedAt,
        Long offerSnapshotId
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_candidate (
                processing_run_id,
                asin,
                product_url,
                title,
                current_price,
                collected_at,
                source,
                offer_snapshot_id,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
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
                asin
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/"
                    + asin
            );

            statement.setString(
                4,
                "Alert test product "
                    + asin
            );

            statement.setBigDecimal(
                5,
                new BigDecimal(
                    "100.00"
                )
            );

            statement.setObject(
                6,
                collectedAt
            );

            statement.setString(
                7,
                "AMAZON_DEALS"
            );

            statement.setObject(
                8,
                offerSnapshotId
            );

            statement.setObject(
                9,
                collectedAt
            );

            return returnedId(
                statement,
                "deal_candidate"
            );
        }
    }

    private void insertCandidates(
        Connection connection,
        long runId,
        int count,
        OffsetDateTime collectedAt,
        int seed
    ) throws Exception {

        for (int index = 0;
             index < count;
             index++) {

            String asin =
                String.format(
                    "T%09d",
                    seed + index
                );

            insertCandidate(
                connection,
                runId,
                asin,
                collectedAt,
                null
            );
        }
    }

    private long insertProduct(
        Connection connection,
        String asin
    ) throws Exception {

        String sql =
            """
            INSERT INTO product (
                asin,
                title,
                product_url
            )
            VALUES (?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                asin
            );

            statement.setString(
                2,
                "Alert product "
                    + asin
            );

            statement.setString(
                3,
                "https://www.amazon.com.br/dp/"
                    + asin
            );

            return returnedId(
                statement,
                "product"
            );
        }
    }

    private long insertSnapshot(
        Connection connection,
        long productId,
        OffsetDateTime collectedAt
    ) throws Exception {

        String sql =
            """
            INSERT INTO offer_snapshot (
                product_id,
                collected_at,
                current_price,
                previous_price,
                discount_percentage,
                sold_percentage,
                rating,
                review_count,
                seller_name,
                delivery_provider,
                source
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                productId
            );

            statement.setObject(
                2,
                collectedAt
            );

            statement.setBigDecimal(
                3,
                new BigDecimal(
                    "100.00"
                )
            );

            statement.setBigDecimal(
                4,
                new BigDecimal(
                    "120.00"
                )
            );

            statement.setBigDecimal(
                5,
                new BigDecimal(
                    "16.67"
                )
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "50"
                )
            );

            statement.setBigDecimal(
                7,
                new BigDecimal(
                    "4.8"
                )
            );

            statement.setInt(
                8,
                500
            );

            statement.setString(
                9,
                "Amazon"
            );

            statement.setString(
                10,
                "Amazon"
            );

            statement.setString(
                11,
                "AMAZON_PRODUCT_PAGE"
            );

            return returnedId(
                statement,
                "offer_snapshot"
            );
        }
    }

    private void linkCandidateToSnapshot(
        Connection connection,
        long candidateId,
        long snapshotId
    ) throws Exception {

        String sql =
            """
            UPDATE deal_candidate
               SET offer_snapshot_id = ?
             WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setLong(
                2,
                candidateId
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private void insertJob(
        Connection connection,
        String jobType,
        String status,
        Long processingRunId,
        Long candidateId,
        Long snapshotId,
        String idempotencyKey
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_job (
                job_type,
                status,
                processing_run_id,
                deal_candidate_id,
                offer_snapshot_id,
                idempotency_key,
                attempt_count,
                max_attempts,
                available_at,
                created_at,
                updated_at,
                finished_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        OffsetDateTime createdAt =
            EVALUATED_AT.minusMinutes(
                30
            );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                jobType
            );

            statement.setString(
                2,
                status
            );

            statement.setObject(
                3,
                processingRunId
            );

            statement.setObject(
                4,
                candidateId
            );

            statement.setObject(
                5,
                snapshotId
            );

            statement.setString(
                6,
                idempotencyKey
            );

            statement.setInt(
                7,
                1
            );

            statement.setInt(
                8,
                5
            );

            statement.setObject(
                9,
                createdAt
            );

            statement.setObject(
                10,
                createdAt
            );

            statement.setObject(
                11,
                createdAt
            );

            statement.setObject(
                12,
                status.equals(
                    "DEAD"
                )
                    || status.equals(
                    "SUCCEEDED"
                )
                    ? createdAt.plusMinutes(
                    1
                )
                    : null
            );

            statement.executeUpdate();
        }
    }

    private void insertFailureObservation(
        Connection connection,
        String integration,
        OffsetDateTime observedAt,
        String failureOrigin
    ) throws Exception {

        String sql =
            """
            INSERT INTO integration_observation (
                observed_at,
                integration,
                operation,
                outcome,
                duration_ms,
                failure_origin,
                failure_type,
                error_code
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                observedAt
            );

            statement.setString(
                2,
                integration
            );

            statement.setString(
                3,
                "GET"
            );

            statement.setString(
                4,
                "FAILURE"
            );

            statement.setLong(
                5,
                100L
            );

            statement.setString(
                6,
                failureOrigin
            );

            statement.setString(
                7,
                "TRANSIENT"
            );

            statement.setString(
                8,
                "ALERT_TEST_FAILURE"
            );

            statement.executeUpdate();
        }
    }

    private void insertSuccessObservation(
        Connection connection,
        String integration,
        OffsetDateTime observedAt
    ) throws Exception {

        String sql =
            """
            INSERT INTO integration_observation (
                observed_at,
                integration,
                operation,
                outcome,
                duration_ms
            )
            VALUES (?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                observedAt
            );

            statement.setString(
                2,
                integration
            );

            statement.setString(
                3,
                "GET"
            );

            statement.setString(
                4,
                "SUCCESS"
            );

            statement.setLong(
                5,
                50L
            );

            statement.executeUpdate();
        }
    }

    private long returnedId(
        PreparedStatement statement,
        String entity
    ) throws Exception {

        try (ResultSet resultSet =
                 statement.executeQuery()) {

            if (!resultSet.next()) {

                throw new IllegalStateException(
                    entity
                        + " insert returned no id"
                );
            }

            return resultSet.getLong(
                "id"
            );
        }
    }

    @FunctionalInterface
    private interface TransactionTest {

        void execute(
            Connection connection
        ) throws Exception;
    }
}
