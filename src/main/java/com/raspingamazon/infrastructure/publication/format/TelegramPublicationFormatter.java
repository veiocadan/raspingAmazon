package com.raspingamazon.infrastructure.publication.format;

import com.raspingamazon.application.publication.channel.PublicationContentFormatter;

import java.util.Objects;

/**
 * Converte a marcação canônica da Publication para HTML aceito
 * pelo Telegram Bot API.
 *
 * <p>Entrada canônica:</p>
 *
 * <pre>
 * **negrito**
 * ~~riscado~~
 * </pre>
 *
 * <p>Saída Telegram:</p>
 *
 * <pre>
 * &lt;b&gt;negrito&lt;/b&gt;
 * &lt;s&gt;riscado&lt;/s&gt;
 * </pre>
 *
 * <p>Todo conteúdo literal é escapado para HTML antes do envio.
 * Assim títulos e outros fatos que contenham caracteres como
 * {@code &}, {@code <} ou {@code >} não alteram acidentalmente
 * a estrutura da mensagem.</p>
 */
public final class TelegramPublicationFormatter
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

        StringBuilder formatted =
            new StringBuilder();

        boolean boldOpen =
            false;

        boolean strikeOpen =
            false;

        int index =
            0;

        while (index < content.length()) {

            if (content.startsWith(
                "**",
                index
            )) {

                formatted.append(
                    boldOpen
                        ? "</b>"
                        : "<b>"
                );

                boldOpen =
                    !boldOpen;

                index +=
                    2;

                continue;
            }

            if (content.startsWith(
                "~~",
                index
            )) {

                formatted.append(
                    strikeOpen
                        ? "</s>"
                        : "<s>"
                );

                strikeOpen =
                    !strikeOpen;

                index +=
                    2;

                continue;
            }

            appendEscapedCharacter(
                formatted,
                content.charAt(
                    index
                )
            );

            index++;
        }

        if (boldOpen
            || strikeOpen) {

            throw new IllegalArgumentException(
                "content contains unbalanced canonical markup"
            );
        }

        return formatted.toString();
    }

    private void appendEscapedCharacter(
        StringBuilder formatted,
        char character
    ) {

        switch (character) {

            case '&' ->
                formatted.append(
                    "&amp;"
                );

            case '<' ->
                formatted.append(
                    "&lt;"
                );

            case '>' ->
                formatted.append(
                    "&gt;"
                );

            default ->
                formatted.append(
                    character
                );
        }
    }
}
