package com.raspingamazon.domain.validation;

import com.raspingamazon.domain.evaluation.RejectionReason;

import java.util.Objects;

/**
 * Valida se uma oferta atende ao critério Amazon da FASE 8.
 *
 * <p>A oferta somente é elegível quando:</p>
 *
 * <ul>
 *     <li>o vendedor é a Amazon; e</li>
 *     <li>a entrega é realizada pela Amazon.</li>
 * </ul>
 *
 * <p>A validação utiliza política fail-closed:
 * qualquer estado desconhecido ou terceiro resulta em rejeição.</p>
 *
 * <p>Esta classe não conhece HTML, PostgreSQL, JDBC ou persistência.</p>
 */
public final class AmazonEligibilityValidator {

    /**
     * Valida vendedor e responsável pela entrega.
     *
     * @param sellerType classificação normalizada do vendedor
     * @param deliveryType classificação normalizada da entrega
     * @return resultado da validação
     */
    public AmazonEligibilityResult validate(
            SellerType sellerType,
            DeliveryType deliveryType
    ) {
        Objects.requireNonNull(
                sellerType,
                "sellerType must not be null"
        );

        Objects.requireNonNull(
                deliveryType,
                "deliveryType must not be null"
        );

        /*
         * Vendedor é avaliado primeiro.
         *
         * Isso mantém uma ordem determinística quando mais de
         * uma condição estiver inválida, já que DealEvaluation
         * possui apenas um rejectionReason.
         */
        switch (sellerType) {

            case UNKNOWN:
                return AmazonEligibilityResult.rejected(
                        RejectionReason.SELLER_UNKNOWN
                );

            case THIRD_PARTY:
                return AmazonEligibilityResult.rejected(
                        RejectionReason.SELLER_THIRD_PARTY
                );

            case AMAZON:
                break;
        }

        /*
         * Somente após confirmar que o vendedor é Amazon,
         * avaliamos o responsável pela entrega.
         */
        switch (deliveryType) {

            case UNKNOWN:
                return AmazonEligibilityResult.rejected(
                        RejectionReason.DELIVERY_UNKNOWN
                );

            case THIRD_PARTY:
                return AmazonEligibilityResult.rejected(
                        RejectionReason.DELIVERY_THIRD_PARTY
                );

            case AMAZON:
                return AmazonEligibilityResult.accepted();
        }

        /*
         * O enum DeliveryType atualmente possui todos os estados
         * tratados acima. Este ponto existe apenas como proteção
         * caso novos estados sejam adicionados posteriormente.
         */
        throw new IllegalStateException(
                "Unsupported delivery type: " + deliveryType
        );
    }
}