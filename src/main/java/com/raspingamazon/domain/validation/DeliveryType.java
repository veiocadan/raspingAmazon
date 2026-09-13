package com.raspingamazon.domain.validation;

/**
 * Representa quem é responsável pela entrega da oferta.
 *
 * O domínio trabalha com uma classificação normalizada, e não com
 * textos específicos apresentados pela página da Amazon.
 *
 * Assim, mudanças na forma como a Amazon apresenta informações de
 * entrega ficam isoladas nas camadas de coleta e normalização.
 *
 * UNKNOWN representa ausência ou insuficiência de evidência.
 * Isso é necessário porque a regra do projeto não permite assumir
 * que uma entrega é realizada pela Amazon sem evidência suficiente.
 */
public enum DeliveryType {

    /**
     * A entrega foi identificada como sendo realizada pela Amazon.
     */
    AMAZON,

    /**
     * A entrega foi identificada como sendo realizada por terceiro.
     */
    THIRD_PARTY,

    /**
     * Não foi possível determinar o responsável pela entrega.
     */
    UNKNOWN
}