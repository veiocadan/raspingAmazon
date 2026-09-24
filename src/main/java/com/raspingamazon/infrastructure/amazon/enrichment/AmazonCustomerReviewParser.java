package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.RatingEvidence;
import com.raspingamazon.application.enrichment.contract.ReviewCountEvidence;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser estrutural de rating e reviewCount da página individual Amazon.
 *
 * <p>O parser é deliberadamente restrito aos containers de customer
 * reviews associados ao ASIN esperado. Ele não busca estrelas ou
 * números globalmente pela página, pois a página pode conter reviews
 * de recomendações, carrosséis e outros produtos.</p>
 */
public final class AmazonCustomerReviewParser {

    private static final Pattern ASIN_PATTERN =
        Pattern.compile(
            "[A-Z0-9]{10}"
        );

    private static final Pattern RATING_PATTERN =
        Pattern.compile(
            "([0-5](?:[\\.,]\\d+)?)"
        );

    private static final Pattern REVIEW_COUNT_PATTERN =
        Pattern.compile(
            "([0-9][0-9\\s\\.,]*)"
        );

    private static final BigDecimal MIN_RATING =
        BigDecimal.ZERO;

    private static final BigDecimal MAX_RATING =
        new BigDecimal(
            "5"
        );

    private static final String PRIMARY_ROOT_ID =
        "averageCustomerReviews_feature_div";

    private static final String FALLBACK_ROOT_ID =
        "averageCustomerReviews";

    private static final String RATING_ELEMENT_ID =
        "acrPopover";

    private static final String REVIEW_COUNT_ELEMENT_ID =
        "acrCustomerReviewText";

    /**
     * Interpreta somente estruturas vinculadas ao ASIN esperado.
     */
    public ParsedCustomerReviews parse(
        String html,
        String expectedAsin
    ) {

        Objects.requireNonNull(
            html,
            "HTML must not be null"
        );

        Objects.requireNonNull(
            expectedAsin,
            "Expected ASIN must not be null"
        );

        if (html.isBlank()) {
            throw new IllegalArgumentException(
                "HTML must not be blank"
            );
        }

        String normalizedAsin =
            normalizeAsin(
                expectedAsin
            );

        Document document =
            Jsoup.parse(
                html
            );

        List<CustomerReviewRoot> roots =
            findCandidateRoots(
                document,
                normalizedAsin
            );

        RatingEvidence ratingEvidence =
            extractRating(
                roots
            );

        ReviewCountEvidence reviewCountEvidence =
            extractReviewCount(
                roots
            );

        return new ParsedCustomerReviews(
            ratingEvidence,
            reviewCountEvidence
        );
    }

    private String normalizeAsin(
        String expectedAsin
    ) {

        String normalized =
            expectedAsin
                .trim()
                .toUpperCase(
                    Locale.ROOT
                );

        if (!ASIN_PATTERN
            .matcher(
                normalized
            )
            .matches()) {

            throw new IllegalArgumentException(
                "Expected ASIN must match [A-Z0-9]{10}"
            );
        }

        return normalized;
    }

    /**
     * A ordem dos roots é significativa:
     *
     * <ol>
     *     <li>feature_div explicitamente associado ao ASIN;</li>
     *     <li>averageCustomerReviews associado ao ASIN.</li>
     * </ol>
     */
    private List<CustomerReviewRoot> findCandidateRoots(
        Document document,
        String expectedAsin
    ) {

        List<CustomerReviewRoot> roots =
            new ArrayList<>();

        for (Element element :
            document.select(
                "[id="
                    + PRIMARY_ROOT_ID
                    + "]"
            )) {

            if (matchesExpectedAsin(
                element,
                expectedAsin
            )) {

                roots.add(
                    new CustomerReviewRoot(
                        element,
                        PRIMARY_ROOT_ID
                    )
                );
            }
        }

        if (!roots.isEmpty()) {

            return List.copyOf(
                roots
            );
        }

        for (Element element :
            document.select(
                "[id="
                    + FALLBACK_ROOT_ID
                    + "][data-asin]"
            )) {

            if (expectedAsin.equals(
                normalizeObservedAsin(
                    element.attr(
                        "data-asin"
                    )
                )
            )) {

                roots.add(
                    new CustomerReviewRoot(
                        element,
                        FALLBACK_ROOT_ID
                    )
                );
            }
        }

        return List.copyOf(
            roots
        );
    }

    /**
     * Quando o feature_div declara data-csa-c-asin, esse valor precisa
     * obrigatoriamente coincidir com o produto esperado.
     *
     * <p>Se o atributo não existir, aceitamos o root somente quando um
     * descendant averageCustomerReviews declara o ASIN esperado.</p>
     */
    private boolean matchesExpectedAsin(
        Element root,
        String expectedAsin
    ) {

        if (root.hasAttr(
            "data-csa-c-asin"
        )) {

            String rootAsin =
                normalizeObservedAsin(
                    root.attr(
                        "data-csa-c-asin"
                    )
                );

            if (rootAsin != null) {

                return expectedAsin.equals(
                    rootAsin
                );
            }
        }

        for (Element descendant :
            root.select(
                "[id="
                    + FALLBACK_ROOT_ID
                    + "][data-asin]"
            )) {

            if (expectedAsin.equals(
                normalizeObservedAsin(
                    descendant.attr(
                        "data-asin"
                    )
                )
            )) {

                return true;
            }
        }

        return false;
    }

