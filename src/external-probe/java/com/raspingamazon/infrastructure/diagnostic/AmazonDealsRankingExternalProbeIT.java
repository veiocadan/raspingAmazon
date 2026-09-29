package com.raspingamazon.infrastructure.diagnostic;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.application.deal.ProcessedDealResult;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;
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
import com.raspingamazon.infrastructure.persistence.adapter.JdbcSuccessfulPublicationHistoryQueryAdapter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Probe externo do ranking de ofertas reais da Amazon.
 *
 * <p>Fluxo exercitado:</p>
 *
 * <pre>
 * Amazon Deals real
 *      ↓
 * AmazonDealsParser
 *      ↓
 * primeiras 30 ofertas
 *      ↓
 * página individual renderizada por Playwright
 *      ↓
 * AmazonProductPageEnrichmentClient
 *      ↓
 * seller / delivery
 * rating / reviewCount
 * payment conditions
 *      ↓
 * Product / OfferSnapshot
 *      ↓
 * elegibilidade / filtros
 *      ↓
 * score
 *      ↓
 * ranking operacional sem histórico
 *      ↓
 * quota diária simulada = 5
 * </pre>
 *
 * <p>A página de Deals continua sendo adquirida pelo mecanismo HTTP
 * normal. Apenas a página individual do produto utiliza DOM renderizado,
 * reproduzindo a estratégia já comprovada pelas probes anteriores.</p>
 *
 * <p>Este teste é deliberadamente opt-in e não pertence ao gate
 * hermético.</p>
 *
 * <p>A persistência executada durante a probe ocorre dentro de uma
 * transação externa e é revertida ao final.</p>
 */
class AmazonDealsRankingExternalProbeIT {

    private static final String ENABLED_PROPERTY =
        "amazon.probe.deals-ranking.enabled";

    private static final String DEALS_URL_PROPERTY =
        "amazon.probe.deals-url";

    private static final String DEFAULT_DEALS_URL =
        "https://www.amazon.com.br/deals";

    private static final int DEAL_LIMIT =
        30;

    private static final int DAILY_QUOTA =
        5;

    private static final String PROBE_CHANNEL =
        "EXTERNAL_PROBE";

    private static final Duration HTTP_TIMEOUT =
        Duration.ofSeconds(
            30
        );

    /**
     * ADR-0010 / ADR-0013 no cenário sem histórico:
     *
     * todos os candidatos pertencem à classe
     * NEVER_SUCCESSFULLY_PUBLISHED.
     *
     * Portanto:
     *
     * 1. maior score;
     * 2. ASIN como desempate estável.
     */
    private static final Comparator<ProbeEvaluation>
        NO_HISTORY_SELECTION_ORDER =
        Comparator
            .comparing(
                ProbeEvaluation::score,
                Comparator.reverseOrder()
            )
            .thenComparing(
                ProbeEvaluation::asin
            );

