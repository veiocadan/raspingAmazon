package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.commercial.PaymentCondition;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Cliente de enriquecimento baseado na página individual do produto.
 *
 * A responsabilidade deste adaptador é:
 *
 * 1. acessar a página individual;
 * 2. obter o HTML;
 * 3. interpretar seller/delivery;
 * 4. interpretar condições comerciais;
 * 5. montar o contrato normalizado de enriquecimento.
 *
 * Ele não decide elegibilidade, filtros, score ou publicação.
 */
public final class AmazonProductPageEnrichmentClient
    implements ProductEnrichmentClient {

    private static final String USER_AGENT =
        "RaspingAmazon/1.0";

    private static final String SOURCE =
        "AMAZON_PRODUCT_PAGE";

    private static final java.time.Duration REQUEST_TIMEOUT =
        java.time.Duration.ofSeconds(
            20
        );

    private final HttpClient httpClient;

    private final AmazonProductPageParser parser;

    private final AmazonPaymentConditionParser
        paymentConditionParser;

    /**
     * Construtor padrão utilizado pela aplicação.
     */
    public AmazonProductPageEnrichmentClient() {
        this(
            HttpClient.newBuilder()
                .connectTimeout(
                    REQUEST_TIMEOUT
                )
                .followRedirects(
                    HttpClient.Redirect.NORMAL
                )
                .build(),
            new AmazonProductPageParser(),
            new AmazonPaymentConditionParser()
        );
    }

    /**
     * Construtor de compatibilidade utilizado pelos testes existentes.
     *
     * A partir da FASE 9-C1.5 ele também habilita automaticamente
     * o parser comercial padrão.
     */
    public AmazonProductPageEnrichmentClient(
        HttpClient httpClient,
        AmazonProductPageParser parser
    ) {
        this(
            httpClient,
            parser,
            new AmazonPaymentConditionParser()
        );
    }

    /**
     * Construtor completo para composição e testes isolados.
     */
    public AmazonProductPageEnrichmentClient(
        HttpClient httpClient,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser
    ) {
        this.httpClient =
            Objects.requireNonNull(
                httpClient,
                "HTTP client must not be null"
            );

        this.parser =
            Objects.requireNonNull(
                parser,
                "Parser must not be null"
            );

        this.paymentConditionParser =
            Objects.requireNonNull(
                paymentConditionParser,
                "Payment condition parser must not be null"
            );
    }

    /**
     * Enriquece uma oferta previamente interpretada pela etapa
     * de Deals.
     */
    @Override
    public ProductEnrichmentResult enrich(
        ParsedDeal parsedDeal
    ) {
        Objects.requireNonNull(
            parsedDeal,
            "Parsed deal must not be null"
        );

        String productUrl =
            parsedDeal.productUrl();

        if (productUrl == null
            || productUrl.isBlank()) {

            throw new ProductEnrichmentException(
                "Parsed deal does not contain a product URL"
            );
        }

        URI uri =
            URI.create(
                productUrl
            );

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(
                    uri
                )
                .timeout(
                    REQUEST_TIMEOUT
                )
                .header(
                    "User-Agent",
                    USER_AGENT
                )
                .header(
                    "Accept",
                    "text/html"
                )
                .GET()
                .build();

        HttpResponse<String> response;

        try {
            response =
                httpClient.send(
                    request,
                    HttpResponse
                        .BodyHandlers
                        .ofString()
                );

        } catch (InterruptedException exception) {

            Thread.currentThread()
                .interrupt();

            throw new ProductEnrichmentException(
                "Product page request was interrupted",
                exception
            );

        } catch (Exception exception) {

            throw new ProductEnrichmentException(
                "Failed to retrieve Amazon product page",
                exception
            );
        }

        if (response.statusCode() < 200
            || response.statusCode() >= 300) {

            throw new ProductEnrichmentException(
                "Amazon product page returned HTTP "
                    + response.statusCode()
            );
        }

        String html =
            response.body();

        if (html == null
            || html.isBlank()) {

            throw new ProductEnrichmentException(
                "Amazon product page returned an empty response"
            );
        }

        AmazonProductPageParser.ParsedProductOffer parsed =
            parser.parse(
                html
            );

        List<PaymentCondition> paymentConditions =
            paymentConditionParser.parse(
                html
            );

        OffsetDateTime collectedAt =
            OffsetDateTime.now(
                ZoneOffset.UTC
            );

        return new ProductEnrichmentResult(
            parsedDeal.asin(),
            parsed.sellerEvidence(),
            parsed.deliveryEvidence(),
            paymentConditions,
            SOURCE,
            productUrl,
            collectedAt
        );
    }

    /**
     * Erro específico da operação de enriquecimento.
     */
    public static final class ProductEnrichmentException
        extends RuntimeException {

        public ProductEnrichmentException(
            String message
        ) {
            super(
                message
            );
        }

        public ProductEnrichmentException(
            String message,
            Throwable cause
        ) {
            super(
                message,
                cause
            );
        }
    }
}