    private String normalizeObservedAsin(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return null;
        }

        return value
            .trim()
            .toUpperCase(
                Locale.ROOT
            );
    }

    private RatingEvidence extractRating(
        List<CustomerReviewRoot> roots
    ) {

        RatingEvidence firstUnparsed =
            null;

        for (CustomerReviewRoot root :
            roots) {

            Element element =
                root.element()
                    .selectFirst(
                        "[id="
                            + RATING_ELEMENT_ID
                            + "][title]"
                    );

            if (element == null) {
                continue;
            }

            String rawValue =
                normalizeRawValue(
                    element.attr(
                        "title"
                    )
                );

            if (rawValue == null) {
                continue;
            }

            String source =
                root.sourcePrefix()
                    + "/"
                    + RATING_ELEMENT_ID
                    + "@title";

            Double rating =
                parseRatingValue(
                    rawValue
                );

            RatingEvidence evidence =
                new RatingEvidence(
                    rawValue,
                    rating,
                    source
                );

            if (evidence.available()) {
                return evidence;
            }

            if (firstUnparsed == null) {
                firstUnparsed =
                    evidence;
            }
        }

        return firstUnparsed == null
            ? RatingEvidence.unavailable()
            : firstUnparsed;
    }

    private ReviewCountEvidence extractReviewCount(
        List<CustomerReviewRoot> roots
    ) {

        ReviewCountEvidence firstUnparsed =
            null;

        for (CustomerReviewRoot root :
            roots) {

            Element element =
                root.element()
                    .selectFirst(
                        "[id="
                            + REVIEW_COUNT_ELEMENT_ID
                            + "][aria-label]"
                    );

            if (element == null) {
                continue;
            }

            String rawValue =
                normalizeRawValue(
                    element.attr(
                        "aria-label"
                    )
                );

            if (rawValue == null) {
                continue;
            }

            String source =
                root.sourcePrefix()
                    + "/"
                    + REVIEW_COUNT_ELEMENT_ID
                    + "@aria-label";

            Long reviewCount =
                parseReviewCountValue(
                    rawValue
                );

            ReviewCountEvidence evidence =
                new ReviewCountEvidence(
                    rawValue,
                    reviewCount,
                    source
                );

            if (evidence.available()) {
                return evidence;
            }

            if (firstUnparsed == null) {
                firstUnparsed =
                    evidence;
            }
        }

        return firstUnparsed == null
            ? ReviewCountEvidence.unavailable()
            : firstUnparsed;
    }

    private Double parseRatingValue(
        String rawValue
    ) {

        Matcher matcher =
            RATING_PATTERN.matcher(
                rawValue
            );

        if (!matcher.find()) {
            return null;
        }

        String normalized =
            matcher.group(
                    1
                )
                .replace(
                    ',',
                    '.'
                );

        BigDecimal value;

        try {

            value =
                new BigDecimal(
                    normalized
                );

        } catch (NumberFormatException exception) {

            return null;
        }

        if (value.compareTo(
            MIN_RATING
        ) < 0
            || value.compareTo(
                MAX_RATING
            ) > 0) {

            return null;
        }

        return value.doubleValue();
    }

    private Long parseReviewCountValue(
        String rawValue
    ) {

        Matcher matcher =
            REVIEW_COUNT_PATTERN.matcher(
                rawValue
            );

        if (!matcher.find()) {
            return null;
        }

        String digits =
            matcher.group(
                    1
                )
                .replaceAll(
                    "[^0-9]",
                    ""
                );

        if (digits.isBlank()) {
            return null;
        }

        try {

            return Long.parseLong(
                digits
            );

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    private String normalizeRawValue(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }

    /**
     * Resultado tipado do parsing de customer reviews.
     */
    public record ParsedCustomerReviews(
        RatingEvidence ratingEvidence,
        ReviewCountEvidence reviewCountEvidence
    ) {

        public ParsedCustomerReviews {

            Objects.requireNonNull(
                ratingEvidence,
                "Rating evidence must not be null"
            );

            Objects.requireNonNull(
                reviewCountEvidence,
                "Review-count evidence must not be null"
            );
        }
    }

    private record CustomerReviewRoot(
        Element element,
        String sourcePrefix
    ) {

        private CustomerReviewRoot {

            Objects.requireNonNull(
                element,
                "Customer-review root element must not be null"
            );

            Objects.requireNonNull(
                sourcePrefix,
                "Customer-review source prefix must not be null"
            );
        }
    }
}
