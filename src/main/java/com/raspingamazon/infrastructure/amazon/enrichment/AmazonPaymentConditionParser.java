package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser das condições comerciais observadas na página individual
 * de produto da Amazon.
 *
 * Esta classe pertence à infraestrutura porque conhece estruturas
 * e textos específicos da página Amazon.
 *
 * Responsabilidades:
 *
 * - localizar promoção à vista;
 * - preservar Pix e NuPay como métodos explícitos;
 * - localizar parcelamento sem juros no cartão;
 * - normalizar os fatos observados em PaymentCondition.
 *
 * Esta classe não:
 *
 * - decide elegibilidade;
 * - aplica filtros;
 * - escolhe o que será publicado;
 * - calcula desconto pela diferença entre preços;
 * - inventa preço Pix;
 * - reconstrói parcelamento matematicamente.
 */
public final class AmazonPaymentConditionParser {

    private static final String CASH_PROMOTION_MARKER =
        "promotionMessageInsideBuyBox_feature_div";

    private static final String CASH_PROMOTION_FALLBACK_MARKER =
        "oneTimePaymentPrice_feature_div";

    private static final String CREDIT_TABLE_MARKER =
        "InstallmentCalculatorTableCredit";

    /*
     * A região é propositalmente limitada.
     *
     * Dessa forma não procuramos "Pix", "NuPay" ou percentuais
     * indiscriminadamente em toda a página.
     */
    private static final int FEATURE_REGION_LENGTH =
        8000;

