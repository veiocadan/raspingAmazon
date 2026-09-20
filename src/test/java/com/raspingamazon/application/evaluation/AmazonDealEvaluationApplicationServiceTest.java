package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.application.history.OfferHistoryQueryPort;
import com.raspingamazon.application.momentum.MomentumAuditRepository;
import com.raspingamazon.application.momentum.MomentumCalculationService;
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
import com.raspingamazon.domain.history.HistoricalOfferObservation;
import com.raspingamazon.domain.history.SnapshotEvolutionCalculator;
import com.raspingamazon.domain.momentum.MomentumAudit;
import com.raspingamazon.domain.momentum.MomentumEngine;
import com.raspingamazon.domain.momentum.MomentumUnavailableReason;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonDealEvaluationApplicationServiceTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-20T13:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-20T13:00:30-03:00"
        );

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

        FakeDealEvaluationRepository evaluationRepository =
            new FakeDealEvaluationRepository();

        FakeMomentumAuditRepository auditRepository =
            new FakeMomentumAuditRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                evaluationRepository,
                auditRepository,
                null
            );

        DealEvaluation persisted =
            service.evaluate(
                createPassingCommercialSnapshot(
                    SellerType.THIRD_PARTY,
                    DeliveryType.THIRD_PARTY
                ),
                SellerType.THIRD_PARTY,
                DeliveryType.THIRD_PARTY,
                EVALUATED_AT
            );

        assertSame(
            persisted,
            evaluationRepository.savedEvaluation
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

        /*
         * Primeira observação:
         *
         * DealEvaluation não recebe momentum.
         */
        assertNull(
            persisted.momentum()
        );

        assertNull(
            persisted.momentumVersion()
        );

        /*
         * A tentativa permanece auditada.
         */
        assertNotNull(
            auditRepository.savedAudit
        );

        assertEquals(
            persisted.id().longValue(),
            auditRepository.savedAudit
                .dealEvaluationId()
        );

        assertFalse(
            auditRepository.savedAudit
                .isAvailable()
        );

        assertEquals(
            MomentumUnavailableReason.NO_PREVIOUS_SNAPSHOT,
            auditRepository.savedAudit
                .unavailableReason()
        );
    }

    @Test
    void shouldEvaluateScoreForAcceptedDeal() {

        FakeDealEvaluationRepository evaluationRepository =
            new FakeDealEvaluationRepository();

        FakeMomentumAuditRepository auditRepository =
            new FakeMomentumAuditRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                evaluationRepository,
                auditRepository,
                null
            );

        DealEvaluation persisted =
            service.evaluate(
                createPassingCommercialSnapshot(
                    SellerType.AMAZON,
                    DeliveryType.AMAZON
                ),
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                EVALUATED_AT
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
            evaluationRepository
                .savedEvaluations
                .size()
        );

        /*
         * Nenhum histórico anterior:
         *
         * score continua normal;
         * momentum continua indisponível.
         */
        assertNull(
            persisted.momentum()
        );

        assertNull(
            persisted.momentumVersion()
        );

        assertEquals(
            1,
            auditRepository
                .savedAudits
                .size()
        );

        assertEquals(
            MomentumUnavailableReason.NO_PREVIOUS_SNAPSHOT,
            auditRepository.savedAudit
                .unavailableReason()
        );
    }

    @Test
    void shouldIncludeObservedSoldPercentageInScore() {

        FakeDealEvaluationRepository evaluationRepository =
            new FakeDealEvaluationRepository();

        FakeMomentumAuditRepository auditRepository =
            new FakeMomentumAuditRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                evaluationRepository,
                auditRepository,
                null
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
                EVALUATED_AT
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

        /*
         * Sold percentage atual existir não basta para momentum.
         * Ainda precisamos de uma observação anterior.
         */
        assertNull(
            persisted.momentum()
        );
    }

    @Test
    void shouldRejectAmazonDealWhenCommercialDiscountFailsWithoutScore() {

        FakeDealEvaluationRepository evaluationRepository =
            new FakeDealEvaluationRepository();

        FakeMomentumAuditRepository auditRepository =
            new FakeMomentumAuditRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                evaluationRepository,
                auditRepository,
                null
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
                EVALUATED_AT
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

        assertNotNull(
            auditRepository.savedAudit
        );
    }

    @Test
    void shouldPreserveStableCombinedRuleOrder() {

        FakeDealEvaluationRepository evaluationRepository =
            new FakeDealEvaluationRepository();

        FakeMomentumAuditRepository auditRepository =
            new FakeMomentumAuditRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                evaluationRepository,
                auditRepository,
                null
            );

        DealEvaluation evaluation =
            service.evaluate(
                createPassingCommercialSnapshot(
                    SellerType.AMAZON,
                    DeliveryType.AMAZON
                ),
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                EVALUATED_AT
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

    @Test
    void shouldPersistAvailableMomentumForEligibleDealWithoutChangingScore() {

        OfferSnapshot current =
            createSnapshot(
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                Percentage.of("80"),
                "25",
                4.7,
                1500L
            );

        HistoricalOfferObservation previous =
            createPreviousObservation(
                current,
                900L,
                "109.90",
                "74"
            );

        FakeDealEvaluationRepository evaluationRepository =
            new FakeDealEvaluationRepository();

        FakeMomentumAuditRepository auditRepository =
            new FakeMomentumAuditRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                evaluationRepository,
                auditRepository,
                previous
            );

        DealEvaluation persisted =
            service.evaluate(
                current,
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                EVALUATED_AT
            );

        assertTrue(
            persisted.eligible()
        );

        /*
         * SCORE_V1 permanece exatamente o mesmo.
         */
        assertBigDecimalEquals(
            "64.0500",
            persisted.score()
        );

        assertEquals(
            "SCORE_V1",
            persisted.scoreVersion()
        );

        /*
         * 74% -> 80% em 3 horas
         *
         * delta = 6 p.p.
         * momentum = 2 p.p./h
         */
        assertBigDecimalEquals(
            "2.0000",
            persisted.momentum()
        );

        assertEquals(
            "MOMENTUM_V1",
            persisted.momentumVersion()
        );

        assertNotNull(
            auditRepository.savedAudit
        );

        assertTrue(
            auditRepository.savedAudit
                .isAvailable()
        );

        assertEquals(
            900L,
            auditRepository.savedAudit
                .previousOfferSnapshotId()
        );

        assertBigDecimalEquals(
            "6",
            auditRepository.savedAudit
                .soldPercentageDelta()
        );

        assertBigDecimalEquals(
            "2.0000",
            auditRepository.savedAudit
                .momentum()
        );

        assertEquals(
            persisted.id().longValue(),
            auditRepository.savedAudit
                .dealEvaluationId()
        );
    }

    @Test
    void shouldPersistAvailableMomentumForRejectedDealWithoutChangingEligibility() {

        OfferSnapshot current =
            createSnapshot(
                SellerType.THIRD_PARTY,
                DeliveryType.AMAZON,
                Percentage.of("80"),
                "25",
                4.7,
                1500L
            );

        HistoricalOfferObservation previous =
            createPreviousObservation(
                current,
                901L,
                "109.90",
                "74"
            );

        FakeDealEvaluationRepository evaluationRepository =
            new FakeDealEvaluationRepository();

        FakeMomentumAuditRepository auditRepository =
            new FakeMomentumAuditRepository();

        AmazonDealEvaluationApplicationService service =
            createService(
                evaluationRepository,
                auditRepository,
                previous
            );

        DealEvaluation persisted =
            service.evaluate(
                current,
                SellerType.THIRD_PARTY,
                DeliveryType.AMAZON,
                EVALUATED_AT
            );

        /*
         * Momentum não pode transformar uma oferta rejeitada
         * em elegível.
         */
        assertFalse(
            persisted.eligible()
        );

        assertEquals(
            RejectionReason.SELLER_THIRD_PARTY,
            persisted.rejectionReason()
        );

        /*
         * Oferta inelegível continua sem score.
         */
        assertNull(
            persisted.score()
        );

        assertNull(
            persisted.scoreVersion()
        );

        /*
         * Evolução histórica continua sendo registrada.
         */
        assertBigDecimalEquals(
            "2.0000",
            persisted.momentum()
        );

        assertEquals(
            "MOMENTUM_V1",
            persisted.momentumVersion()
        );

        assertTrue(
            auditRepository.savedAudit
                .isAvailable()
        );

        assertBigDecimalEquals(
            "2.0000",
            auditRepository.savedAudit
                .momentum()
        );
    }

    private AmazonDealEvaluationApplicationService createService(
        FakeDealEvaluationRepository evaluationRepository,
        FakeMomentumAuditRepository auditRepository,
        HistoricalOfferObservation previousObservation
    ) {

        BestCashDiscountSelector bestCashDiscountSelector =
            new BestCashDiscountSelector();

        MomentumCalculationService momentumCalculationService =
            new MomentumCalculationService(
                new StubOfferHistoryQueryPort(
                    previousObservation
                ),
                new SnapshotEvolutionCalculator(
                    bestCashDiscountSelector
                ),
                new MomentumEngine()
            );

        return new AmazonDealEvaluationApplicationService(
            new AmazonEligibilityValidator(),
            new CommercialFilterEngine(),
            FILTER_PROFILE_PROVIDER,
            SCORE_PROFILE_PROVIDER,
            new ScoreEngine(),
            bestCashDiscountSelector,
            momentumCalculationService,
            evaluationRepository,
            auditRepository
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
            cashCondition(
                cashDiscount
            );

        return new OfferSnapshot(
            1L,
            product,
            COLLECTED_AT,
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

    private HistoricalOfferObservation createPreviousObservation(
        OfferSnapshot current,
        long snapshotId,
        String currentPrice,
        String soldPercentage
    ) {

        return new HistoricalOfferObservation(
            snapshotId,
            current.product().asin(),
            current.collectedAt()
                .minusHours(
                    3
                ),
            Money.of(
                currentPrice
            ),
            soldPercentage == null
                ? null
                : Percentage.of(
                soldPercentage
            ),
            current.source(),
            List.of(
                cashCondition(
                    "25"
                )
            )
        );
    }

    private PaymentCondition cashCondition(
        String discount
    ) {

        return new PaymentCondition(
            PaymentConditionType.CASH,
            Money.of(
                "79.90"
            ),
            Percentage.of(
                discount
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

    private static final class FakeDealEvaluationRepository
        implements DealEvaluationRepository {

        private final List<DealEvaluation> savedEvaluations =
            new ArrayList<>();

        private DealEvaluation savedEvaluation;

        private long nextId =
            1000L;

        @Override
        public DealEvaluation save(
            DealEvaluation evaluation
        ) {

            DealEvaluation persisted =
                new DealEvaluation(
                    nextId++,
                    evaluation.offerSnapshot(),
                    evaluation.eligible(),
                    evaluation.rejectionReason(),
                    evaluation.eligibilityPolicyVersion(),
                    evaluation.filterProfileVersion(),
                    evaluation.ruleResults(),
                    evaluation.score(),
                    evaluation.scoreVersion(),
                    evaluation.scoreFactors(),
                    evaluation.momentum(),
                    evaluation.momentumVersion(),
                    evaluation.evaluatedAt()
                );

            savedEvaluation =
                persisted;

            savedEvaluations.add(
                persisted
            );

            return persisted;
        }
    }

    private static final class FakeMomentumAuditRepository
        implements MomentumAuditRepository {

        private final List<MomentumAudit> savedAudits =
            new ArrayList<>();

        private MomentumAudit savedAudit;

        private long nextId =
            5000L;

        @Override
        public MomentumAudit save(
            MomentumAudit audit
        ) {

            MomentumAudit persisted =
                audit.withId(
                    nextId++
                );

            savedAudit =
                persisted;

            savedAudits.add(
                persisted
            );

            return persisted;
        }
    }

    private static final class StubOfferHistoryQueryPort
        implements OfferHistoryQueryPort {

        private final HistoricalOfferObservation previous;

        private StubOfferHistoryQueryPort(
            HistoricalOfferObservation previous
        ) {

            this.previous =
                previous;
        }

        @Override
        public List<HistoricalOfferObservation> findHistoryByAsin(
            Asin asin
        ) {

            return previous == null
                ? List.of()
                : List.of(
                previous
            );
        }

        @Override
        public Optional<HistoricalOfferObservation> findFirstByAsin(
            Asin asin
        ) {

            return Optional.ofNullable(
                previous
            );
        }

        @Override
        public Optional<HistoricalOfferObservation> findLatestByAsin(
            Asin asin
        ) {

            return Optional.ofNullable(
                previous
            );
        }

        @Override
        public Optional<HistoricalOfferObservation> findPreviousByAsin(
            Asin asin,
            OffsetDateTime collectedAt
        ) {

            return Optional.ofNullable(
                previous
            );
        }

        @Override
        public long countByAsin(
            Asin asin
        ) {

            return previous == null
                ? 0L
                : 1L;
        }
    }
}
