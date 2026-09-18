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

    private static final List<EvaluationRuleResult> PASSED_RULES =
            List.of(
                    EvaluationRuleResult.passed(
                            "SELLER_IS_AMAZON",
                            "AMAZON",
                            "AMAZON"
                    ),
                    EvaluationRuleResult.passed(
                            "DELIVERY_IS_AMAZON",
                            "AMAZON",
                            "AMAZON"
                    )
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
                        PASSED_RULES,
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
                2,
                evaluation.ruleResults().size()
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
    void shouldCreateRejectedEvaluationWithMultipleFailures() {

        List<EvaluationRuleResult> rules =
                List.of(
                        EvaluationRuleResult.failed(
                                "SELLER_IS_AMAZON",
                                "THIRD_PARTY",
                                "AMAZON",
                                RejectionReason.SELLER_THIRD_PARTY
                        ),
                        EvaluationRuleResult.failed(
                                "DELIVERY_IS_AMAZON",
                                "THIRD_PARTY",
                                "AMAZON",
                                RejectionReason.DELIVERY_THIRD_PARTY
                        )
                );

        DealEvaluation evaluation =
                new DealEvaluation(
                        21L,
                        SNAPSHOT,
                        false,
                        RejectionReason.SELLER_THIRD_PARTY,
                        "eligibility-v1",
                        null,
                        rules,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                );

        assertFalse(
                evaluation.eligible()
        );

        assertEquals(
                2,
                evaluation.ruleResults().size()
        );

        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                evaluation.rejectionReason()
        );

        assertEquals(
                RejectionReason.DELIVERY_THIRD_PARTY,
                evaluation.ruleResults()
                        .get(1)
                        .reasonCode()
        );
    }

    @Test
    void shouldRejectEligibleEvaluationWithFailedRule() {

        List<EvaluationRuleResult> rules =
                List.of(
                        EvaluationRuleResult.failed(
                                "SELLER_IS_AMAZON",
                                "THIRD_PARTY",
                                "AMAZON",
                                RejectionReason.SELLER_THIRD_PARTY
                        )
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "eligibility-v1",
                        null,
                        rules,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectIneligibleEvaluationWhenAllRulesPassed() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        false,
                        RejectionReason.SELLER_THIRD_PARTY,
                        "eligibility-v1",
                        null,
                        PASSED_RULES,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectWrongPrimaryRejectionReason() {

        List<EvaluationRuleResult> rules =
                List.of(
                        EvaluationRuleResult.failed(
                                "SELLER_IS_AMAZON",
                                "THIRD_PARTY",
                                "AMAZON",
                                RejectionReason.SELLER_THIRD_PARTY
                        ),
                        EvaluationRuleResult.failed(
                                "DELIVERY_IS_AMAZON",
                                "THIRD_PARTY",
                                "AMAZON",
                                RejectionReason.DELIVERY_THIRD_PARTY
                        )
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        false,

                        /*
                         * Incorreto:
                         * a primeira falha é SELLER.
                         */
                        RejectionReason.DELIVERY_THIRD_PARTY,

                        "eligibility-v1",
                        null,
                        rules,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectEmptyRuleResults() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new DealEvaluation(
                        null,
                        SNAPSHOT,
                        true,
                        null,
                        "eligibility-v1",
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectNullRuleResults() {

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
                        PASSED_RULES,
                        new BigDecimal("50.00"),
                        null,
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
                        PASSED_RULES,
                        null,
                        null,
                        new BigDecimal("10.00"),
                        null,
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
                        PASSED_RULES,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}