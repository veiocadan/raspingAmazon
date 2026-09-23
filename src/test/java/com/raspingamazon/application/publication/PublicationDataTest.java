package com.raspingamazon.application.publication;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationDataTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T12:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-23T12:05:00-03:00"
        );

    @Test
    void shouldExposePersistedPublicationGraph() {

        DealEvaluation evaluation =
            createEvaluation(
                30L,
                20L,
                10L
            );

        PublicationData publicationData =
            new PublicationData(
                evaluation
            );

        assertSame(
            evaluation,
            publicationData.dealEvaluation()
        );

        assertEquals(
            30L,
            publicationData.dealEvaluationId()
        );

        assertSame(
            evaluation.offerSnapshot(),
            publicationData.offerSnapshot()
        );

        assertEquals(
            20L,
            publicationData.offerSnapshotId()
        );

        assertSame(
            evaluation.offerSnapshot().product(),
            publicationData.product()
        );

        assertEquals(
            10L,
            publicationData.productId()
        );

        assertSame(
            evaluation.offerSnapshot().paymentConditions(),
            publicationData.paymentConditions()
        );
    }

    @Test
    void shouldRejectNullDealEvaluation() {

        assertThrows(
            NullPointerException.class,
            () -> new PublicationData(
                null
            )
        );
    }

    @Test
    void shouldRejectDealEvaluationWithoutPersistedId() {

        DealEvaluation evaluation =
            createEvaluation(
                null,
                20L,
                10L
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationData(
                evaluation
            )
        );
    }

    @Test
    void shouldRejectOfferSnapshotWithoutPersistedId() {

        DealEvaluation evaluation =
            createEvaluation(
                30L,
                null,
                10L
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationData(
                evaluation
            )
        );
    }

    @Test
    void shouldRejectProductWithoutPersistedId() {

        DealEvaluation evaluation =
            createEvaluation(
                30L,
                20L,
                null
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationData(
                evaluation
            )
        );
    }

    private DealEvaluation createEvaluation(
        Long evaluationId,
        Long snapshotId,
        Long productId
    ) {

        Product product =
            new Product(
                productId,
                new Asin(
                    "B0TEST0001"
                ),
                "Produto de teste",
                null,
                "https://www.amazon.com.br/dp/B0TEST0001"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                snapshotId,
                product,
                COLLECTED_AT,
                Money.of(
                    "99.90"
                ),
                Money.of(
                    "129.90"
                ),
                null,
                null,
                4.8,
                1200L,
                "Amazon.com.br",
                "Amazon.com.br",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "AMAZON_DEALS",
                List.of()
            );

        return new DealEvaluation(
            evaluationId,
            snapshot,
            true,
            null,
            "AMAZON_ELIGIBILITY_V1",
            "COMMERCIAL_FILTER_V1",
            List.of(
                EvaluationRuleResult.passed(
                    "SELLER_IS_AMAZON",
                    "AMAZON",
                    "AMAZON"
                )
            ),
            null,
            null,
            null,
            null,
            EVALUATED_AT
        );
    }
}
