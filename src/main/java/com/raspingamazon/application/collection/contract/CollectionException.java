package com.raspingamazon.application.collection.contract;

/**
 * Representa uma falha técnica ocorrida durante uma tentativa de coleta.
 *
 * <p>A exceção pertence ao contrato da aplicação porque uma implementação
 * concreta de Collector pode utilizar diferentes tecnologias de transporte
 * sem obrigar o restante da aplicação a conhecer essas tecnologias.</p>
 *
 * <p>Falhas como timeout, erro de conexão, resposta HTTP não aceita ou
 * resposta sem conteúdo devem ser convertidas para este contrato antes
 * de deixarem a fronteira da implementação de coleta.</p>
 */
public class CollectionException extends RuntimeException {

    /**
     * Cria uma falha de coleta com uma mensagem descritiva.
     *
     * @param message descrição da falha ocorrida
     */
    public CollectionException(String message) {
        super(message);
    }

    /**
     * Cria uma falha de coleta preservando a causa técnica original.
     *
     * @param message descrição da falha ocorrida
     * @param cause causa original da falha
     */
    public CollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}