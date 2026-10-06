package com.raspingamazon.infrastructure.resilience;

import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionDecision;
import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionRequest;
import com.raspingamazon.application.publication.outbox.recovery.PublicationOutboxLeaseRecoveryResult;
import com.raspingamazon.application.publication.outbox.recovery.PublicationOutboxLeaseRecoveryService;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationDeliveryResolutionAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxQueueAdapter;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class Phase20PublicationRecoveryDestructiveTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2000-01-01T00:30:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW.toInstant(),
            ZoneOffset.UTC
        );

    @Test
    void shouldTurnAbandonedStartedAttemptIntoUnknownBeforeAllowingControlledRedelivery()
        throws Exception {

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

                Phase20PublicationCrashFixture.Scenario scenario =
                    Phase20PublicationCrashFixture.create(
                        connection,
                        NOW.minusMinutes(
                            20
                        )
                    );

                JdbcPublicationOutboxQueueAdapter queue =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    );

                PublicationOutboxLeaseRecoveryResult recovery =
                    new PublicationOutboxLeaseRecoveryService(
                        queue,
                        Duration.ofMinutes(
                            5
                        ),
                        CLOCK
                    ).recoverOnce();

                assertEquals(
                    1,
                    recovery.recoveredLeaseCount()
                );

                assertEquals(
                    "DELIVERY_UNKNOWN",
                    outboxStatus(
                        connection,
                        scenario.outboxId()
                    )
                );

                assertEquals(
                    "DELIVERY_UNKNOWN",
                    attemptStatus(
                        connection,
                        scenario.attemptId()
                    )
                );

                assertEquals(
                    1L,
                    countAttempts(
                        connection,
                        scenario.outboxId()
                    )
                );

                assertTrue(
                    !isClaimable(
                        connection,
                        scenario.outboxId(),
                        NOW.plusSeconds(
                            1
                        )
                    )
                );

                OffsetDateTime resolvedAt =
                    NOW.plusMinutes(
                        1
                    );

                new JdbcPublicationDeliveryResolutionAdapter(
                    connection
                ).resolve(
                    new PublicationDeliveryResolutionRequest(
                        scenario.outboxId(),
                        "resolution:"
                            + UUID.randomUUID(),
                        PublicationDeliveryResolutionDecision
                            .CONFIRMED_NOT_DELIVERED,
                        "phase20-destructive-test",
                        "operator confirmed provider did not accept the request",
                        null
                    ),
                    resolvedAt
                );

                assertEquals(
                    "PENDING",
                    outboxStatus(
                        connection,
                        scenario.outboxId()
                    )
                );

                assertTrue(
                    isClaimable(
                        connection,
                        scenario.outboxId(),
                        resolvedAt.plusSeconds(
                            1
                        )
                    )
                );

                /*
                 * Recovery + controlled resolution never rewrites the
                 * historical ambiguous attempt and never manufactures
                 * a second attempt before the provider is called again.
                 */
                assertEquals(
                    "DELIVERY_UNKNOWN",
                    attemptStatus(
                        connection,
                        scenario.attemptId()
                    )
                );

                assertEquals(
                    1L,
                    countAttempts(
                        connection,
                        scenario.outboxId()
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void unresolvedAmbiguityShouldRemainTerminalForAutomaticClaim()
        throws Exception {

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

                Phase20PublicationCrashFixture.Scenario scenario =
                    Phase20PublicationCrashFixture.create(
                        connection,
                        NOW.minusMinutes(
                            20
                        )
                    );

                JdbcPublicationOutboxQueueAdapter queue =
                    new JdbcPublicationOutboxQueueAdapter(
                        connection
                    );

                new PublicationOutboxLeaseRecoveryService(
                    queue,
                    Duration.ofMinutes(
                        5
                    ),
                    CLOCK
                ).recoverOnce();

                new JdbcPublicationDeliveryResolutionAdapter(
                    connection
                ).resolve(
                    new PublicationDeliveryResolutionRequest(
                        scenario.outboxId(),
                        "resolution:"
                            + UUID.randomUUID(),
                        PublicationDeliveryResolutionDecision
                            .REMAINS_UNKNOWN,
                        "phase20-destructive-test",
                        "provider has no trustworthy evidence to resolve delivery",
                        null
                    ),
                    NOW.plusMinutes(
                        1
                    )
                );

                assertEquals(
                    "DELIVERY_UNKNOWN",
                    outboxStatus(
                        connection,
                        scenario.outboxId()
                    )
                );

                assertTrue(
                    !isClaimable(
                        connection,
                        scenario.outboxId(),
                        NOW.plusHours(
                            1
                        )
                    )
                );

                assertEquals(
                    1L,
                    countResolutionEvents(
                        connection,
                        scenario.outboxId()
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private boolean isClaimable(
        Connection connection,
        long outboxId,
        OffsetDateTime at
    ) throws Exception {

        String sql =
            """
            SELECT EXISTS (
                SELECT 1
                FROM publication_outbox
                WHERE id = ?
                  AND status = 'PENDING'
                  AND available_at <= ?
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
            );

            statement.setObject(
                2,
                at
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getBoolean(
                    1
                );
            }
        }
    }

    private String outboxStatus(
        Connection connection,
        long outboxId
    ) throws Exception {

        return singleString(
            connection,
            """
            SELECT status
            FROM publication_outbox
            WHERE id = ?
            """,
            outboxId
        );
    }

    private String attemptStatus(
        Connection connection,
        long attemptId
    ) throws Exception {

        return singleString(
            connection,
            """
            SELECT status
            FROM publication_attempt
            WHERE id = ?
            """,
            attemptId
        );
    }

    private String singleString(
        Connection connection,
        String sql,
        long id
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                id
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Expected persisted row was not found"
                    );
                }

                return resultSet.getString(
                    1
                );
            }
        }
    }

    private long countAttempts(
        Connection connection,
        long outboxId
    ) throws Exception {

        return countByOutbox(
            connection,
            """
            SELECT COUNT(*)
            FROM publication_attempt
            WHERE publication_outbox_id = ?
            """,
            outboxId
        );
    }

    private long countResolutionEvents(
        Connection connection,
        long outboxId
    ) throws Exception {

        return countByOutbox(
            connection,
            """
            SELECT COUNT(*)
            FROM publication_delivery_resolution_event
            WHERE publication_outbox_id = ?
            """,
            outboxId
        );
    }

    private long countByOutbox(
        Connection connection,
        String sql,
        long outboxId
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
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
}
