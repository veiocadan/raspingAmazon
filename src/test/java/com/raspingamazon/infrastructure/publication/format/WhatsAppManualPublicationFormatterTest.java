package com.raspingamazon.infrastructure.publication.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsAppManualPublicationFormatterTest {

    private final WhatsAppManualPublicationFormatter formatter =
        new WhatsAppManualPublicationFormatter();

    @Test
    void shouldConvertCanonicalPublicationToWhatsAppMarkup() {

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
            🔹*Tênis Reserva Troy*
            💰 De ~R$ 323,00~ por *R$ 235,99* à vista no NuPay!
            💸 No Pix: *R$ 239,90* à vista.
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
    void shouldPreserveContentWithoutCanonicalMarkup() {

        String content =
            """
            Produto
            Texto comum
            https://example.com
            """
                .strip();

        assertEquals(
            content,
            formatter.format(
                content
            )
        );
    }

    @Test
    void shouldPreserveAffiliateUrlExactly() {

        String canonical =
            """
            🔹**Produto**
            🔗 https://www.amazon.com.br/dp/B0TESTE?tag=rasping-20&ref_=abc
            """
                .strip();

        String formatted =
            formatter.format(
                canonical
            );

        assertEquals(
            """
            🔹*Produto*
            🔗 https://www.amazon.com.br/dp/B0TESTE?tag=rasping-20&ref_=abc
            """
                .strip(),
            formatted
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
                    "   "
                )
        );
    }
}
