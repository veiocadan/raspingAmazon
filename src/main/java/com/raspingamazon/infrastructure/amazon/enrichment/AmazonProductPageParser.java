package com.raspingamazon.infrastructure.amazon.enrichment;

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
 * evidências e as normaliza para os tipos de domínio:</p>
 *
 * <ul>
 *     <li>{@link SellerType}</li>
 *     <li>{@link DeliveryType}</li>
 * </ul>
 *
 * <p>A decisão de elegibilidade pertence à FASE 8.</p>
 *
 * <p>As evidências brutas e a estrutura que forneceu cada evidência
 * são preservadas para permitir rastreabilidade e auditoria.</p>
 */
public final class AmazonProductPageParser {

    /**
     * Interpreta o HTML de uma página individual de produto.
     *
     * @param html conteúdo HTML da página da Amazon
     * @return evidências normalizadas da oferta principal
     * @throws NullPointerException quando o HTML é nulo
     * @throws IllegalArgumentException quando o HTML está em branco
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
         * Seller e delivery são extraídos de forma independente.
         *
         * A independência é importante porque uma oferta pode ser:
         *
         * - vendida pela Amazon e entregue pela Amazon;
         * - vendida por terceiro e entregue pela Amazon;
         * - vendida por terceiro e entregue por terceiro;
         * - ou possuir evidência insuficiente.
         */
        Evidence sellerEvidence =
                extractSellerEvidence(html);

        Evidence deliveryEvidence =
                extractDeliveryEvidence(html);

        /*
         * A normalização converte o texto específico da Amazon
         * para os tipos definidos pelo domínio.
         *
         * O parser não decide elegibilidade.
         */
        SellerType sellerType =
                normalizeSellerType(
                        sellerEvidence.value()
                );

        DeliveryType deliveryType =
                normalizeDeliveryType(
                        deliveryEvidence.value()
                );

