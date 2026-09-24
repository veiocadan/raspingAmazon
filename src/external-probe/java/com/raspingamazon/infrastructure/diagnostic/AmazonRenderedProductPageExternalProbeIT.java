package com.raspingamazon.infrastructure.diagnostic;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonPaymentConditionParser;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Probe manual da página individual renderizada pela engine de
 * navegador.
 *
 * <p>Esta classe acessa uma página real da Amazon e deliberadamente
 * não pertence à suíte hermética padrão.</p>
 *
 * <p>O produto analisado não é fixo. A URL deve ser informada em
 * cada execução:</p>
 *
 * <pre>
 * mvn -Pexternal-probe \
 *   -Dtest=AmazonRenderedProductPageExternalProbeIT \
 *   -Damazon.probe.product-url=https://www.amazon.com.br/dp/ASIN \
 *   test
 * </pre>
 *
 * <p>Este probe não verifica preços, descontos ou parcelamentos
 * específicos. Dados comerciais reais são temporalmente instáveis:
 * ofertas terminam, preços mudam, estoque muda e a própria Amazon
 * pode alterar a experiência apresentada.</p>
 *
 * <p>Testes determinísticos dessas regras devem utilizar fixtures
 * congeladas e herméticas.</p>
 */
class AmazonRenderedProductPageExternalProbeIT {

    private static final String PRODUCT_URL_PROPERTY =
        "amazon.probe.product-url";

    private static final Pattern ASIN_PATTERN =
        Pattern.compile(
            "/(?:dp|gp/product)/([A-Z0-9]{10})(?:[/?.]|$)",
            Pattern.CASE_INSENSITIVE
        );

    private static final Pattern PRICE_TO_PAY_PATTERN =
        Pattern.compile(
            "data-csa-c-price-to-pay\\s*=\\s*"
                + "[\"']([0-9]+(?:\\.[0-9]+)?)[\"']",
            Pattern.CASE_INSENSITIVE
        );

    private static final int FEATURE_REGION_LENGTH =
        16000;

    @Test
    void shouldCaptureRenderedAmazonProductPage()
        throws Exception {

        URI productUri =
            configuredProductUri();

        String asin =
            extractAsin(
                productUri
            );

        Path outputDirectory =
            Path.of(
                "target",
                "diagnostics",
                "rendered-product-page"
            );

        Files.createDirectories(
            outputDirectory
        );

        try (PlaywrightRenderedProductPageContentProvider provider =
                 new PlaywrightRenderedProductPageContentProvider()) {

            ProductPageContent content =
                provider.load(
                    productUri
                );

            String html =
                content.html();

            /*
             * Esta é uma verificação técnica, não comercial.
             *
             * Uma página real pode deixar de ter promoção, Pix,
             * parcelamento ou qualquer preço específico. Contudo,
             * o provider deve entregar algum DOM quando a aquisição
             * propriamente dita foi bem-sucedida.
             */
            assertFalse(
                html.isBlank(),
                "Rendered HTML must not be blank"
            );

            Path htmlPath =
                outputDirectory.resolve(
                    asin
                        + "-rendered.html"
                );

            Files.writeString(
                htmlPath,
                html,
                StandardCharsets.UTF_8
            );

            String promotionRegion =
                exactIdRegion(
                    html,
                    "promotionMessageInsideBuyBox_feature_div"
                );

            String oneTimeRegion =
                exactIdRegion(
                    html,
                    "oneTimePaymentPrice_feature_div"
                );

            String installmentRegion =
                exactIdRegion(
                    html,
                    "installmentCalculatorCentral_feature_div"
                );

            String corePriceRegion =
                firstNonBlank(
                    exactIdRegion(
                        html,
                        "corePriceDisplay_desktop_feature_div"
                    ),
                    exactIdRegion(
                        html,
                        "corePrice_feature_div"
                    )
                );

            String promotionText =
                normalizeHtmlText(
                    promotionRegion
                );

            String oneTimeText =
                normalizeHtmlText(
                    oneTimeRegion
                );

            String installmentText =
                normalizeHtmlText(
                    installmentRegion
                );

            String priceToPay =
                extractPriceToPay(
                    corePriceRegion
                );

            boolean cashEvidence =
                containsIgnoreCase(
                    promotionText,
                    "pix"
                )
                    || containsIgnoreCase(
                    promotionText,
                    "nupay"
                )
                    || containsIgnoreCase(
                    oneTimeText,
                    "pix"
                )
                    || containsIgnoreCase(
                    oneTimeText,
                    "nupay"
                );

            boolean installmentEvidence =
                containsIgnoreCase(
                    installmentText,
                    "sem juros"
                )
                    || containsIgnoreCase(
                    installmentText,
                    "x de r$"
                );

            boolean possibleChallenge =
                containsIgnoreCase(
                    html,
                    "enter the characters you see below"
                )
                    || containsIgnoreCase(
                    html,
                    "digite os caracteres"
                )
                    || containsIgnoreCase(
                    html,
                    "captcha"
                );

            AmazonPaymentConditionParser paymentParser =
                new AmazonPaymentConditionParser();

            List<PaymentCondition> parsedConditions =
                paymentParser.parse(
                    html
                );

            String report =
                buildReport(
                    productUri,
                    content,
                    htmlPath,
                    priceToPay,
                    promotionText,
                    oneTimeText,
                    installmentText,
                    cashEvidence,
                    installmentEvidence,
                    possibleChallenge,
                    parsedConditions
                );

            Path reportPath =
                outputDirectory.resolve(
                    asin
                        + "-rendered-report.txt"
                );

            Files.writeString(
                reportPath,
                report,
                StandardCharsets.UTF_8
            );

            System.out.println();
            System.out.println(
                report
            );

            /*
             * Não existe assert de preço, desconto, Pix,
             * NuPay, quantidade de parcelas ou elegibilidade.
             *
             * Ausência desses fatos em uma execução real é uma
             * observação diagnóstica válida, não uma falha de
             * regressão do código.
             */
        }
    }

