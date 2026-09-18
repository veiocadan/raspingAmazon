package com.raspingamazon.domain.evaluation;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes das invariantes estruturais de DealEvaluation.
 */
class DealEvaluationTest {

    private static final Product PRODUCT =
            new Product(
                    1L,
                    new Asin("B0FN4BK3V7"),
                    "Produto de teste",
                    null,
                    "https://www.amazon.com.br/dp/B0FN4BK3V7"
            );

    private static final OfferSnapshot SNAPSHOT =
            new OfferSnapshot(
                    10L,
                    PRODUCT,
                    OffsetDateTime.parse(
                            "2026-09-13T19:00:00-03:00"
                    ),
                    Money.of("199.90"),
                    null,
                    Money.of("249.90"),
                    null,
                    4.7,
                    1520L,
                    "Amazon.com.br",
                    "Amazon",
                    SellerType.AMAZON,
                    DeliveryType.AMAZON,
                    "amazon-deals",
                    List.of()
            );

    @Test
    void shouldCreateCompleteEvaluation() {

        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-13T19:05:00-03:00"
                );

        DealEvaluation evaluation =
                new DealEvaluation(
                        20L,
                        SNAPSHOT,
                        true,
                        null,

                        "eligibility-v1",
                        "filter-v1",

                        new BigDecimal("85.50"),
                        "score-v1",

                        new BigDecimal("12.30"),
                        "momentum-v1",

                        evaluatedAt
                );

        assertEquals(
                20L,
                evaluation.id()
        );

        assertEquals(
                SNAPSHOT,
                evaluation.offerSnapshot()
        );

        assertTrue(
                evaluation.eligible()
        );

        assertNull(
                evaluation.rejectionReason()
        );

        assertEquals(
                "eligibility-v1",
                evaluation.eligibilityPolicyVersion()
        );

        assertEquals(
                "filter-v1",
                evaluation.filterProfileVersion()
        );

        assertEquals(
                new BigDecimal("85.50"),
                evaluation.score()
        );

        assertEquals(
                "score-v1",
                evaluation.scoreVersion()
        );

        assertEquals(
                new BigDecimal("12.30"),
                evaluation.momentum()
        );

        assertEquals(
                "momentum-v1",
                evaluation.momentumVersion()
        );

        assertEquals(
                evaluatedAt,
                evaluation.evaluatedAt()
        );
    }

    @Test
    void shouldCreateRejectedEvaluation() {

        DealEvaluation evaluation =
                new DealEvaluation(
                        21L,
                        SNAPSHOT,
                        false,
                        RejectionReason.SELLER_THIRD_PARTY,
                        "eligibility-v1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.parse(
                                "2026-09-13T19:05:00-03:00"
                        )
                );

        assertFalse(
                evaluation.eligible()
        );

        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                evaluation.rejectionReason()
        );
    }

    @Test
    void shouldAllowNullIdBeforePersistence() {

        DealEvaluation evaluation =
                createCurrentPhaseEvaluation(
                        null
                );

        assertNull(
                evaluation.id()
        );
    }

    @Test
    void shouldAllowFutureVersionsToRemainNull() {

        DealEvaluation evaluation =
                createCurrentPhaseEvaluation(
                        null
                );

        assertNull(
                evaluation.filterProfileVersion()
        );

        assertNull(
                evaluation.score()
        );

        assertNull(
                evaluation.scoreVersion()
        );

        assertNull(
                evaluation.momentum()
        );

        assertNull(
                evaluation.momentumVersion()
        );
    }

    @Test
    void shouldRejectEligibleEvaluationWithRejectionReason() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        RejectionReason.SELLER_THIRD_PARTY,
                        "eligibility-v1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectRejectedEvaluationWithoutRejectionReason() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        false,
                        null,
                        "eligibility-v1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectNullOfferSnapshot() {

        assertThrows(
                NullPointerException.class,
                () -> new DealEvaluation(
                        null,
                        null,
                        true,
                        null,
                        "eligibility-v1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectNullEligibilityPolicyVersion() {

        assertThrows(
                NullPointerException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectBlankEligibilityPolicyVersion() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "   ",
                        null,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectBlankOptionalVersionFields() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "eligibility-v1",
                        "   ",
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectScoreWithoutScoreVersion() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "eligibility-v1",
                        null,
                        new BigDecimal("50.00"),
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectScoreVersionWithoutScore() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "eligibility-v1",
                        null,
                        null,
                        "score-v1",
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectMomentumWithoutMomentumVersion() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "eligibility-v1",
                        null,
                        null,
                        null,
                        new BigDecimal("10.00"),
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectMomentumVersionWithoutMomentum() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "eligibility-v1",
                        null,
                        null,
                        null,
                        null,
                        "momentum-v1",
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectNullEvaluatedAt() {

        assertThrows(
                NullPointerException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "eligibility-v1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    /**
     * Representa exatamente o estágio atual do projeto:
     *
     * - elegibilidade existente;
     * - filtros ainda não aplicados;
     * - score ainda inexistente;
     * - momentum ainda inexistente.
     */
    private DealEvaluation createCurrentPhaseEvaluation(
            Long id
    ) {
        return new DealEvaluation(
                id,
                SNAPSHOT,
                true,
                null,
                "AMAZON_SELLER_DELIVERY_V1",
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse(
                        "2026-09-13T19:05:00-03:00"
                )
        );
    }
}