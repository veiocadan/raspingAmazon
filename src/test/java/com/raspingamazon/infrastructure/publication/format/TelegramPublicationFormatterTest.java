package com.raspingamazon.infrastructure.publication.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramPublicationFormatterTest {

    private final TelegramPublicationFormatter formatter =
        new TelegramPublicationFormatter();

    @Test
    void shouldConvertCanonicalPublicationToTelegramHtml() {

        String canonical =
            """
            🔹**Tênis Reserva Troy**
            💰 De ~~R$ 323,00~~ por **R$ 235,99** à vista no NuPay!
            💸 No Pix: **R$ 239,90** à vista.
            💳 Ou 11x R$ 21,49 sem juros no cartão.
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/B0TESTE
            """
                .strip();

        String expected =
            """
            🔹<b>Tênis Reserva Troy</b>
            💰 De <s>R$ 323,00</s> por <b>R$ 235,99</b> à vista no NuPay!
            💸 No Pix: <b>R$ 239,90</b> à vista.
            💳 Ou 11x R$ 21,49 sem juros no cartão.
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/B0TESTE
            """
                .strip();

        assertEquals(
            expected,
            formatter.format(
                canonical
            )
        );
    }

    @Test
    void shouldEscapeTelegramHtmlCharactersInLiteralContent() {

        String canonical =
            """
            🔹**TV A&B <2026>**
            💰 Por **R$ 999,90**!
            🔗 https://example.com?a=1&b=2
            """
                .strip();

        String expected =
            """
            🔹<b>TV A&amp;B &lt;2026&gt;</b>
            💰 Por <b>R$ 999,90</b>!
            🔗 https://example.com?a=1&amp;b=2
            """
                .strip();

        assertEquals(
            expected,
            formatter.format(
                canonical
            )
        );
    }

    @Test
    void shouldPreservePlainTextExceptForHtmlEscaping() {

        assertEquals(
            "Produto &amp; Oferta",
            formatter.format(
                "Produto & Oferta"
            )
        );
    }

    @Test
    void shouldRejectUnbalancedBoldMarkup() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                formatter.format(
                    "🔹**Produto"
                )
        );
    }

    @Test
    void shouldRejectUnbalancedStrikeMarkup() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                formatter.format(
                    "💰 De ~~R$ 100,00"
                )
        );
    }

    @Test
    void shouldRejectNullContent() {

        assertThrows(
            NullPointerException.class,
            () ->
                formatter.format(
                    null
                )
        );
    }

    @Test
    void shouldRejectBlankContent() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                formatter.format(
                    "\n \t"
                )
        );
    }
}
