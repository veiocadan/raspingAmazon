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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AmazonCommercialPresentationV1Test {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T15:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-23T15:05:00-03:00"
        );

    private final AmazonCommercialPresentationV1 policy =
        new AmazonCommercialPresentationV1();

    @Test
    void shouldExposePolicyVersionAndObservedPrices() {

        PublicationData data =
            createPublicationData(
                List.of()
            );

        CommercialPresentation presentation =
            policy.present(
                data
            );

        assertEquals(
            AmazonCommercialPresentationV1.VERSION,
            policy.version()
        );

        assertEquals(
            AmazonCommercialPresentationV1.VERSION,
            presentation.policyVersion()
        );

        assertEquals(
            Money.of(
                "99.90"
            ),
            presentation.currentPrice()
        );

        assertEquals(
            Money.of(
                "129.90"
            ),
            presentation.basisPrice()
        );

        assertEquals(
            Money.of(
                "119.90"
            ),
            presentation.previousPrice()
        );
    }

    @Test
    void shouldPresentNuPayAsPrimaryAndPixAsSecondaryWhenNuPayDiscountIsGreater() {

        PaymentCondition pix =
            cashCondition(
                PaymentMethod.PIX,
                "94.90",
                "5.00"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                "89.90",
                "10.00"
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
            PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
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
                "89.90",
                "10.00"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                "94.90",
                "5.00"
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
    void shouldPresentOnlyPixWhenDiscountsAreEqual() {

        PaymentCondition pix =
            cashCondition(
                PaymentMethod.PIX,
                "89.90",
                "10.00"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                "89.90",
                "10.00"
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
    void shouldPresentOnlyPixWhenOnlyPixExists() {

        PaymentCondition pix =
            cashCondition(
                PaymentMethod.PIX,
                "94.90",
                "5.00"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        pix
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
                "89.90",
                "10.00"
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
    void shouldNotTreatMissingDiscountAsZero() {

        PaymentCondition pix =
            cashConditionWithoutDiscount(
                PaymentMethod.PIX,
                "99.90"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                "89.90",
                "10.00"
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

        /*
         * Como Pix não possui desconto observado,
         * não podemos afirmar matematicamente que NuPay
         * tem desconto maior.
         */
        assertEquals(
            PaymentMethod.PIX,
            presentation.primaryCashCondition()
                .paymentMethod()
        );

        assertEquals(
            PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
            presentation.secondaryCashCondition()
                .paymentMethod()
        );
    }

    @Test
    void shouldReturnNoCashPresentationWhenNoCashConditionExists() {

        PaymentCondition installment =
            installmentCondition(
                6,
                "16.65",
                "99.90",
                "0"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        installment
                    )
                )
            );

        assertNull(
            presentation.primaryCashCondition()
        );

        assertNull(
            presentation.secondaryCashCondition()
        );
    }

    @Test
    void shouldPreferLargestInterestFreeInstallmentOverLargerInterestBearingOption() {

        PaymentCondition sixInterestFree =
            installmentCondition(
                6,
                "16.65",
                "99.90",
                "0"
            );

        PaymentCondition tenInterestFree =
            installmentCondition(
                10,
                "9.99",
                "99.90",
                "0"
            );

        PaymentCondition twelveWithInterest =
            installmentCondition(
                12,
                "10.50",
                "126.00",
                "15"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        sixInterestFree,
                        twelveWithInterest,
                        tenInterestFree
                    )
                )
            );

        assertSame(
            tenInterestFree,
            presentation.installmentCondition()
        );
    }

    @Test
    void shouldUseLargestInstallmentCountWhenNoInterestFreeConditionExists() {

        PaymentCondition sixWithInterest =
            installmentCondition(
                6,
                "18.00",
                "108.00",
                "8"
            );

        PaymentCondition twelveWithInterest =
            installmentCondition(
                12,
                "10.50",
                "126.00",
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

        assertSame(
            twelveWithInterest,
            presentation.installmentCondition()
        );
    }

    @Test
    void shouldPreserveFirstConditionWhenSamePaymentMethodHasEqualDiscount() {

        PaymentCondition firstPix =
            cashCondition(
                PaymentMethod.PIX,
                "94.90",
                "5.00"
            );

        PaymentCondition secondPix =
            cashCondition(
                PaymentMethod.PIX,
                "94.90",
                "5.00"
            );

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        firstPix,
                        secondPix
                    )
                )
            );

        assertSame(
            firstPix,
            presentation.primaryCashCondition()
                .condition()
        );
    }

    private PublicationData createPublicationData(
        List<PaymentCondition> paymentConditions
    ) {

        Product product =
            new Product(
                10L,
                new Asin(
                    "B0PUB13002"
                ),
                "Produto para publicação",
                null,
                "https://www.amazon.com.br/dp/B0PUB13002"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                20L,
                product,
                COLLECTED_AT,
                Money.of(
                    "99.90"
                ),
                Money.of(
                    "129.90"
                ),
                Money.of(
                    "119.90"
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
                "TEST_PUBLICATION",
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
