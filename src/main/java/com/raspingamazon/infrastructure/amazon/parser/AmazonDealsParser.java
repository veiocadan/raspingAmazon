package com.raspingamazon.infrastructure.amazon.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Implementação do parser das ofertas da Amazon Brasil.
 *
 * <p>O parser descreve fatos observados na fonte.
 * Ele não aplica elegibilidade, filtros, score ou regras de publicação.</p>
 */
public class AmazonDealsParser implements DealsParser {

    private static final String PRODUCT_SEARCH_RESPONSE_FIELD =
            "\"productSearchResponse\"";

    private static final String ASIN_REGEX =
            "[A-Z0-9]{10}";

    private static final BigDecimal MIN_PERCENTAGE =
            BigDecimal.ZERO;

    private static final BigDecimal MAX_PERCENTAGE =
            new BigDecimal("100");

    private static final BigDecimal MIN_RATING =
            BigDecimal.ZERO;

    private static final BigDecimal MAX_RATING =
            new BigDecimal("5");

    private final ObjectMapper objectMapper;

    public AmazonDealsParser() {
        this(new ObjectMapper());
    }

    public AmazonDealsParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "ObjectMapper must not be null"
        );
    }

    @Override
    public List<ParsedDeal> parse(CollectionResult collectionResult) {
        Objects.requireNonNull(
                collectionResult,
                "Collection result must not be null"
        );

        JsonNode productSearchResponse =
                extractProductSearchResponse(
                        collectionResult.content()
                );

        JsonNode products =
                productSearchResponse.get("products");

        if (products == null || !products.isArray()) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse does not contain a products array"
            );
        }

        List<ParsedDeal> parsedDeals = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (JsonNode product : products) {
            ParsedDeal parsedDeal =
                    parseProduct(
                            product,
                            collectionResult.collectedAt(),
                            collectionResult.source()
                    );

            if (parsedDeal == null) {
                continue;
            }

            String deduplicationKey =
                    buildDeduplicationKey(parsedDeal);

            if (seenKeys.add(deduplicationKey)) {
                parsedDeals.add(parsedDeal);
            }
        }

        return List.copyOf(parsedDeals);
    }

    private JsonNode extractProductSearchResponse(String content) {
        int fieldPosition =
                content.indexOf(
                        PRODUCT_SEARCH_RESPONSE_FIELD
                );

        if (fieldPosition < 0) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse was not found"
            );
        }

        int colonPosition =
                content.indexOf(
                        ':',
                        fieldPosition + PRODUCT_SEARCH_RESPONSE_FIELD.length()
                );

        if (colonPosition < 0) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse does not contain a value"
            );
        }

        int objectStart =
                findNextNonWhitespaceCharacter(
                        content,
                        colonPosition + 1
                );

        if (objectStart < 0
                || content.charAt(objectStart) != '{') {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse is not a JSON object"
            );
        }

        int objectEnd =
                findJsonObjectEnd(
                        content,
                        objectStart
                );

        if (objectEnd < 0) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse JSON object is incomplete"
            );
        }

        String json =
                content.substring(
                        objectStart,
                        objectEnd + 1
                );

        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new AmazonDealsParsingException(
                    "Amazon productSearchResponse could not be parsed as JSON",
                    exception
            );
        }
    }

    private int findNextNonWhitespaceCharacter(
            String content,
            int start
    ) {
        for (int index = start;
             index < content.length();
             index++) {
            if (!Character.isWhitespace(content.charAt(index))) {
                return index;
            }
        }

        return -1;
    }

    private int findJsonObjectEnd(
            String content,
            int objectStart
    ) {
        int depth = 0;
        boolean insideString = false;
        boolean escaped = false;

        for (int index = objectStart;
             index < content.length();
             index++) {

            char current = content.charAt(index);

            if (insideString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }

                if (current == '\\') {
                    escaped = true;
                    continue;
                }

                if (current == '"') {
                    insideString = false;
                }

                continue;
            }

            if (current == '"') {
                insideString = true;
                continue;
            }

            if (current == '{') {
                depth++;
                continue;
            }

            if (current == '}') {
                depth--;

                if (depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }

    private ParsedDeal parseProduct(
            JsonNode product,
            OffsetDateTime collectedAt,
            String source
    ) {
        if (product == null || !product.isObject()) {
            return null;
        }

        String asin =
                normalizeAsin(
                        textValue(product, "asin")
                );

        if (asin == null) {
            return null;
        }

        String title =
                normalizeRequiredText(
                        textValue(product, "title")
                );

        if (title == null) {
            return null;
        }

        String productUrl =
                normalizeProductUrl(
                        textValue(product, "link"),
                        source
                );

        if (productUrl == null) {
            return null;
        }

        BigDecimal currentPrice =
                extractPrice(
                        product,
                        "priceToPay"
                );

        if (currentPrice == null) {
            return null;
        }

        BigDecimal basisPrice =
                extractPrice(
                        product,
                        "basisPrice"
                );

        /*
         * basisPrice e previousPrice possuem semânticas distintas.
         * Sem fonte histórica explícita, previousPrice permanece null.
         */
        BigDecimal previousPrice = null;

        BigDecimal soldPercentage =
                extractSoldPercentage(product);

        Double rating =
                extractRating(product);

        Long reviewCount =
                extractReviewCount(product);

        String imageUrl =
                extractImageUrl(product);

        return new ParsedDeal(
                asin,
                productUrl,
                title,
                imageUrl,
                currentPrice,
                basisPrice,
                previousPrice,
                soldPercentage,
                rating,
                reviewCount,
                collectedAt,
                source
        );
    }

    private String normalizeAsin(String value) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim().toUpperCase();

        if (!normalized.matches(ASIN_REGEX)) {
            return null;
        }

        return normalized;
    }

    private String normalizeRequiredText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private String normalizeProductUrl(
            String link,
            String source
    ) {
        if (link == null || link.isBlank()) {
            return null;
        }

        String normalizedLink = link.trim();

        try {
            URI linkUri = URI.create(normalizedLink);

            if (linkUri.isAbsolute()) {
                return linkUri.toString();
            }

            URI sourceUri = URI.create(source);

            return sourceUri
                    .resolve(normalizedLink)
                    .toString();

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private BigDecimal extractPrice(
            JsonNode product,
            String priceField
    ) {
        JsonNode price = product.get("price");

        if (price == null || !price.isObject()) {
            return null;
        }

        JsonNode priceNode = price.get(priceField);

        if (priceNode == null || !priceNode.isObject()) {
            return null;
        }

        return parseDecimal(
                textValue(
                        priceNode,
                        "price"
                )
        );
    }

    /**
     * Extrai dealDetails.percentClaimed.
     *
     * <p>O valor somente atravessa o contrato quando pertence
     * ao intervalo percentual válido de 0 a 100.</p>
     */
    private BigDecimal extractSoldPercentage(JsonNode product) {
        JsonNode dealDetails =
                product.get("dealDetails");

        if (dealDetails == null || !dealDetails.isObject()) {
            return null;
        }

        JsonNode percentClaimed =
                dealDetails.get("percentClaimed");

        if (percentClaimed == null || percentClaimed.isNull()) {
            return null;
        }

        BigDecimal value =
                percentClaimed.isNumber()
                        ? percentClaimed.decimalValue()
                        : parseDecimal(percentClaimed.asText());

        return normalizePercentage(value);
    }

    /**
     * Extrai product.customerReviews.rating.shortDisplayString.
     *
     * <p>O valor é localizado, por exemplo "4,7", e é
     * normalizado para Double.</p>
     */
    private Double extractRating(JsonNode product) {
        JsonNode customerReviews =
                product.get("customerReviews");

        if (customerReviews == null
                || !customerReviews.isObject()) {
            return null;
        }

        JsonNode rating =
                customerReviews.get("rating");

        if (rating == null || !rating.isObject()) {
            return null;
        }

        BigDecimal value =
                parseDecimal(
                        textValue(
                                rating,
                                "shortDisplayString"
                        )
                );

        if (value == null) {
            return null;
        }

        if (value.compareTo(MIN_RATING) < 0
                || value.compareTo(MAX_RATING) > 0) {
            return null;
        }

        return value.doubleValue();
    }

    /**
     * Extrai product.customerReviews.count.value.
     *
     * <p>Usamos o campo numérico value, e não displayString,
     * para evitar dependência de separadores localizados.</p>
     */
    private Long extractReviewCount(JsonNode product) {
        JsonNode customerReviews =
                product.get("customerReviews");

        if (customerReviews == null
                || !customerReviews.isObject()) {
            return null;
        }

        JsonNode count =
                customerReviews.get("count");

        if (count == null || !count.isObject()) {
            return null;
        }

        JsonNode value =
                count.get("value");

        if (value == null
                || value.isNull()
                || !value.isIntegralNumber()) {
            return null;
        }

        long reviewCount = value.longValue();

        return reviewCount < 0
                ? null
                : reviewCount;
    }

    private BigDecimal normalizePercentage(BigDecimal value) {
        if (value == null) {
            return null;
        }

        if (value.compareTo(MIN_PERCENTAGE) < 0
                || value.compareTo(MAX_PERCENTAGE) > 0) {
            return null;
        }

        return value;
    }

    private String extractImageUrl(JsonNode product) {
        JsonNode image = product.get("image");

        if (image == null || !image.isObject()) {
            return null;
        }

        JsonNode hiRes = image.get("hiRes");

        if (hiRes != null && hiRes.isObject()) {
            String url =
                    composeImageUrl(
                            textValue(hiRes, "baseUrl"),
                            textValue(hiRes, "extension")
                    );

            if (url != null) {
                return url;
            }
        }

        JsonNode lowRes = image.get("lowRes");

        if (lowRes != null && lowRes.isObject()) {
            return composeImageUrl(
                    textValue(lowRes, "baseUrl"),
                    textValue(lowRes, "extension")
            );
        }

        return null;
    }

    private String composeImageUrl(
            String baseUrl,
            String extension
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }

        if (extension == null || extension.isBlank()) {
            return baseUrl.trim();
        }

        String normalizedExtension =
                extension.trim();

        if (normalizedExtension.startsWith(".")) {
            return baseUrl.trim()
                    + normalizedExtension;
        }

        return baseUrl.trim()
                + "."
                + normalizedExtension;
    }

    private String textValue(
            JsonNode node,
            String field
    ) {
        if (node == null || !node.isObject()) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized =
                value.trim()
                        .replace(',', '.');

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String buildDeduplicationKey(
            ParsedDeal parsedDeal
    ) {
        return String.join(
                "|",
                parsedDeal.asin(),
                parsedDeal.productUrl(),
                parsedDeal.currentPrice().toPlainString(),
                parsedDeal.basisPrice() == null
                        ? ""
                        : parsedDeal.basisPrice().toPlainString()
        );
    }
}
