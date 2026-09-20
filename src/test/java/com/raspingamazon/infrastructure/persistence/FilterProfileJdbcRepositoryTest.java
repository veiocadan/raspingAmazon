package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.filter.FilterProfile;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FilterProfileJdbcRepositoryTest {

    @Test
    void shouldLoadActiveCommercialFilterProfile()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        /*
         * Garante que V7 tenha sido aplicada antes da leitura.
         */
        DatabaseMigration.migrate(
            config
        );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            FilterProfileJdbcRepository repository =
                new FilterProfileJdbcRepository(
                    connection
                );

            FilterProfile profile =
                repository.activeProfile();

            assertNotNull(
                profile
            );

            assertEquals(
                "COMMERCIAL_FILTER_V1",
                profile.version()
            );

            assertEquals(
                Percentage.of("20.00"),
                profile.minCashDiscountPercentage()
            );

            assertEquals(
                new BigDecimal("4.30"),
                profile.minRating()
            );

            assertEquals(
                100L,
                profile.minReviewCount()
            );
        }
    }
}
