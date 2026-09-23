package com.raspingamazon.application.publication.template;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.application.publication.presentation.AmazonCommercialPresentationV1;
import com.raspingamazon.application.publication.presentation.CommercialPresentation;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmazonPublicationV1Test {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T16:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-23T16:05:00-03:00"
        );

    private static final String AFFILIATE_URL =
        "https://www.amazon.com.br/dp/B0PUB13003?tag=test-20";

    private final AmazonCommercialPresentationV1
        presentationPolicy =
        new AmazonCommercialPresentationV1();

    private final AmazonPublicationV1 template =
        new AmazonPublicationV1();

    @Test
    void shouldExposeTemplateVersion() {

        assertEquals(
            "AMAZON_PUBLICATION_V1",
            template.version()
        );
    }

    @Test
    void shouldRenderCompletePublicationDeterministically() {

        PaymentCondition pix =
            cashCondition(
                PaymentMethod.PIX,
                "94.90",
                "5"
            );

        PaymentCondition nuPay =
            cashCondition(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                "89.90",
                "10"
            );

        PaymentCondition installment =
            installmentCondition(
                10,
                "9.99",
                "99.90",
                "0"
            );

        PublicationData data =
            createPublicationData(
                "Produto completo",
                Money.of(
                    "99.90"
                ),
                Money.of(
                    "129.90"
                ),
                Money.of(
                    "119.90"
                ),
                List.of(
                    pix,
                    nuPay,
                    installment
                )
            );

        CommercialPresentation presentation =
            presentationPolicy.present(
                data
            );

        String rendered =
            template.render(
                new PublicationTemplateInput(
                    data,
                    presentation,
                    AFFILIATE_URL
                )
            );

        assertEquals(
            """
            Produto completo
            Preço de referência: R$ 129,90
            Preço anterior: R$ 119,90
            Preço atual: R$ 99,90
            NuPay: R$ 89,90 (10% de desconto)
            Pix: R$ 94,90 (5% de desconto)
            Cartão: 10x de R$ 9,99 sem juros
            Link patrocinado: https://www.amazon.com.br/dp/B0PUB13003?tag=test-20""",
            rendered
        );
    }

    @Test
    void shouldOmitAbsentOptionalCommercialData() {

        PublicationData data =
            createPublicationData(
                "Produto simples",
                Money.of(
                    "79.90"
                ),
                null,
                null,
                List.of()
            );

        CommercialPresentation presentation =
            presentationPolicy.present(
                data
            );

        String rendered =
            template.render(
                new PublicationTemplateInput(
                    data,
                    presentation,
                    AFFILIATE_URL
                )
            );

        assertEquals(
            """
            Produto simples
            Preço atual: R$ 79,90
            Link patrocinado: https://www.amazon.com.br/dp/B0PUB13003?tag=test-20""",
            rendered
        );

        assertFalse(
            rendered.contains(
                "Preço de referência:"
            )
        );

        assertFalse(
            rendered.contains(
                "Preço anterior:"
            )
        );

        assertFalse(
            rendered.contains(
                "Pix:"
            )
        );

        assertFalse(
            rendered.contains(
                "NuPay:"
            )
        );

        assertFalse(
            rendered.contains(
                "Cartão:"
            )
        );
    }

    @Test
    void shouldRenderInterestBearingInstallmentWithoutInventingInterestFreeClaim() {

        PaymentCondition installment =
            installmentCondition(
                12,
                "10.50",
                "126.00",
                "15"
            );

        PublicationData data =
            createPublicationData(
                "Produto parcelado",
                Money.of(
                    "99.90"
                ),
                null,
                null,
                List.of(
                    installment
                )
            );

        CommercialPresentation presentation =
            presentationPolicy.present(
                data
            );

        String rendered =
            template.render(
                new PublicationTemplateInput(
                    data,
                    presentation,
                    AFFILIATE_URL
                )
            );

        assertEquals(
            """
            Produto parcelado
            Preço atual: R$ 99,90
            Cartão: 12x de R$ 10,50 com 15% de juros
            Link patrocinado: https://www.amazon.com.br/dp/B0PUB13003?tag=test-20""",
            rendered
        );
    }

    @Test
    void shouldRenderMoneyUsingBrazilianDecimalAndThousandsSeparators() {

        PublicationData data =
            createPublicationData(
                "Produto caro",
                Money.of(
                    "12345.67"
                ),
                null,
                null,
                List.of()
            );

        CommercialPresentation presentation =
            presentationPolicy.present(
                data
            );

        String rendered =
            template.render(
                new PublicationTemplateInput(
                    data,
                    presentation,
                    AFFILIATE_URL
                )
            );

        assertEquals(
            """
            Produto caro
            Preço atual: R$ 12.345,67
            Link patrocinado: https://www.amazon.com.br/dp/B0PUB13003?tag=test-20""",
            rendered
        );
    }

    @Test
    void shouldRejectBlankAffiliateUrl() {

        PublicationData data =
            createPublicationData(
                "Produto teste",
                Money.of(
                    "99.90"
                ),
                null,
                null,
                List.of()
            );

        CommercialPresentation presentation =
            presentationPolicy.present(
                data
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationTemplateInput(
                data,
                presentation,
                " "
            )
        );
    }

    private PublicationData createPublicationData(
        String title,
        Money currentPrice,
        Money basisPrice,
        Money previousPrice,
        List<PaymentCondition> paymentConditions
    ) {

        Product product =
            new Product(
                10L,
                new Asin(
                    "B0PUB13003"
                ),
                title,
                null,
                "https://www.amazon.com.br/dp/B0PUB13003"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                20L,
                product,
                COLLECTED_AT,
                currentPrice,
                basisPrice,
                previousPrice,
                Percentage.of(
                    "42"
                ),
                4.8,
                1500L,
                "Amazon.com.br",
                "Amazon.com.br",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST_PUBLICATION_TEMPLATE",
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
