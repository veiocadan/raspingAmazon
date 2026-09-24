package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;
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
 *
 * <p>A extração é deliberadamente estrutural. Seller e delivery são
 * procurados apenas dentro dos respectivos feature blocks da oferta
 * principal. Isso evita que labels ou valores presentes em recomendações,
 * ofertas secundárias ou outros blocos da página contaminem a evidência
 * principal.</p>
 */
public final class AmazonProductPageParser {

    private static final String MERCHANT_FEATURE =
        "merchantInfoFeature";

    private static final String FULFILLER_FEATURE =
        "fulfillerInfoFeature";

    private static final String OFFER_DISPLAY_VALUE_CLASS =
        "offer-display-feature-text-message";

    private static final List<String> SELLER_LABELS =
        List.of(
            "Vendido por",
            "Enviado / Vendido"
        );

    private static final List<String> DELIVERY_LABELS =
        List.of(
            "Enviado por"
        );

    private static final List<String> COMBINED_LABELS =
        List.of(
            "Enviado / Vendido"
        );

    /**
     * Interpreta o HTML de uma página individual de produto.
     *
     * @param html conteúdo HTML da página da Amazon
     * @return evidências normalizadas da oferta principal
     */
    public ParsedProductOffer parse(
        String html
    ) {

        Objects.requireNonNull(
            html,
            "HTML must not be null"
        );

        if (html.isBlank()) {

            throw new IllegalArgumentException(
                "HTML must not be blank"
            );
        }

        Document document =
            Jsoup.parse(
                html
            );

        /*
         * Seller e delivery são extraídos de forma independente.
         *
         * "Enviado pela Amazon" não implica "Vendido pela Amazon".
         */
        Evidence rawSellerEvidence =
            extractSellerEvidence(
                document
            );

        Evidence rawDeliveryEvidence =
            extractDeliveryEvidence(
                document
            );

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
     *
     * <p>Somente o bloco merchantInfoFeature selecionado como principal
     * pode fornecer essa evidência.</p>
     */
    private Evidence extractSellerEvidence(
        Document document
    ) {

        Element merchantFeature =
            findPrimaryFeature(
                document,
                MERCHANT_FEATURE
            );

        if (merchantFeature == null) {

            return Evidence.empty();
        }

        String value =
            extractLabeledValue(
                merchantFeature,
                SELLER_LABELS
            );

        if (value == null) {

            return Evidence.empty();
        }

        return new Evidence(
            value,
            MERCHANT_FEATURE
        );
    }

    /**
     * Extrai a evidência responsável pela entrega.
     *
     * <p>A fonte principal é fulfillerInfoFeature. Somente quando esse
     * bloco não fornece evidência confiável é utilizado o fallback
     * combinado de merchantInfoFeature.</p>
     */
    private Evidence extractDeliveryEvidence(
        Document document
    ) {

        Element fulfillerFeature =
            findPrimaryFeature(
                document,
                FULFILLER_FEATURE
            );

        if (fulfillerFeature != null) {

            String explicitDelivery =
                extractLabeledValue(
                    fulfillerFeature,
                    DELIVERY_LABELS
                );

            if (explicitDelivery != null) {

                return new Evidence(
                    explicitDelivery,
                    FULFILLER_FEATURE
                );
            }
        }

        return extractCombinedDeliveryEvidence(
            document
        );
    }

    /**
     * Extrai delivery da estrutura combinada "Enviado / Vendido".
     */
    private Evidence extractCombinedDeliveryEvidence(
        Document document
    ) {

        Element merchantFeature =
            findPrimaryFeature(
                document,
                MERCHANT_FEATURE
            );

        if (merchantFeature == null) {

            return Evidence.empty();
        }

        String combinedValue =
            extractLabeledValue(
                merchantFeature,
                COMBINED_LABELS
            );

        if (combinedValue == null) {

            return Evidence.empty();
        }

        /*
         * A estrutura combinada pode representar a própria Amazon como:
         *
         * Amazon
         * Amazon.com.br
         * Amazon Global
         *
         * Para delivery, essas representações continuam sendo
         * normalizadas para o valor textual estável "Amazon", como já
         * ocorria no contrato anterior.
         */
        String normalized =
            combinedValue
                .trim()
                .toLowerCase(
                    Locale.ROOT
                );

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
            MERCHANT_FEATURE
        );
    }

