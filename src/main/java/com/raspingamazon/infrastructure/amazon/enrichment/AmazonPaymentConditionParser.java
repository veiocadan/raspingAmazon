package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser das condições comerciais observadas na página individual
 * de produto da Amazon.
 *
 * <p>Esta classe interpreta somente fatos explicitamente presentes
 * no HTML recebido.</p>
 *
 * <p>Responsabilidades:</p>
 *
 * <ul>
 *     <li>localizar os widgets comerciais reais por id;</li>
 *     <li>interpretar promoção à vista;</li>
 *     <li>distinguir Pix, NuPay e NuPay Limite Adicional;</li>
 *     <li>ler preço à vista estruturado quando disponível;</li>
 *     <li>interpretar todas as linhas sem juros da tabela de cartão;</li>
 *     <li>normalizar valores monetários brasileiros.</li>
 * </ul>
 *
 * <p>Esta classe não:</p>
 *
 * <ul>
 *     <li>decide elegibilidade;</li>
 *     <li>aplica filtros;</li>
 *     <li>escolhe a condição que será publicada;</li>
 *     <li>calcula desconto pela diferença entre preços;</li>
 *     <li>reconstrói valores comerciais ausentes.</li>
 * </ul>
 */
public final class AmazonPaymentConditionParser {

    private static final String
        CASH_PROMOTION_ID =
        "promotionMessageInsideBuyBox_feature_div";

    private static final String
        CASH_PRICE_ID =
        "oneTimePaymentPrice_feature_div";

    private static final String
        CREDIT_TABLE_ID =
        "InstallmentCalculatorTableCredit";

    /**
     * Segmento específico da condição à vista.
     *
     * <p>Exemplos suportados:</p>
     *
     * <pre>
     * 25% off à vista no Pix
     * à vista no Pix ou NuPay (10% off)
     * à vista no Pix ou NuPay
     * </pre>
     *
     * <p>O segmento termina antes da apresentação de parcelamento,
     * entrega ou demais opções comerciais. Isso impede que textos
     * como "Limite Adicional" presentes em outro sub-bloco sejam
     * atribuídos indevidamente à condição CASH.</p>
     */
    private static final Pattern
        CASH_SEGMENT_PATTERN =
        Pattern.compile(
            "(?:"
                + "\\d+(?:[.,]\\d+)?"
                + "\\s*%"
                + "\\s*(?:off|de\\s+desconto)"
                + "\\s*"
                + ")?"
                + "(?:à|a)"
                + "\\s+vista"
                + "\\s+no"
                + "\\s+.+?"
                + "(?="
                + "\\s+ou\\s+(?:em\\b|r\\$)"
                + "|\\s+entrega\\b"
                + "|\\s+ver\\s+op"
                + "|$"
                + ")",
            Pattern.CASE_INSENSITIVE
                | Pattern.UNICODE_CASE
        );

