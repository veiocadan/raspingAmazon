package com.raspingamazon.application.publication.template;

import com.raspingamazon.application.publication.presentation.CommercialPresentation;
import com.raspingamazon.application.publication.presentation.PresentedCashCondition;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Primeira versão do template textual de publicação Amazon.
 *
 * <p>Esta classe é deliberadamente determinística. A mesma entrada
 * produz o mesmo texto.</p>
 *
 * <p>Alterações futuras no formato da publicação não devem modificar
 * silenciosamente esta versão. Uma mudança incompatível deverá criar
 * AMAZON_PUBLICATION_V2.</p>
 */
public final class AmazonPublicationV1
    implements PublicationTemplate {

    public static final String VERSION =
        "AMAZON_PUBLICATION_V1";

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public String render(
        PublicationTemplateInput input
    ) {

        Objects.requireNonNull(
            input,
            "input must not be null"
        );

        StringBuilder text =
            new StringBuilder();

        appendLine(
            text,
            input.publicationData()
                .product()
                .title()
        );

        CommercialPresentation presentation =
            input.commercialPresentation();

        appendReferencePrices(
            text,
            presentation
        );

        appendLine(
            text,
            "Preço atual: "
                + formatMoney(
                presentation.currentPrice()
            )
        );

        appendCashConditions(
            text,
            presentation
        );

        appendInstallmentCondition(
            text,
            presentation.installmentCondition()
        );

        /*
         * A identificação permanece imediatamente junto do link,
         * de forma clara para quem receber o conteúdo.
         */
        appendLine(
            text,
            "Link patrocinado: "
                + input.affiliateUrl()
        );

        return text.toString();
    }

    private void appendReferencePrices(
        StringBuilder text,
        CommercialPresentation presentation
    ) {

        if (presentation.basisPrice() != null) {

            appendLine(
                text,
                "Preço de referência: "
                    + formatMoney(
                    presentation.basisPrice()
                )
            );
        }

        if (presentation.previousPrice() != null) {

            appendLine(
                text,
                "Preço anterior: "
                    + formatMoney(
                    presentation.previousPrice()
                )
            );
        }
    }

    private void appendCashConditions(
        StringBuilder text,
        CommercialPresentation presentation
    ) {

        if (presentation.primaryCashCondition() != null) {

            appendCashCondition(
                text,
                presentation.primaryCashCondition()
            );
        }

        if (presentation.secondaryCashCondition() != null) {

            appendCashCondition(
                text,
                presentation.secondaryCashCondition()
            );
        }
    }

    private void appendCashCondition(
        StringBuilder text,
        PresentedCashCondition presentedCondition
    ) {

        PaymentCondition condition =
            presentedCondition.condition();

        StringBuilder line =
            new StringBuilder();

        line.append(
            paymentMethodLabel(
                presentedCondition.paymentMethod()
            )
        );

        if (condition.price() != null) {

            line.append(
                ": "
            );

            line.append(
                formatMoney(
                    condition.price()
                )
            );

        } else {

            line.append(
                ": condição disponível"
            );
        }

        if (condition.discountPercentage() != null) {

            line.append(
                " ("
            );

            line.append(
                formatPercentage(
                    condition.discountPercentage()
                )
            );

            line.append(
                " de desconto)"
            );
        }

        appendLine(
            text,
            line.toString()
        );
    }

    private void appendInstallmentCondition(
        StringBuilder text,
        PaymentCondition condition
    ) {

        if (condition == null) {
            return;
        }

        StringBuilder line =
            new StringBuilder();

        line.append(
            "Cartão: "
        );

        line.append(
            condition.installmentCount()
        );

        line.append(
            "x de "
        );

        line.append(
            formatMoney(
                condition.installmentAmount()
            )
        );

        if (isExplicitlyInterestFree(
            condition
        )) {

            line.append(
                " sem juros"
            );

        } else if (condition.interest() != null) {

            line.append(
                " com "
            );

            line.append(
                formatPercentage(
                    condition.interest()
                )
            );

            line.append(
                " de juros"
            );
        }

        appendLine(
            text,
            line.toString()
        );
    }

    private String paymentMethodLabel(
        PaymentMethod paymentMethod
    ) {

        return switch (paymentMethod) {

            case PIX ->
                "Pix";

            case NUPAY_ADDITIONAL_LIMIT ->
                "NuPay";

            case CREDIT_CARD ->
                throw new IllegalArgumentException(
                    "Credit card is not a cash presentation method"
                );
        };
    }

    private boolean isExplicitlyInterestFree(
        PaymentCondition condition
    ) {

        return condition.interest() != null
            && condition.interest()
            .value()
            .compareTo(
                BigDecimal.ZERO
            ) == 0;
    }

    private String formatMoney(
        Money money
    ) {

        BigDecimal amount =
            money.amount()
                .setScale(
                    2,
                    RoundingMode.HALF_UP
                );

        String plain =
            amount.toPlainString();

        String[] parts =
            plain.split(
                "\\."
            );

        String integerPart =
            addThousandsSeparators(
                parts[0]
            );

        String decimalPart =
            parts.length > 1
                ? parts[1]
                : "00";

        return "R$ "
            + integerPart
            + ","
            + decimalPart;
    }

    private String addThousandsSeparators(
        String integerPart
    ) {

        StringBuilder reversed =
            new StringBuilder(
                integerPart
            )
                .reverse();

        StringBuilder formatted =
            new StringBuilder();

        for (int index = 0;
             index < reversed.length();
             index++) {

            if (index > 0
                && index % 3 == 0) {

                formatted.append(
                    '.'
                );
            }

            formatted.append(
                reversed.charAt(
                    index
                )
            );
        }

        return formatted
            .reverse()
            .toString();
    }

    private String formatPercentage(
        Percentage percentage
    ) {

        BigDecimal value =
            percentage.value()
                .stripTrailingZeros();

        return value.toPlainString()
            .replace(
                '.',
                ','
            )
            + "%";
    }

    private void appendLine(
        StringBuilder builder,
        String line
    ) {

        if (!builder.isEmpty()) {
            builder.append(
                '\n'
            );
        }

        builder.append(
            line
        );
    }
}
