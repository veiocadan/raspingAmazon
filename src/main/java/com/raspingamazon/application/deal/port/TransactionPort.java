package com.raspingamazon.application.deal.port;

import java.util.function.Supplier;

/**
 * Porta responsável por executar uma unidade de trabalho
 * dentro de uma transação.
 *
 * <p>A camada de aplicação conhece somente este contrato.
 * JDBC, Connection, commit e rollback pertencem à infraestrutura.</p>
 */
public interface TransactionPort {

    /**
     * Executa uma operação transacional que produz um resultado.
     *
     * <p>Se a operação terminar normalmente, a implementação deve
     * efetivar a transação.</p>
     *
     * <p>Se qualquer exceção for lançada, a implementação deve
     * desfazer integralmente a unidade de trabalho e propagar a falha.</p>
     *
     * @param operation operação pertencente à unidade transacional
     * @param <T> tipo do resultado
     * @return resultado produzido pela operação
     */
    <T> T execute(
            Supplier<T> operation
    );
}