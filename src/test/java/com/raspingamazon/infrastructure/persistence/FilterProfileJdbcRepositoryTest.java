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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integração entre Flyway, PostgreSQL e o provider do perfil
 * comercial ativo.
 *
 * <p>A partir da V16 o perfil operacional esperado é
 * COMMERCIAL_FILTER_V2.</p>
 */
class FilterProfileJdbcRepositoryTest {

    @Test
    void shouldLoadActiveCommercialFilterProfile()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        /*
         * Garante que todas as migrations, inclusive V16,
         * tenham sido aplicadas.
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
                "COMMERCIAL_FILTER_V2",
                profile.version()
            );

            /*
             * A semântica histórica CASH não é reutilizada.
             */
            assertNull(
                profile.minCashDiscountPercentage()
            );

            assertEquals(
                Percentage.of(
                    "20.0000"
                ),
                profile.minBasisDiscountPercentage()
            );

            assertFalse(
                profile.usesCashDiscountRule()
            );

            assertTrue(
                profile.usesBasisDiscountRule()
            );

            assertEquals(
                new BigDecimal(
                    "4.30"
                ),
                profile.minRating()
            );

            assertEquals(
                100L,
                profile.minReviewCount()
            );
        }
    }
}
