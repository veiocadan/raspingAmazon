package com.raspingamazon.domain.validation;

import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa a política estrutural Amazon e sua explicabilidade.
 */
class AmazonEligibilityValidatorTest {

    private final AmazonEligibilityValidator validator =
            new AmazonEligibilityValidator();

    @Test
    void shouldAcceptAmazonSellerAndAmazonDelivery() {

        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.AMAZON,
                        DeliveryType.AMAZON
                );

        assertTrue(
                result.eligible()
        );

        assertNull(
                result.rejectionReason()
        );

        assertEquals(
                2,
                result.ruleResults().size()
        );

        assertTrue(
                result.ruleResults()
                        .stream()
                        .allMatch(
                                EvaluationRuleResult::passed
                        )
        );
    }

    @Test
    void shouldRejectThirdPartySeller() {

        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.THIRD_PARTY,
                        DeliveryType.AMAZON
                );

        assertFalse(
                result.eligible()
        );

        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                result.rejectionReason()
        );

        EvaluationRuleResult seller =
                result.ruleResults().get(0);

        assertEquals(
                "SELLER_IS_AMAZON",
                seller.ruleCode()
        );

        assertFalse(
                seller.passed()
        );

        assertEquals(
                "THIRD_PARTY",
                seller.observedValue()
        );

        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                seller.reasonCode()
        );

        EvaluationRuleResult delivery =
                result.ruleResults().get(1);

        assertTrue(
                delivery.passed()
        );
    }

    @Test
    void shouldRejectUnknownSeller() {

        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.UNKNOWN,
                        DeliveryType.AMAZON
                );

        assertFalse(
                result.eligible()
        );

        assertEquals(
                RejectionReason.SELLER_UNKNOWN,
                result.rejectionReason()
        );
    }

    @Test
    void shouldRejectAmazonSellerWithThirdPartyDelivery() {

        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.AMAZON,
                        DeliveryType.THIRD_PARTY
                );

        assertFalse(
                result.eligible()
        );

        assertEquals(
                RejectionReason.DELIVERY_THIRD_PARTY,
                result.rejectionReason()
        );

        EvaluationRuleResult delivery =
                result.ruleResults().get(1);

        assertFalse(
                delivery.passed()
        );

        assertEquals(
                "DELIVERY_IS_AMAZON",
                delivery.ruleCode()
        );

        assertEquals(
                "THIRD_PARTY",
                delivery.observedValue()
        );
    }

    @Test
    void shouldRejectAmazonSellerWithUnknownDelivery() {

        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.AMAZON,
                        DeliveryType.UNKNOWN
                );

        assertFalse(
                result.eligible()
        );

        assertEquals(
                RejectionReason.DELIVERY_UNKNOWN,
                result.rejectionReason()
        );
    }

    @Test
    void shouldPreserveBothFailuresWhenSellerAndDeliveryFail() {

        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.THIRD_PARTY,
                        DeliveryType.THIRD_PARTY
                );

        assertFalse(
                result.eligible()
        );

        /*
         * O motivo principal continua seller para preservar
         * o comportamento histórico.
         */
        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                result.rejectionReason()
        );

        List<EvaluationRuleResult> rules =
                result.ruleResults();

        assertEquals(
                2,
                rules.size()
        );

        EvaluationRuleResult seller =
                rules.get(0);

        assertFalse(
                seller.passed()
        );

        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                seller.reasonCode()
        );

        EvaluationRuleResult delivery =
                rules.get(1);

        /*
         * Este é o comportamento novo:
         * mesmo com seller inválido, delivery também foi avaliado.
         */
        assertFalse(
                delivery.passed()
        );

        assertEquals(
                RejectionReason.DELIVERY_THIRD_PARTY,
                delivery.reasonCode()
        );
    }

    @Test
    void shouldPreserveBothUnknownFailures() {

        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.UNKNOWN,
                        DeliveryType.UNKNOWN
                );

        assertFalse(
                result.eligible()
        );

        assertEquals(
                RejectionReason.SELLER_UNKNOWN,
                result.rejectionReason()
        );

        assertEquals(
                RejectionReason.SELLER_UNKNOWN,
                result.ruleResults()
                        .get(0)
                        .reasonCode()
        );

        assertEquals(
                RejectionReason.DELIVERY_UNKNOWN,
                result.ruleResults()
                        .get(1)
                        .reasonCode()
        );
    }
}