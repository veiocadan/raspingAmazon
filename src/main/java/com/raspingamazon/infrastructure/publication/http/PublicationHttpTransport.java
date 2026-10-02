package com.raspingamazon.infrastructure.publication.http;

/**
 * Transporte HTTP utilizado pelos adapters concretos de
 * canais externos de publicação.
 *
 * <p>O contrato conhece apenas HTTP. Não conhece Telegram,
 * WhatsApp, ofertas, outbox ou regras de publicação.</p>
 */
@FunctionalInterface
public interface PublicationHttpTransport {

    /**
     * Executa uma requisição HTTP POST.
     *
     * @param request requisição completamente definida
     * @return resposta HTTP recebida
     * @throws PublicationHttpTransportException quando a operação
     *                                           de transporte falha
     */
    PublicationHttpResponse post(
        PublicationHttpRequest request
    );
}
