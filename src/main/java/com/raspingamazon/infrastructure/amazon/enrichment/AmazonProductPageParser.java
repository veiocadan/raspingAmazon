package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.util.Locale;
import java.util.Objects;

public final class AmazonProductPageParser {

    public ParsedProductOffer parse(String html) {
        Objects.requireNonNull(html, "HTML must not be null");

        if (html.isBlank()) {
            throw new IllegalArgumentException("HTML must not be blank");
        }

        String rawSellerValue = extractSellerValue(html);
        String rawDeliveryValue = extractDeliveryValue(html);

        SellerType sellerType = normalizeSellerType(rawSellerValue);
        DeliveryType deliveryType = normalizeDeliveryType(rawDeliveryValue);

        return new ParsedProductOffer(
                rawSellerValue,
                sellerType,
                rawDeliveryValue,
                deliveryType
        );
    }

    private String extractSellerValue(String html) {
        int featureStart = findFeatureStart(
                html,
                "merchantInfoFeature"
        );

        if (featureStart < 0) {
            return null;
        }

        String featureHtml = html.substring(featureStart);

        int sellerLabelIndex = featureHtml.indexOf(
                "Vendido por"
        );

        int combinedLabelIndex = featureHtml.indexOf(
                "Enviado / Vendido"
        );

        int labelIndex;

        if (sellerLabelIndex >= 0 && combinedLabelIndex >= 0) {
            labelIndex = Math.min(
                    sellerLabelIndex,
                    combinedLabelIndex
            );
        } else if (sellerLabelIndex >= 0) {
            labelIndex = sellerLabelIndex;
        } else {
            labelIndex = combinedLabelIndex;
        }

        if (labelIndex < 0) {
            return null;
        }

        String afterLabel = featureHtml.substring(labelIndex);

        return extractOfferDisplayFeatureText(afterLabel);
    }

    private String extractDeliveryValue(String html) {
        int featureStart = findFeatureStart(
                html,
                "fulfillerInfoFeature"
        );

        if (featureStart >= 0) {
            String featureHtml = html.substring(featureStart);

            int deliveryLabelIndex = featureHtml.indexOf(
                    "Enviado por"
            );

            if (deliveryLabelIndex >= 0) {
                String afterLabel = featureHtml.substring(
                        deliveryLabelIndex
                );

                String explicitDelivery =
                        extractOfferDisplayFeatureText(afterLabel);

                if (explicitDelivery != null) {
                    return explicitDelivery;
                }
            }
        }

        return extractCombinedDeliveryValue(html);
    }

    private String extractCombinedDeliveryValue(String html) {
        int featureStart = findFeatureStart(
                html,
                "merchantInfoFeature"
        );

        if (featureStart < 0) {
            return null;
        }

        String featureHtml = html.substring(featureStart);

        int combinedLabelIndex = featureHtml.indexOf(
                "Enviado / Vendido"
        );

        if (combinedLabelIndex < 0) {
            return null;
        }

        String afterLabel = featureHtml.substring(
                combinedLabelIndex
        );

        String combinedValue =
                extractOfferDisplayFeatureText(afterLabel);

        if (combinedValue == null
                || combinedValue.isBlank()) {
            return null;
        }

        String normalized = combinedValue
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "amazon",
                 "amazon.com.br",
                 "amazon global" -> "Amazon";

            default -> combinedValue;
        };
    }

    private int findFeatureStart(
            String html,
            String featureName
    ) {
        String viewSourceMarker =
                "data-feature-name</span>=\"<a class=\"attribute-value\">"
                        + featureName;

        int viewSourceIndex = html.indexOf(
                viewSourceMarker
        );

        if (viewSourceIndex >= 0) {
            return viewSourceIndex;
        }

        String rawHtmlMarker =
                "data-feature-name=\"" + featureName + "\"";

        return html.indexOf(rawHtmlMarker);
    }

    private String extractOfferDisplayFeatureText(
            String html
    ) {
        String viewSourceMarker =
                "offer-display-feature-text-message</a>\"&gt;</span><span>";

        int viewSourceMarkerIndex =
                html.indexOf(viewSourceMarker);

        if (viewSourceMarkerIndex >= 0) {
            int valueStart =
                    viewSourceMarkerIndex
                            + viewSourceMarker.length();

            return extractViewSourceValue(
                    html,
                    valueStart
            );
        }

        String rawHtmlMarker =
                "offer-display-feature-text-message\">";

        int rawHtmlMarkerIndex =
                html.indexOf(rawHtmlMarker);

        if (rawHtmlMarkerIndex >= 0) {
            int valueStart =
                    rawHtmlMarkerIndex
                            + rawHtmlMarker.length();

            int valueEnd = html.indexOf(
                    "</span>",
                    valueStart
            );

            if (valueEnd < 0) {
                return null;
            }

            String value = html
                    .substring(valueStart, valueEnd)
                    .trim();

            return value.isBlank()
                    ? null
                    : value;
        }

        return null;
    }

    private String extractViewSourceValue(
            String html,
            int valueStart
    ) {
        int valueEnd = findViewSourceValueEnd(
                html,
                valueStart
        );

        if (valueEnd < 0) {
            return null;
        }

        String value = html
                .substring(valueStart, valueEnd)
                .trim();

        return value.isBlank()
                ? null
                : value;
    }

    private int findViewSourceValueEnd(
            String html,
            int valueStart
    ) {
        String[] possibleEndMarkers = {
                "</span><span>",
                "&lt;/span&gt;&lt;span&gt;",
                "&lt;/<span class=\"end-tag\">span</span>&gt;"
        };

        int nearestEnd = -1;

        for (String endMarker : possibleEndMarkers) {
            int candidate = html.indexOf(
                    endMarker,
                    valueStart
            );

            if (candidate >= 0
                    && (nearestEnd < 0
                    || candidate < nearestEnd)) {

                nearestEnd = candidate;
            }
        }

        return nearestEnd;
    }

    private SellerType normalizeSellerType(
            String rawSellerValue
    ) {
        if (rawSellerValue == null
                || rawSellerValue.isBlank()) {

            return SellerType.UNKNOWN;
        }

        String normalized = rawSellerValue
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "amazon",
                 "amazon.com.br",
                 "amazon global" -> SellerType.AMAZON;

            default -> SellerType.THIRD_PARTY;
        };
    }

    private DeliveryType normalizeDeliveryType(
            String rawDeliveryValue
    ) {
        if (rawDeliveryValue == null
                || rawDeliveryValue.isBlank()) {

            return DeliveryType.UNKNOWN;
        }

        String normalized = rawDeliveryValue
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "amazon" -> DeliveryType.AMAZON;

            default -> DeliveryType.THIRD_PARTY;
        };
    }

    public record ParsedProductOffer(
            String rawSellerValue,
            SellerType sellerType,
            String rawDeliveryValue,
            DeliveryType deliveryType
    ) {
    }
}