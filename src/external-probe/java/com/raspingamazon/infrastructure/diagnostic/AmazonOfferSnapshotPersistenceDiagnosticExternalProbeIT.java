package com.raspingamazon.infrastructure.diagnostic;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonPaymentConditionParser;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageEnrichmentClient;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageParser;
import com.raspingamazon.infrastructure.amazon.parser.AmazonDealsParser;
import com.raspingamazon.infrastructure.collection.HttpCollectionCollector;
import com.raspingamazon.infrastructure.composition.AmazonDealProcessingComposition;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.http.JavaHttpTransport;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Probe diagnóstico temporário para identificar a causa SQL exata
 * das falhas de persistência de OfferSnapshot observadas no probe
 * real de ranking da Amazon.
 *
 * <p>Este teste não corrige nem contorna a persistência.</p>
 *
 * <p>Ele procura, entre as primeiras ofertas reais de Deals, uma
 * oferta cujo enrichment possua seller ou delivery ausente e então
 * executa exatamente o pipeline persistente normal para essa oferta.</p>
 *
 * <p>Quando ocorrer a falha, toda a cadeia de causas é impressa
 * antes da exceção ser relançada ao JUnit.</p>
 *
 * <p>A transação externa é sempre revertida.</p>
 */
class AmazonOfferSnapshotPersistenceDiagnosticExternalProbeIT {

    private static final String ENABLED_PROPERTY =
        "amazon.probe.offer-snapshot-diagnostic.enabled";

    private static final String DEALS_URL_PROPERTY =
        "amazon.probe.deals-url";

    private static final String DEFAULT_DEALS_URL =
        "https://www.amazon.com.br/deals";

    private static final int SEARCH_LIMIT =
        30;

    private static final Duration HTTP_TIMEOUT =
        Duration.ofSeconds(
            30
        );

