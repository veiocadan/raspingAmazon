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
 * Primeira política versionada de apresentação comercial.
 *
 * <p>Implementa as decisões estabelecidas para a publicação
 * Amazon da FASE 13.</p>
 *
 * <p>Regras principais:</p>
 *
 * <ul>
 *     <li>currentPrice permanece o preço principal;</li>
 *     <li>basisPrice e previousPrice preservam suas semânticas;</li>
 *     <li>Pix é preferido quando empata com NuPay;</li>
 *     <li>NuPay é principal somente quando seu desconto é
 *         explicitamente maior que o desconto Pix;</li>
 *     <li>NuPay genérico e NuPay Limite Adicional permanecem
 *         métodos distintos no domínio;</li>
 *     <li>quando NuPay é comprovadamente melhor, Pix continua
 *         disponível como alternativa;</li>
 *     <li>ausência de desconto nunca é interpretada como zero;</li>
 *     <li>parcelamento sem juros possui preferência;</li>
 *     <li>dentro da mesma classe de preferência, vence a maior
 *         quantidade de parcelas.</li>
 * </ul>
 */
public final class AmazonCommercialPresentationV1
        implements CommercialPresentationPolicy {

    public static final String VERSION =
            "AMAZON_COMMERCIAL_PRESENTATION_V1";

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
                selectBestInstallmentCondition(
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

    /**
     * Seleciona a melhor condição observada para um método à vista.
     */
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

            if (best == null) {

                best =
                        condition;

                continue;
            }

            if (isBetterCashCondition(
                    condition,
                    best
            )) {

                best =
                        condition;
            }
        }

        return best;
    }

    /**
     * Seleciona a melhor condição pertencente à família NuPay.
     *
     * <p>NUPAY e NUPAY_ADDITIONAL_LIMIT permanecem fatos distintos.
     * Esta etapa somente permite que a política comercial trate ambos
     * como alternativas NuPay para fins de apresentação.</p>
     *
     * <p>Empates preservam a primeira condição observada.</p>
     */
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

            if (best == null) {

                best =
                        candidate;

                continue;
            }

            if (isBetterCashCondition(
                    condition,
                    best.condition()
            )) {

                best =
                        candidate;
            }
        }

        return best;
    }

    /**
     * Determina qual variante NuPay foi explicitamente observada.
     *
     * <p>Quando uma condição excepcionalmente carregar os dois
     * métodos, NUPAY genérico é preservado como apresentação por ser
     * a descrição menos específica. A condição original continua
     * preservando ambos os métodos.</p>
     */
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

    /**
     * Compara duas condições do mesmo grupo comercial.
     *
     * <p>null não representa zero.</p>
     */
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
         * Pelo menos um desconto está ausente.
         *
         * Ausência não é convertida em zero.
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
     * Seleciona a melhor condição de parcelamento.
     *
     * <p>Prioridade:</p>
     *
     * <ol>
     *     <li>condições explicitamente sem juros;</li>
     *     <li>maior quantidade de parcelas.</li>
     * </ol>
     */
    private PaymentCondition selectBestInstallmentCondition(
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

            if (best == null
                    || isBetterInstallment(
                    condition,
                    best
            )) {

                best =
                        condition;
            }
        }

        return best;
    }

    private boolean isBetterInstallment(
            PaymentCondition candidate,
            PaymentCondition current
    ) {

        boolean candidateInterestFree =
                isExplicitlyInterestFree(
                        candidate
                );

        boolean currentInterestFree =
                isExplicitlyInterestFree(
                        current
                );

        if (candidateInterestFree
                && !currentInterestFree) {

            return true;
        }

        if (!candidateInterestFree
                && currentInterestFree) {

            return false;
        }

        return candidate.installmentCount()
                > current.installmentCount();
    }

    private boolean isExplicitlyInterestFree(
            PaymentCondition condition
    ) {

        if (condition.interest() == null) {

            return false;
        }

        return condition.interest()
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
