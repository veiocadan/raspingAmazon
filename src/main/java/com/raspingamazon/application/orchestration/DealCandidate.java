package com.raspingamazon.application.orchestration;

import com.raspingamazon.application.parsing.contract.ParsedDeal;

import java.util.Objects;

/**
 * Envelope persistente de um ParsedDeal.
 *
 * <p>O parser continua sendo responsável por produzir ParsedDeal.</p>
 *
 * <p>A orquestração acrescenta apenas identidade persistente e vínculo
 * com a ProcessingRun que originou essa observação.</p>
 *
 * <p>Esse desenho evita duplicar o contrato comercial do parser dentro
 * da camada de orquestração.</p>
 */
public record DealCandidate(

    /**
     * Identidade persistente.
     *
     * <p>Pode ser null antes da persistência.</p>
     */
    Long id,

    /**
     * ProcessingRun que originou o candidato.
     */
    long processingRunId,

    /**
     * Oferta normalizada produzida pelo parser.
     */
    ParsedDeal parsedDeal
) {

    public DealCandidate {

        if (id != null && id <= 0) {
            throw new IllegalArgumentException(
                "DealCandidate id must be positive when present"
            );
        }

        if (processingRunId <= 0) {
            throw new IllegalArgumentException(
                "DealCandidate processingRunId must be positive"
            );
        }

        Objects.requireNonNull(
            parsedDeal,
            "DealCandidate parsedDeal must not be null"
        );
    }

    /**
     * Informa se o candidato já possui identidade persistente.
     */
    public boolean persisted() {

        return id != null;
    }
}
