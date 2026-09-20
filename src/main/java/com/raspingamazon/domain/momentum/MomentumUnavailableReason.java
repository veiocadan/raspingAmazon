package com.raspingamazon.domain.momentum;

/**
 * Explica por que um indicador de momentum não pôde ser produzido.
 *
 * <p>A ausência de momentum não deve ser confundida com momentum zero.</p>
 *
 * <p>Exemplos:</p>
 *
 * <ul>
 *     <li>sem snapshot anterior: não existe comparação temporal;</li>
 *     <li>percentual vendido indisponível: existe histórico, mas falta
 *         o sinal utilizado pelo MOMENTUM_V1.</li>
 * </ul>
 */
public enum MomentumUnavailableReason {

    /**
     * Não existe observação anterior suficiente para comparação.
     */
    NO_PREVIOUS_SNAPSHOT,

    /**
     * Uma das observações não possui percentual vendido.
     */
    SOLD_PERCENTAGE_UNAVAILABLE
}
