package com.raspingamazon.domain.scoring;

/**
 * Estado de disponibilidade de um fator utilizado pelo score.
 *
 * <p>É importante distinguir um valor observado igual a zero
 * de um dado que simplesmente não estava disponível.</p>
 */
public enum ScoreFactorStatus {

    /**
     * O fator possuía evidência suficiente para ser normalizado
     * e utilizado no cálculo.
     */
    AVAILABLE,

    /**
     * O dado necessário para o fator não estava disponível.
     *
     * <p>No SCORE_V1 essa situação poderá produzir contribuição
     * zero, mas continuará explicitamente registrada como ausência
     * de informação.</p>
     */
    UNAVAILABLE
}
