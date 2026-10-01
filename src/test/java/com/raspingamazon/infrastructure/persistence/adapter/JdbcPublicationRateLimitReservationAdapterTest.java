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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationRateLimitReservationAdapterTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-01T23:00:00Z"
        );

    @Test
    void firstAdmissionShouldBeImmediatelyAllowed()
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

                PublicationRateLimitReservation reservation =
                    adapter.reserve(
                        uniqueIntegrationKey(
                            "first"
                        ),
                        Duration.ofSeconds(
                            1
                        ),
                        NOW
                    );

                assertTrue(
                    reservation.immediatelyAllowed()
                );

                assertEquals(
                    NOW.toInstant(),
                    reservation.allowedAt()
                        .toInstant()
                );

                assertEquals(
                    NOW.plusSeconds(
                        1
                    ).toInstant(),
                    reservation.nextAllowedAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void secondAdmissionAtSameInstantShouldBeDeferredWithoutAdvancingState()
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
                        "deferred"
                    );

                PublicationRateLimitReservation first =
                    adapter.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            2
                        ),
                        NOW
                    );

                PublicationRateLimitReservation second =
                    adapter.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            2
                        ),
                        NOW
                    );

                assertTrue(
                    first.immediatelyAllowed()
                );

                assertFalse(
                    second.immediatelyAllowed()
                );

                assertTrue(
                    second.deferred()
                );

                assertEquals(
                    NOW.plusSeconds(
                        2
                    ).toInstant(),
                    second.allowedAt()
                        .toInstant()
                );

                /*
                 * Como a segunda chamada NÃO adquiriu um slot,
                 * o estado persistente não avança para +4s.
                 */
                assertEquals(
                    second.allowedAt()
                        .toInstant(),
                    second.nextAllowedAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void deferredCallerShouldBeAbleToAcquireWhenRetryInstantArrives()
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
                        "retry"
                    );

                adapter.reserve(
                    integrationKey,
                    Duration.ofSeconds(
                        2
                    ),
                    NOW
                );

                PublicationRateLimitReservation deferred =
                    adapter.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            2
                        ),
                        NOW
                    );

                OffsetDateTime retryAt =
                    deferred.allowedAt();

                PublicationRateLimitReservation retry =
                    adapter.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            2
                        ),
                        retryAt
                    );

                assertTrue(
                    retry.immediatelyAllowed()
                );

                assertEquals(
                    retryAt.toInstant(),
                    retry.allowedAt()
                        .toInstant()
                );

                assertEquals(
                    retryAt.plusSeconds(
                        2
                    ).toInstant(),
                    retry.nextAllowedAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void differentIntegrationsShouldHaveIndependentTimelines()
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

                PublicationRateLimitReservation telegram =
                    adapter.reserve(
                        uniqueIntegrationKey(
                            "telegram"
                        ),
                        Duration.ofSeconds(
                            5
                        ),
                        NOW
                    );

                PublicationRateLimitReservation whatsApp =
                    adapter.reserve(
                        uniqueIntegrationKey(
                            "whatsapp"
                        ),
                        Duration.ofSeconds(
                            5
                        ),
                        NOW
                    );

                assertTrue(
                    telegram.immediatelyAllowed()
                );

                assertTrue(
                    whatsApp.immediatelyAllowed()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void requestAfterPersistedWindowShouldStartFromRequestedTime()
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
                        "future"
                    );

                adapter.reserve(
                    integrationKey,
                    Duration.ofSeconds(
                        1
                    ),
                    NOW
                );

                OffsetDateTime later =
                    NOW.plusMinutes(
                        10
                    );

                PublicationRateLimitReservation reservation =
                    adapter.reserve(
                        integrationKey,
                        Duration.ofSeconds(
                            1
                        ),
                        later
                    );

                assertTrue(
                    reservation.immediatelyAllowed()
                );

                assertEquals(
                    later.toInstant(),
                    reservation.allowedAt()
                        .toInstant()
                );

                assertEquals(
                    later.plusSeconds(
                        1
                    ).toInstant(),
                    reservation.nextAllowedAt()
                        .toInstant()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void independentConnectionsShouldObserveSamePersistedAdmissionState()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String integrationKey =
            uniqueIntegrationKey(
                "multi-connection"
            );

        try (Connection firstConnection =
                 DatabaseConnection.open(
                     config
                 );
             Connection secondConnection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcPublicationRateLimitReservationAdapter firstAdapter =
                new JdbcPublicationRateLimitReservationAdapter(
                    firstConnection
                );

            JdbcPublicationRateLimitReservationAdapter secondAdapter =
                new JdbcPublicationRateLimitReservationAdapter(
                    secondConnection
                );

            PublicationRateLimitReservation first =
                firstAdapter.reserve(
                    integrationKey,
                    Duration.ofSeconds(
                        3
                    ),
                    NOW
                );

            PublicationRateLimitReservation second =
                secondAdapter.reserve(
                    integrationKey,
                    Duration.ofSeconds(
                        3
                    ),
                    NOW
                );

            assertTrue(
                first.immediatelyAllowed()
            );

            assertTrue(
                second.deferred()
            );

            assertEquals(
                NOW.plusSeconds(
                    3
                ).toInstant(),
                second.allowedAt()
                    .toInstant()
            );

            /*
             * Agora avançamos o relógio lógico para o instante que
             * a segunda conexão recebeu.
             *
             * Como ela não havia reservado esse slot previamente,
             * precisa competir novamente. Como não há concorrente
             * neste ponto do teste, consegue adquiri-lo.
             */
            PublicationRateLimitReservation retry =
                secondAdapter.reserve(
                    integrationKey,
                    Duration.ofSeconds(
                        3
                    ),
                    second.allowedAt()
                );

            assertTrue(
                retry.immediatelyAllowed()
            );

            assertEquals(
                NOW.plusSeconds(
                    6
                ).toInstant(),
                retry.nextAllowedAt()
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

        return "RATE_LIMIT_TEST_"
            + suffix.toUpperCase()
            + "_"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
