package com.raspingamazon.domain.publication.contract;

import com.raspingamazon.domain.evaluation.DealEvaluation;

import java.util.Objects;

/**
 * Representa a entrada necessária para o processo de criação
 * de uma Publication.
 *
 * <p>Este objeto pertence ao contrato interno do domínio.
 * Ele não conhece banco de dados, SQL, filas, HTTP ou canais
 * de publicação.</p>
 *
 * <p>A avaliação da oferta é recebida como objeto de domínio,
 * permitindo que o processo de publicação utilize somente
 * informações já validadas pelo fluxo anterior.</p>
 */
public record PublicationRequest(
        DealEvaluation dealEvaluation,
        String templateVersion
) {

    /**
     * Validação estrutural do contrato.
     *
     * <p>O contrato exige uma avaliação e uma versão de template.
     * Regras mais específicas sobre elegibilidade ou conteúdo
     * continuam pertencendo aos respectivos componentes do domínio.</p>
     */
    public PublicationRequest {
        Objects.requireNonNull(
                dealEvaluation,
                "Deal evaluation must not be null"
        );

        Objects.requireNonNull(
                templateVersion,
                "Template version must not be null"
        );

        if (templateVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "Template version must not be blank"
            );
        }
    }
}