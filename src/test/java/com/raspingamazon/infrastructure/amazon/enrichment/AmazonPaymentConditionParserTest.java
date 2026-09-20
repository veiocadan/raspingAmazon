package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonPaymentConditionParserTest {

    private final AmazonPaymentConditionParser parser =
        new AmazonPaymentConditionParser();

    @Test
    void shouldParseCashAndCreditConditionsFromCommercialFixture()
        throws Exception {

        List<PaymentCondition> conditions =
            parser.parse(
                loadFixture(
                    "amazon-commercial.html"
                )
            );

        assertEquals(
            2,
            conditions.size()
        );

        PaymentCondition cash =
            conditions.get(0);

        assertEquals(
            PaymentConditionType.CASH,
            cash.type()
        );

        assertEquals(
            Money.of("79.90"),
            cash.price()
        );

        assertEquals(
            Percentage.of("25"),
            cash.discountPercentage()
        );

        assertEquals(
            List.of(
                PaymentMethod.PIX,
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT
            ),
            cash.paymentMethods()
        );

        assertNull(
            cash.installmentCount()
        );

        PaymentCondition credit =
            conditions.get(1);

        assertEquals(
            PaymentConditionType.CREDIT_INSTALLMENT,
            credit.type()
        );

        assertEquals(
            10,
            credit.installmentCount()
        );

        assertEquals(
            Money.of("9.99"),
            credit.installmentAmount()
        );

        assertEquals(
            Money.of("99.90"),
            credit.installmentTotal()
        );

        assertEquals(
            Percentage.of("0"),
            credit.interest()
        );

        assertEquals(
            List.of(
                PaymentMethod.CREDIT_CARD
            ),
            credit.paymentMethods()
        );
    }

    @Test
    void shouldPreserveCashConditionWhenDiscountIsMissing() {

        String html = """
                <html>
                <body>
                    <div id="promotionMessageInsideBuyBox_feature_div">
                        Pagamento à vista no Pix
                    </div>
                </body>
                </html>
                """;

        List<PaymentCondition> conditions =
            parser.parse(
                html
            );

        assertEquals(
            1,
            conditions.size()
        );

        PaymentCondition cash =
            conditions.getFirst();

        assertEquals(
            PaymentConditionType.CASH,
            cash.type()
        );

        assertEquals(
            List.of(
                PaymentMethod.PIX
            ),
            cash.paymentMethods()
        );

        assertNull(
            cash.discountPercentage()
        );

        assertNull(
            cash.price()
        );
    }

    @Test
    void shouldIgnoreUnrelatedPromotionWithoutRecognizedCashMethod() {

        String html = """
                <html>
                <body>
                    <div id="promotionMessageInsideBuyBox_feature_div">
                        30% off para membros Prime
                    </div>
                </body>
                </html>
                """;

        List<PaymentCondition> conditions =
            parser.parse(
                html
            );

        assertTrue(
            conditions.isEmpty()
        );
    }

    @Test
    void shouldNotInferDiscountFromCustomerVisiblePrice() {

        String html = """
                <html>
                <body>
                    <input
                        name="items[0].base][customerVisiblePrice][amount]"
                        value="80.00">

                    <div id="promotionMessageInsideBuyBox_feature_div">
                        à vista no Pix
                    </div>
                </body>
                </html>
                """;

        List<PaymentCondition> conditions =
            parser.parse(
                html
            );

        assertEquals(
            1,
            conditions.size()
        );

        PaymentCondition cash =
            conditions.getFirst();

        assertEquals(
            Money.of("80.00"),
            cash.price()
        );

        assertNull(
            cash.discountPercentage()
        );
    }

    @Test
    void shouldReturnEmptyListWhenNoCommercialConditionExists() {

        List<PaymentCondition> conditions =
            parser.parse(
                """
                <html>
                <body>
                    <p>Produto sem condição comercial observável.</p>
                </body>
                </html>
                """
            );

        assertTrue(
            conditions.isEmpty()
        );
    }

    @Test
    void shouldRejectNullHtml() {

        assertThrows(
            NullPointerException.class,
            () -> parser.parse(
                null
            )
        );
    }

    @Test
    void shouldRejectBlankHtml() {

        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(
                "   "
            )
        );
    }

    private String loadFixture(
        String fileName
    ) throws Exception {

        String resourcePath =
            "/amazon/fixtures/product/"
                + fileName;

        try (var inputStream =
                 getClass()
                     .getResourceAsStream(
                         resourcePath
                     )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                    "Fixture not found: "
                        + resourcePath
                );
            }

            return new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }
}
