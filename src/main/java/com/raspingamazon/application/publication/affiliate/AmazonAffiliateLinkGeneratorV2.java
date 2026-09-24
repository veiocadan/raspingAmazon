package com.raspingamazon.application.publication.affiliate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Segunda estratégia versionada para geração de links de associado
 * destinados à Amazon Brasil.
 *
 * <p>Esta versão corrige a dupla codificação de paths que já chegam
 * percent-encoded da Amazon.</p>
 *
 * <p>Exemplo preservado por esta versão:</p>
 *
 * <pre>
 * /Atualiza%C3%A7%C3%B5es/
 * </pre>
 *
 * <p>não pode ser transformado em:</p>
 *
 * <pre>
 * /Atualiza%25C3%25A7%25C3%25B5es/
 * </pre>
 *
 * <p>A V1 permanece imutável para preservar a reprodutibilidade de
 * publicações históricas que registraram AMAZON_AFFILIATE_LINK_V1.</p>
 */
public final class AmazonAffiliateLinkGeneratorV2
    implements AffiliateLinkGenerator {

    public static final String VERSION =
        "AMAZON_AFFILIATE_LINK_V2";

    private static final String AMAZON_BR_HOST =
        "www.amazon.com.br";

    private final String associateTag;

    public AmazonAffiliateLinkGeneratorV2(
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
     * <p>Diferentemente da V1, o path bruto não é entregue ao
     * construtor de URI que recebe componentes separados. Aquele
     * construtor interpreta o argumento de path como conteúdo ainda
     * não escapado e, por isso, escapa novamente o caractere '%'.</p>
     *
     * <p>A V2 preserva o raw path já validado e monta uma URI completa
     * para que sequências percent-encoded válidas permaneçam codificadas
     * exatamente uma vez.</p>
     */
    private String buildAffiliateUrl(
        URI sourceUri,
        String encodedTag
    ) {

        String rawPath =
            sourceUri.getRawPath();

        String rawAffiliateUrl =
            "https://"
                + AMAZON_BR_HOST
                + rawPath
                + "?tag="
                + encodedTag;

        try {

            return new URI(
                rawAffiliateUrl
            ).toASCIIString();

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
