package com.raspingamazon.application.publication.template;

/**
 * Contrato de um template versionado de publicação.
 *
 * <p>O template recebe dados já selecionados pela aplicação
 * e produz somente o conteúdo textual da publicação.</p>
 *
 * <p>Ele não:</p>
 *
 * <ul>
 *     <li>consulta banco de dados;</li>
 *     <li>decide elegibilidade;</li>
 *     <li>aplica filtros comerciais;</li>
 *     <li>recalcula score;</li>
 *     <li>seleciona condições comerciais;</li>
 *     <li>constrói link de associado;</li>
 *     <li>persiste Publication;</li>
 *     <li>conhece Telegram ou WhatsApp.</li>
 * </ul>
 */
public interface PublicationTemplate {

    /**
     * Retorna a versão semântica e imutável do template.
     */
    String version();

    /**
     * Renderiza o texto da publicação.
     *
     * @param input dados necessários para renderização
     * @return texto final da publicação
     */
    String render(
        PublicationTemplateInput input
    );
}
