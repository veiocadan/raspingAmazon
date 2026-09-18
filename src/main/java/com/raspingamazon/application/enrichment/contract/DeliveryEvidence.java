package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.domain.validation.DeliveryType;

import java.util.Objects;

/**
 * Evidência normalizada referente ao responsável pela entrega.
 *
 * <p>O objetivo é manter associados:</p>
 *
 * <ul>
 *     <li>o valor bruto observado;</li>
 *     <li>a classificação normalizada;</li>
 *     <li>a origem da evidência.</li>
 * </ul>
 *
 * <p>Este objeto não contém regra de elegibilidade. A decisão
 * Amazon + Amazon continua pertencendo à camada de validação.</p>
 */
public record DeliveryEvidence(

        /**
         * Texto observado na fonte para o responsável pela entrega.
         *
         * <p>Exemplos:</p>
         *
         * <ul>
         *     <li>{@code Amazon}</li>
         *     <li>{@code BOYA DO BRASIL}</li>
         * </ul>
         *
         * <p>Pode ser {@code null} quando não existe evidência
         * suficiente.</p>
         */
        String rawValue,

        /**
         * Classificação normalizada da entrega.
         *
         * <p>Quando a informação não pode ser determinada,
         * deve ser {@link DeliveryType#UNKNOWN}.</p>
         */
        DeliveryType deliveryType,

        /**
         * Componente/estrutura que forneceu a evidência.
         *
         * <p>Exemplos:</p>
         *
         * <ul>
         *     <li>{@code fulfillerInfoFeature}</li>
         *     <li>{@code merchantInfoFeature}</li>
         * </ul>
         */
        String source
) {

    /**
     * Validação estrutural do contrato.
     */
    public DeliveryEvidence {
        Objects.requireNonNull(
                deliveryType,
                "Delivery evidence type must not be null"
        );

        if (rawValue != null && rawValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Delivery evidence raw value must not be blank"
            );
        }

        if (source != null && source.isBlank()) {
            throw new IllegalArgumentException(
                    "Delivery evidence source must not be blank"
            );
        }
    }
}