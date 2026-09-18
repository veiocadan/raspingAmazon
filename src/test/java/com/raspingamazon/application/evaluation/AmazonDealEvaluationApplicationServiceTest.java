package com.raspingamazon.application.evaluation;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
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

/**
 * Testes do caso de uso persistente de avaliação Amazon.
 *
 * <p>Após a FASE 8.5-C, a versão da política de elegibilidade
 * é registrada separadamente da futura versão dos filtros.</p>
 */
class AmazonDealEvaluationApplicationServiceTest {

    @Test
    void shouldEvaluateAndPersistRejectedDeal() {

        FakeDealEvaluationRepository repository =
                new FakeDealEvaluationRepository();

        AmazonDealEvaluationApplicationService service =
                createService(repository);

        OfferSnapshot offerSnapshot =
                createOfferSnapshot();

        OffsetDateTime evaluatedAt =
                OffsetDateTime.now();

        DealEvaluation persisted =
                service.evaluate(
                        offerSnapshot,
                        SellerType.THIRD_PARTY,
                        DeliveryType.AMAZON,
                        evaluatedAt
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

        /*
         * A política Amazon agora possui campo próprio.
         */
        assertEquals(
                "AMAZON_SELLER_DELIVERY_V1",
                persisted.eligibilityPolicyVersion()
        );

        /*
         * FASE 9 ainda não foi aplicada.
         */
        assertNull(
                persisted.filterProfileVersion()
        );

        /*
         * Score e momentum também ainda não existem.
         */
        assertNull(
                persisted.score()
        );

        assertNull(
                persisted.scoreVersion()
        );

        assertNull(
                persisted.momentum()
        );

        assertNull(
                persisted.momentumVersion()
        );

        assertEquals(
                evaluatedAt,
                persisted.evaluatedAt()
        );

        assertEquals(
                1,
                repository.savedEvaluations.size()
        );
    }

    @Test
    void shouldEvaluateAndPersistAcceptedDeal() {

        FakeDealEvaluationRepository repository =
                new FakeDealEvaluationRepository();

        AmazonDealEvaluationApplicationService service =
                createService(repository);

        OfferSnapshot offerSnapshot =
                createOfferSnapshot();

        OffsetDateTime evaluatedAt =
                OffsetDateTime.now();

        DealEvaluation persisted =
                service.evaluate(
                        offerSnapshot,
                        SellerType.AMAZON,
                        DeliveryType.AMAZON,
                        evaluatedAt
                );

        assertSame(
                persisted,
                repository.savedEvaluation
        );

        assertTrue(
                persisted.eligible()
        );

        assertNull(
                persisted.rejectionReason()
        );

        assertEquals(
                "AMAZON_SELLER_DELIVERY_V1",
                persisted.eligibilityPolicyVersion()
        );

        assertNull(
                persisted.filterProfileVersion()
        );

        assertNull(
                persisted.score()
        );

        assertNull(
                persisted.scoreVersion()
        );

        assertNull(
                persisted.momentum()
        );

        assertNull(
                persisted.momentumVersion()
        );

        assertEquals(
                evaluatedAt,
                persisted.evaluatedAt()
        );

        assertEquals(
                1,
                repository.savedEvaluations.size()
        );
    }

    private AmazonDealEvaluationApplicationService createService(
            FakeDealEvaluationRepository repository
    ) {
        AmazonEligibilityValidator validator =
                new AmazonEligibilityValidator();

        return new AmazonDealEvaluationApplicationService(
                validator,
                repository
        );
    }

    private OfferSnapshot createOfferSnapshot() {

        Product product =
                new Product(
                        1L,
                        new Asin("B000TEST86"),
                        "Produto de teste",
                        null,
                        "https://example.invalid/produto"
                );

        return new OfferSnapshot(
                1L,
                product,
                OffsetDateTime.now(),
                new Money(
                        new BigDecimal("99.90")
                ),
                null,
                null,
                null,
                null,
                null,
                "Vendedor teste",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST",
                List.of()
        );
    }

    /**
     * Repository em memória usado para testar somente a camada de aplicação.
     */
    private static final class FakeDealEvaluationRepository
            implements DealEvaluationRepository {

        private final List<DealEvaluation> savedEvaluations =
                new ArrayList<>();

        private DealEvaluation savedEvaluation;

        @Override
        public DealEvaluation save(
                DealEvaluation evaluation
        ) {
            savedEvaluation = evaluation;
            savedEvaluations.add(evaluation);

            return evaluation;
        }
    }
}