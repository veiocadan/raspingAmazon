package com.raspingamazon.domain.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes das invariantes de EvaluationRuleResult.
 */
class EvaluationRuleResultTest {

    @Test
    void shouldCreatePassedRule() {

        EvaluationRuleResult result =
                EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                );

        assertEquals(
                "SELLER_IS_AMAZON",
                result.ruleCode()
        );

        assertTrue(
                result.passed()
        );

        assertEquals(
                "AMAZON",
                result.observedValue()
        );

        assertEquals(
                "AMAZON",
                result.threshold()
        );

        assertNull(
                result.reasonCode()
        );
    }

    @Test
    void shouldCreateFailedRule() {

        EvaluationRuleResult result =
                EvaluationRuleResult.failed(
                        "SELLER_IS_AMAZON",
                        "THIRD_PARTY",
                        "AMAZON",
                        RejectionReason.SELLER_THIRD_PARTY
                );

        assertFalse(
                result.passed()
        );

        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                result.reasonCode()
        );
    }

    @Test
    void shouldRejectPassedRuleWithReason() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationRuleResult(
                        "SELLER_IS_AMAZON",
                        true,
                        "AMAZON",
                        "AMAZON",
                        RejectionReason.SELLER_THIRD_PARTY
                )
        );
    }

    @Test
    void shouldRejectFailedRuleWithoutReason() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluationRuleResult(
                        "SELLER_IS_AMAZON",
                        false,
                        "THIRD_PARTY",
                        "AMAZON",
                        null
                )
        );
    }

    @Test
    void shouldRejectBlankRuleCode() {

        assertThrows(
                IllegalArgumentException.class,
                () -> EvaluationRuleResult.passed(
                        "   ",
                        "AMAZON",
                        "AMAZON"
                )
        );
    }

    @Test
    void shouldRejectBlankObservedValue() {

        assertThrows(
                IllegalArgumentException.class,
                () -> EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "   ",
                        "AMAZON"
                )
        );
    }

    @Test
    void shouldRejectBlankThreshold() {

        assertThrows(
                IllegalArgumentException.class,
                () -> EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "   "
                )
        );
    }
}