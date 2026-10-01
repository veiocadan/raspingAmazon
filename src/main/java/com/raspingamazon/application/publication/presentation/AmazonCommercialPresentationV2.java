package com.raspingamazon.application.publication.presentation;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Segunda política versionada de apresentação comercial Amazon.
 *
 * <p>A V2 preserva a política Pix/NuPay e torna explícita a
 * regra de publicação do parcelamento:</p>
 *
 * <pre>
 * Cartão de Crédito
 *     -> somente parcelas sem juros
 *     -> maior quantidade de parcelas
 *     -> utilizar essa condição na publicação
 * </pre>
 *
 * <p>Condições com juros continuam preservadas no domínio e na
 * persistência, mas não são selecionadas para apresentação.</p>
 */
public final class AmazonCommercialPresentationV2
    implements CommercialPresentationPolicy {

    public static final String VERSION =
        "AMAZON_COMMERCIAL_PRESENTATION_V2";

    @Override
    public String version() {

        return VERSION;
    }

    @Override
    public CommercialPresentation present(
        PublicationData publicationData
    ) {

        Objects.requireNonNull(
            publicationData,
            "publicationData must not be null"
        );

        List<PaymentCondition> conditions =
            publicationData.paymentConditions();

        PaymentCondition pixCondition =
            selectBestCashCondition(
                conditions,
                PaymentMethod.PIX
            );

        PresentedCashCondition nuPayPresentation =
            selectBestNuPayPresentation(
                conditions
            );

        CashPresentation cashPresentation =
            selectCashPresentation(
                pixCondition,
                nuPayPresentation
            );

        PaymentCondition installmentCondition =
            selectBestInterestFreeInstallmentCondition(
                conditions
            );

        return new CommercialPresentation(
            VERSION,
            publicationData.offerSnapshot()
                .currentPrice(),
            publicationData.offerSnapshot()
                .basisPrice(),
            publicationData.offerSnapshot()
                .previousPrice(),
            cashPresentation.primary(),
            cashPresentation.secondary(),
            installmentCondition
        );
    }

    private PaymentCondition selectBestCashCondition(
        List<PaymentCondition> conditions,
        PaymentMethod paymentMethod
    ) {

        PaymentCondition best =
            null;

        for (PaymentCondition condition
            : conditions) {

            if (condition.type()
                != PaymentConditionType.CASH) {

                continue;
            }

            if (!condition.paymentMethods()
                .contains(
                    paymentMethod
                )) {

                continue;
            }

            if (best == null
                || isBetterCashCondition(
                condition,
                best
            )) {

                best =
                    condition;
            }
        }

        return best;
    }

    private PresentedCashCondition selectBestNuPayPresentation(
        List<PaymentCondition> conditions
    ) {

        PresentedCashCondition best =
            null;

        for (PaymentCondition condition
            : conditions) {

            if (condition.type()
                != PaymentConditionType.CASH) {

                continue;
            }

            PaymentMethod method =
                observedNuPayMethod(
                    condition
                );

            if (method == null) {

                continue;
            }

            PresentedCashCondition candidate =
                new PresentedCashCondition(
                    method,
                    condition
                );

            if (best == null
                || isBetterCashCondition(
                condition,
                best.condition()
            )) {

                best =
                    candidate;
            }
        }

        return best;
    }

    private PaymentMethod observedNuPayMethod(
        PaymentCondition condition
    ) {

        if (condition.paymentMethods()
            .contains(
                PaymentMethod.NUPAY
            )) {

            return PaymentMethod.NUPAY;
        }

        if (condition.paymentMethods()
            .contains(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT
            )) {

            return PaymentMethod.NUPAY_ADDITIONAL_LIMIT;
        }

        return null;
    }

    private boolean isBetterCashCondition(
        PaymentCondition candidate,
        PaymentCondition current
    ) {

        Percentage candidateDiscount =
            candidate.discountPercentage();

        Percentage currentDiscount =
            current.discountPercentage();

        if (candidateDiscount == null) {

            return false;
        }

        if (currentDiscount == null) {

            return true;
        }

        return candidateDiscount.value()
            .compareTo(
                currentDiscount.value()
            ) > 0;
    }

    /**
     * Aplica a política Pix/NuPay.
     *
     * <p>NuPay somente assume a posição principal quando
     * possui desconto explicitamente maior que Pix.</p>
     */
    private CashPresentation selectCashPresentation(
        PaymentCondition pixCondition,
        PresentedCashCondition nuPayPresentation
    ) {

        if (pixCondition == null
            && nuPayPresentation == null) {

            return CashPresentation.empty();
        }

        if (pixCondition != null
            && nuPayPresentation == null) {

            return CashPresentation.primaryOnly(
                new PresentedCashCondition(
                    PaymentMethod.PIX,
                    pixCondition
                )
            );
        }

        if (pixCondition == null) {

            return CashPresentation.primaryOnly(
                nuPayPresentation
            );
        }

        PaymentCondition nuPayCondition =
            nuPayPresentation.condition();

        Percentage pixDiscount =
            pixCondition.discountPercentage();

        Percentage nuPayDiscount =
            nuPayCondition.discountPercentage();

        if (pixDiscount != null
            && nuPayDiscount != null) {

            int comparison =
                nuPayDiscount.value()
                    .compareTo(
                        pixDiscount.value()
                    );

            if (comparison > 0) {

                return new CashPresentation(
                    nuPayPresentation,
                    new PresentedCashCondition(
                        PaymentMethod.PIX,
                        pixCondition
                    )
                );
            }

            /*
             * Pix maior ou empate.
             */
            return CashPresentation.primaryOnly(
                new PresentedCashCondition(
                    PaymentMethod.PIX,
                    pixCondition
                )
            );
        }

        /*
         * Ausência de desconto não significa desconto zero.
         *
         * Sem evidência suficiente para afirmar superioridade
         * econômica do NuPay, Pix permanece primário e NuPay
         * continua disponível como condição secundária.
         */
        return new CashPresentation(
            new PresentedCashCondition(
                PaymentMethod.PIX,
                pixCondition
            ),
            nuPayPresentation
        );
    }

    /**
     * Seleciona exclusivamente parcelamento sem juros no cartão.
     */
    private PaymentCondition selectBestInterestFreeInstallmentCondition(
        List<PaymentCondition> conditions
    ) {

        PaymentCondition best =
            null;

        for (PaymentCondition condition
            : conditions) {

            if (condition.type()
                != PaymentConditionType.CREDIT_INSTALLMENT) {

                continue;
            }

            if (!condition.paymentMethods()
                .contains(
                    PaymentMethod.CREDIT_CARD
                )) {

                continue;
            }

            if (!isExplicitlyInterestFree(
                condition
            )) {

                continue;
            }

            if (best == null
                || condition.installmentCount()
                > best.installmentCount()) {

                best =
                    condition;
            }
        }

        return best;
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

    private record CashPresentation(
        PresentedCashCondition primary,
        PresentedCashCondition secondary
    ) {

        private static CashPresentation empty() {

            return new CashPresentation(
                null,
                null
            );
        }

        private static CashPresentation primaryOnly(
            PresentedCashCondition primary
        ) {

            return new CashPresentation(
                primary,
                null
            );
        }
    }
}
