package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.commercial.PaymentCondition;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Objects;

/**
 * Cliente de enrichment baseado na página individual do produto.
 *
 * <p>A responsabilidade deste adaptador é:</p>
 *
 * <ol>
 *     <li>solicitar o conteúdo da página a um provider;</li>
 *     <li>interpretar seller e delivery;</li>
 *     <li>interpretar rating e reviewCount;</li>
 *     <li>interpretar condições comerciais;</li>
 *     <li>montar o contrato normalizado de enrichment.</li>
 * </ol>
 *
 * <p>Todos os parsers recebem exatamente o mesmo HTML adquirido pelo
 * ProductPageContentProvider. Não existe segunda chamada HTTP para
 * rating/reviewCount.</p>
 *
 * <p>O mecanismo utilizado para adquirir o conteúdo da página fica
 * deliberadamente separado desta classe. Assim, HTTP bruto e DOM
 * renderizado podem ser estratégias substituíveis sem alterar
 * parsers ou regras de negócio.</p>
 *
 * <p>Esta classe não decide elegibilidade, filtros, score ou
 * publicação.</p>
 */
public final class AmazonProductPageEnrichmentClient
    implements ProductEnrichmentClient {

    private static final String SOURCE =
        "AMAZON_PRODUCT_PAGE";

    private final ProductPageContentProvider
        contentProvider;

    private final AmazonProductPageParser
        parser;

    private final AmazonPaymentConditionParser
        paymentConditionParser;

    private final AmazonCustomerReviewParser
        customerReviewParser;

    /**
     * Construtor padrão utilizado pela aplicação.
     */
    public AmazonProductPageEnrichmentClient() {

        this(
            new HttpProductPageContentProvider(),
            new AmazonProductPageParser(),
            new AmazonPaymentConditionParser(),
            new AmazonCustomerReviewParser()
        );
    }

    /**
     * Construtor de compatibilidade utilizado pela composition e
     * pelos testes existentes.
     */
    public AmazonProductPageEnrichmentClient(
        HttpClient httpClient,
        AmazonProductPageParser parser
    ) {

        this(
            new HttpProductPageContentProvider(
                httpClient
            ),
            parser,
            new AmazonPaymentConditionParser(),
            new AmazonCustomerReviewParser()
        );
    }

    /**
     * Construtor de compatibilidade para consumidores que injetam
     * explicitamente os parsers já existentes.
     */
    public AmazonProductPageEnrichmentClient(
        HttpClient httpClient,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser
    ) {

        this(
            new HttpProductPageContentProvider(
                httpClient
            ),
            parser,
            paymentConditionParser,
            new AmazonCustomerReviewParser()
        );
    }

    /**
     * Construtor estrutural de compatibilidade para providers
     * alternativos já existentes.
     */
    public AmazonProductPageEnrichmentClient(
        ProductPageContentProvider contentProvider,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser
    ) {

        this(
            contentProvider,
            parser,
            paymentConditionParser,
            new AmazonCustomerReviewParser()
        );
    }

    /**
     * Construtor completo.
     *
     * @param contentProvider provider responsável por adquirir o conteúdo
     * @param parser parser de seller/delivery
     * @param paymentConditionParser parser das condições comerciais
     * @param customerReviewParser parser estrutural de rating/reviewCount
     */
    public AmazonProductPageEnrichmentClient(
        ProductPageContentProvider contentProvider,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser,
        AmazonCustomerReviewParser customerReviewParser
    ) {

        this.contentProvider =
            Objects.requireNonNull(
                contentProvider,
                "contentProvider must not be null"
            );

        this.parser =
            Objects.requireNonNull(
                parser,
                "parser must not be null"
            );

        this.paymentConditionParser =
            Objects.requireNonNull(
                paymentConditionParser,
                "paymentConditionParser must not be null"
            );

        this.customerReviewParser =
            Objects.requireNonNull(
                customerReviewParser,
                "customerReviewParser must not be null"
            );
    }

    /**
     * Enriquece uma oferta previamente interpretada pela etapa de Deals.
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

        URI uri;

        try {

            uri =
                URI.create(
                    productUrl
                );

        } catch (IllegalArgumentException exception) {

            throw new ProductEnrichmentException(
                "Parsed deal contains an invalid product URL",
                exception
            );
        }

        ProductPageContent pageContent;

        try {

            pageContent =
                contentProvider.load(
                    uri
                );

        } catch (ProductPageContentProviderException exception) {

            throw new ProductEnrichmentException(
                "Failed to retrieve Amazon product page",
                exception
            );
        }

        String html =
            pageContent.html();

        AmazonProductPageParser.ParsedProductOffer parsed =
            parser.parse(
                html
            );

        AmazonCustomerReviewParser.ParsedCustomerReviews
            customerReviews =
            customerReviewParser.parse(
                html,
                parsedDeal.asin()
            );

        List<PaymentCondition> paymentConditions =
            paymentConditionParser.parse(
                html
            );

        return new ProductEnrichmentResult(
            parsedDeal.asin(),
            parsed.sellerEvidence(),
            parsed.deliveryEvidence(),
            customerReviews.ratingEvidence(),
            customerReviews.reviewCountEvidence(),
            paymentConditions,
            SOURCE,
            productUrl,
            pageContent.collectedAt()
        );
    }

    /**
     * Erro específico da operação de enrichment.
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
