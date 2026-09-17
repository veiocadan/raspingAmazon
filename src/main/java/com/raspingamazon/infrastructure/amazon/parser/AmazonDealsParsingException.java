package com.raspingamazon.infrastructure.amazon.parser;

/**
 * Exceção lançada quando o conteúdo coletado da Amazon não pode ser
 * interpretado de acordo com a estrutura esperada pelo parser.
 *
 * <p>Esta exceção pertence à infraestrutura porque representa uma falha
 * na interpretação da fonte externa. Ela não deve ser propagada para o
 * domínio como um conceito específico da Amazon.</p>
 */
public class AmazonDealsParsingException extends RuntimeException {

    /**
     * Cria uma exceção de parsing com uma mensagem descritiva.
     *
     * @param message descrição da falha
     */
    public AmazonDealsParsingException(String message) {
        super(message);
    }

    /**
     * Cria uma exceção de parsing preservando a causa original.
     *
     * @param message descrição da falha
     * @param cause exceção que originou a falha
     */
    public AmazonDealsParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}