    /**
     * Percentual explicitamente observado dentro do segmento CASH.
     */
    private static final Pattern
        DISCOUNT_PATTERN =
        Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)"
                + "\\s*%",
            Pattern.CASE_INSENSITIVE
                | Pattern.UNICODE_CASE
        );

    /**
     * Linha de parcelamento sem juros.
     *
     * <p>Depois que Jsoup transforma a tabela em texto, uma linha
     * possui formato equivalente a:</p>
     *
     * <pre>
     * Em 12x de R$ 158,24 sem juros R$ 1.898,00
     * </pre>
     */
    private static final Pattern
        INTEREST_FREE_INSTALLMENT_PATTERN =
        Pattern.compile(
            "(?:em\\s+)?"
                + "(\\d+)x"
                + "\\s+de"
                + "\\s+r\\$"
                + "\\s*"
                + "([0-9][0-9.]*,[0-9]{2})"
                + "\\s+sem\\s+juros"
                + "\\s+r\\$"
                + "\\s*"
                + "([0-9][0-9.]*,[0-9]{2})",
            Pattern.CASE_INSENSITIVE
                | Pattern.UNICODE_CASE
        );

    /**
     * Extrai todas as condições comerciais suportadas.
     *
     * <p>A lista é imutável.</p>
     */
    public List<PaymentCondition> parse(
        String html
    ) {

        Objects.requireNonNull(
            html,
            "html must not be null"
        );

        if (html.isBlank()) {

            throw new IllegalArgumentException(
                "html must not be blank"
            );
        }

        Document document =
            Jsoup.parse(
                html
            );

        List<PaymentCondition> conditions =
            new ArrayList<>();

        PaymentCondition cashCondition =
            extractCashCondition(
                document
            );

        if (cashCondition != null) {

            conditions.add(
                cashCondition
            );
        }

        conditions.addAll(
            extractCreditInstallmentConditions(
                document
            )
        );

        return List.copyOf(
            conditions
        );
    }

    /**
     * Extrai uma única condição CASH representando a promoção
     * explicitamente apresentada para os métodos à vista.
     */
    private PaymentCondition extractCashCondition(
        Document document
    ) {

        List<String> cashSegments =
            extractCashSegments(
                document
            );

        if (cashSegments.isEmpty()) {

            return null;
        }

        List<PaymentMethod> methods =
            extractCashPaymentMethods(
                cashSegments
            );

        if (methods.isEmpty()) {

            return null;
        }

        Percentage discount =
            extractConsistentCashDiscount(
                cashSegments
            );

        Money cashPrice =
            extractCashPrice(
                document
            );

        return new PaymentCondition(
            PaymentConditionType.CASH,
            cashPrice,
            discount,
            null,
            null,
            null,
            null,
            methods
        );
    }

    /**
     * Obtém somente o trecho semântico referente à promoção CASH.
     *
     * <p>Os ids podem aparecer mais de uma vez no DOM, por exemplo
     * em variantes responsivas. Elementos duplicados são considerados,
     * mas segmentos textualmente idênticos são deduplicados.</p>
     */
    private List<String> extractCashSegments(
        Document document
    ) {

        Set<String> segments =
            new LinkedHashSet<>();

        collectCashSegments(
            document,
            CASH_PROMOTION_ID,
            segments
        );

        collectCashSegments(
            document,
            CASH_PRICE_ID,
            segments
        );

        return List.copyOf(
            segments
        );
    }

    private void collectCashSegments(
        Document document,
        String elementId,
        Set<String> segments
    ) {

        Elements elements =
            document.getElementsByAttributeValue(
                "id",
                elementId
            );

        for (Element element : elements) {

            String text =
                normalizeText(
                    element.text()
                );

            if (text.isBlank()) {
                continue;
            }

            Matcher matcher =
                CASH_SEGMENT_PATTERN.matcher(
                    text
                );

            while (matcher.find()) {

                String segment =
                    normalizeText(
                        matcher.group()
                    );

                if (!segment.isBlank()) {

                    segments.add(
                        segment
                    );
                }
            }
        }
    }

    /**
     * Extrai os meios explicitamente presentes no segmento CASH.
     *
     * <p>Importante: "NuPay" só vira NUPAY_ADDITIONAL_LIMIT quando
     * "Limite Adicional" aparece dentro do próprio segmento da
     * promoção à vista.</p>
     */
    private List<PaymentMethod>
    extractCashPaymentMethods(
        List<String> cashSegments
    ) {

        Set<PaymentMethod> methods =
            new LinkedHashSet<>();

        for (String segment : cashSegments) {

            String lower =
                segment.toLowerCase(
                    Locale.ROOT
                );

            if (lower.contains(
                "pix"
            )) {

                methods.add(
                    PaymentMethod.PIX
                );
            }

            if (lower.contains(
                "nupay"
            )) {

                if (lower.contains(
                    "limite adicional"
                )) {

                    methods.add(
                        PaymentMethod
                            .NUPAY_ADDITIONAL_LIMIT
                    );

                } else {

                    methods.add(
                        PaymentMethod.NUPAY
                    );
                }
            }
        }

        return List.copyOf(
            methods
        );
    }

    /**
     * Retorna o desconto quando as ocorrências explícitas encontradas
     * são consistentes entre si.
     *
     * <p>Se widgets duplicados apresentarem percentuais conflitantes,
     * a ausência é preservada em vez de escolher arbitrariamente um
     * dos valores.</p>
     */
    private Percentage extractConsistentCashDiscount(
        List<String> cashSegments
    ) {

        BigDecimal observed =
            null;

        for (String segment : cashSegments) {

            Matcher matcher =
                DISCOUNT_PATTERN.matcher(
                    segment
                );

            if (!matcher.find()) {
                continue;
            }

            BigDecimal candidate =
                parsePercentageValue(
                    matcher.group(
                        1
                    )
                );

            if (candidate == null) {
                continue;
            }

            if (observed == null) {

                observed =
                    candidate;

                continue;
            }

            if (observed.compareTo(
                candidate
            ) != 0) {

                return null;
            }
        }

        if (observed == null) {

            return null;
        }

        try {

            return new Percentage(
                observed
            );

        } catch (IllegalArgumentException exception) {

            return null;
        }
    }

    /**
     * Extrai o preço à vista de fontes estruturadas.
     *
     * <p>A fonte histórica customerVisiblePrice permanece prioritária.
     * Quando ela não existir, data-csa-c-price-to-pay é usado como
     * fallback estruturado.</p>
     *
     * <p>Quando múltiplas ocorrências apresentam valores conflitantes,
     * nenhuma delas é escolhida arbitrariamente.</p>
     */
    private Money extractCashPrice(
        Document document
    ) {

        BigDecimal customerVisiblePrice =
            extractCustomerVisiblePrice(
                document
            );

        if (customerVisiblePrice != null) {

            return new Money(
                customerVisiblePrice
            );
        }

        BigDecimal priceToPay =
            extractPriceToPay(
                document
            );

        if (priceToPay == null) {

            return null;
        }

        return new Money(
            priceToPay
        );
    }

    private BigDecimal extractCustomerVisiblePrice(
        Document document
    ) {

        List<BigDecimal> values =
            new ArrayList<>();

        for (Element input
            : document.getElementsByTag(
            "input"
        )) {

            String name =
                input.attr(
                    "name"
                );

            if (name == null
                || !name.contains(
                "customerVisiblePrice"
            )
                || !name.contains(
                "amount"
            )
                || !input.hasAttr(
                "value"
            )) {

                continue;
            }

            BigDecimal value =
                parseMachineDecimal(
                    input.attr(
                        "value"
                    )
                );

            if (value != null) {

                values.add(
                    value
                );
            }
        }

        return uniqueNumericValue(
            values
        );
    }

    private BigDecimal extractPriceToPay(
        Document document
    ) {

        List<BigDecimal> values =
            new ArrayList<>();

        Elements elements =
            document.getElementsByAttribute(
                "data-csa-c-price-to-pay"
            );

        for (Element element : elements) {

            BigDecimal value =
                parseMachineDecimal(
                    element.attr(
                        "data-csa-c-price-to-pay"
                    )
                );

            if (value != null) {

                values.add(
                    value
                );
            }
        }

        return uniqueNumericValue(
            values
        );
    }

    /**
     * Extrai todas as linhas sem juros explicitamente observadas na
     * tabela de cartão.
     *
     * <p>Não escolhe a "melhor" parcela. Essa decisão pertence à
     * camada de apresentação/publicação.</p>
     */
    private List<PaymentCondition>
    extractCreditInstallmentConditions(
        Document document
    ) {

        Elements tables =
            document.getElementsByAttributeValue(
                "id",
                CREDIT_TABLE_ID
            );

        if (tables.isEmpty()) {

            return List.of();
        }

        Map<
            InstallmentKey,
            PaymentCondition
            > conditions =
            new LinkedHashMap<>();

        for (Element table : tables) {

            String text =
                normalizeText(
                    table.text()
                );

            Matcher matcher =
                INTEREST_FREE_INSTALLMENT_PATTERN
                    .matcher(
                        text
                    );

            while (matcher.find()) {

                Integer installmentCount =
                    parseInstallmentCount(
                        matcher.group(
                            1
                        )
                    );

                BigDecimal installmentAmount =
                    parseBrazilianMoney(
                        matcher.group(
                            2
                        )
                    );

                BigDecimal installmentTotal =
                    parseBrazilianMoney(
                        matcher.group(
                            3
                        )
                    );

                if (installmentCount == null
                    || installmentAmount == null
                    || installmentTotal == null) {

                    continue;
                }

                InstallmentKey key =
                    new InstallmentKey(
                        installmentCount,
                        installmentAmount,
                        installmentTotal
                    );

                conditions.putIfAbsent(
                    key,
                    new PaymentCondition(
                        PaymentConditionType
                            .CREDIT_INSTALLMENT,
                        null,
                        null,
                        installmentCount,
                        new Money(
                            installmentAmount
                        ),
                        new Money(
                            installmentTotal
                        ),
                        Percentage.of(
                            "0"
                        ),
                        List.of(
                            PaymentMethod.CREDIT_CARD
                        )
                    )
                );
            }
        }

        return List.copyOf(
            conditions.values()
        );
    }

    private Integer parseInstallmentCount(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return null;
        }

        try {

            int parsed =
                Integer.parseInt(
                    value.trim()
                );

            return parsed > 0
                ? parsed
                : null;

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /**
     * Interpreta valor monetário apresentado no formato brasileiro.
     *
     * <p>Exemplos:</p>
     *
     * <pre>
     * 949,00    -> 949.00
     * 1.898,00  -> 1898.00
     * </pre>
     */
    private BigDecimal parseBrazilianMoney(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return null;
        }

        String normalized =
            value
                .replace(
                    "\u00A0",
                    ""
                )
                .replace(
                    " ",
                    ""
                )
                .replace(
                    ".",
                    ""
                )
                .replace(
                    ',',
                    '.'
                );

        try {

            return new BigDecimal(
                normalized
            );

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /**
     * Interpreta valores estruturados normalmente representados como
     * decimal técnico, por exemplo 1708.20.
     */
    private BigDecimal parseMachineDecimal(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return null;
        }

        String normalized =
            value.trim();

        if (normalized.contains(
            ","
        )
            && !normalized.contains(
            "."
        )) {

            normalized =
                normalized.replace(
                    ',',
                    '.'
                );
        }

        try {

            return new BigDecimal(
                normalized
            );

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    private BigDecimal parsePercentageValue(
        String value
    ) {

        if (value == null
            || value.isBlank()) {

            return null;
        }

        try {

            return new BigDecimal(
                value.trim()
                    .replace(
                        ',',
                        '.'
                    )
            );

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /**
     * Retorna o valor somente quando todas as ocorrências válidas são
     * numericamente equivalentes.
     */
    private BigDecimal uniqueNumericValue(
        List<BigDecimal> values
    ) {

        BigDecimal observed =
            null;

        for (BigDecimal value : values) {

            if (observed == null) {

                observed =
                    value;

                continue;
            }

            if (observed.compareTo(
                value
            ) != 0) {

                return null;
            }
        }

        return observed;
    }

    private String normalizeText(
        String value
    ) {

        if (value == null) {

            return "";
        }

        return value
            .replace(
                '\u00A0',
                ' '
            )
            .replaceAll(
                "\\s+",
                " "
            )
            .trim();
    }

    /**
     * Chave utilizada apenas para eliminar linhas duplicadas do DOM.
     */
    private record InstallmentKey(
        int installmentCount,
        BigDecimal installmentAmount,
        BigDecimal installmentTotal
    ) {
    }
}
