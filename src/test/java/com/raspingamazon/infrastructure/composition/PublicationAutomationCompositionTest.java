package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.PublicationProcessingRunDispatchService;
import com.raspingamazon.application.publication.PublicationSelectionDispatchResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.config.PublicationAutomationPolicyConfig;
import com.raspingamazon.infrastructure.config.PublicationOperationalPolicyConfig;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class PublicationAutomationCompositionTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final ZoneId ZONE =
        ZoneId.of(
            "America/Sao_Paulo"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-30T22:20:00Z"
            ),
            ZoneOffset.UTC
        );

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    @Test
    void shouldSynchronizePoliciesAndProcessEmptyProcessingRun()
        throws Exception {

        ApplicationConfig applicationConfig =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     applicationConfig
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                String destination =
                    uniqueDestination(
                        "complete"
                    );

                PublicationAutomationPolicyConfig desiredConfig =
                    configuration(
                        destination
                    );

                AtomicInteger generationCalls =
                    new AtomicInteger();

                AtomicInteger readinessCalls =
                    new AtomicInteger();

                AtomicInteger enqueueCalls =
                    new AtomicInteger();

                PublicationProcessingRunDispatchService service =
                    PublicationAutomationComposition.create(
                        connection,
                        desiredConfig,
                        dealEvaluationId -> {

                            generationCalls.incrementAndGet();

                            throw new AssertionError(
                                "generation must not be called "
                                    + "for an empty ProcessingRun"
                            );
                        },
                        publicationId -> {

                            readinessCalls.incrementAndGet();

                            throw new AssertionError(
                                "readiness must not be called "
                                    + "for an empty ProcessingRun"
                            );
                        },
                        request -> {

                            enqueueCalls.incrementAndGet();

                            return PublicationOutboxEnqueueResult
                                .enqueued(
                                    999L
                                );
                        },
                        CLOCK
                    );

                long processingRunId =
                    insertProcessingRun(
                        connection
                    );

                PublicationSelectionDispatchResult result =
                    service.process(
                        processingRunId
                    );

                /*
                 * As três políticas foram sincronizadas para
                 * exatamente o mesmo escopo.
                 */
                assertEquals(
                    "PUBLICATION_SELECTION_V1_CONFIG_V1",
                    activeVersion(
                        connection,
                        "publication_selection_profile",
                        destination
                    )
                );

                assertEquals(
                    "QUOTA_AUTOMATION_V1",
                    activeVersion(
                        connection,
                        "publication_quota_profile",
                        destination
                    )
                );

                assertEquals(
                    "CADENCE_AUTOMATION_V1",
                    activeVersion(
                        connection,
                        "publication_cadence_profile",
                        destination
                    )
                );

                /*
                 * Mesmo com zero candidatos, a seleção é uma
                 * execução auditável e recebe SelectionRun própria.
                 */
                assertTrue(
                    result.selectionRunId() > 0L
                );

                assertEquals(
                    0,
                    result.selectedCandidateCount()
                );

                assertEquals(
                    0,
                    result.attemptedCount()
                );

                assertTrue(
                    result.items()
                        .isEmpty()
                );

                assertSelectionRunScope(
                    connection,
                    result.selectionRunId(),
                    destination
                );

                /*
                 * Nenhum candidato significa nenhuma geração,
                 * readiness ou reserva de outbox.
                 */
                assertEquals(
                    0,
                    generationCalls.get()
                );

                assertEquals(
                    0,
                    readinessCalls.get()
                );

                assertEquals(
                    0,
                    enqueueCalls.get()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectUnsupportedPrimaryChannelBeforeSynchronizing()
        throws Exception {

        ApplicationConfig applicationConfig =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     applicationConfig
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                String destination =
                    uniqueDestination(
                        "unsupported"
                    );

                PublicationAutomationPolicyConfig invalidConfig =
                    configuration(
                        "WHATSAPP",
                        destination
                    );

                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        PublicationAutomationComposition.create(
                            connection,
                            invalidConfig,
                            dealEvaluationId -> {
                                throw new AssertionError();
                            },
                            publicationId -> {
                                throw new AssertionError();
                            },
                            request ->
                                PublicationOutboxEnqueueResult
                                    .enqueued(
                                        1L
                                    ),
                            CLOCK
                        )
                );

                /*
                 * A validação ocorre antes da sincronização.
                 */
                assertFalse(
                    activeProfileExists(
                        connection,
                        "publication_selection_profile",
                        "WHATSAPP",
                        destination
                    )
                );

                assertFalse(
                    activeProfileExists(
                        connection,
                        "publication_quota_profile",
                        "WHATSAPP",
                        destination
                    )
                );

                assertFalse(
                    activeProfileExists(
                        connection,
                        "publication_cadence_profile",
                        "WHATSAPP",
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private PublicationAutomationPolicyConfig configuration(
        String destination
    ) {

        return configuration(
            CHANNEL,
            destination
        );
    }

    private PublicationAutomationPolicyConfig configuration(
        String channel,
        String destination
    ) {

        PublicationOperationalPolicyConfig operationalPolicy =
            new PublicationOperationalPolicyConfig(
                channel,
                destination,
                new PublicationQuotaProfile(
                    "QUOTA_AUTOMATION_V1",
                    7,
                    ZONE
                ),
                new PublicationCadenceProfile(
                    "CADENCE_AUTOMATION_V1",
                    Duration.ofHours(
                        2
                    ),
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    ZONE
                )
            );

        PublicationSelectionProfile selectionProfile =
            new PublicationSelectionProfile(
                "PUBLICATION_SELECTION_V1_CONFIG_V1",
                Duration.ofDays(
                    2
                ),
                Duration.ofDays(
                    7
                )
            );

        return new PublicationAutomationPolicyConfig(
            operationalPolicy,
            selectionProfile
        );
    }

    private long insertProcessingRun(
        Connection connection
    ) throws Exception {

        String unique =
            Long.toUnsignedString(
                System.nanoTime()
            );

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at
            )
            VALUES (
                ?,
                ?,
                'COMPLETED',
                ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                "publication-automation-" + unique
            );

            statement.setString(
                2,
                "https://example.test/automation/" + unique
            );

            statement.setObject(
                3,
                OffsetDateTime.ofInstant(
                    CLOCK.instant(),
                    ZoneOffset.UTC
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private String activeVersion(
        Connection connection,
        String table,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT version
            FROM %s
            WHERE channel = ?
              AND destination = ?
              AND active = true
            """
                .formatted(
                    table
                );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
            );

            statement.setString(
                2,
                destination
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                String version =
                    resultSet.getString(
                        "version"
                    );

                assertFalse(
                    resultSet.next()
                );

                return version;
            }
        }
    }

    private boolean activeProfileExists(
        Connection connection,
        String table,
        String channel,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT 1
            FROM %s
            WHERE channel = ?
              AND destination = ?
              AND active = true
            """
                .formatted(
                    table
                );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                channel
            );

            statement.setString(
                2,
                destination
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    private void assertSelectionRunScope(
        Connection connection,
        long selectionRunId,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT
                channel,
                destination,
                selection_profile_version,
                quota_profile_version
            FROM publication_selection_run
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                selectionRunId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    CHANNEL,
                    resultSet.getString(
                        "channel"
                    )
                );

                assertEquals(
                    destination,
                    resultSet.getString(
                        "destination"
                    )
                );

                assertEquals(
                    "PUBLICATION_SELECTION_V1_CONFIG_V1",
                    resultSet.getString(
                        "selection_profile_version"
                    )
                );

                assertEquals(
                    "QUOTA_AUTOMATION_V1",
                    resultSet.getString(
                        "quota_profile_version"
                    )
                );

                assertFalse(
                    resultSet.next()
                );
            }
        }
    }

    private String uniqueDestination(
        String suffix
    ) {

        return "@automation-composition-"
            + suffix
            + "-"
            + SEQUENCE.incrementAndGet()
            + "-"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
