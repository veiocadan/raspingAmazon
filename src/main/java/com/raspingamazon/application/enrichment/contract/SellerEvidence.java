package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.domain.validation.SellerType;

import java.util.Objects;

/**
 * Evidência normalizada referente ao vendedor de uma oferta.
 *
 * <p>Este objeto reúne, em um único conceito, três informações que
 * precisam permanecer associadas:</p>
 *
 * <ul>
 *     <li>o valor bruto observado na fonte;</li>
 *     <li>a classificação normalizada utilizada pelo domínio;</li>
 *     <li>a origem específica da evidência.</li>
 * </ul>
 *
 * <p>A classe não decide se a oferta é elegível. Ela apenas descreve
 * a evidência encontrada durante o enriquecimento.</p>
 */
public record SellerEvidence(

        /**
         * Texto exatamente observado na fonte.
         *
         * <p>Exemplos:</p>
         *
         * <ul>
         *     <li>{@code Amazon.com.br}</li>
         *     <li>{@code Amazon Global}</li>
         *     <li>{@code Imagem Hitech FULL}</li>
         * </ul>
         *
         * <p>Pode ser {@code null} quando nenhuma evidência confiável
         * foi encontrada.</p>
         */
        String rawValue,

        /**
         * Classificação normalizada do vendedor.
         *
         * <p>Mesmo quando não existe evidência suficiente, este campo
         * continua obrigatório e deve utilizar
         * {@link SellerType#UNKNOWN}.</p>
         */
        SellerType sellerType,

        /**
         * Componente/estrutura da fonte que forneceu a evidência.
         *
         * <p>Exemplo: {@code merchantInfoFeature}.</p>
         *
         * <p>Pode ser {@code null} quando não houve evidência.</p>
         */
        String source
) {

    /**
     * Garante consistência estrutural do objeto.
     *
     * <p>O tipo normalizado nunca pode ser nulo. Valor bruto e origem,
     * por outro lado, podem estar ausentes quando a classificação é
     * UNKNOWN.</p>
     */
    public SellerEvidence {
        Objects.requireNonNull(
                sellerType,
                "Seller evidence type must not be null"
        );

        if (rawValue != null && rawValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Seller evidence raw value must not be blank"
            );
        }

        if (source != null && source.isBlank()) {
            throw new IllegalArgumentException(
                    "Seller evidence source must not be blank"
            );
        }
    }
}