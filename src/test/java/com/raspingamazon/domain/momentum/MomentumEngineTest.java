package com.raspingamazon.domain.momentum;

import com.raspingamazon.domain.history.SnapshotEvolution;
import com.raspingamazon.domain.product.Asin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentumEngineTest {

    private static final Asin ASIN =
        new Asin(
            "B0HIST1101"
        );

    private final MomentumEngine engine =
        new MomentumEngine();

    @Test
    void shouldCalculatePositiveMomentumInPercentagePointsPerHour() {

        SnapshotEvolution evolution =
            evolution(
                "2026-09-20T10:00:00-03:00",
                "2026-09-20T13:00:00-03:00",
                "6"
            );

        MomentumResult result =
            engine.calculate(
                evolution
            );

        assertTrue(
            result.isAvailable()
        );

        assertEquals(
            "MOMENTUM_V1",
            result.version()
        );

        assertBigDecimalEquals(
            "2.0000",
            result.value()
        );

        assertNull(
            result.unavailableReason()
        );
    }

    @Test
    void shouldPreserveNegativeMomentum() {

        SnapshotEvolution evolution =
            evolution(
                "2026-09-20T10:00:00-03:00",
                "2026-09-20T14:00:00-03:00",
                "-8"
            );

        MomentumResult result =
            engine.calculate(
                evolution
            );

        assertTrue(
            result.isAvailable()
        );

        assertBigDecimalEquals(
            "-2.0000",
            result.value()
        );
    }

    @Test
    void shouldProduceAvailableZeroMomentumWhenSoldPercentageDidNotChange() {

        SnapshotEvolution evolution =
            evolution(
                "2026-09-20T10:00:00-03:00",
                "2026-09-20T13:00:00-03:00",
                "0"
            );

        MomentumResult result =
            engine.calculate(
                evolution
            );

        /*
         * Zero observado é diferente de informação indisponível.
         */
        assertTrue(
            result.isAvailable()
        );

        assertBigDecimalEquals(
            "0.0000",
            result.value()
        );

        assertNull(
            result.unavailableReason()
        );
    }

    @Test
    void shouldSupportIntervalsThatAreNotWholeHours() {

        SnapshotEvolution evolution =
            evolution(
                "2026-09-20T10:00:00-03:00",
                "2026-09-20T11:30:00-03:00",
                "3"
            );

        MomentumResult result =
            engine.calculate(
                evolution
            );

        /*
         * 3 p.p. / 1.5 h = 2 p.p./h
         */
        assertBigDecimalEquals(
            "2.0000",
            result.value()
        );
    }

    @Test
    void shouldRoundMomentumUsingFourDecimalPlacesAndHalfUp() {

        SnapshotEvolution evolution =
            evolution(
                "2026-09-20T10:00:00-03:00",
                "2026-09-20T13:00:00-03:00",
                "1"
            );

        MomentumResult result =
            engine.calculate(
                evolution
            );

        /*
         * 1 / 3 = 0.333333...
         */
        assertBigDecimalEquals(
            "0.3333",
            result.value()
        );

        assertEquals(
            4,
            result.value()
                .scale()
        );
    }

    @Test
    void shouldCalculateDocumentedTwentyOnePointEvolution() {

        SnapshotEvolution evolution =
            evolution(
                "2026-09-20T10:00:00-03:00",
                "2026-09-20T19:00:00-03:00",
                "21"
            );

        MomentumResult result =
            engine.calculate(
                evolution
            );

        /*
         * 21 p.p. / 9 h = 2.333333...
         */
        assertBigDecimalEquals(
            "2.3333",
            result.value()
        );
    }

    @Test
    void shouldReturnUnavailableWhenSoldPercentageDeltaIsMissing() {

        SnapshotEvolution evolution =
            new SnapshotEvolution(
                ASIN,
                1L,
                2L,
                OffsetDateTime.parse(
                    "2026-09-20T10:00:00-03:00"
                ),
                OffsetDateTime.parse(
                    "2026-09-20T13:00:00-03:00"
                ),
                null,
                new BigDecimal(
                    "-10.00"
                ),
                new BigDecimal(
                    "-10.0000"
                ),
                new BigDecimal(
                    "5"
                )
            );

        MomentumResult result =
            engine.calculate(
                evolution
            );

        assertFalse(
            result.isAvailable()
        );

        assertEquals(
            "MOMENTUM_V1",
            result.version()
        );

        assertNull(
            result.value()
        );

        assertEquals(
            MomentumUnavailableReason
                .SOLD_PERCENTAGE_UNAVAILABLE,
            result.unavailableReason()
        );
    }

    @Test
    void shouldRepresentMissingPreviousSnapshotExplicitly() {

        MomentumResult result =
            MomentumResult.unavailable(
                MomentumEngine.VERSION,
                MomentumUnavailableReason
                    .NO_PREVIOUS_SNAPSHOT
            );

        assertFalse(
            result.isAvailable()
        );

        assertEquals(
            "MOMENTUM_V1",
            result.version()
        );

        assertNull(
            result.value()
        );

        assertEquals(
            MomentumUnavailableReason
                .NO_PREVIOUS_SNAPSHOT,
            result.unavailableReason()
        );
    }

    @Test
    void shouldRejectNullEvolution() {

        assertThrows(
            NullPointerException.class,
            () ->
                engine.calculate(
                    null
                )
        );
    }

    private SnapshotEvolution evolution(
        String previousCollectedAt,
        String currentCollectedAt,
        String soldPercentageDelta
    ) {

        return new SnapshotEvolution(
            ASIN,
            1L,
            2L,
            OffsetDateTime.parse(
                previousCollectedAt
            ),
            OffsetDateTime.parse(
                currentCollectedAt
            ),
            new BigDecimal(
                soldPercentageDelta
            ),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {

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
