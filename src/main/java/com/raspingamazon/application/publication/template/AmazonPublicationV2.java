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
 * Segunda versão do template textual de publicação Amazon.
 *
 * <p>A V2 utiliza uma representação canônica independente do
 * provider de mensageria.</p>
 *
 * <p>Marcação canônica:</p>
 *
 * <pre>
 * **texto** -> destaque forte
 * ~~texto~~ -> texto riscado
 * </pre>
 *
 * <p>Telegram e WhatsApp poderão transformar posteriormente
 * somente essa marcação visual.</p>
 */
public final class AmazonPublicationV2
    implements PublicationTemplate {

    public static final String VERSION =
        "AMAZON_PUBLICATION_V2";

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

        CommercialPresentation presentation =
            input.commercialPresentation();

        EffectiveOffer effectiveOffer =
            selectEffectiveOffer(
                presentation
            );

        Money referencePrice =
            selectReferencePrice(
                presentation,
                effectiveOffer.price()
            );

        StringBuilder text =
            new StringBuilder();

        appendLine(
            text,
            "🔹"
                + bold(
                input.publicationData()
                    .product()
                    .title()
            )
        );

        appendLine(
            text,
            renderPriceLine(
                referencePrice,
                effectiveOffer
            )
        );

        appendPixAlternativeWhenRequired(
            text,
            presentation,
            effectiveOffer
        );

        appendInstallmentLine(
            text,
            presentation.installmentCondition()
        );

        appendLine(
            text,
            "👇 Tá em Promo!"
        );

        appendLine(
            text,
            "🔗 "
                + input.affiliateUrl()
        );

        return text.toString();
    }

    private EffectiveOffer selectEffectiveOffer(
        CommercialPresentation presentation
    ) {

        Money currentPrice =
            presentation.currentPrice();

        PresentedCashCondition primary =
            presentation.primaryCashCondition();

        if (!isDisplayableCashAdvantage(
            primary,
            currentPrice
        )) {

            return new EffectiveOffer(
                currentPrice,
                null
            );
        }

        return new EffectiveOffer(
            primary.condition()
                .price(),
            primary.paymentMethod()
        );
    }

    private boolean isDisplayableCashAdvantage(
        PresentedCashCondition cashCondition,
        Money currentPrice
    ) {

        if (cashCondition == null) {

            return false;
        }

        PaymentCondition condition =
            cashCondition.condition();

        if (condition.price() == null
            || condition.discountPercentage() == null) {

            return false;
        }

        if (condition.discountPercentage()
            .value()
            .compareTo(
                BigDecimal.ZERO
            ) <= 0) {

            return false;
        }

        /*
         * Não calculamos um desconto implícito por diferença
         * de preços. O desconto já precisa ter sido observado.
         *
         * A comparação monetária serve somente como proteção
         * adicional contra uma apresentação incoerente.
         */
        return condition.price()
            .amount()
            .compareTo(
                currentPrice.amount()
            ) < 0;
    }

    private Money selectReferencePrice(
        CommercialPresentation presentation,
        Money effectivePrice
    ) {

        Money basisPrice =
            presentation.basisPrice();

        if (basisPrice != null
            && isGreater(
            basisPrice,
            effectivePrice
        )) {

            return basisPrice;
        }

        Money previousPrice =
            presentation.previousPrice();

        if (previousPrice != null
            && isGreater(
            previousPrice,
            effectivePrice
        )) {

            return previousPrice;
        }

        return null;
    }

    private String renderPriceLine(
        Money referencePrice,
        EffectiveOffer effectiveOffer
    ) {

        StringBuilder line =
            new StringBuilder(
                "💰 "
            );

        if (referencePrice != null) {

            line.append(
                "De "
            );

            line.append(
                strike(
                    formatMoney(
                        referencePrice
                    )
                )
            );

            line.append(
                " por "
            );

        } else {

            line.append(
                "Por "
            );
        }

        line.append(
            bold(
                formatMoney(
                    effectiveOffer.price()
                )
            )
        );

        if (effectiveOffer.paymentMethod()
            != null) {

            line.append(
                " à vista no "
            );

            line.append(
                paymentMethodLabel(
                    effectiveOffer.paymentMethod()
                )
            );
        }

        line.append(
            '!'
        );

        return line.toString();
    }

    /**
     * Quando NuPay possui vantagem estritamente maior,
     * Pix permanece visível como alternativa.
     */
    private void appendPixAlternativeWhenRequired(
        StringBuilder text,
        CommercialPresentation presentation,
        EffectiveOffer effectiveOffer
    ) {

        if (!isNuPay(
            effectiveOffer.paymentMethod()
        )) {

            return;
        }

        PresentedCashCondition primary =
            presentation.primaryCashCondition();

        PresentedCashCondition secondary =
            presentation.secondaryCashCondition();

        if (!isStrictlyBetterNuPayWithPixAlternative(
            primary,
            secondary
        )) {

            return;
        }

        PaymentCondition pixCondition =
            secondary.condition();

        if (pixCondition.price() != null) {

            appendLine(
                text,
                "💸 No Pix: "
                    + bold(
                    formatMoney(
                        pixCondition.price()
                    )
                )
                    + " à vista."
            );

            return;
        }

        appendLine(
            text,
            "💸 Pix também disponível à vista."
        );
    }

    private boolean isStrictlyBetterNuPayWithPixAlternative(
        PresentedCashCondition primary,
        PresentedCashCondition secondary
    ) {

        if (primary == null
            || secondary == null) {

            return false;
        }

        if (!isNuPay(
            primary.paymentMethod()
        )
            || secondary.paymentMethod()
            != PaymentMethod.PIX) {

            return false;
        }

        Percentage nuPayDiscount =
            primary.condition()
                .discountPercentage();

        Percentage pixDiscount =
            secondary.condition()
                .discountPercentage();

        if (nuPayDiscount == null
            || pixDiscount == null) {

            return false;
        }

        return nuPayDiscount.value()
            .compareTo(
                pixDiscount.value()
            ) > 0;
    }

    /**
     * Defesa adicional do template.
     *
     * <p>Mesmo que outra política entregue uma condição com juros,
     * ela não será publicada.</p>
     */
    private void appendInstallmentLine(
        StringBuilder text,
        PaymentCondition condition
    ) {

        if (condition == null
            || !isExplicitlyInterestFree(
            condition
        )) {

            return;
        }

        appendLine(
            text,
            "💳 Ou "
                + condition.installmentCount()
                + "x "
                + formatMoney(
                condition.installmentAmount()
            )
                + " sem juros no cartão."
        );
    }

    private boolean isExplicitlyInterestFree(
        PaymentCondition condition
    ) {

        return condition.interest()
            != null
            && condition.interest()
            .value()
            .compareTo(
                BigDecimal.ZERO
            ) == 0;
    }

    private boolean isNuPay(
        PaymentMethod paymentMethod
    ) {

        return paymentMethod
            == PaymentMethod.NUPAY
            || paymentMethod
            == PaymentMethod.NUPAY_ADDITIONAL_LIMIT;
    }

    private String paymentMethodLabel(
        PaymentMethod paymentMethod
    ) {

        return switch (paymentMethod) {

            case PIX ->
                "Pix";

            case NUPAY,
                 NUPAY_ADDITIONAL_LIMIT ->
                "NuPay";

            case CREDIT_CARD ->
                throw new IllegalArgumentException(
                    "Credit card is not a cash presentation method"
                );
        };
    }

    private boolean isGreater(
        Money candidate,
        Money reference
    ) {

        return candidate.amount()
            .compareTo(
                reference.amount()
            ) > 0;
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

    private String bold(
        String value
    ) {

        return "**"
            + value
            + "**";
    }

    private String strike(
        String value
    ) {

        return "~~"
            + value
            + "~~";
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

    private record EffectiveOffer(
        Money price,
        PaymentMethod paymentMethod
    ) {

        private EffectiveOffer {

            Objects.requireNonNull(
                price,
                "price must not be null"
            );
        }
    }
}