        return new ParsedProductOffer(
                sellerEvidence.value(),
                sellerType,
                sellerEvidence.source(),
                deliveryEvidence.value(),
                deliveryType,
                deliveryEvidence.source()
        );
    }

    /**
     * Extrai a evidência do vendedor da oferta principal.
     *
     * <p>A estrutura principal utilizada é {@code merchantInfoFeature}.
     * Dentro dela, o parser procura primeiro a indicação explícita
     * "Vendido por" ou a estrutura combinada "Enviado / Vendido".</p>
     *
     * @param html HTML da página do produto
     * @return evidência do vendedor e sua origem
     */
    private Evidence extractSellerEvidence(String html) {
        int featureStart = findFeatureStart(
                html,
                "merchantInfoFeature"
        );

        if (featureStart < 0) {
            return new Evidence(
                    null,
                    null
            );
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

            /*
             * Quando as duas estruturas existem, utilizamos
             * a primeira ocorrência encontrada dentro da feature.
             */
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
            return new Evidence(
                    null,
                    null
            );
        }

        String afterLabel =
                featureHtml.substring(labelIndex);

        String value =
                extractOfferDisplayFeatureText(
                        afterLabel
                );

        if (value == null
                || value.isBlank()) {

            return new Evidence(
                    null,
                    null
            );
        }

        return new Evidence(
                value,
                "merchantInfoFeature"
        );
    }

    /**
     * Extrai a evidência responsável pela entrega.
     *
     * <p>A primeira fonte é {@code fulfillerInfoFeature},
     * procurando a indicação explícita "Enviado por".</p>
     *
     * <p>Quando essa evidência não está disponível, o parser utiliza
     * a estrutura combinada {@code merchantInfoFeature} com
     * "Enviado / Vendido".</p>
     *
     * <p>O fallback preserva a estrutura que efetivamente forneceu
     * a evidência.</p>
     *
     * @param html HTML da página do produto
     * @return evidência da entrega e sua origem
     */
    private Evidence extractDeliveryEvidence(String html) {
        int featureStart = findFeatureStart(
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
         * Caso a estrutura específica de entrega não tenha
         * produzido uma evidência confiável, tenta-se a estrutura
         * combinada da oferta principal.
         */
        return extractCombinedDeliveryEvidence(html);
    }

    /**
     * Extrai a evidência de entrega a partir da estrutura combinada
     * "Enviado / Vendido".
     *
     * <p>Essa estrutura pode representar simultaneamente vendedor
     * e responsável pela entrega.</p>
     *
     * @param html HTML da página do produto
     * @return evidência da entrega ou evidência vazia
     */
    private Evidence extractCombinedDeliveryEvidence(
            String html
    ) {
        int featureStart = findFeatureStart(
                html,
                "merchantInfoFeature"
        );

        if (featureStart < 0) {
            return new Evidence(
                    null,
                    null
            );
        }

        String featureHtml =
                html.substring(featureStart);

        int combinedLabelIndex =
                featureHtml.indexOf(
                        "Enviado / Vendido"
                );

        if (combinedLabelIndex < 0) {
            return new Evidence(
                    null,
                    null
            );
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

            return new Evidence(
                    null,
                    null
            );
        }

        /*
         * A estrutura combinada pode apresentar diferentes
         * representações da própria Amazon.
         *
         * O valor bruto do seller continua preservado.
         * Para delivery, entretanto, a classificação de domínio
         * trabalha com "Amazon" como valor normalizado.
         */
        String normalized =
                combinedValue
                        .trim()
                        .toLowerCase(Locale.ROOT);

        String normalizedValue =
                switch (normalized) {

                    case "amazon",
                         "amazon.com.br",
                         "amazon global" -> "Amazon";

                    default -> combinedValue;
                };

        return new Evidence(
                normalizedValue,
                "merchantInfoFeature"
        );
    }

    /**
     * Localiza o início de uma feature específica da página.
     *
     * <p>A Amazon pode entregar o conteúdo em representações
     * diferentes. Por isso, são suportadas tanto a representação
     * observada em view-source quanto a marcação HTML direta.</p>
     *
     * @param html HTML da página
     * @param featureName nome da feature procurada
     * @return posição inicial da feature ou {@code -1}
     */
    private int findFeatureStart(
            String html,
            String featureName
    ) {
        /*
         * Representação observada no conteúdo de view-source.
         */
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

        /*
         * Representação HTML direta.
         */
        String rawHtmlMarker =
                "data-feature-name=\""
                        + featureName
                        + "\"";

        return html.indexOf(
                rawHtmlMarker
        );
    }

    /**
     * Extrai o texto associado à feature de vendedor ou entrega.
     *
     * <p>São suportadas as representações observadas nas fixtures:
     * conteúdo de view-source e HTML direto.</p>
     *
     * @param html fragmento da feature
     * @return texto encontrado ou {@code null}
     */
    private String extractOfferDisplayFeatureText(
            String html
    ) {
        /*
         * Estrutura observada em view-source.
         */
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

        /*
         * Estrutura HTML direta.
         */
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
     * Extrai o valor de uma representação de view-source.
     *
     * @param html conteúdo HTML
     * @param valueStart posição inicial do valor
     * @return valor extraído ou {@code null}
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
     * Localiza o fim do valor em diferentes representações
     * do conteúdo de view-source.
     *
     * @param html conteúdo HTML
     * @param valueStart início do valor
     * @return posição final ou {@code -1}
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
     * Normaliza a classificação do vendedor.
     *
     * <p>A classificação pertence ao domínio e não depende
     * de textos livres.</p>
     *
     * <p>As representações conhecidas da Amazon são normalizadas
     * para {@link SellerType#AMAZON}.</p>
     *
     * <p>Ausência de evidência resulta em
     * {@link SellerType#UNKNOWN}.</p>
     *
     * @param rawSellerValue valor observado na página
     * @return classificação normalizada
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
     * Normaliza a classificação da entrega.
     *
     * <p>A ausência de evidência não pode ser interpretada
     * como entrega pela Amazon.</p>
     *
     * @param rawDeliveryValue valor observado na página
     * @return classificação normalizada
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
     * Resultado da interpretação da oferta principal.
     *
     * <p>Além das classificações normalizadas, o resultado mantém
     * o valor bruto e a estrutura que forneceu cada evidência.</p>
     *
     * <p>Este objeto ainda não contém uma decisão de elegibilidade.
     * Essa responsabilidade pertence à FASE 8.</p>
     */
    public record ParsedProductOffer(

            /**
             * Valor original observado para o vendedor.
             */
            String rawSellerValue,

            /**
             * Classificação normalizada do vendedor.
             */
            SellerType sellerType,

            /**
             * Estrutura da página que forneceu a evidência do vendedor.
             */
            String sellerEvidenceSource,

            /**
             * Valor observado para o responsável pela entrega.
             */
            String rawDeliveryValue,

            /**
             * Classificação normalizada da entrega.
             */
            DeliveryType deliveryType,

            /**
             * Estrutura da página que forneceu a evidência da entrega.
             */
            String deliveryEvidenceSource
    ) {
    }

    /**
     * Representa uma evidência extraída da página.
     *
     * <p>O valor é o conteúdo observado e source identifica
     * a estrutura que forneceu esse conteúdo.</p>
     *
     * <p>Quando a evidência não existe, ambos podem ser
     * {@code null}.</p>
     */
    private record Evidence(
            String value,
            String source
    ) {
    }
}