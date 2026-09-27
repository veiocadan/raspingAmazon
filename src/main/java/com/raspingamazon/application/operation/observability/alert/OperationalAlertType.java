package com.raspingamazon.application.operation.observability.alert;

/**
 * Tipos de alerta operacional previstos pela FASE 16.
 *
 * <p>Alertas são fatos derivados do estado operacional persistido.
 * Eles não alteram jobs, runs, retries ou decisões comerciais.</p>
 */
public enum OperationalAlertType {

    /**
     * Uma integração externa apresentou falhas repetidas dentro da
     * janela definida pela política operacional.
     */
    REPEATED_EXTERNAL_FAILURES,

    /**
     * Uma ProcessingRun possui um ou mais jobs no estado DEAD.
     */
    DEAD_JOBS,

    /**
     * Uma ProcessingRun concluída não produziu candidatos.
     */
    ZERO_CANDIDATES,

    /**
     * O volume coletado ficou significativamente abaixo da referência
     * histórica determinada pela política operacional.
     */
    SUSPICIOUS_COLLECTION_DROP
}
