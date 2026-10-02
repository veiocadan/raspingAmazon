package com.raspingamazon.application.publication.channel;

/**
 * Transforma o conteúdo textual de uma publicação para uma
 * representação específica de entrega.
 *
 * <p>O formatter não pode:</p>
 *
 * <ul>
 *     <li>alterar fatos da oferta;</li>
 *     <li>recalcular preços;</li>
 *     <li>recalcular descontos;</li>
 *     <li>trocar o link de associado;</li>
 *     <li>decidir elegibilidade;</li>
 *     <li>realizar I/O externo.</li>
 * </ul>
 *
 * <p>Sua responsabilidade é exclusivamente de apresentação.</p>
 */
@FunctionalInterface
public interface PublicationContentFormatter {

    String format(
        String content
    );
}
