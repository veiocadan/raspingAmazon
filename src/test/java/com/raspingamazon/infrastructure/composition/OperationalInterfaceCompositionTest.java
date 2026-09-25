package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalInterfaceCompositionTest {

    @Test
    void shouldBuildCompleteOperationalInterface() {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (OperationalInterfaceComposition composition =
                 OperationalInterfaceComposition.open(
                     config
                 )) {

            assertNotNull(
                composition.listDealEvaluations()
            );

            assertNotNull(
                composition.getDealEvaluationDetail()
            );

            assertNotNull(
                composition.listProcessingRuns()
            );

            assertNotNull(
                composition.listProcessingJobs()
            );

            assertNotNull(
                composition.listPublications()
            );

            assertNotNull(
                composition.getPublicationDetail()
            );
        }
    }

    @Test
    void shouldRejectNullConfig() {

        assertThrows(
            NullPointerException.class,
            () -> OperationalInterfaceComposition.open(
                null
            )
        );
    }

    @Test
    void shouldRejectNullOwnedConnection() {

        assertThrows(
            NullPointerException.class,
            () ->
                OperationalInterfaceComposition
                    .fromOwnedConnection(
                        null
                    )
        );
    }

    @Test
    void shouldCloseOwnedConnection()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Connection connection =
            DatabaseConnection.open(
                config
            );

        try {

            OperationalInterfaceComposition composition =
                OperationalInterfaceComposition
                    .fromOwnedConnection(
                        connection
                    );

            composition.close();

            assertTrue(
                connection.isClosed()
            );

        } finally {

            if (!connection.isClosed()) {
                connection.close();
            }
        }
    }

    @Test
    void shouldAllowRepeatedClose()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Connection connection =
            DatabaseConnection.open(
                config
            );

        try {

            OperationalInterfaceComposition composition =
                OperationalInterfaceComposition
                    .fromOwnedConnection(
                        connection
                    );

            composition.close();
            composition.close();

            assertTrue(
                connection.isClosed()
            );

        } finally {

            if (!connection.isClosed()) {
                connection.close();
            }
        }
    }
}
