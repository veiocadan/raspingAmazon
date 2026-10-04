package com.raspingamazon.application.publication.channel;

/**
 * Classificação estável do resultado de uma tentativa
 * de publicação externa.
 *
 * <p>O domínio da aplicação não conhece exceções ou códigos
 * específicos de SDKs de Telegram, WhatsApp ou outros providers.</p>
 */
public enum PublicationResultStatus {

    /**
     * O provider confirmou a entrega.
     */
    SUCCESS,

    /**
     * A operação falhou e existe evidência suficiente de que ela pode
     * entrar na política automática de retry.
     *
     * <p>Este estado não deve ser usado quando o request pode ter
     * produzido efeito externo sem confirmação local.</p>
     */
    FAILED_TRANSIENT,

    /**
     * A operação falhou de forma que não deve entrar
     * automaticamente em loop de retry.
     *
     * <p>Exemplos incluem destino inválido ou configuração
     * permanentemente rejeitada.</p>
     */
    FAILED_PERMANENT,

    /**
     * A chamada externa pode ter produzido efeito, mas não existe
     * confirmação suficiente para classificá-la como sucesso ou falha.
     *
     * <p>Esse estado é terminal para retry automático. Qualquer
     * reprocessamento exige reconciliação, prova externa ou decisão
     * operacional explícita.</p>
     */
    DELIVERY_UNKNOWN
}
