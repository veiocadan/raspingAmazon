package com.raspingamazon.application.publication.template;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.application.publication.presentation.AmazonCommercialPresentationV2;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonPublicationV2Test {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-30T18:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-30T18:05:00-03:00"
        );

    private static final String AFFILIATE_URL =
        "https://www.amazon.com.br/dp/B0PUB19001?tag=test-20";

    private final AmazonCommercialPresentationV2 presentationPolicy =
        new AmazonCommercialPresentationV2();

    private final AmazonPublicationV2 template =
        new AmazonPublicationV2();

    @Test
    void shouldExposeV2TemplateVersion() {

        assertEquals(
            "AMAZON_PUBLICATION_V2",
            template.version()
        );
    }

    @Test
    void shouldRenderNuPayAsBestPriceAndPixAsAlternative() {

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

        PaymentCondition installment =
            installmentCondition(
                11,
                "21.49",
                "236.39",
                "0"
            );

        PublicationData data =
            createPublicationData(
                "Tênis Reserva Troy",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
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
            🔹**Tênis Reserva Troy**
            💰 De ~~R$ 323,00~~ por **R$ 235,99** à vista no NuPay!
            💸 No Pix: **R$ 239,90** à vista.
            💳 Ou 11x R$ 21,49 sem juros no cartão.
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/B0PUB19001?tag=test-20""",
            rendered
        );

        assertFalse(
            rendered.contains(
                "("
            )
        );

        assertFalse(
            rendered.contains(
                ")"
            )
        );
    }

    @Test
    void shouldRenderEntireAmazonTitleInBold() {

        PublicationData data =
            createPublicationData(
                "Tênis Reserva Troy Masculino Azul Tamanho 42",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
                List.of()
            );

        String rendered =
            render(
                data
            );

        assertTrue(
            rendered.startsWith(
                "🔹**Tênis Reserva Troy Masculino Azul Tamanho 42**"
            )
        );
    }

    @Test
    void shouldRenderPixAsBestPriceWithoutNuPayAlternativeWhenPixWins() {

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

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
                List.of(
                    pix,
                    nuPay
                )
            );

        String rendered =
            render(
                data
            );

        assertTrue(
            rendered.contains(
                "💰 De ~~R$ 323,00~~ por **R$ 235,99** à vista no Pix!"
            )
        );

        assertFalse(
            rendered.contains(
                "No NuPay"
            )
        );

        assertFalse(
            rendered.contains(
                "💸"
            )
        );
    }

    @Test
    void shouldPreferPixWhenPixAndNuPayDiscountsAreEqual() {

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

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
                List.of(
                    pix,
                    nuPay
                )
            );

        String rendered =
            render(
                data
            );

        assertTrue(
            rendered.contains(
                "**R$ 235,99** à vista no Pix!"
            )
        );

        assertFalse(
            rendered.contains(
                "à vista no NuPay"
            )
        );
    }

    @Test
    void shouldUseCurrentPriceWithoutCashTextWhenNoExplicitCashAdvantageExists() {

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
                List.of()
            );

        String rendered =
            render(
                data
            );

        assertTrue(
            rendered.contains(
                "💰 De ~~R$ 323,00~~ por **R$ 249,90**!"
            )
        );

        assertFalse(
            rendered.contains(
                "à vista"
            )
        );
    }

    @Test
    void shouldUsePreviousPriceWhenBasisPriceIsAbsent() {

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                null,
                Money.of(
                    "299.90"
                ),
                List.of()
            );

        String rendered =
            render(
                data
            );

        assertTrue(
            rendered.contains(
                "💰 De ~~R$ 299,90~~ por **R$ 249,90**!"
            )
        );
    }

    @Test
    void shouldRenderOnlyPorWhenNoValidReferencePriceExists() {

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                null,
                null,
                List.of()
            );

        String rendered =
            render(
                data
            );

        assertTrue(
            rendered.contains(
                "💰 Por **R$ 249,90**!"
            )
        );

        assertFalse(
            rendered.contains(
                "💰 De "
            )
        );
    }

    @Test
    void shouldRenderLargestInterestFreeInstallmentAndIgnoreLargerInterestBearingOption() {

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
                "25.00",
                "300.00",
                "15"
            );

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
                List.of(
                    sixInterestFree,
                    twelveWithInterest,
                    elevenInterestFree
                )
            );

        String rendered =
            render(
                data
            );

        assertTrue(
            rendered.contains(
                "💳 Ou 11x R$ 21,49 sem juros no cartão."
            )
        );

        assertFalse(
            rendered.contains(
                "12x"
            )
        );

        assertFalse(
            rendered.contains(
                "com juros"
            )
        );
    }

    @Test
    void shouldOmitInstallmentWhenOnlyInterestBearingOptionsExist() {

        PaymentCondition installment =
            installmentCondition(
                12,
                "25.00",
                "300.00",
                "15"
            );

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
                List.of(
                    installment
                )
            );

        String rendered =
            render(
                data
            );

        assertFalse(
            rendered.contains(
                "💳"
            )
        );

        assertFalse(
            rendered.contains(
                "juros"
            )
        );

        assertTrue(
            rendered.contains(
                "👇 Tá em Promo!"
            )
        );
    }

    @Test
    void shouldPreserveAffiliateUrlExactly() {

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
                List.of()
            );

        String rendered =
            render(
                data
            );

        assertTrue(
            rendered.endsWith(
                "🔗 " + AFFILIATE_URL
            )
        );
    }

    @Test
    void shouldBeDeterministic() {

        PublicationData data =
            createPublicationData(
                "Produto",
                Money.of(
                    "249.90"
                ),
                Money.of(
                    "323.00"
                ),
                null,
                List.of()
            );

        CommercialPresentation presentation =
            presentationPolicy.present(
                data
            );

        PublicationTemplateInput input =
            new PublicationTemplateInput(
                data,
                presentation,
                AFFILIATE_URL
            );

        assertEquals(
            template.render(
                input
            ),
            template.render(
                input
            )
        );
    }

    private String render(
        PublicationData data
    ) {

        CommercialPresentation presentation =
            presentationPolicy.present(
                data
            );

        return template.render(
            new PublicationTemplateInput(
                data,
                presentation,
                AFFILIATE_URL
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
                    "B0PUB19001"
                ),
                title,
                null,
                "https://www.amazon.com.br/dp/B0PUB19001"
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
