package com.raspingamazon.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuração necessária para entrega de publicações
 * através da WhatsApp Cloud API.
 *
 * <p>O número destinatário não pertence a esta configuração.
 * Ele é fornecido individualmente por PublicationCommand e
 * permanece persistido na outbox.</p>
 *
 * <p>As publicações automatizadas utilizam um message template
 * previamente aprovado na WhatsApp Business Platform. O conteúdo
 * aprovado da Publication será posteriormente fornecido ao template
 * como parâmetro dinâmico.</p>
 *
 * <p>O access token é segredo operacional e não deve aparecer
 * em logs. Por isso, toString() deliberadamente o mascara.</p>
 */
public record WhatsAppChannelConfig(
    URI graphApiBaseUri,
    String graphApiVersion,
    String phoneNumberId,
    String accessToken,
    String templateName,
    String templateLanguage,
    Duration requestTimeout
) {

    public WhatsAppChannelConfig {

        Objects.requireNonNull(
            graphApiBaseUri,
            "graphApiBaseUri must not be null"
        );

        if (!graphApiBaseUri.isAbsolute()) {

            throw new IllegalArgumentException(
                "graphApiBaseUri must be absolute"
            );
        }

        String scheme =
            graphApiBaseUri.getScheme();

        boolean validScheme =
            "http".equalsIgnoreCase(
                scheme
            )
                || "https".equalsIgnoreCase(
                scheme
            );

        if (!validScheme
            || graphApiBaseUri.getHost() == null
            || graphApiBaseUri.getHost().isBlank()) {

            throw new IllegalArgumentException(
                "graphApiBaseUri must be an absolute HTTP URI"
            );
        }

        graphApiVersion =
            requireText(
                graphApiVersion,
                "graphApiVersion"
            );

        if (!graphApiVersion.matches(
            "v\\d+\\.\\d+"
        )) {

            throw new IllegalArgumentException(
                "graphApiVersion must follow format vN.N"
            );
        }

        phoneNumberId =
            requireText(
                phoneNumberId,
                "phoneNumberId"
            );

        accessToken =
            requireText(
                accessToken,
                "accessToken"
            );

        templateName =
            requireText(
                templateName,
                "templateName"
            );

        templateLanguage =
            requireText(
                templateLanguage,
                "templateLanguage"
            );

        Objects.requireNonNull(
            requestTimeout,
            "requestTimeout must not be null"
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

        return "WhatsAppChannelConfig["
            + "graphApiBaseUri="
            + graphApiBaseUri
            + ", graphApiVersion="
            + graphApiVersion
            + ", phoneNumberId="
            + phoneNumberId
            + ", accessToken=<redacted>"
            + ", templateName="
            + templateName
            + ", templateLanguage="
            + templateLanguage
            + ", requestTimeout="
            + requestTimeout
            + "]";
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
