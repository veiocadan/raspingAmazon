package com.raspingamazon.application.publication.affiliate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Primeira estratégia versionada para geração de links de associado
 * destinados à Amazon Brasil.
 *
 * <p>A estratégia recebe uma URL de produto já conhecida pelo sistema,
 * valida que ela pertence à Amazon Brasil e produz uma URL limpa com
 * o parâmetro de identificação do associado.</p>
 *
 * <p>A identificação do associado é recebida por configuração.
 * Ela nunca deve ser hardcoded nesta classe.</p>
 */
public final class AmazonAffiliateLinkGeneratorV1
    implements AffiliateLinkGenerator {

    public static final String VERSION =
        "AMAZON_AFFILIATE_LINK_V1";

    private static final String AMAZON_BR_HOST =
        "www.amazon.com.br";

    private final String associateTag;

    public AmazonAffiliateLinkGeneratorV1(
        String associateTag
    ) {

        this.associateTag =
            requireText(
                associateTag,
                "associateTag must not be blank"
            );
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public AffiliateLink generate(
        String productUrl
    ) {

        String requiredProductUrl =
            requireText(
                productUrl,
                "productUrl must not be blank"
            );

        URI sourceUri =
            parseUri(
                requiredProductUrl
            );

        validateSourceUri(
            sourceUri
        );

        String encodedTag =
            URLEncoder.encode(
                associateTag,
                StandardCharsets.UTF_8
            );

        String affiliateUrl =
            buildAffiliateUrl(
                sourceUri,
                encodedTag
            );

        return new AffiliateLink(
            VERSION,
            affiliateUrl
        );
    }

    private URI parseUri(
        String productUrl
    ) {

        try {

            return new URI(
                productUrl
            );

        } catch (URISyntaxException exception) {

            throw new IllegalArgumentException(
                "productUrl must be a valid URI",
                exception
            );
        }
    }

    private void validateSourceUri(
        URI uri
    ) {

        if (!"https".equalsIgnoreCase(
            uri.getScheme()
        )) {

            throw new IllegalArgumentException(
                "Amazon product URL must use HTTPS"
            );
        }

        String host =
            uri.getHost();

        if (host == null
            || !AMAZON_BR_HOST.equals(
            host.toLowerCase(
                Locale.ROOT
            )
        )) {

            throw new IllegalArgumentException(
                "Amazon product URL must use www.amazon.com.br"
            );
        }

        String path =
            uri.getRawPath();

        if (path == null
            || path.isBlank()
            || "/".equals(
            path
        )) {

            throw new IllegalArgumentException(
                "Amazon product URL must contain a product path"
            );
        }
    }

    /**
     * Remove query string e fragmento da URL observada antes
     * de adicionar a identificação do associado.
     *
     * <p>Isso torna a saída determinística e evita preservar
     * parâmetros transitórios de navegação ou uma tag antiga.</p>
     */
    private String buildAffiliateUrl(
        URI sourceUri,
        String encodedTag
    ) {

        try {

            URI cleanUri =
                new URI(
                    "https",
                    null,
                    AMAZON_BR_HOST,
                    -1,
                    sourceUri.getRawPath(),
                    "tag=" + encodedTag,
                    null
                );

            return cleanUri.toASCIIString();

        } catch (URISyntaxException exception) {

            throw new IllegalStateException(
                "Could not build Amazon affiliate URL",
                exception
            );
        }
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