    private URI configuredProductUri() {

        String configuredUrl =
            System.getProperty(
                PRODUCT_URL_PROPERTY
            );

        if (configuredUrl == null
            || configuredUrl.isBlank()) {

            throw new IllegalStateException(
                "External probe requires -D"
                    + PRODUCT_URL_PROPERTY
                    + "=https://www.amazon.com.br/dp/ASIN"
            );
        }

        URI uri;

        try {

            uri =
                URI.create(
                    configuredUrl.trim()
                );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                "Invalid product URL supplied in -D"
                    + PRODUCT_URL_PROPERTY,
                exception
            );
        }

        String scheme =
            uri.getScheme();

        if (scheme == null
            || (!scheme.equalsIgnoreCase("https")
            && !scheme.equalsIgnoreCase("http"))) {

            throw new IllegalStateException(
                "Product URL must use http or https"
            );
        }

        return uri;
    }

    private String buildReport(
        URI requestedUri,
        ProductPageContent content,
        Path htmlPath,
        String priceToPay,
        String promotionText,
        String oneTimeText,
        String installmentText,
        boolean cashEvidence,
        boolean installmentEvidence,
        boolean possibleChallenge,
        List<PaymentCondition> parsedConditions
    ) {

        StringBuilder builder =
            new StringBuilder();

        builder.append(
            "AMAZON RENDERED PRODUCT PAGE EXTERNAL PROBE"
        ).append(
            System.lineSeparator()
        );

        builder.append(
            "==========================================="
        ).append(
            System.lineSeparator()
        );

        append(
            builder,
            "Requested URI",
            requestedUri.toString()
        );

        append(
            builder,
            "Resolved URI",
            content.resolvedUri()
                .toString()
        );

        append(
            builder,
            "Collected at",
            content.collectedAt()
                .toString()
        );

        append(
            builder,
            "Rendered HTML chars",
            Integer.toString(
                content.html()
                    .length()
            )
        );

        append(
            builder,
            "Rendered HTML file",
            htmlPath.toAbsolutePath()
                .normalize()
                .toString()
        );

        append(
            builder,
            "data-csa-c-price-to-pay",
            valueOrAbsent(
                priceToPay
            )
        );

        append(
            builder,
            "Cash evidence",
            Boolean.toString(
                cashEvidence
            )
        );

        append(
            builder,
            "Installment evidence",
            Boolean.toString(
                installmentEvidence
            )
        );

        append(
            builder,
            "Possible challenge page",
            Boolean.toString(
                possibleChallenge
            )
        );

        builder.append(
            System.lineSeparator()
        );

        builder.append(
            "promotionMessageInsideBuyBox:"
        ).append(
            System.lineSeparator()
        );

        builder.append(
            abbreviate(
                promotionText,
                1000
            )
        ).append(
            System.lineSeparator()
        );

        builder.append(
            System.lineSeparator()
        );

        builder.append(
            "oneTimePaymentPrice:"
        ).append(
            System.lineSeparator()
        );

        builder.append(
            abbreviate(
                oneTimeText,
                1000
            )
        ).append(
            System.lineSeparator()
        );

        builder.append(
            System.lineSeparator()
        );

        builder.append(
            "installmentCalculatorCentral:"
        ).append(
            System.lineSeparator()
        );

        builder.append(
            abbreviate(
                installmentText,
                1500
            )
        ).append(
            System.lineSeparator()
        );

        builder.append(
            System.lineSeparator()
        );

        builder.append(
            "Current AmazonPaymentConditionParser:"
        ).append(
            System.lineSeparator()
        );

        builder.append(
            "conditionCount="
        ).append(
            parsedConditions.size()
        ).append(
            System.lineSeparator()
        );

        for (int index = 0;
             index < parsedConditions.size();
             index++) {

            appendCondition(
                builder,
                index,
                parsedConditions.get(
                    index
                )
            );
        }

        return builder.toString();
    }

    private void appendCondition(
        StringBuilder builder,
        int index,
        PaymentCondition condition
    ) {

        builder.append(
            System.lineSeparator()
        );

        builder.append(
            "condition["
        ).append(
            index
        ).append(
            "]"
        ).append(
            System.lineSeparator()
        );

        append(
            builder,
            "  type",
            condition.type()
                .name()
        );

        append(
            builder,
            "  price",
            condition.price() == null
                ? "<absent>"
                : condition.price()
                .amount()
                .toPlainString()
        );

        append(
            builder,
            "  discountPercentage",
            condition.discountPercentage() == null
                ? "<absent>"
                : condition.discountPercentage()
                .value()
                .toPlainString()
        );

        append(
            builder,
            "  installmentCount",
            condition.installmentCount() == null
                ? "<absent>"
                : condition.installmentCount()
                .toString()
        );

        append(
            builder,
            "  installmentAmount",
            condition.installmentAmount() == null
                ? "<absent>"
                : condition.installmentAmount()
                .amount()
                .toPlainString()
        );

        append(
            builder,
            "  installmentTotal",
            condition.installmentTotal() == null
                ? "<absent>"
                : condition.installmentTotal()
                .amount()
                .toPlainString()
        );

        append(
            builder,
            "  interest",
            condition.interest() == null
                ? "<absent>"
                : condition.interest()
                .value()
                .toPlainString()
        );

        append(
            builder,
            "  paymentMethods",
            condition.paymentMethods()
                .toString()
        );
    }

    private void append(
        StringBuilder builder,
        String label,
        String value
    ) {

        builder.append(
            label
        ).append(
            ": "
        ).append(
            value
        ).append(
            System.lineSeparator()
        );
    }

    private String extractAsin(
        URI uri
    ) {

        Matcher matcher =
            ASIN_PATTERN.matcher(
                uri.getPath()
            );

        if (matcher.find()) {

            return matcher.group(
                1
            ).toUpperCase(
                Locale.ROOT
            );
        }

        return "UNKNOWN-ASIN";
    }

    private String extractPriceToPay(
        String html
    ) {

        if (html == null
            || html.isBlank()) {

            return null;
        }

        Matcher matcher =
            PRICE_TO_PAY_PATTERN.matcher(
                html
            );

        if (!matcher.find()) {

            return null;
        }

        return matcher.group(
            1
        );
    }

    private String exactIdRegion(
        String html,
        String id
    ) {

        String marker =
            "id=\""
                + id
                + "\"";

        int index =
            html.indexOf(
                marker
            );

        if (index < 0) {

            marker =
                "id='"
                    + id
                    + "'";

            index =
                html.indexOf(
                    marker
                );
        }

        if (index < 0) {

            return "";
        }

        int end =
            Math.min(
                html.length(),
                index
                    + FEATURE_REGION_LENGTH
            );

        return html.substring(
            index,
            end
        );
    }

    private String normalizeHtmlText(
        String html
    ) {

        if (html == null
            || html.isBlank()) {

            return "";
        }

        return html
            .replace(
                "&nbsp;",
                " "
            )
            .replace(
                "&#160;",
                " "
            )
            .replace(
                "&percnt;",
                "%"
            )
            .replace(
                "&agrave;",
                "à"
            )
            .replace(
                "&Agrave;",
                "À"
            )
            .replace(
                "&atilde;",
                "ã"
            )
            .replace(
                "&otilde;",
                "õ"
            )
            .replace(
                "&ccedil;",
                "ç"
            )
            .replaceAll(
                "<[^>]+>",
                " "
            )
            .replaceAll(
                "\\s+",
                " "
            )
            .trim();
    }

    private boolean containsIgnoreCase(
        String text,
        String expected
    ) {

        if (text == null
            || expected == null) {

            return false;
        }

        return text.toLowerCase(
            Locale.ROOT
        ).contains(
            expected.toLowerCase(
                Locale.ROOT
            )
        );
    }

    private String firstNonBlank(
        String first,
        String second
    ) {

        if (first != null
            && !first.isBlank()) {

            return first;
        }

        return second == null
            ? ""
            : second;
    }

    private String valueOrAbsent(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return "<absent>";
        }

        return value;
    }

    private String abbreviate(
        String value,
        int maximumLength
    ) {

        if (value == null
            || value.isBlank()) {

            return "<absent>";
        }

        if (value.length()
            <= maximumLength) {

            return value;
        }

        return value.substring(
            0,
            maximumLength
        ) + " ...";
    }
}