    private static final Pattern CASH_DISCOUNT_PATTERN =
        Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*%\\s*(?:off)?\\s*(?:à|a)\\s*vista",
            Pattern.CASE_INSENSITIVE
                | Pattern.UNICODE_CASE
        );

    /*
     * Fonte estruturada identificada na FASE 0 v2:
     *
     * name="items[0].base][customerVisiblePrice][amount]"
     * value="161.40"
     */
    private static final Pattern CUSTOMER_VISIBLE_PRICE_PATTERN =
        Pattern.compile(
            "customerVisiblePrice\\]\\[amount\\][^>]*"
                + "value\\s*=\\s*[\"']([0-9]+(?:[.,][0-9]+)?)[\"']",
            Pattern.CASE_INSENSITIVE
                | Pattern.UNICODE_CASE
        );

    /*
     * Exemplo normalizado da fonte:
     *
     * Em 6x de R$ 31,65
     * sem juros
     * R$ 189,90
     */
    private static final Pattern CREDIT_INSTALLMENT_PATTERN =
        Pattern.compile(
            "(?:em\\s+)?"
                + "(\\d+)x\\s+de\\s+r\\$\\s*"
                + "([0-9]+(?:[.,][0-9]+)?)"
                + ".*?"
                + "sem\\s+juros"
                + ".*?"
                + "r\\$\\s*"
                + "([0-9]+(?:[.,][0-9]+)?)",
            Pattern.CASE_INSENSITIVE
                | Pattern.UNICODE_CASE
                | Pattern.DOTALL
        );

    /**
     * Extrai todas as condições comerciais suportadas encontradas
     * explicitamente na página.
     *
     * A lista retornada é imutável.
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

        List<PaymentCondition> conditions =
            new ArrayList<>();

        PaymentCondition cashCondition =
            extractCashCondition(
                html
            );

        if (cashCondition != null) {
            conditions.add(
                cashCondition
            );
        }

        PaymentCondition creditCondition =
            extractCreditInstallmentCondition(
                html
            );

        if (creditCondition != null) {
            conditions.add(
                creditCondition
            );
        }

        return List.copyOf(
            conditions
        );
    }

    /**
     * Extrai a promoção à vista explicitamente associada ao bloco
     * comercial investigado na FASE 0 v2.
     */
    private PaymentCondition extractCashCondition(
        String html
    ) {
        String promotionRegion =
            featureRegion(
                html,
                CASH_PROMOTION_MARKER
            );

        if (promotionRegion == null) {
            promotionRegion =
                featureRegion(
                    html,
                    CASH_PROMOTION_FALLBACK_MARKER
                );
        }

        if (promotionRegion == null) {
            return null;
        }

        String text =
            normalizeHtmlText(
                promotionRegion
            );

        List<PaymentMethod> methods =
            extractCashPaymentMethods(
                text
            );

        /*
         * Sem método explicitamente reconhecido, não construímos
         * uma condição CASH.
         *
         * Isso evita transformar qualquer percentual promocional
         * encontrado na página em desconto Pix/NuPay.
         */
        if (methods.isEmpty()) {
            return null;
        }

        Percentage discount =
            extractCashDiscount(
                text
            );

        Money cashPrice =
            extractCustomerVisiblePrice(
                html
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
     * Extrai apenas os meios explicitamente associados à promoção
     * à vista.
     */
    private List<PaymentMethod> extractCashPaymentMethods(
        String normalizedText
    ) {
        String lower =
            normalizedText.toLowerCase(
                Locale.ROOT
            );

        List<PaymentMethod> methods =
            new ArrayList<>();

        if (lower.contains("pix")) {
            methods.add(
                PaymentMethod.PIX
            );
        }

        if (lower.contains("nupay")
            || lower.contains("limite adicional")) {

            methods.add(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT
            );
        }

        return List.copyOf(
            methods
        );
    }

    /**
     * Retorna o percentual somente quando ele está explicitamente
     * descrito como desconto à vista dentro da região comercial.
     *
     * Ausência continua sendo null.
     */
    private Percentage extractCashDiscount(
        String normalizedText
    ) {
        Matcher matcher =
            CASH_DISCOUNT_PATTERN.matcher(
                normalizedText
            );

        if (!matcher.find()) {
            return null;
        }

        BigDecimal value =
            parseDecimal(
                matcher.group(1)
            );

        if (value == null) {
            return null;
        }

        try {
            return new Percentage(
                value
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Lê customerVisiblePrice apenas como preço comercial associado
     * à condição observada.
     *
     * A ausência desse valor não invalida a existência da condição.
     */
    private Money extractCustomerVisiblePrice(
        String html
    ) {
        Matcher matcher =
            CUSTOMER_VISIBLE_PRICE_PATTERN.matcher(
                html
            );

        if (!matcher.find()) {
            return null;
        }

        BigDecimal value =
            parseDecimal(
                matcher.group(1)
            );

        if (value == null) {
            return null;
        }

        try {
            return new Money(
                value
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Extrai uma condição de cartão sem juros diretamente da tabela
     * de parcelamento identificada na investigação.
     *
     * Parcelamentos com juros ainda não são normalizados aqui porque
     * o modelo atual não possui uma representação explícita para
     * "juros presentes, percentual desconhecido".
     *
     * Preservamos ausência em vez de inventar esse percentual.
     */
    private PaymentCondition extractCreditInstallmentCondition(
        String html
    ) {
        String creditRegion =
            featureRegion(
                html,
                CREDIT_TABLE_MARKER
            );

        if (creditRegion == null) {
            return null;
        }

        String text =
            normalizeHtmlText(
                creditRegion
            );

        Matcher matcher =
            CREDIT_INSTALLMENT_PATTERN.matcher(
                text
            );

        if (!matcher.find()) {
            return null;
        }

        Integer installmentCount;

        try {
            installmentCount =
                Integer.valueOf(
                    matcher.group(1)
                );
        } catch (NumberFormatException exception) {
            return null;
        }

        BigDecimal installmentAmountValue =
            parseDecimal(
                matcher.group(2)
            );

        BigDecimal installmentTotalValue =
            parseDecimal(
                matcher.group(3)
            );

        if (installmentAmountValue == null
            || installmentTotalValue == null) {

            return null;
        }

        return new PaymentCondition(
            PaymentConditionType.CREDIT_INSTALLMENT,
            null,
            null,
            installmentCount,
            new Money(
                installmentAmountValue
            ),
            new Money(
                installmentTotalValue
            ),
            Percentage.of(
                "0"
            ),
            List.of(
                PaymentMethod.CREDIT_CARD
            )
        );
    }

    /**
     * Obtém uma região limitada do HTML a partir de um marcador.
     *
     * O objetivo é impedir que textos de promoções concorrentes,
     * cupons, Prime ou cartão Amazon sejam misturados com a promoção
     * específica que estamos interpretando.
     */
    private String featureRegion(
        String html,
        String marker
    ) {
        int markerIndex =
            html.indexOf(
                marker
            );

        if (markerIndex < 0) {
            return null;
        }

        int end =
            Math.min(
                html.length(),
                markerIndex
                    + FEATURE_REGION_LENGTH
            );

        return html.substring(
            markerIndex,
            end
        );
    }

    /**
     * Converte um trecho HTML em texto suficientemente estável
     * para interpretar os padrões comerciais investigados.
     *
     * Não é um parser HTML genérico. É uma normalização localizada
     * de uma região previamente selecionada.
     */
    private String normalizeHtmlText(
        String html
    ) {
        return html
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&percnt;", "%")
            .replace("&agrave;", "à")
            .replace("&Agrave;", "À")
            .replace("&atilde;", "ã")
            .replace("&otilde;", "õ")
            .replace("&ccedil;", "ç")
            .replaceAll(
                "<[^>]+>",
                " "
            )
            .replaceAll(
                "\\s+",
                " "
            )
            .trim();
    }

    private BigDecimal parseDecimal(
        String value
    ) {
        if (value == null
            || value.isBlank()) {

            return null;
        }

        String normalized =
            value.trim()
                .replace(',', '.');

        try {
            return new BigDecimal(
                normalized
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
