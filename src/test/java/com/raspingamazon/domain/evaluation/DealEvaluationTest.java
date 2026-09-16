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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa a estrutura de DealEvaluation.
 *
 * Estes testes não implementam as regras de elegibilidade ou scoring.
 * Eles verificam somente se a avaliação consegue representar
 * corretamente o resultado produzido por essas regras.
 */
class DealEvaluationTest {

    private static final Product PRODUCT = new Product(
            1L,
            new Asin("B0FN4BK3V7"),
            "Produto de teste",
            null,
            "https://www.amazon.com.br/dp/B0FN4BK3V7"
    );

    private static final OfferSnapshot SNAPSHOT = new OfferSnapshot(
            10L,
            PRODUCT,
            OffsetDateTime.parse("2026-09-13T19:00:00-03:00"),
            Money.of("199.90"),
            null,
            Money.of("249.90"),
            null,
            null,
            4.7,
            1520L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "amazon-deals"
    );

    @Test
    void shouldCreateEligibleEvaluation() {
        OffsetDateTime evaluatedAt = OffsetDateTime.parse(
                "2026-09-13T19:05:00-03:00"
        );

        DealEvaluation evaluation = new DealEvaluation(
                20L,
                SNAPSHOT,
                true,
                null,
                "v1",
                new BigDecimal("85.50"),
                new BigDecimal("12.30"),
                evaluatedAt
        );

        assertEquals(20L, evaluation.id());
        assertEquals(SNAPSHOT, evaluation.offerSnapshot());
        assertTrue(evaluation.eligible());
        assertNull(evaluation.rejectionReason());
        assertEquals("v1", evaluation.filterVersion());
        assertEquals(
                new BigDecimal("85.50"),
                evaluation.score()
        );
        assertEquals(
                new BigDecimal("12.30"),
                evaluation.momentum()
        );
        assertEquals(evaluatedAt, evaluation.evaluatedAt());
    }

    @Test
    void shouldCreateRejectedEvaluation() {
        DealEvaluation evaluation = new DealEvaluation(
                21L,
                SNAPSHOT,
                false,
                RejectionReason.SELLER_THIRD_PARTY,
                "v1",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OffsetDateTime.parse("2026-09-13T19:05:00-03:00")
        );

        assertFalse(evaluation.eligible());
        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                evaluation.rejectionReason()
        );
    }

    @Test
    void shouldAllowNullIdBeforePersistence() {
        DealEvaluation evaluation = new DealEvaluation(
                null,
                SNAPSHOT,
                true,
                null,
                "v1",
                null,
                null,
                OffsetDateTime.parse("2026-09-13T19:05:00-03:00")
        );

        assertNull(evaluation.id());
    }

    @Test
    void shouldAllowNullScoreAndMomentum() {
        /*
         * O cálculo do score e do momentum ainda será implementado.
         * Portanto a entidade estrutural permite que esses resultados
         * estejam ausentes enquanto não houver valor calculado.
         */
        DealEvaluation evaluation = new DealEvaluation(
                null,
                SNAPSHOT,
                true,
                null,
                "v1",
                null,
                null,
                OffsetDateTime.parse("2026-09-13T19:05:00-03:00")
        );

        assertNull(evaluation.score());
        assertNull(evaluation.momentum());
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
                        "v1",
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
                        "v1",
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
                        "v1",
                        null,
                        null,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectNullFilterVersion() {
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
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void shouldRejectBlankFilterVersion() {
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
                        "v1",
                        null,
                        null,
                        null
                )
        );
    }
}