    @Test
    void shouldExposeExactPersistenceFailureForMissingCommercialEvidence()
        throws Exception {

        requireExplicitOptIn();

        Clock clock =
            Clock.systemUTC();

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

        HttpClient httpClient =
            HttpClient.newBuilder()
                .connectTimeout(
                    HTTP_TIMEOUT
                )
                .followRedirects(
                    HttpClient.Redirect.NORMAL
                )
                .build();

        CollectionCollector realCollector =
            new HttpCollectionCollector(
                new JavaHttpTransport(
                    httpClient,
                    HTTP_TIMEOUT
                ),
                clock
            );

        CollectionRequest request =
            new CollectionRequest(
                configuredDealsUri()
            );

        CollectionResult collection =
            realCollector.collect(
                request
            );

        List<ParsedDeal> parsedDeals =
            new AmazonDealsParser()
                .parse(
                    collection
                );

        if (parsedDeals.isEmpty()) {

            fail(
                "Amazon Deals returned no parsed offers"
            );
        }

        int inspectionLimit =
            Math.min(
                SEARCH_LIMIT,
                parsedDeals.size()
            );

        ProductEnrichmentClient realEnrichmentClient =
            new AmazonProductPageEnrichmentClient(
                httpClient,
                new AmazonProductPageParser(),
                new AmazonPaymentConditionParser()
            );

        DiagnosticCandidate candidate =
            findCandidateWithMissingEvidence(
                parsedDeals,
                inspectionLimit,
                realEnrichmentClient
            );

        if (candidate == null) {

            fail(
                "No offer with missing seller/delivery evidence "
                    + "was found among the first "
                    + inspectionLimit
                    + " parsed Amazon Deals offers"
            );
        }

        System.out.println();
        System.out.println(
            "OFFER SNAPSHOT PERSISTENCE DIAGNOSTIC"
        );
        System.out.println(
            "====================================="
        );
        System.out.println(
            "ASIN: "
                + candidate.deal()
                .asin()
        );
        System.out.println(
            "Source position: "
                + candidate.sourcePosition()
        );
        System.out.println(
            "Seller raw value: "
                + printable(
                candidate.enrichment()
                    .sellerEvidence()
                    .rawValue()
            )
        );
        System.out.println(
            "Delivery raw value: "
                + printable(
                candidate.enrichment()
                    .deliveryEvidence()
                    .rawValue()
            )
        );
        System.out.println();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                /*
                 * A coleta já ocorreu de verdade.
                 *
                 * Nesta execução diagnóstica devolvemos exatamente
                 * o mesmo CollectionResult para evitar uma segunda
                 * requisição à página de Deals.
                 */
                CollectionCollector fixedCollector =
                    ignoredRequest ->
                        collection;

                /*
                 * Executamos somente a oferta selecionada para tornar
                 * a stack trace curta e inequívoca.
                 */
                DealsParser singleDealParser =
                    ignoredCollection ->
                        List.of(
                            candidate.deal()
                        );

                /*
                 * O enrichment também já ocorreu de verdade.
                 *
                 * Devolvemos exatamente o resultado observado para
                 * reproduzir a persistência sem fazer segunda chamada
                 * à página do produto.
                 */
                ProductEnrichmentClient fixedEnrichmentClient =
                    ignoredDeal ->
                        candidate.enrichment();

                AmazonDealProcessingService processingService =
                    AmazonDealProcessingComposition.create(
                        connection,
                        clock,
                        fixedCollector,
                        singleDealParser,
                        fixedEnrichmentClient
                    );

                try {

                    processingService.process(
                        request
                    );

                    fail(
                        "Expected OfferSnapshot persistence to fail "
                            + "for the diagnostic candidate, but the "
                            + "pipeline completed successfully"
                    );

                } catch (RuntimeException exception) {

                    printCauseChain(
                        exception
                    );

                    throw exception;
                }

            } finally {

                /*
                 * Mesmo se o problema desaparecer e a persistência
                 * passar, nenhuma evidência deste diagnóstico deverá
                 * permanecer no banco.
                 */
                connection.rollback();
            }
        }
    }

    private DiagnosticCandidate findCandidateWithMissingEvidence(
        List<ParsedDeal> deals,
        int inspectionLimit,
        ProductEnrichmentClient enrichmentClient
    ) {

        for (int index = 0;
             index < inspectionLimit;
             index++) {

            ParsedDeal deal =
                deals.get(
                    index
                );

            int sourcePosition =
                index + 1;

            try {

                ProductEnrichmentResult enrichment =
                    enrichmentClient.enrich(
                        deal
                    );

                String seller =
                    enrichment
                        .sellerEvidence()
                        .rawValue();

                String delivery =
                    enrichment
                        .deliveryEvidence()
                        .rawValue();

                boolean missingSeller =
                    seller == null
                        || seller.isBlank();

                boolean missingDelivery =
                    delivery == null
                        || delivery.isBlank();

                System.out.printf(
                    "[%02d/%02d] ASIN=%s seller=%s delivery=%s%n",
                    sourcePosition,
                    inspectionLimit,
                    deal.asin(),
                    printable(
                        seller
                    ),
                    printable(
                        delivery
                    )
                );

                if (missingSeller
                    || missingDelivery) {

                    return new DiagnosticCandidate(
                        sourcePosition,
                        deal,
                        enrichment
                    );
                }

            } catch (RuntimeException exception) {

                System.out.printf(
                    "[%02d/%02d] ASIN=%s enrichment_failed=%s%n",
                    sourcePosition,
                    inspectionLimit,
                    deal.asin(),
                    summarize(
                        exception
                    )
                );
            }
        }

        return null;
    }

    private void printCauseChain(
        Throwable throwable
    ) {

        System.err.println();
        System.err.println(
            "EXCEPTION CAUSE CHAIN"
        );
        System.err.println(
            "====================="
        );

        Throwable current =
            throwable;

        int level =
            0;

        while (current != null) {

            System.err.println(
                "cause["
                    + level
                    + "] "
                    + current.getClass()
                    .getName()
                    + ": "
                    + printable(
                    current.getMessage()
                )
            );

            current =
                current.getCause();

            level++;
        }

        System.err.println();
    }

    private URI configuredDealsUri() {

        String configured =
            System.getProperty(
                DEALS_URL_PROPERTY,
                DEFAULT_DEALS_URL
            );

        URI uri;

        try {

            uri =
                URI.create(
                    configured.trim()
                );

        } catch (RuntimeException exception) {

            throw new IllegalStateException(
                "Invalid Amazon Deals URL supplied in -D"
                    + DEALS_URL_PROPERTY,
                exception
            );
        }

        if (!uri.isAbsolute()) {

            throw new IllegalStateException(
                "Amazon Deals URL must be absolute"
            );
        }

        String scheme =
            uri.getScheme();

        if (scheme == null
            || (!scheme.equalsIgnoreCase(
            "http"
        )
            && !scheme.equalsIgnoreCase(
            "https"
        ))) {

            throw new IllegalStateException(
                "Amazon Deals URL must use HTTP or HTTPS"
            );
        }

        return uri;
    }

    private void requireExplicitOptIn() {

        boolean enabled =
            Boolean.parseBoolean(
                System.getProperty(
                    ENABLED_PROPERTY,
                    "false"
                )
            );

        Assumptions.assumeTrue(
            enabled,
            "OfferSnapshot persistence diagnostic probe is opt-in. "
                + "Enable with -D"
                + ENABLED_PROPERTY
                + "=true"
        );
    }

    private static String summarize(
        RuntimeException exception
    ) {

        String message =
            exception.getMessage();

        if (message == null
            || message.isBlank()) {

            return exception.getClass()
                .getSimpleName();
        }

        return exception.getClass()
            .getSimpleName()
            + ": "
            + message;
    }

    private static String printable(
        String value
    ) {

        if (value == null) {
            return "<null>";
        }

        if (value.isBlank()) {
            return "<blank>";
        }

        return value
            .replaceAll(
                "\\s+",
                " "
            )
            .trim();
    }

    private record DiagnosticCandidate(
        int sourcePosition,
        ParsedDeal deal,
        ProductEnrichmentResult enrichment
    ) {
    }
}
