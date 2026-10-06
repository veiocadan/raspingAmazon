package com.raspingamazon.infrastructure.resilience;

import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitReservation;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationRateLimitReservationAdapter;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class Phase20RateLimitRestartDestructiveTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2000-01-01T01:30:00Z"
        );

    @Test
    void providerRetryFloorShouldSurviveConnectionRestartAndProtectNextWorker()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String integrationKey =
            "PHASE20_RESTART_"
                + UUID.randomUUID();

        OffsetDateTime providerFloor =
            NOW.plusMinutes(
                5
            );

        try {

            /*
             * Primeira "instância": admite uma chamada e recebe
             * posteriormente um Retry-After do provider.
             */
            try (Connection firstConnection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcPublicationRateLimitReservationAdapter first =
                    new JdbcPublicationRateLimitReservationAdapter(
                        firstConnection
                    );

                PublicationRateLimitReservation admitted =
                    first.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            1
                        ),
                        NOW
                    );

                assertTrue(
                    admitted.immediatelyAllowed()
                );

                OffsetDateTime persistedFloor =
                    first.extendNotBefore(
                        integrationKey,
                        providerFloor,
                        NOW.plusSeconds(
                            1
                        )
                    );

                assertEquals(
                    providerFloor.toInstant(),
                    persistedFloor.toInstant()
                );
            }

            /*
             * Segunda "instância": nova Connection, sem memória local.
             * A autoridade PostgreSQL ainda deve impor o floor.
             */
            try (Connection secondConnection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcPublicationRateLimitReservationAdapter second =
                    new JdbcPublicationRateLimitReservationAdapter(
                        secondConnection
                    );

                PublicationRateLimitReservation deferred =
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
                    deferred.deferred()
                );

                assertEquals(
                    providerFloor.toInstant(),
                    deferred.allowedAt()
                        .toInstant()
                );

                PublicationRateLimitReservation acquiredAfterFloor =
                    second.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            1
                        ),
                        providerFloor
                    );

                assertTrue(
                    acquiredAfterFloor.immediatelyAllowed()
                );
            }

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
}
