package com.raspingamazon.application.publication.presentation;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AmazonCommercialPresentationV2Test {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-30T18:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-30T18:05:00-03:00"
        );

    private final AmazonCommercialPresentationV2 policy =
        new AmazonCommercialPresentationV2();

    @Test
    void shouldExposeV2PolicyVersionAndObservedPrices() {

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of()
                )
            );

        assertEquals(
            "AMAZON_COMMERCIAL_PRESENTATION_V2",
            policy.version()
        );

        assertEquals(
            "AMAZON_COMMERCIAL_PRESENTATION_V2",
            presentation.policyVersion()
        );

        assertEquals(
            Money.of(
                "249.90"
            ),
            presentation.currentPrice()
        );

        assertEquals(
            Money.of(
                "323.00"
            ),
            presentation.basisPrice()
        );

        assertEquals(
            Money.of(
                "299.90"
            ),
            presentation.previousPrice()
        );
    }

    @Test
    void shouldPresentNuPayAsPrimaryAndPixAsSecondaryWhenNuPayDiscountIsGreater() {

        PaymentCondition pix =
            cashCondition(
                PaymentMethod.PIX,
                "239.90",
                "4"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY,
                "235.99",
                "6"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        pix,
                        nuPay
                    )
                )
            );

        assertEquals(
            PaymentMethod.NUPAY,
            presentation.primaryCashCondition()
                .paymentMethod()
        );

        assertSame(
            nuPay,
            presentation.primaryCashCondition()
                .condition()
        );

        assertEquals(
            PaymentMethod.PIX,
            presentation.secondaryCashCondition()
                .paymentMethod()
        );

        assertSame(
            pix,
            presentation.secondaryCashCondition()
                .condition()
        );
    }

    @Test
    void shouldPresentOnlyPixWhenPixDiscountIsGreater() {

        PaymentCondition pix =
            cashCondition(
                PaymentMethod.PIX,
                "235.99",
                "6"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY,
                "239.90",
                "4"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        pix,
                        nuPay
                    )
                )
            );

        assertEquals(
            PaymentMethod.PIX,
            presentation.primaryCashCondition()
                .paymentMethod()
        );

        assertSame(
            pix,
            presentation.primaryCashCondition()
                .condition()
        );

        assertNull(
            presentation.secondaryCashCondition()
        );
    }

    @Test
    void shouldPresentOnlyPixWhenPixAndNuPayDiscountsAreEqual() {

        PaymentCondition pix =
            cashCondition(
                PaymentMethod.PIX,
                "235.99",
                "6"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY,
                "235.99",
                "6"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        pix,
                        nuPay
                    )
                )
            );

        assertEquals(
            PaymentMethod.PIX,
            presentation.primaryCashCondition()
                .paymentMethod()
        );

        assertSame(
            pix,
            presentation.primaryCashCondition()
                .condition()
        );

        assertNull(
            presentation.secondaryCashCondition()
        );
    }

    @Test
    void shouldPresentOnlyNuPayWhenOnlyNuPayExists() {

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                "235.99",
                "6"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        nuPay
                    )
                )
            );

        assertEquals(
            PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
            presentation.primaryCashCondition()
                .paymentMethod()
        );

        assertSame(
            nuPay,
            presentation.primaryCashCondition()
                .condition()
        );

        assertNull(
            presentation.secondaryCashCondition()
        );
    }

    @Test
    void shouldSelectLargestInterestFreeInstallment() {

        PaymentCondition sixInterestFree =
            installmentCondition(
                6,
                "39.32",
                "235.92",
                "0"
            );

        PaymentCondition elevenInterestFree =
            installmentCondition(
                11,
                "21.49",
                "236.39",
                "0"
            );

        PaymentCondition twelveWithInterest =
            installmentCondition(
                12,
                "22.50",
                "270.00",
                "15"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        sixInterestFree,
                        twelveWithInterest,
                        elevenInterestFree
                    )
                )
            );

        assertSame(
            elevenInterestFree,
            presentation.installmentCondition()
        );
    }

    @Test
    void shouldNotSelectInterestBearingInstallmentWhenNoInterestFreeOptionExists() {

        PaymentCondition sixWithInterest =
            installmentCondition(
                6,
                "45.00",
                "270.00",
                "8"
            );

        PaymentCondition twelveWithInterest =
            installmentCondition(
                12,
                "25.00",
                "300.00",
                "15"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        sixWithInterest,
                        twelveWithInterest
                    )
                )
            );

        assertNull(
            presentation.installmentCondition()
        );
    }

    @Test
    void shouldPreserveMissingDiscountWithoutTreatingItAsZero() {

        PaymentCondition pix =
            cashConditionWithoutDiscount(
                PaymentMethod.PIX,
                "239.90"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY,
                "235.99",
                "6"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        pix,
                        nuPay
                    )
                )
            );

        assertEquals(
            PaymentMethod.PIX,
            presentation.primaryCashCondition()
                .paymentMethod()
        );

        assertEquals(
            PaymentMethod.NUPAY,
            presentation.secondaryCashCondition()
                .paymentMethod()
        );
    }

    private PublicationData createPublicationData(
        List<PaymentCondition> paymentConditions
    ) {

        Product product =
            new Product(
                10L,
                new Asin(
                    "B0PUB19001"
                ),
                "Tênis Reserva Troy",
                null,
                "https://www.amazon.com.br/dp/B0PUB19001"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                20L,
                product,
                COLLECTED_AT,
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                Money.of(
                    "299.90"
                ),
                Percentage.of(
                    "42"
                ),
                4.8,
                1500L,
                "Amazon.com.br",
                "Amazon.com.br",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST_PUBLICATION_V2",
                paymentConditions
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                30L,
                snapshot,
                true,
                null,
                "AMAZON_ELIGIBILITY_TEST",
                "COMMERCIAL_FILTER_TEST",
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

        return new PublicationData(
            evaluation
        );
    }

    private PaymentCondition cashCondition(
        PaymentMethod method,
        String price,
        String discount
    ) {

        return new PaymentCondition(
            PaymentConditionType.CASH,
            Money.of(
                price
            ),
            Percentage.of(
                discount
            ),
            null,
            null,
            null,
            null,
            List.of(
                method
            )
        );
    }

    private PaymentCondition cashConditionWithoutDiscount(
        PaymentMethod method,
        String price
    ) {

        return new PaymentCondition(
            PaymentConditionType.CASH,
            Money.of(
                price
            ),
            null,
            null,
            null,
            null,
            null,
            List.of(
                method
            )
        );
    }

    private PaymentCondition installmentCondition(
        int installmentCount,
        String installmentAmount,
        String installmentTotal,
        String interest
    ) {

        return new PaymentCondition(
            PaymentConditionType.CREDIT_INSTALLMENT,
            null,
            null,
            installmentCount,
            Money.of(
                installmentAmount
            ),
            Money.of(
                installmentTotal
            ),
            Percentage.of(
                interest
            ),
            List.of(
                PaymentMethod.CREDIT_CARD
            )
        );
    }
}
