package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.application.scoring.ScoreProfileProvider;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.filter.BestCashDiscountSelector;
import com.raspingamazon.domain.filter.CommercialFilterEngine;
import com.raspingamazon.domain.filter.FilterProfile;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.scoring.ScoreEngine;
import com.raspingamazon.domain.scoring.ScoreFactorCode;
import com.raspingamazon.domain.scoring.ScoreFactorStatus;
import com.raspingamazon.domain.scoring.ScoreProfile;
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

    private static final ScoreProfile SCORE_PROFILE =
        new ScoreProfile(
            "SCORE_V1",
            new BigDecimal("30"),
            new BigDecimal("25"),
            new BigDecimal("20"),
            new BigDecimal("15"),
            1000L
        );

    private static final FilterProfileProvider FILTER_PROFILE_PROVIDER =
        () -> FILTER_PROFILE;

    private static final ScoreProfileProvider SCORE_PROFILE_PROVIDER =
        () -> SCORE_PROFILE;

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

        /*
         * Oferta rejeitada não entra no estágio de score.
         */
        assertNull(
            persisted.score()
        );

        assertNull(
            persisted.scoreVersion()
        );

        assertTrue(
            persisted.scoreFactors().isEmpty()
        );
    }

    @Test
    void shouldEvaluateScoreForAcceptedDeal() {

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

        /*
         * Snapshot:
         *
         * soldPercentage = null
         * cashDiscount   = 25
         * rating         = 4.7
         * reviewCount    = 1500
         *
         * SCORE_V1:
         *
         * sold:
         * unavailable -> 0
         *
         * cash:
         * 25 / 100 * 25 = 6.25
         *
         * rating:
         * 4.7 / 5 * 100 = 94
         * 94 / 100 * 20 = 18.8
         *
         * reviews:
         * 1500 >= 1000
         * normalized = 100
         * contribution = 15
         *
         * total = 40.05
         */
        assertBigDecimalEquals(
            "40.0500",
            persisted.score()
        );

        assertEquals(
            "SCORE_V1",
            persisted.scoreVersion()
        );

        assertEquals(
            4,
            persisted.scoreFactors().size()
        );

        assertEquals(
            ScoreFactorStatus.UNAVAILABLE,
            persisted.scoreFactors()
                .get(0)
                .status()
        );

        assertEquals(
            ScoreFactorCode.SOLD_PERCENTAGE,
            persisted.scoreFactors()
                .get(0)
                .code()
        );

        assertEquals(
            1,
            repository.savedEvaluations.size()
        );
    }

    @Test
    void shouldIncludeObservedSoldPercentageInScore() {

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
                    Percentage.of("80"),
                    "25",
                    4.7,
                    1500L
                ),
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                OffsetDateTime.now()
            );

        /*
         * Score anterior sem soldPercentage = 40.05
         *
         * soldPercentage:
         *
         * 80 / 100 * 30 = 24
         *
         * total = 64.05
         */
        assertBigDecimalEquals(
            "64.0500",
            persisted.score()
        );

        assertEquals(
            ScoreFactorStatus.AVAILABLE,
            persisted.scoreFactors()
                .get(0)
                .status()
        );

        assertBigDecimalEquals(
            "80",
            persisted.scoreFactors()
                .get(0)
                .rawValue()
        );

        assertBigDecimalEquals(
            "24.0000",
            persisted.scoreFactors()
                .get(0)
                .contribution()
        );
    }

    @Test
    void shouldRejectAmazonDealWhenCommercialDiscountFailsWithoutScore() {

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
                    null,
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

        assertNull(
            persisted.score()
        );

        assertNull(
            persisted.scoreVersion()
        );

        assertTrue(
            persisted.scoreFactors().isEmpty()
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
            SCORE_PROFILE_PROVIDER,
            new ScoreEngine(),
            new BestCashDiscountSelector(),
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
            null,
            "25",
            4.7,
            1500L
        );
    }

    private OfferSnapshot createSnapshot(
        SellerType sellerType,
        DeliveryType deliveryType,
        Percentage soldPercentage,
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
            soldPercentage,
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

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {
        assertEquals(
            0,
            new BigDecimal(expected).compareTo(actual)
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
