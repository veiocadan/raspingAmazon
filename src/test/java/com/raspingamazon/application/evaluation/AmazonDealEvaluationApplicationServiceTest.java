package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.filter.CommercialFilterEngine;
import com.raspingamazon.domain.filter.FilterProfile;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonDealEvaluationApplicationServiceTest {

    private static final FilterProfile FILTER_PROFILE =
        new FilterProfile(
            "COMMERCIAL_FILTER_V1",
            Percentage.of("20"),
            new BigDecimal("4.3"),
            100L
        );

    private static final FilterProfileProvider FILTER_PROFILE_PROVIDER =
        () -> FILTER_PROFILE;

    @Test
    void shouldEvaluateAndPersistRejectedStructuralDealWithAllRuleResults() {

        FakeDealEvaluationRepository repository =
            new FakeDealEvaluationRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                repository
            );

        DealEvaluation persisted =
            service.evaluate(
                createPassingCommercialSnapshot(
                    SellerType.THIRD_PARTY,
                    DeliveryType.THIRD_PARTY
                ),
                SellerType.THIRD_PARTY,
                DeliveryType.THIRD_PARTY,
                OffsetDateTime.now()
            );

        assertSame(
            persisted,
            repository.savedEvaluation
        );

        assertFalse(
            persisted.eligible()
        );

        /*
         * A falha estrutural permanece prioritária porque as regras
         * de seller/delivery vêm antes das comerciais.
         */
        assertEquals(
            RejectionReason.SELLER_THIRD_PARTY,
            persisted.rejectionReason()
        );

        assertEquals(
            "AMAZON_SELLER_DELIVERY_V1",
            persisted.eligibilityPolicyVersion()
        );

        assertEquals(
            "COMMERCIAL_FILTER_V1",
            persisted.filterProfileVersion()
        );

        assertEquals(
            5,
            persisted.ruleResults().size()
        );

        assertFalse(
            persisted.ruleResults()
                .get(0)
                .passed()
        );

        assertFalse(
            persisted.ruleResults()
                .get(1)
                .passed()
        );

        /*
         * Apesar da falha estrutural, as três regras comerciais
         * continuam sendo avaliadas.
         */
        assertTrue(
            persisted.ruleResults()
                .get(2)
                .passed()
        );

        assertTrue(
            persisted.ruleResults()
                .get(3)
                .passed()
        );

        assertTrue(
            persisted.ruleResults()
                .get(4)
                .passed()
        );
    }

    @Test
    void shouldEvaluateAndPersistAcceptedDealWhenAllRulesPass() {

        FakeDealEvaluationRepository repository =
            new FakeDealEvaluationRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                repository
            );

        DealEvaluation persisted =
            service.evaluate(
                createPassingCommercialSnapshot(
                    SellerType.AMAZON,
                    DeliveryType.AMAZON
                ),
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                OffsetDateTime.now()
            );

        assertTrue(
            persisted.eligible()
        );

        assertNull(
            persisted.rejectionReason()
        );

        assertEquals(
            "COMMERCIAL_FILTER_V1",
            persisted.filterProfileVersion()
        );

        assertEquals(
            5,
            persisted.ruleResults().size()
        );

        assertTrue(
            persisted.ruleResults()
                .stream()
                .allMatch(
                    result ->
                        result.passed()
                )
        );

        assertEquals(
            1,
            repository.savedEvaluations.size()
        );
    }

    @Test
    void shouldRejectAmazonDealWhenCommercialDiscountFails() {

        FakeDealEvaluationRepository repository =
            new FakeDealEvaluationRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                repository
            );

        DealEvaluation persisted =
            service.evaluate(
                createSnapshot(
                    SellerType.AMAZON,
                    DeliveryType.AMAZON,
                    "10",
                    4.7,
                    1500L
                ),
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                OffsetDateTime.now()
            );

        assertFalse(
            persisted.eligible()
        );

        assertEquals(
            RejectionReason.CASH_DISCOUNT_BELOW_MINIMUM,
            persisted.rejectionReason()
        );

        assertEquals(
            5,
            persisted.ruleResults().size()
        );

        assertTrue(
            persisted.ruleResults()
                .get(0)
                .passed()
        );

        assertTrue(
            persisted.ruleResults()
                .get(1)
                .passed()
        );

        assertFalse(
            persisted.ruleResults()
                .get(2)
                .passed()
        );

        assertEquals(
            RejectionReason.CASH_DISCOUNT_BELOW_MINIMUM,
            persisted.ruleResults()
                .get(2)
                .reasonCode()
        );

        assertTrue(
            persisted.ruleResults()
                .get(3)
                .passed()
        );

        assertTrue(
            persisted.ruleResults()
                .get(4)
                .passed()
        );
    }

    @Test
    void shouldPreserveStableCombinedRuleOrder() {

        FakeDealEvaluationRepository repository =
            new FakeDealEvaluationRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                repository
            );

        DealEvaluation evaluation =
            service.evaluate(
                createPassingCommercialSnapshot(
                    SellerType.AMAZON,
                    DeliveryType.AMAZON
                ),
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                OffsetDateTime.now()
            );

        assertEquals(
            "SELLER_IS_AMAZON",
            evaluation.ruleResults()
                .get(0)
                .ruleCode()
        );

        assertEquals(
            "DELIVERY_IS_AMAZON",
            evaluation.ruleResults()
                .get(1)
                .ruleCode()
        );

        assertEquals(
            "MIN_CASH_DISCOUNT",
            evaluation.ruleResults()
                .get(2)
                .ruleCode()
        );

        assertEquals(
            "MIN_RATING",
            evaluation.ruleResults()
                .get(3)
                .ruleCode()
        );

        assertEquals(
            "MIN_REVIEW_COUNT",
            evaluation.ruleResults()
                .get(4)
                .ruleCode()
        );
    }

    private AmazonDealEvaluationApplicationService createService(
        FakeDealEvaluationRepository repository
    ) {
        return new AmazonDealEvaluationApplicationService(
            new AmazonEligibilityValidator(),
            new CommercialFilterEngine(),
            FILTER_PROFILE_PROVIDER,
            repository
        );
    }

    private OfferSnapshot createPassingCommercialSnapshot(
        SellerType sellerType,
        DeliveryType deliveryType
    ) {
        return createSnapshot(
            sellerType,
            deliveryType,
            "25",
            4.7,
            1500L
        );
    }

    private OfferSnapshot createSnapshot(
        SellerType sellerType,
        DeliveryType deliveryType,
        String cashDiscount,
        Double rating,
        Long reviewCount
    ) {

        Product product =
            new Product(
                1L,
                new Asin(
                    "B000TEST86"
                ),
                "Produto de teste",
                null,
                "https://example.invalid/produto"
            );

        PaymentCondition cash =
            new PaymentCondition(
                PaymentConditionType.CASH,
                Money.of(
                    "79.90"
                ),
                Percentage.of(
                    cashDiscount
                ),
                null,
                null,
                null,
                null,
                List.of(
                    PaymentMethod.PIX,
                    PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                )
            );

        return new OfferSnapshot(
            1L,
            product,
            OffsetDateTime.now(),
            Money.of(
                "99.90"
            ),
            null,
            null,
            null,
            rating,
            reviewCount,
            "Vendedor teste",
            "Amazon",
            sellerType,
            deliveryType,
            "TEST",
            List.of(
                cash
            )
        );
    }

    private static final class FakeDealEvaluationRepository
        implements DealEvaluationRepository {

        private final List<DealEvaluation> savedEvaluations =
            new ArrayList<>();

        private DealEvaluation savedEvaluation;

        @Override
        public DealEvaluation save(
            DealEvaluation evaluation
        ) {
            savedEvaluation =
                evaluation;

            savedEvaluations.add(
                evaluation
            );

            return evaluation;
        }
    }
}
