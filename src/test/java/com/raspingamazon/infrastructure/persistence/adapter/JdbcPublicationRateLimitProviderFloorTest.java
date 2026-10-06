package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitReservation;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationRateLimitProviderFloorTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-06T21:00:00Z"
        );

    @Test
    void shouldExtendPersistedTimelineWhenProviderFloorIsLater()
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

                JdbcPublicationRateLimitReservationAdapter adapter =
                    new JdbcPublicationRateLimitReservationAdapter(
                        connection
                    );

                String integrationKey =
                    uniqueIntegrationKey(
                        "extend"
                    );

                adapter.reserve(
                    integrationKey,
                    Duration.ofSeconds(
                        1
                    ),
                    NOW
                );

                OffsetDateTime providerFloor =
                    NOW.plusMinutes(
                        2
                    );

                OffsetDateTime effective =
                    adapter.extendNotBefore(
                        integrationKey,
                        providerFloor,
                        NOW.plusSeconds(
                            1
                        )
                    );

                assertEquals(
                    providerFloor.toInstant(),
                    effective.toInstant()
                );

                PublicationRateLimitReservation laterAdmission =
                    adapter.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            1
                        ),
                        NOW.plusSeconds(
                            30
                        )
                    );

                assertTrue(
                    laterAdmission.deferred()
                );

                assertEquals(
                    providerFloor.toInstant(),
                    laterAdmission.allowedAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldNeverShortenExistingPersistedTimeline()
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

                JdbcPublicationRateLimitReservationAdapter adapter =
                    new JdbcPublicationRateLimitReservationAdapter(
                        connection
                    );

                String integrationKey =
                    uniqueIntegrationKey(
                        "no-shorten"
                    );

                OffsetDateTime existingFloor =
                    NOW.plusMinutes(
                        5
                    );

                adapter.reserve(
                    integrationKey,
                    Duration.ofMinutes(
                        5
                    ),
                    NOW
                );

                OffsetDateTime effective =
                    adapter.extendNotBefore(
                        integrationKey,
                        NOW.plusMinutes(
                            2
                        ),
                        NOW.plusSeconds(
                            10
                        )
                    );

                assertEquals(
                    existingFloor.toInstant(),
                    effective.toInstant()
                );

                PublicationRateLimitReservation admission =
                    adapter.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            1
                        ),
                        NOW.plusMinutes(
                            3
                        )
                    );

                assertTrue(
                    admission.deferred()
                );

                assertEquals(
                    existingFloor.toInstant(),
                    admission.allowedAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void independentConnectionShouldObserveProviderFloor()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String integrationKey =
            uniqueIntegrationKey(
                "shared"
            );

        try (Connection firstConnection =
                 DatabaseConnection.open(
                     config
                 );
             Connection secondConnection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcPublicationRateLimitReservationAdapter first =
                new JdbcPublicationRateLimitReservationAdapter(
                    firstConnection
                );

            JdbcPublicationRateLimitReservationAdapter second =
                new JdbcPublicationRateLimitReservationAdapter(
                    secondConnection
                );

            first.reserve(
                integrationKey,
                Duration.ofSeconds(
                    1
                ),
                NOW
            );

            OffsetDateTime providerFloor =
                NOW.plusMinutes(
                    3
                );

            first.extendNotBefore(
                integrationKey,
                providerFloor,
                NOW.plusSeconds(
                    1
                )
            );

            PublicationRateLimitReservation observedBySecond =
                second.reserve(
                    integrationKey,
                    Duration.ofSeconds(
                        1
                    ),
                    NOW.plusMinutes(
                        1
                    )
                );

            assertTrue(
                observedBySecond.deferred()
            );

            assertEquals(
                providerFloor.toInstant(),
                observedBySecond.allowedAt()
                    .toInstant()
            );

        } finally {

            deleteRateLimitState(
                config,
                integrationKey
            );
        }
    }

    private void deleteRateLimitState(
        ApplicationConfig config,
        String integrationKey
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            String sql =
                """
                DELETE FROM publication_rate_limit_state
                WHERE integration_key = ?
                """;

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         sql
                     )) {

                statement.setString(
                    1,
                    integrationKey
                );

                statement.executeUpdate();
            }
        }
    }

    private String uniqueIntegrationKey(
        String suffix
    ) {

        return "RATE_LIMIT_PROVIDER_FLOOR_"
            + suffix.toUpperCase()
            + "_"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
