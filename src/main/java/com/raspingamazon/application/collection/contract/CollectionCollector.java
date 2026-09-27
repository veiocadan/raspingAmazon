package com.raspingamazon.application.collection.contract;

import com.raspingamazon.application.observability.OperationalLogContext;

import java.util.Objects;

/**
 * Porta de coleta utilizada pela aplicação.
 *
 * <p>O método histórico de um argumento permanece como a única operação
 * abstrata. Assim, a interface continua funcional e consumidores
 * existentes baseados em lambda permanecem compatíveis.</p>
 *
 * <p>A sobrecarga contextual transporta somente correlação operacional
 * já conhecida pelo chamador. Implementações que não observam
 * integrações podem utilizar o comportamento default, que delega para
 * o método histórico sem alterar o resultado funcional.</p>
 */
public interface CollectionCollector {

    CollectionResult collect(CollectionRequest request);

    /**
     * Executa a mesma coleta carregando correlação operacional.
     *
     * <p>O contexto não muda a semântica funcional da coleta e não
     * participa de idempotência, retry ou decisão de negócio.</p>
     *
     * @param request comando funcional de coleta
     * @param context correlação operacional conhecida pelo chamador
     * @return resultado funcional da coleta
     */
    default CollectionResult collect(
        CollectionRequest request,
        OperationalLogContext context
    ) {

        Objects.requireNonNull(
            context,
            "Operational log context must not be null"
        );

        return collect(
            request
        );
    }
}
