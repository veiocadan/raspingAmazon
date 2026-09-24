package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialFilterEngineBasisDiscountTest {

    @Test
    void shouldUseBasisDiscountRuleForBasisProfile() {
        FilterProfile profile =
            FilterProfile.forBasisDiscount(
                "COMMERCIAL_FILTER_V2",
                Percentage.of("20"),
                new BigDecimal("4.30"),
                100
            );

        OfferSnapshot snapshot =
            snapshot(
                Money.of("3298.99"),
                Money.of("5499.00")
            );

        List<EvaluationRuleResult> results =
            new CommercialFilterEngine()
                .evaluate(
                    snapshot,
                    profile
                );

        assertEquals(
            3,
            results.size()
        );

        EvaluationRuleResult discount =
            results.getFirst();

        assertEquals(
            "MIN_BASIS_DISCOUNT",
            discount.ruleCode()
        );

        assertTrue(
            discount.passed()
        );

        assertEquals(
            "DISCOUNT=40.0075|BASIS=5499.00|EFFECTIVE=3298.99|SOURCE=CURRENT_PRICE",
            discount.observedValue()
        );
    }

    private OfferSnapshot snapshot(
        Money currentPrice,
        Money basisPrice
    ) {
        Product product =
            new Product(
                1L,
                new Asin("B0FILTER01"),
                "Produto",
                null,
                "https://www.amazon.com.br/dp/B0FILTER01"
            );

        return new OfferSnapshot(
            10L,
            product,
            OffsetDateTime.parse(
                "2026-09-23T22:00:00-03:00"
            ),
            currentPrice,
            basisPrice,
            null,
            null,
            4.8,
            500L,
            "Amazon.com.br",
            "Amazon.com.br",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "TEST",
            List.of()
        );
    }
}
