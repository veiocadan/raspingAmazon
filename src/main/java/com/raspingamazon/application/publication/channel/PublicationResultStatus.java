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
     * A operação falhou, mas poderá ser tentada novamente.
     *
     * <p>Exemplos futuros podem incluir indisponibilidade temporária
     * ou limitação transitória do provider.</p>
     */
    FAILED_TRANSIENT,

    /**
     * A operação falhou de forma que não deve entrar
     * automaticamente em loop de retry.
     *
     * <p>Exemplos futuros podem incluir destino inválido ou
     * configuração permanentemente rejeitada.</p>
     */
    FAILED_PERMANENT
}
