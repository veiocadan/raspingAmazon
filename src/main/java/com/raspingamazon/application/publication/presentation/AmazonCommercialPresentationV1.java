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

        PaymentCondition nuPayCondition =
            selectBestCashCondition(
                conditions,
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT
            );

        CashPresentation cashPresentation =
            selectCashPresentation(
                pixCondition,
                nuPayCondition
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
     *
     * <p>Quando houver mais de uma condição do mesmo método,
     * uma condição com desconto explícito é preferida sobre outra
     * sem desconto explícito.</p>
     *
     * <p>Quando ambas possuem desconto explícito, vence o maior
     * percentual.</p>
     *
     * <p>Empates preservam a primeira condição observada. Isso
     * mantém comportamento determinístico sem introduzir um
     * critério comercial adicional não definido.</p>
     */
    private PaymentCondition selectBestCashCondition(
        List<PaymentCondition> conditions,
        PaymentMethod paymentMethod
    ) {

        PaymentCondition best =
            null;

        for (PaymentCondition condition : conditions) {

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
     * Compara duas condições do mesmo método.
     *
     * <p>null não representa zero. Uma condição sem percentual
     * explícito nunca é considerada superior a outra somente por
     * ausência de dado.</p>
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
     *
     * <p>NuPay somente assume o papel principal quando existe
     * evidência comparável de que seu desconto é estritamente
     * maior que o desconto Pix.</p>
     *
     * <p>Quando os percentuais não são comparáveis porque um deles
     * está ausente, não inventamos um resultado. Pix permanece
     * como condição principal por sua maior abrangência e NuPay
     * pode permanecer como condição secundária.</p>
     */
    private CashPresentation selectCashPresentation(
        PaymentCondition pixCondition,
        PaymentCondition nuPayCondition
    ) {

        if (pixCondition == null
            && nuPayCondition == null) {

            return CashPresentation.empty();
        }

        if (pixCondition != null
            && nuPayCondition == null) {

            return CashPresentation.primaryOnly(
                new PresentedCashCondition(
                    PaymentMethod.PIX,
                    pixCondition
                )
            );
        }

        if (pixCondition == null) {

            return CashPresentation.primaryOnly(
                new PresentedCashCondition(
                    PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                    nuPayCondition
                )
            );
        }

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
                    new PresentedCashCondition(
                        PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                        nuPayCondition
                    ),
                    new PresentedCashCondition(
                        PaymentMethod.PIX,
                        pixCondition
                    )
                );
            }

            /*
             * Pix maior ou empate:
             *
             * ADR-0001 determina que Pix seja apresentado e
             * NuPay não precisa ser destacado.
             */
            return CashPresentation.primaryOnly(
                new PresentedCashCondition(
                    PaymentMethod.PIX,
                    pixCondition
                )
            );
        }

        /*
         * Os descontos não são diretamente comparáveis porque
         * pelo menos um deles está ausente.
         *
         * Ausência não é convertida para zero.
         *
         * Dessa forma não podemos afirmar que NuPay é
         * economicamente superior.
         *
         * Mantemos Pix como principal e NuPay como alternativa,
         * sem alegar vantagem inexistente.
         */
        return new CashPresentation(
            new PresentedCashCondition(
                PaymentMethod.PIX,
                pixCondition
            ),
            new PresentedCashCondition(
                PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
                nuPayCondition
            )
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
     *
     * <p>Uma condição somente é considerada explicitamente
     * sem juros quando interest está presente e vale zero.</p>
     *
     * <p>interest == null permanece ausência de informação e
     * não é convertido implicitamente em zero.</p>
     */
    private PaymentCondition selectBestInstallmentCondition(
        List<PaymentCondition> conditions
    ) {

        PaymentCondition best =
            null;

        for (PaymentCondition condition : conditions) {

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
