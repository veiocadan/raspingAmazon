package com.raspingamazon.application.publication.channel;

/**
 * Porta para entrega externa de uma Publication.
 *
 * <p>Implementações futuras poderão representar Telegram,
 * WhatsApp ou qualquer outro canal sem alterar o fluxo central.</p>
 *
 * <p>Responsabilidades de uma implementação:</p>
 *
 * <ul>
 *     <li>validar o destino recebido no comando;</li>
 *     <li>realizar a publicação;</li>
 *     <li>traduzir o retorno do provider;</li>
 *     <li>classificar falhas como transitórias ou permanentes;</li>
 *     <li>preservar a referência do provider quando disponível.</li>
 * </ul>
 *
 * <p>O canal não deve consultar entidades ou repositories internos
 * para construir o conteúdo da mensagem. PublicationCommand já é
 * autocontido.</p>
 *
 * <p>A interface possui uma única operação abstrata de propósito
 * deliberado. Isso permite fake adapters simples e mantém o contrato
 * independente da estratégia usada posteriormente para registrar
 * ou resolver canais.</p>
 */
@FunctionalInterface
public interface PublicationChannel {

    /**
     * Publica o comando informado.
     *
     * <p>A implementação deve converter resultados e falhas externas
     * para PublicationResult. Exceções específicas de provider não
     * devem escapar como contrato funcional normal de entrega.</p>
     *
     * @param command comando autocontido de publicação
     * @return resultado estruturado da tentativa
     */
    PublicationResult publish(
        PublicationCommand command
    );
}