    /**
     * Localiza o feature block da oferta principal.
     *
     * <p>Quando existe o id canônico da Amazon, ele tem precedência.
     * Isso é importante porque páginas reais podem conter estruturas
     * repetidas para ofertas secundárias ou componentes auxiliares.</p>
     *
     * <p>Fixtures mínimas e algumas variações de HTML podem não trazer
     * o id canônico. Nesse caso é utilizado o primeiro elemento cujo
     * data-feature-name corresponde exatamente à feature procurada.</p>
     */
    private Element findPrimaryFeature(
        Document document,
        String featureName
    ) {

        String canonicalId =
            featureName
                + "_feature_div";

        Element canonicalFeature =
            document.getElementById(
                canonicalId
            );

        if (canonicalFeature != null) {

            return canonicalFeature;
        }

        return document
            .selectFirst(
                "[data-feature-name=\""
                    + featureName
                    + "\"]"
            );
    }

    /**
     * Extrai o valor associado a um dos labels esperados, sempre dentro
     * do feature block recebido.
     *
     * <p>A busca percorre os elementos em ordem de documento. Primeiro
     * localiza o label e, somente depois dele, aceita o primeiro elemento
     * com a classe offer-display-feature-text-message.</p>
     *
     * <p>O uso de {@link Element#text()} remove markup interno de anchors,
     * spans e outras estruturas de apresentação. Assim o rawValue
     * auditável representa o texto visível observado, e não fragmentos
     * de HTML.</p>
     */
    private String extractLabeledValue(
        Element feature,
        List<String> expectedLabels
    ) {

        List<Element> elements =
            feature.getAllElements();

        int labelIndex =
            findLabelIndex(
                elements,
                expectedLabels
            );

        if (labelIndex < 0) {

            return null;
        }

        for (int index = labelIndex + 1;
             index < elements.size();
             index++) {

            Element element =
                elements.get(
                    index
                );

            if (!element.hasClass(
                OFFER_DISPLAY_VALUE_CLASS
            )) {

                continue;
            }

            String value =
                normalizeVisibleText(
                    element.text()
                );

            if (value != null) {

                return value;
            }
        }

        return null;
    }

    /**
     * Localiza o primeiro label esperado usando somente o texto próprio
     * de cada elemento.
     *
     * <p>ownText evita que um container ancestral seja confundido com o
     * label apenas porque contém, em seus descendentes, tanto o label
     * quanto o valor.</p>
     */
    private int findLabelIndex(
        List<Element> elements,
        List<String> expectedLabels
    ) {

        for (int index = 0;
             index < elements.size();
             index++) {

            String ownText =
                normalizeVisibleText(
                    elements.get(
                        index
                    ).ownText()
                );

            if (ownText == null) {

                continue;
            }

            for (String expectedLabel :
                expectedLabels) {

                if (containsNormalizedLabel(
                    ownText,
                    expectedLabel
                )) {

                    return index;
                }
            }
        }

        return -1;
    }

    /**
     * Mantém compatibilidade com labels que possam conter dois-pontos ou
     * pequenas variações de espaços sem fazer busca global na página.
     */
    private boolean containsNormalizedLabel(
        String observedText,
        String expectedLabel
    ) {

        String normalizedObserved =
            observedText.toLowerCase(
                Locale.ROOT
            );

        String normalizedExpected =
            expectedLabel.toLowerCase(
                Locale.ROOT
            );

        return normalizedObserved.contains(
            normalizedExpected
        );
    }

    /**
     * Normaliza somente apresentação textual.
     *
     * <p>Não altera o significado do vendedor ou entregador. A função
     * remove non-breaking spaces, compacta sequências de whitespace e
     * retorna null para ausência efetiva de texto.</p>
     */
    private String normalizeVisibleText(
        String value
    ) {

        if (value == null) {

            return null;
        }

        String normalized =
            value
                .replace(
                    '\u00A0',
                    ' '
                )
                .replaceAll(
                    "\\s+",
                    " "
                )
                .trim();

        return normalized.isEmpty()
            ? null
            : normalized;
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
                .toLowerCase(
                    Locale.ROOT
                );

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
                .toLowerCase(
                    Locale.ROOT
                );

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
     * e não como uma sequência de Strings posicionais.</p>
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