    @Test
    void shouldRankFirstThirtyRealAmazonDealsWithDailyQuotaFive()
        throws Exception {

        requireExplicitOptIn();

        Clock clock =
            Clock.systemUTC();

        URI dealsUri =
            configuredDealsUri();

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

        /*
         * A página de Deals continua utilizando a fronteira HTTP normal.
         *
         * O problema diagnosticado não estava nessa aquisição, mas na
         * aquisição HTTP bruta das páginas individuais dos produtos.
         */
        HttpClient dealsHttpClient =
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
                    dealsHttpClient,
                    HTTP_TIMEOUT
                ),
                clock
            );

        CollectionRequest collectionRequest =
            new CollectionRequest(
                dealsUri
            );

        CollectionResult collectionResult =
            realCollector.collect(
                collectionRequest
            );

        AmazonDealsParser realDealsParser =
            new AmazonDealsParser();

        List<ParsedDeal> allParsedDeals =
            realDealsParser.parse(
                collectionResult
            );

        assertTrue(
            allParsedDeals.size() >= DEAL_LIMIT,
            "Amazon Deals probe requires at least "
                + DEAL_LIMIT
                + " parsed deals, but found "
                + allParsedDeals.size()
        );

        List<ParsedDeal> firstDeals =
            List.copyOf(
                allParsedDeals.subList(
                    0,
                    DEAL_LIMIT
                )
            );

        assertEquals(
            DEAL_LIMIT,
            firstDeals.size()
        );

        String probeDestination =
            "ranking-probe-"
                + System.nanoTime();

        List<ProbeObservation> observations =
            new ArrayList<>();

        /*
         * Um único provider Playwright é reutilizado durante toda a probe.
         *
         * Assim não abrimos um novo browser para cada produto.
         */
        try (PlaywrightRenderedProductPageContentProvider renderedProvider =
                 new PlaywrightRenderedProductPageContentProvider()) {

            ProductEnrichmentClient enrichmentClient =
                new AmazonProductPageEnrichmentClient(
                    renderedProvider,
                    new AmazonProductPageParser(),
                    new AmazonPaymentConditionParser()
                );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                /*
                 * AmazonDealProcessingService utiliza
                 * JdbcTransactionAdapter.
                 *
                 * Como já existe uma transação externa, cada oferta usa
                 * savepoint e não faz commit da transação principal.
                 */
                connection.setAutoCommit(
                    false
                );

                try {

                    for (int index = 0;
                         index < firstDeals.size();
                         index++) {

                        ParsedDeal parsedDeal =
                            firstDeals.get(
                                index
                            );

                        int sourcePosition =
                            index + 1;

                        ProbeObservation observation =
                            processSingleDeal(
                                connection,
                                clock,
                                collectionRequest,
                                collectionResult,
                                parsedDeal,
                                sourcePosition,
                                enrichmentClient
                            );

                        observations.add(
                            observation
                        );

                        printProgress(
                            sourcePosition,
                            observation
                        );
                    }

                    List<ProbeEvaluation> eligibleScored =
                        observations.stream()
                            .filter(
                                ProbeObservation::evaluated
                            )
                            .map(
                                ProbeObservation::evaluation
                            )
                            .filter(
                                ProbeEvaluation::eligible
                            )
                            .filter(
                                evaluation ->
                                    evaluation.score() != null
                            )
                            .toList();

                    /*
                     * O destination sintético torna esta execução
                     * independente de publicações reais anteriores.
                     */
                    assertNoPublicationHistory(
                        connection,
                        eligibleScored,
                        probeDestination
                    );

                    List<ProbeEvaluation> ranked =
                        eligibleScored.stream()
                            .sorted(
                                NO_HISTORY_SELECTION_ORDER
                            )
                            .toList();

                    String filterProfileVersion =
                        loadActiveVersion(
                            connection,
                            "filter_profile"
                        );

                    String scoreProfileVersion =
                        loadActiveVersion(
                            connection,
                            "score_profile"
                        );

                    String report =
                        buildReport(
                            dealsUri,
                            collectionResult,
                            observations,
                            ranked,
                            probeDestination,
                            filterProfileVersion,
                            scoreProfileVersion
                        );

                    writeReport(
                        report
                    );

                    System.out.println();
                    System.out.println(
                        report
                    );

                    /*
                     * Não reduzimos artificialmente a quota para acomodar
                     * uma aquisição incompleta.
                     *
                     * O experimento solicitado exige cinco candidatos
                     * elegíveis com score.
                     */
                    assertTrue(
                        ranked.size() >= DAILY_QUOTA,
                        "Probe produced only "
                            + ranked.size()
                            + " eligible scored offers; "
                            + DAILY_QUOTA
                            + " are required to simulate the requested quota"
                    );

                    assertRankingIsDescending(
                        ranked
                    );

                } finally {

                    /*
                     * A probe não deve poluir o banco local com produtos,
                     * snapshots, avaliações ou momentum temporários.
                     */
                    connection.rollback();
                }
            }
        }
    }

    private ProbeObservation processSingleDeal(
        Connection connection,
        Clock clock,
        CollectionRequest collectionRequest,
        CollectionResult collectionResult,
        ParsedDeal parsedDeal,
        int sourcePosition,
        ProductEnrichmentClient enrichmentClient
    ) {

        /*
         * A coleta de Deals já ocorreu uma vez.
         *
         * Esta composição individual permite que uma falha de uma oferta
         * não impeça o diagnóstico das demais 29.
         */
        CollectionCollector fixedCollector =
            ignoredRequest ->
                collectionResult;

        DealsParser singleDealParser =
            ignoredCollection ->
                List.of(
                    parsedDeal
                );

        AmazonDealProcessingService processingService =
            AmazonDealProcessingComposition.create(
                connection,
                clock,
                fixedCollector,
                singleDealParser,
                enrichmentClient
            );

        try {

            List<ProcessedDealResult> processed =
                processingService.process(
                    collectionRequest
                );

            if (processed.size() != 1) {

                throw new IllegalStateException(
                    "Single-deal probe expected exactly one "
                        + "ProcessedDealResult but received "
                        + processed.size()
                );
            }

            ProcessedDealResult processedDeal =
                processed.getFirst();

            Long snapshotId =
                processedDeal.offerSnapshot()
                    .id();

            if (snapshotId == null
                || snapshotId <= 0L) {

                throw new IllegalStateException(
                    "Processed probe snapshot must have "
                        + "a positive persisted id"
                );
            }

            ProbeEvaluation evaluation =
                loadEvaluation(
                    connection,
                    sourcePosition,
                    snapshotId
                );

            return ProbeObservation.evaluated(
                sourcePosition,
                parsedDeal,
                evaluation
            );

        } catch (RuntimeException exception) {

            return ProbeObservation.failed(
                sourcePosition,
                parsedDeal,
                summarizeFailure(
                    exception
                )
            );
        }
    }

    private ProbeEvaluation loadEvaluation(
        Connection connection,
        int sourcePosition,
        long snapshotId
    ) {

        String sql =
            """
            SELECT
                p.asin,
                p.title,
                p.product_url,
                os.current_price,
                os.rating,
                os.review_count,
                os.seller_name,
                os.delivery_provider,
                de.eligible,
                de.score,
                de.rejection_reason,
                de.score_version,
                de.evaluated_at
            FROM deal_evaluation AS de
            JOIN offer_snapshot AS os
              ON os.id = de.offer_snapshot_id
            JOIN product AS p
              ON p.id = os.product_id
            WHERE de.offer_snapshot_id = ?
            ORDER BY
                de.evaluated_at DESC,
                de.id DESC
            LIMIT 1
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "No DealEvaluation was found for "
                            + "OfferSnapshot "
                            + snapshotId
                    );
                }

                return new ProbeEvaluation(
                    sourcePosition,
                    resultSet.getString(
                        "asin"
                    ),
                    resultSet.getString(
                        "title"
                    ),
                    resultSet.getString(
                        "product_url"
                    ),
                    resultSet.getBigDecimal(
                        "current_price"
                    ),
                    resultSet.getBigDecimal(
                        "rating"
                    ),
                    nullableLong(
                        resultSet,
                        "review_count"
                    ),
                    resultSet.getString(
                        "seller_name"
                    ),
                    resultSet.getString(
                        "delivery_provider"
                    ),
                    resultSet.getBoolean(
                        "eligible"
                    ),
                    resultSet.getBigDecimal(
                        "score"
                    ),
                    resultSet.getString(
                        "rejection_reason"
                    ),
                    resultSet.getString(
                        "score_version"
                    ),
                    resultSet.getObject(
                        "evaluated_at",
                        OffsetDateTime.class
                    )
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load DealEvaluation for OfferSnapshot "
                    + snapshotId,
                exception
            );
        }
    }

    private void assertNoPublicationHistory(
        Connection connection,
        List<ProbeEvaluation> evaluations,
        String probeDestination
    ) {

        Set<Asin> asins =
            new LinkedHashSet<>();

        for (ProbeEvaluation evaluation : evaluations) {

            asins.add(
                new Asin(
                    evaluation.asin()
                )
            );
        }

        if (asins.isEmpty()) {
            return;
        }

        Map<Asin, SuccessfulPublicationHistory> history =
            new JdbcSuccessfulPublicationHistoryQueryAdapter(
                connection
            ).findSuccessfulByAsins(
                asins,
                PROBE_CHANNEL,
                probeDestination
            );

        assertTrue(
            history.isEmpty(),
            "Synthetic ranking probe scope must not contain "
                + "successful publication history"
        );
    }

    private String buildReport(
        URI dealsUri,
        CollectionResult collectionResult,
        List<ProbeObservation> observations,
        List<ProbeEvaluation> ranked,
        String probeDestination,
        String filterProfileVersion,
        String scoreProfileVersion
    ) {

        StringBuilder builder =
            new StringBuilder();

        line(
            builder,
            "AMAZON DEALS RANKING EXTERNAL PROBE"
        );

        line(
            builder,
            "==================================="
        );

        field(
            builder,
            "Deals URL",
            dealsUri.toString()
        );

        field(
            builder,
            "Product-page acquisition",
            "PLAYWRIGHT_RENDERED_DOM"
        );

        field(
            builder,
            "Collected at",
            collectionResult.collectedAt()
                .toString()
        );

        field(
            builder,
            "Deals inspected",
            Integer.toString(
                DEAL_LIMIT
            )
        );

        field(
            builder,
            "Daily quota simulated",
            Integer.toString(
                DAILY_QUOTA
            )
        );

        field(
            builder,
            "Filter profile",
            filterProfileVersion
        );

        field(
            builder,
            "Score profile",
            scoreProfileVersion
        );

        field(
            builder,
            "Selection policy",
            "ADR-0010/ADR-0013 - no-history case"
        );

        field(
            builder,
            "Publication history scope",
            PROBE_CHANNEL
                + " / "
                + probeDestination
        );

        field(
            builder,
            "Successful history rows",
            "0"
        );

        long processingFailures =
            observations.stream()
                .filter(
                    observation ->
                        !observation.evaluated()
                )
                .count();

        long ineligible =
            observations.stream()
                .filter(
                    ProbeObservation::evaluated
                )
                .map(
                    ProbeObservation::evaluation
                )
                .filter(
                    evaluation ->
                        !evaluation.eligible()
                )
                .count();

        field(
            builder,
            "Processing failures",
            Long.toString(
                processingFailures
            )
        );

        field(
            builder,
            "Ineligible after evaluation",
            Long.toString(
                ineligible
            )
        );

        field(
            builder,
            "Eligible with score",
            Integer.toString(
                ranked.size()
            )
        );

        line(
            builder,
            ""
        );

        line(
            builder,
            "TOP 5 — SELECTED"
        );

        line(
            builder,
            "================"
        );

        int selectedCount =
            Math.min(
                DAILY_QUOTA,
                ranked.size()
            );

        for (int index = 0;
             index < selectedCount;
             index++) {

            ProbeEvaluation evaluation =
                ranked.get(
                    index
                );

            int rank =
                index + 1;

            line(
                builder,
                ""
            );

            line(
                builder,
                "#"
                    + rank
                    + " | SELECTED"
            );

            field(
                builder,
                "ASIN",
                evaluation.asin()
            );

            field(
                builder,
                "Score",
                decimal(
                    evaluation.score()
                )
            );

            field(
                builder,
                "Recency",
                "NEVER_SUCCESSFULLY_PUBLISHED"
            );

            field(
                builder,
                "Successful publication count",
                "0"
            );

            field(
                builder,
                "Title",
                clean(
                    evaluation.title()
                )
            );

            field(
                builder,
                "Current price",
                money(
                    evaluation.currentPrice()
                )
            );

            field(
                builder,
                "Rating",
                decimalOrAbsent(
                    evaluation.rating()
                )
            );

            field(
                builder,
                "Review count",
                evaluation.reviewCount() == null
                    ? "<absent>"
                    : evaluation.reviewCount()
                    .toString()
            );

            field(
                builder,
                "Seller",
                valueOrAbsent(
                    evaluation.sellerName()
                )
            );

            field(
                builder,
                "Delivery",
                valueOrAbsent(
                    evaluation.deliveryProvider()
                )
            );

            field(
                builder,
                "Score version",
                valueOrAbsent(
                    evaluation.scoreVersion()
                )
            );

            field(
                builder,
                "Deals source position",
                Integer.toString(
                    evaluation.sourcePosition()
                )
            );

            field(
                builder,
                "URL",
                valueOrAbsent(
                    evaluation.productUrl()
                )
            );
        }

        line(
            builder,
            ""
        );

        line(
            builder,
            "REMAINING ELIGIBLE CANDIDATES"
        );

        line(
            builder,
            "============================="
        );

        if (ranked.size() <= DAILY_QUOTA) {

            line(
                builder,
                "<none>"
            );

        } else {

            for (int index = DAILY_QUOTA;
                 index < ranked.size();
                 index++) {

                ProbeEvaluation evaluation =
                    ranked.get(
                        index
                    );

                int rank =
                    index + 1;

                line(
                    builder,
                    String.format(
                        Locale.ROOT,
                        "#%02d | ASIN=%s | score=%s | status=NOT_SELECTED_DUE_TO_QUOTA",
                        rank,
                        evaluation.asin(),
                        decimal(
                            evaluation.score()
                        )
                    )
                );
            }
        }

        /*
         * Somente ofertas que efetivamente participaram do ranking
         * podem ser recusadas por quota.
         */
        List<ProbeObservation> nonQuotaExclusions =
            observations.stream()
                .filter(
                    observation -> {

                        if (!observation.evaluated()) {
                            return true;
                        }

                        ProbeEvaluation evaluation =
                            observation.evaluation();

                        return !evaluation.eligible()
                            || evaluation.score() == null;
                    }
                )
                .toList();

        if (!nonQuotaExclusions.isEmpty()) {

            line(
                builder,
                ""
            );

            line(
                builder,
                "NON-QUOTA EXCLUSIONS"
            );

            line(
                builder,
                "===================="
            );

            for (ProbeObservation observation
                : nonQuotaExclusions) {

                if (!observation.evaluated()) {

                    line(
                        builder,
                        String.format(
                            Locale.ROOT,
                            "source#%02d | ASIN=%s | status=PROCESSING_FAILED | error=%s",
                            observation.sourcePosition(),
                            valueOrAbsent(
                                observation.asin()
                            ),
                            clean(
                                observation.failure()
                            )
                        )
                    );

                    continue;
                }

                ProbeEvaluation evaluation =
                    observation.evaluation();

                line(
                    builder,
                    String.format(
                        Locale.ROOT,
                        "source#%02d | ASIN=%s | status=INELIGIBLE | reason=%s",
                        observation.sourcePosition(),
                        evaluation.asin(),
                        valueOrAbsent(
                            evaluation.rejectionReason()
                        )
                    )
                );
            }
        }

        return builder.toString();
    }

    private void writeReport(
        String report
    ) throws Exception {

        Path outputDirectory =
            Path.of(
                "target",
                "diagnostics",
                "deals-ranking-probe"
            );

        Files.createDirectories(
            outputDirectory
        );

        Path reportPath =
            outputDirectory.resolve(
                "amazon-deals-ranking-report.txt"
            );

        Files.writeString(
            reportPath,
            report,
            StandardCharsets.UTF_8
        );

        System.out.println(
            "Probe report written to: "
                + reportPath.toAbsolutePath()
                .normalize()
        );
    }

    private void assertRankingIsDescending(
        List<ProbeEvaluation> ranked
    ) {

        for (int index = 1;
             index < ranked.size();
             index++) {

            ProbeEvaluation previous =
                ranked.get(
                    index - 1
                );

            ProbeEvaluation current =
                ranked.get(
                    index
                );

            int comparison =
                NO_HISTORY_SELECTION_ORDER.compare(
                    previous,
                    current
                );

            assertTrue(
                comparison <= 0,
                "Ranking order violated between "
                    + previous.asin()
                    + " and "
                    + current.asin()
            );
        }
    }

    private String loadActiveVersion(
        Connection connection,
        String tableName
    ) {

        if (!Set.of(
            "filter_profile",
            "score_profile"
        ).contains(
            tableName
        )) {

            throw new IllegalArgumentException(
                "Unsupported profile table: "
                    + tableName
            );
        }

        String sql =
            "SELECT version "
                + "FROM "
                + tableName
                + " "
                + "WHERE active = TRUE";

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 );
             ResultSet resultSet =
                 statement.executeQuery()) {

            if (!resultSet.next()) {

                throw new IllegalStateException(
                    "No active profile found in "
                        + tableName
                );
            }

            String version =
                resultSet.getString(
                    "version"
                );

            if (resultSet.next()) {

                throw new IllegalStateException(
                    "More than one active profile found in "
                        + tableName
                );
            }

            return version;

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load active profile from "
                    + tableName,
                exception
            );
        }
    }

    private void printProgress(
        int sourcePosition,
        ProbeObservation observation
    ) {

        String asin =
            valueOrAbsent(
                observation.asin()
            );

        if (!observation.evaluated()) {

            System.out.printf(
                Locale.ROOT,
                "[%02d/%02d] ASIN=%s status=PROCESSING_FAILED error=%s%n",
                sourcePosition,
                DEAL_LIMIT,
                asin,
                clean(
                    observation.failure()
                )
            );

            return;
        }

        ProbeEvaluation evaluation =
            observation.evaluation();

        if (!evaluation.eligible()) {

            System.out.printf(
                Locale.ROOT,
                "[%02d/%02d] ASIN=%s status=INELIGIBLE seller=%s delivery=%s reason=%s%n",
                sourcePosition,
                DEAL_LIMIT,
                asin,
                valueOrAbsent(
                    evaluation.sellerName()
                ),
                valueOrAbsent(
                    evaluation.deliveryProvider()
                ),
                valueOrAbsent(
                    evaluation.rejectionReason()
                )
            );

            return;
        }

        System.out.printf(
            Locale.ROOT,
            "[%02d/%02d] ASIN=%s status=ELIGIBLE score=%s seller=%s delivery=%s%n",
            sourcePosition,
            DEAL_LIMIT,
            asin,
            decimalOrAbsent(
                evaluation.score()
            ),
            valueOrAbsent(
                evaluation.sellerName()
            ),
            valueOrAbsent(
                evaluation.deliveryProvider()
            )
        );
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
            "https"
        )
            && !scheme.equalsIgnoreCase(
            "http"
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
            "Real Amazon Deals ranking probe is opt-in. "
                + "Enable with -D"
                + ENABLED_PROPERTY
                + "=true"
        );
    }

    private Long nullableLong(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        long value =
            resultSet.getLong(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private String summarizeFailure(
        RuntimeException exception
    ) {

        Throwable root =
            exception;

        while (root.getCause() != null) {

            root =
                root.getCause();
        }

        String outerMessage =
            exception.getMessage();

        String rootMessage =
            root.getMessage();

        if (root == exception) {

            if (outerMessage == null
                || outerMessage.isBlank()) {

                return exception.getClass()
                    .getSimpleName();
            }

            return exception.getClass()
                .getSimpleName()
                + ": "
                + outerMessage;
        }

        return exception.getClass()
            .getSimpleName()
            + ": "
            + valueOrAbsent(
            outerMessage
        )
            + " | root="
            + root.getClass()
            .getSimpleName()
            + ": "
            + valueOrAbsent(
            rootMessage
        );
    }

    private static String money(
        BigDecimal value
    ) {

        if (value == null) {
            return "<absent>";
        }

        return "R$ "
            + value.setScale(
            2,
            java.math.RoundingMode.HALF_UP
        ).toPlainString();
    }

    private static String decimal(
        BigDecimal value
    ) {

        Objects.requireNonNull(
            value,
            "decimal value must not be null"
        );

        return value.stripTrailingZeros()
            .toPlainString();
    }

    private static String decimalOrAbsent(
        BigDecimal value
    ) {

        if (value == null) {
            return "<absent>";
        }

        return decimal(
            value
        );
    }

    private static String valueOrAbsent(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return "<absent>";
        }

        return value;
    }

    private static String clean(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return "<absent>";
        }

        return value
            .replaceAll(
                "\\s+",
                " "
            )
            .trim();
    }

    private static void field(
        StringBuilder builder,
        String label,
        String value
    ) {

        builder.append(
            label
        );

        builder.append(
            ": "
        );

        builder.append(
            value
        );

        builder.append(
            System.lineSeparator()
        );
    }

    private static void line(
        StringBuilder builder,
        String value
    ) {

        builder.append(
            value
        );

        builder.append(
            System.lineSeparator()
        );
    }

    private record ProbeObservation(
        int sourcePosition,
        String asin,
        ProbeEvaluation evaluation,
        String failure
    ) {

        private ProbeObservation {

            if (sourcePosition <= 0) {

                throw new IllegalArgumentException(
                    "sourcePosition must be positive"
                );
            }

            boolean hasEvaluation =
                evaluation != null;

            boolean hasFailure =
                failure != null
                    && !failure.isBlank();

            if (hasEvaluation == hasFailure) {

                throw new IllegalArgumentException(
                    "ProbeObservation must contain exactly "
                        + "one of evaluation or failure"
                );
            }
        }

        static ProbeObservation evaluated(
            int sourcePosition,
            ParsedDeal parsedDeal,
            ProbeEvaluation evaluation
        ) {

            Objects.requireNonNull(
                parsedDeal,
                "parsedDeal must not be null"
            );

            Objects.requireNonNull(
                evaluation,
                "evaluation must not be null"
            );

            return new ProbeObservation(
                sourcePosition,
                parsedDeal.asin(),
                evaluation,
                null
            );
        }

        static ProbeObservation failed(
            int sourcePosition,
            ParsedDeal parsedDeal,
            String failure
        ) {

            Objects.requireNonNull(
                parsedDeal,
                "parsedDeal must not be null"
            );

            return new ProbeObservation(
                sourcePosition,
                parsedDeal.asin(),
                null,
                Objects.requireNonNull(
                    failure,
                    "failure must not be null"
                )
            );
        }

        boolean evaluated() {

            return evaluation != null;
        }
    }

    private record ProbeEvaluation(
        int sourcePosition,
        String asin,
        String title,
        String productUrl,
        BigDecimal currentPrice,
        BigDecimal rating,
        Long reviewCount,
        String sellerName,
        String deliveryProvider,
        boolean eligible,
        BigDecimal score,
        String rejectionReason,
        String scoreVersion,
        OffsetDateTime evaluatedAt
    ) {

        private ProbeEvaluation {

            if (sourcePosition <= 0) {

                throw new IllegalArgumentException(
                    "sourcePosition must be positive"
                );
            }

            Objects.requireNonNull(
                asin,
                "asin must not be null"
            );

            Objects.requireNonNull(
                evaluatedAt,
                "evaluatedAt must not be null"
            );

            if (eligible
                && score == null) {

                throw new IllegalArgumentException(
                    "Eligible probe evaluation requires score"
                );
            }
        }
    }
}
