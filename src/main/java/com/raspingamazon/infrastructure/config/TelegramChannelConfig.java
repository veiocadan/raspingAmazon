package com.raspingamazon.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuração do adapter concreto do Telegram.
 *
 * <p>O token é segredo e nunca deve ser exposto por {@link #toString()}.</p>
 *
 * <p>As preferências de link preview pertencem ao adapter do canal.
 * Elas alteram somente a apresentação da mensagem no Telegram e não
 * modificam os fatos contidos em Publication.</p>
 */
public record TelegramChannelConfig(
    URI apiBaseUri,
    String botToken,
    Duration requestTimeout,
    boolean linkPreviewEnabled,
    LinkPreviewPosition linkPreviewPosition,
    LinkPreviewSize linkPreviewSize
) {

    /**
     * Construtor compatível com os pontos do código anteriores à
     * introdução da configuração explícita de preview.
     *
     * <p>O padrão da FASE 19 passa a ser:</p>
     *
     * <pre>
     * preview habilitado
     * posição ABOVE
     * tamanho LARGE
     * </pre>
     */
    public TelegramChannelConfig(
        URI apiBaseUri,
        String botToken,
        Duration requestTimeout
    ) {

        this(
            apiBaseUri,
            botToken,
            requestTimeout,
            true,
            LinkPreviewPosition.ABOVE,
            LinkPreviewSize.LARGE
        );
    }

    public TelegramChannelConfig {

        Objects.requireNonNull(
            apiBaseUri,
            "apiBaseUri must not be null"
        );

        Objects.requireNonNull(
            botToken,
            "botToken must not be null"
        );

        Objects.requireNonNull(
            requestTimeout,
            "requestTimeout must not be null"
        );

        Objects.requireNonNull(
            linkPreviewPosition,
            "linkPreviewPosition must not be null"
        );

        Objects.requireNonNull(
            linkPreviewSize,
            "linkPreviewSize must not be null"
        );

        if (!apiBaseUri.isAbsolute()) {

            throw new IllegalArgumentException(
                "apiBaseUri must be absolute"
            );
        }

        String scheme =
            apiBaseUri.getScheme();

        if (!"http".equalsIgnoreCase(
            scheme
        )
            && !"https".equalsIgnoreCase(
            scheme
        )) {

            throw new IllegalArgumentException(
                "apiBaseUri must use http or https"
            );
        }

        botToken =
            requireText(
                botToken,
                "botToken"
            );

        if (requestTimeout.isZero()
            || requestTimeout.isNegative()) {

            throw new IllegalArgumentException(
                "requestTimeout must be positive"
            );
        }
    }

    @Override
    public String toString() {

        return "TelegramChannelConfig["
            + "apiBaseUri="
            + apiBaseUri
            + ", botToken=<redacted>"
            + ", requestTimeout="
            + requestTimeout
            + ", linkPreviewEnabled="
            + linkPreviewEnabled
            + ", linkPreviewPosition="
            + linkPreviewPosition
            + ", linkPreviewSize="
            + linkPreviewSize
            + "]";
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }

    public enum LinkPreviewPosition {

        ABOVE,

        BELOW
    }

    public enum LinkPreviewSize {

        /**
         * Não solicita mudança específica de tamanho ao Telegram.
         */
        DEFAULT,

        SMALL,

        LARGE
    }
}
