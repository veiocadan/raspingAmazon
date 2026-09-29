package com.raspingamazon.application.publication.channel;

/**
 * Resolve a implementação de PublicationChannel responsável
 * por uma identidade lógica de canal.
 *
 * <p>A aplicação não conhece Telegram, WhatsApp, SDKs ou
 * detalhes de composição de adapters.</p>
 */
@FunctionalInterface
public interface PublicationChannelResolver {

    /**
     * Resolve o adapter configurado para o canal informado.
     *
     * @param channel identidade lógica persistida na outbox
     * @return canal responsável pela entrega
     * @throws IllegalStateException quando não houver adapter
     *                               configurado para o canal
     */
    PublicationChannel resolve(
        String channel
    );
}
