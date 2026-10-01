package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.publication.outbox.PublicationOutboxFanoutEnqueueService;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.config.PublicationChannelActivationConfig;
import com.raspingamazon.infrastructure.config.WhatsAppManualStagingConfig;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxEnqueueAdapter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PostgresIntegrationTest
class TelegramPublicationOutboxEnqueueCompositionTest {

    @Test
    void shouldUseOriginalPrimaryEnqueueWhenManualWhatsAppIsDisabled()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            PublicationOutboxEnqueuePort port =
                TelegramPublicationOutboxEnqueueComposition.create(
                    connection,
                    new PublicationChannelActivationConfig(
                        true,
                        false,
                        false
                    ),
                    null
                );

            assertInstanceOf(
                JdbcPublicationOutboxEnqueueAdapter.class,
                port
            );
        }
    }

    @Test
    void shouldUseFanoutWhenManualWhatsAppIsEnabled()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            PublicationOutboxEnqueuePort port =
                TelegramPublicationOutboxEnqueueComposition.create(
                    connection,
                    new PublicationChannelActivationConfig(
                        true,
                        true,
                        false
                    ),
                    new WhatsAppManualStagingConfig(
                        "-1001234567890"
                    )
                );

            assertInstanceOf(
                PublicationOutboxFanoutEnqueueService.class,
                port
            );
        }
    }

    @Test
    void shouldRequireStagingConfigOnlyWhenManualWhatsAppIsEnabled()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            assertThrows(
                NullPointerException.class,
                () ->
                    TelegramPublicationOutboxEnqueueComposition.create(
                        connection,
                        new PublicationChannelActivationConfig(
                            true,
                            true,
                            false
                        ),
                        null
                    )
            );
        }
    }

    @Test
    void shouldNotRequireAutomaticWhatsAppToEnableManualFanout()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            PublicationOutboxEnqueuePort port =
                TelegramPublicationOutboxEnqueueComposition.create(
                    connection,
                    new PublicationChannelActivationConfig(
                        true,
                        true,
                        false
                    ),
                    new WhatsAppManualStagingConfig(
                        "-1001234567890"
                    )
                );

            assertInstanceOf(
                PublicationOutboxFanoutEnqueueService.class,
                port
            );
        }
    }
}
