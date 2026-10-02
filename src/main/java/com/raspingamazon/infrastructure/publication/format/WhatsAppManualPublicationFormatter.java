package com.raspingamazon.infrastructure.publication.format;

import com.raspingamazon.application.publication.channel.PublicationContentFormatter;

import java.util.Objects;

/**
 * Converte a marcação canônica da Publication para a sintaxe
 * textual utilizada no fluxo manual de WhatsApp.
 *
 * <p>Entrada canônica:</p>
 *
 * <pre>
 * **negrito**
 * ~~riscado~~
 * </pre>
 *
 * <p>Saída para cópia no WhatsApp:</p>
 *
 * <pre>
 * *negrito*
 * ~riscado~
 * </pre>
 *
 * <p>O formatter não conhece preços, Pix, NuPay, parcelamento
 * ou qualquer outra regra comercial.</p>
 */
public final class WhatsAppManualPublicationFormatter
    implements PublicationContentFormatter {

    @Override
    public String format(
        String content
    ) {

        Objects.requireNonNull(
            content,
            "content must not be null"
        );

        if (content.isBlank()) {

            throw new IllegalArgumentException(
                "content must not be blank"
            );
        }

        validateBalancedCanonicalMarkup(
            content
        );

        return content
            .replace(
                "**",
                "*"
            )
            .replace(
                "~~",
                "~"
            );
    }

    /**
     * A Publication canônica é produzida pelo template e, portanto,
     * deve possuir pares completos de marcadores.
     *
     * <p>Falhar explicitamente é preferível a produzir uma mensagem
     * visualmente corrompida.</p>
     */
    private void validateBalancedCanonicalMarkup(
        String content
    ) {

        boolean boldOpen =
            false;

        boolean strikeOpen =
            false;

        int index =
            0;

        while (index < content.length()) {

            if (startsWith(
                content,
                index,
                "**"
            )) {

                boldOpen =
                    !boldOpen;

                index +=
                    2;

                continue;
            }

            if (startsWith(
                content,
                index,
                "~~"
            )) {

                strikeOpen =
                    !strikeOpen;

                index +=
                    2;

                continue;
            }

            index++;
        }

        if (boldOpen
            || strikeOpen) {

            throw new IllegalArgumentException(
                "content contains unbalanced canonical markup"
            );
        }
    }

    private boolean startsWith(
        String content,
        int index,
        String marker
    ) {

        return content.startsWith(
            marker,
            index
        );
    }
}
