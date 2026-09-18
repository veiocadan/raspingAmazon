package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.util.Locale;
import java.util.Objects;

/**
 * Parser responsável por interpretar as evidências de vendedor e entrega
 * presentes na página individual de um produto da Amazon.
 *
 * <p>Esta classe pertence à infraestrutura porque conhece a estrutura
 * específica do HTML da Amazon.</p>
 *
 * <p>O parser não decide se a oferta é elegível. Ele somente extrai
 * evidências e as normaliza para tipos estáveis utilizados pelo restante
 * da aplicação.</p>
 */
public final class AmazonProductPageParser {

    /**
     * Interpreta o HTML de uma página individual de produto.
     *
     * @param html conteúdo HTML da página da Amazon
     * @return evidências normalizadas da oferta principal
     */
    public ParsedProductOffer parse(String html) {
        Objects.requireNonNull(
                html,
                "HTML must not be null"
        );

        if (html.isBlank()) {
            throw new IllegalArgumentException(
                    "HTML must not be blank"
            );
        }

        /*
         * Extraímos seller e delivery independentemente.
         *
         * Isso é indispensável porque "Enviado pela Amazon"
         * não significa necessariamente "Vendido pela Amazon".
         */
        Evidence rawSellerEvidence =
                extractSellerEvidence(html);

        Evidence rawDeliveryEvidence =
                extractDeliveryEvidence(html);

        SellerEvidence sellerEvidence =
                new SellerEvidence(
                        rawSellerEvidence.value(),
                        normalizeSellerType(
                                rawSellerEvidence.value()
                        ),
                        rawSellerEvidence.source()
                );

        DeliveryEvidence deliveryEvidence =
                new DeliveryEvidence(
                        rawDeliveryEvidence.value(),
                        normalizeDeliveryType(
                                rawDeliveryEvidence.value()
                        ),
                        rawDeliveryEvidence.source()
                );

        return new ParsedProductOffer(
                sellerEvidence,
                deliveryEvidence
        );
    }

    /**
     * Extrai a evidência do vendedor da oferta principal.
     */
    private Evidence extractSellerEvidence(String html) {
        int featureStart =
                findFeatureStart(
                        html,
                        "merchantInfoFeature"
                );

        if (featureStart < 0) {
            return Evidence.empty();
        }

        String featureHtml =
                html.substring(featureStart);

        int sellerLabelIndex =
                featureHtml.indexOf(
                        "Vendido por"
                );

        int combinedLabelIndex =
                featureHtml.indexOf(
                        "Enviado / Vendido"
                );

        int labelIndex;

        if (sellerLabelIndex >= 0
                && combinedLabelIndex >= 0) {

            labelIndex =
                    Math.min(
                            sellerLabelIndex,
                            combinedLabelIndex
                    );

        } else if (sellerLabelIndex >= 0) {

            labelIndex = sellerLabelIndex;

        } else {

            labelIndex = combinedLabelIndex;
        }

        if (labelIndex < 0) {
            return Evidence.empty();
        }

        String afterLabel =
                featureHtml.substring(labelIndex);

        String value =
                extractOfferDisplayFeatureText(
                        afterLabel
                );

        if (value == null
                || value.isBlank()) {

            return Evidence.empty();
        }

        return new Evidence(
                value,
                "merchantInfoFeature"
        );
    }

    /**
     * Extrai a evidência responsável pela entrega.
     */
    private Evidence extractDeliveryEvidence(
            String html
    ) {
        int featureStart =
                findFeatureStart(
                        html,
                        "fulfillerInfoFeature"
                );

        if (featureStart >= 0) {

            String featureHtml =
                    html.substring(featureStart);

            int deliveryLabelIndex =
                    featureHtml.indexOf(
                            "Enviado por"
                    );

            if (deliveryLabelIndex >= 0) {

                String afterLabel =
                        featureHtml.substring(
                                deliveryLabelIndex
                        );

                String explicitDelivery =
                        extractOfferDisplayFeatureText(
                                afterLabel
                        );

                if (explicitDelivery != null
                        && !explicitDelivery.isBlank()) {

                    return new Evidence(
                            explicitDelivery,
                            "fulfillerInfoFeature"
                    );
                }
            }
        }

        /*
         * Fallback controlado para a estrutura combinada
         * "Enviado / Vendido".
         */
        return extractCombinedDeliveryEvidence(
                html
        );
    }

