package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Teste estrutural do composition root.
 *
 * <p>O teste deliberadamente não executa process().
 * Portanto não acessa a Amazon nem produz registros de negócio.</p>
 */
class AmazonDealProcessingCompositionTest {

    @Test
    void shouldBuildCompleteProcessingService()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();

        try (Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            HttpClient httpClient =
                    HttpClient.newBuilder()
                            .connectTimeout(
                                    Duration.ofSeconds(
                                            5
                                    )
                            )
                            .build();

            AmazonDealProcessingService service =
                    AmazonDealProcessingComposition.create(
                            connection,
                            Clock.systemUTC(),
                            httpClient
                    );

            assertNotNull(
                    service
            );
        }
    }
}