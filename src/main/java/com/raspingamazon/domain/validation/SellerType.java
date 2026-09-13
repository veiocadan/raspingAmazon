package com.raspingamazon.domain.validation;

/**
 * Representa a classificação do vendedor de uma oferta dentro do domínio.
 *
 * Esta enumeração existe para impedir que a regra de negócio dependa
 * diretamente de textos vindos da Amazon, como "Amazon.com.br" ou
 * qualquer outra representação específica da fonte.
 *
 * A interpretação desses valores será responsabilidade das camadas
 * de coleta e normalização. O domínio recebe apenas uma classificação
 * já normalizada.
 *
 * UNKNOWN é importante porque o projeto adota uma política de
 * "fail closed": quando não houver evidência suficiente para afirmar
 * que o vendedor é a Amazon, a oferta não deve ser considerada elegível.
 */
public enum SellerType {

    /**
     * A oferta foi identificada como vendida pela própria Amazon.
     */
    AMAZON,

    /**
     * A oferta foi identificada como vendida por terceiro.
     */
    THIRD_PARTY,

    /**
     * Não foi possível determinar o vendedor com segurança.
     */
    UNKNOWN
}