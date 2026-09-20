package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.scoring.ScoreProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ScoreProfileJdbcRepositoryTest {

    @Test
    void shouldLoadActiveScoreProfile()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        /*
         * Garante que a V8 tenha sido aplicada antes da leitura.
         */
        DatabaseMigration.migrate(
            config
        );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            ScoreProfileJdbcRepository repository =
                new ScoreProfileJdbcRepository(
                    connection
                );

            ScoreProfile profile =
                repository.activeProfile();

            assertNotNull(
                profile
            );

            assertEquals(
                "SCORE_V1",
                profile.version()
            );

            assertBigDecimalEquals(
                "30.0000",
                profile.soldPercentageWeight()
            );

            assertBigDecimalEquals(
                "25.0000",
                profile.cashDiscountWeight()
            );

            assertBigDecimalEquals(
                "20.0000",
                profile.ratingWeight()
            );

            assertBigDecimalEquals(
                "15.0000",
                profile.reviewCountWeight()
            );

            assertEquals(
                1000L,
                profile.reviewCountFullScoreThreshold()
            );

            assertBigDecimalEquals(
                "90.0000",
                profile.totalActiveWeight()
            );
        }
    }

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {
        assertEquals(
            0,
            new BigDecimal(expected).compareTo(actual)
        );
    }
}
