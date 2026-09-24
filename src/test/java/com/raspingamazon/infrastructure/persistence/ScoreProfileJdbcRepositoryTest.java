package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.scoring.ScoreProfile;
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
 * Integração entre Flyway, PostgreSQL e o provider do ScoreProfile
 * ativo.
 *
 * <p>A partir da V16 o perfil operacional esperado é SCORE_V2.</p>
 */
class ScoreProfileJdbcRepositoryTest {

    @Test
    void shouldLoadActiveScoreProfile()
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
                "SCORE_V2",
                profile.version()
            );

            assertBigDecimalEquals(
                "30.0000",
                profile.soldPercentageWeight()
            );

            /*
             * CASH_DISCOUNT permanece histórico no SCORE_V1.
             */
            assertNull(
                profile.cashDiscountWeight()
            );

            assertBigDecimalEquals(
                "25.0000",
                profile.basisDiscountWeight()
            );

            assertFalse(
                profile.usesCashDiscountFactor()
            );

            assertTrue(
                profile.usesBasisDiscountFactor()
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

        assertNotNull(
            actual
        );

        assertEquals(
            0,
            new BigDecimal(
                expected
            ).compareTo(
                actual
            )
        );
    }
}
