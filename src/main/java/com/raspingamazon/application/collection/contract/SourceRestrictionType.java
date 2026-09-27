package com.raspingamazon.application.collection.contract;

/**
 * Restrições explícitas retornadas por uma fonte externa mesmo quando
 * a camada de transporte conseguiu obter uma resposta.
 *
 * <p>Esses valores descrevem somente a natureza observada da
 * restrição. Eles não definem mecanismos de contorno.</p>
 */
public enum SourceRestrictionType {

    /**
     * A fonte apresentou um desafio intermediário destinado a
     * verificar o cliente antes de fornecer o conteúdo solicitado.
     */
    CHALLENGE,

    /**
     * A fonte apresentou explicitamente um CAPTCHA.
     */
    CAPTCHA,

    /**
     * A fonte informou bloqueio ou restrição a acesso automatizado.
     */
    BLOCKED
}
