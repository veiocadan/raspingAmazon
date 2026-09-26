package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertThrows;

@PostgresIntegrationTest
class JdbcOfferSnapshotEvaluationLockAdapterTest {

    @Test
    void shouldRejectInvalidSnapshotId()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcOfferSnapshotEvaluationLockAdapter adapter =
                new JdbcOfferSnapshotEvaluationLockAdapter(
                    connection
                );

            assertThrows(
                IllegalArgumentException.class,
                () -> adapter.lockById(
                    0
                )
            );
        }
    }

    @Test
    void shouldFailWhenSnapshotDoesNotExist()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcOfferSnapshotEvaluationLockAdapter adapter =
                new JdbcOfferSnapshotEvaluationLockAdapter(
                    connection
                );

            assertThrows(
                IllegalArgumentException.class,
                () -> adapter.lockById(
                    Long.MAX_VALUE
                )
            );
        }
    }
}