    /**
     * Extrai delivery da estrutura combinada.
     */
    private Evidence extractCombinedDeliveryEvidence(
            String html
    ) {
        int featureStart =
                findFeatureStart(
                        html,
                        "merchantInfoFeature"
                );

        if (featureStart < 0) {
            return Evidence.empty();
        }

        String featureHtml =
                html.substring(featureStart);

        int combinedLabelIndex =
                featureHtml.indexOf(
                        "Enviado / Vendido"
                );

        if (combinedLabelIndex < 0) {
            return Evidence.empty();
        }

        String afterLabel =
                featureHtml.substring(
                        combinedLabelIndex
                );

        String combinedValue =
                extractOfferDisplayFeatureText(
                        afterLabel
                );

        if (combinedValue == null
                || combinedValue.isBlank()) {

            return Evidence.empty();
        }

        /*
         * A fonte pode utilizar diferentes representações textuais
         * para a própria Amazon.
         *
         * Para delivery, normalizamos essas representações para
         * "Amazon", preservando depois a classificação tipada.
         */
        String normalized =
                combinedValue
                        .trim()
                        .toLowerCase(Locale.ROOT);

        String normalizedValue =
                switch (normalized) {

                    case "amazon",
                         "amazon.com.br",
                         "amazon global" ->
                            "Amazon";

                    default ->
                            combinedValue;
                };

        return new Evidence(
                normalizedValue,
                "merchantInfoFeature"
        );
    }

    /**
     * Localiza o início da feature procurada.
     *
     * <p>São suportadas a representação de view-source e a
     * representação HTML direta encontradas durante a investigação.</p>
     */
    private int findFeatureStart(
            String html,
            String featureName
    ) {
        String viewSourceMarker =
                "data-feature-name</span>=\"<a class=\"attribute-value\">"
                        + featureName;

        int viewSourceIndex =
                html.indexOf(
                        viewSourceMarker
                );

        if (viewSourceIndex >= 0) {
            return viewSourceIndex;
        }

        String rawHtmlMarker =
                "data-feature-name=\""
                        + featureName
                        + "\"";

        return html.indexOf(
                rawHtmlMarker
        );
    }

    /**
     * Extrai o texto associado a seller/delivery.
     */
    private String extractOfferDisplayFeatureText(
            String html
    ) {
        String viewSourceMarker =
                "offer-display-feature-text-message</a>\"&gt;</span><span>";

        int viewSourceMarkerIndex =
                html.indexOf(
                        viewSourceMarker
                );

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
                html.indexOf(
                        rawHtmlMarker
                );

        if (rawHtmlMarkerIndex >= 0) {

            int valueStart =
                    rawHtmlMarkerIndex
                            + rawHtmlMarker.length();

            int valueEnd =
                    html.indexOf(
                            "</span>",
                            valueStart
                    );

            if (valueEnd < 0) {
                return null;
            }

            String value =
                    html.substring(
                            valueStart,
                            valueEnd
                    ).trim();

            return value.isBlank()
                    ? null
                    : value;
        }

        return null;
    }

    /**
     * Extrai o valor presente na representação view-source.
     */
    private String extractViewSourceValue(
            String html,
            int valueStart
    ) {
        int valueEnd =
                findViewSourceValueEnd(
                        html,
                        valueStart
                );

        if (valueEnd < 0) {
            return null;
        }

        String value =
                html.substring(
                        valueStart,
                        valueEnd
                ).trim();

        return value.isBlank()
                ? null
                : value;
    }

    /**
     * Localiza o marcador mais próximo que encerra o valor.
     */
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

        for (String endMarker :
                possibleEndMarkers) {

            int candidate =
                    html.indexOf(
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

    /**
     * Converte a evidência textual de seller para o tipo de domínio.
     */
    private SellerType normalizeSellerType(
            String rawSellerValue
    ) {
        if (rawSellerValue == null
                || rawSellerValue.isBlank()) {

            return SellerType.UNKNOWN;
        }

        String normalized =
                rawSellerValue
                        .trim()
                        .toLowerCase(Locale.ROOT);

        return switch (normalized) {

            case "amazon",
                 "amazon.com.br",
                 "amazon global" ->
                    SellerType.AMAZON;

            default ->
                    SellerType.THIRD_PARTY;
        };
    }

    /**
     * Converte a evidência textual de delivery para o tipo de domínio.
     */
    private DeliveryType normalizeDeliveryType(
            String rawDeliveryValue
    ) {
        if (rawDeliveryValue == null
                || rawDeliveryValue.isBlank()) {

            return DeliveryType.UNKNOWN;
        }

        String normalized =
                rawDeliveryValue
                        .trim()
                        .toLowerCase(Locale.ROOT);

        return switch (normalized) {

            case "amazon" ->
                    DeliveryType.AMAZON;

            default ->
                    DeliveryType.THIRD_PARTY;
        };
    }

    /**
     * Resultado interno do parser da página individual.
     *
     * <p>Seller e delivery são transportados como conceitos tipados,
     * e não mais como uma sequência de Strings posicionais.</p>
     */
    public record ParsedProductOffer(
            SellerEvidence sellerEvidence,
            DeliveryEvidence deliveryEvidence
    ) {

        public ParsedProductOffer {
            Objects.requireNonNull(
                    sellerEvidence,
                    "Seller evidence must not be null"
            );

            Objects.requireNonNull(
                    deliveryEvidence,
                    "Delivery evidence must not be null"
            );
        }
    }

    /**
     * Evidência textual intermediária utilizada apenas durante
     * a interpretação do HTML.
     */
    private record Evidence(
            String value,
            String source
    ) {

        /**
         * Representa ausência de evidência.
         */
        private static Evidence empty() {
            return new Evidence(
                    null,
                    null
            );
        }
    }
}