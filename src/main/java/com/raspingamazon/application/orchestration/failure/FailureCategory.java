package com.raspingamazon.application.orchestration.failure;

/**
 * Categoria operacional de uma falha.
 *
 * <p>Esta dimensão descreve a natureza operacional da falha e é
 * independente de sua semântica de retry.</p>
 *
 * <p>Por exemplo:</p>
 *
 * <pre>
 * NETWORK + TRANSIENT
 * DATABASE + PERMANENT
 * SOURCE_RESTRICTION + PERMANENT
 * </pre>
 *
 * <p>A categoria não substitui o código específico da falha.
 * O código continua fornecendo granularidade diagnóstica, enquanto
 * esta enumeração permite políticas operacionais estáveis.</p>
 */
public enum FailureCategory {

    /**
     * Falha de conectividade, transporte ou indisponibilidade técnica
     * observada durante comunicação remota.
     */
    NETWORK,

    /**
     * Restrição explícita imposta pela fonte externa.
     *
     * <p>Exemplos: CAPTCHA, challenge e bloqueio.</p>
     */
    SOURCE_RESTRICTION,

    /**
     * Estrutura ou contrato observado na fonte deixou de corresponder
     * ao formato conhecido pela aplicação.
     */
    SOURCE_CHANGED,

    /**
     * Credencial, token ou autorização foi recusado.
     */
    AUTHENTICATION,

    /**
     * Limite de frequência ou capacidade imposto pela integração.
     */
    RATE_LIMIT,

    /**
     * Evidência ou recurso necessário não está disponível de forma
     * confiável.
     */
    DATA_UNAVAILABLE,

    /**
     * Falha pertencente ao PostgreSQL ou à camada de persistência.
     */
    DATABASE,

    /**
     * Falha pertencente à entrega através de um canal de publicação.
     */
    CHANNEL,

    /**
     * Configuração obrigatória ausente, inválida ou incoerente.
     */
    CONFIGURATION,

    /**
     * Falha interna da execução ou de uma invariante operacional.
     */
    PROCESSING,

    /**
     * A causa ainda não possui classificação operacional específica.
     *
     * <p>UNKNOWN é deliberadamente conservador e não implica
     * retry automático.</p>
     */
    UNKNOWN
}
