package com.raspingamazon.domain.evaluation;

/**
 * Representa os motivos controlados pelos quais uma oferta pode
 * ser considerada inelegível pelo domínio.
 *
 * A intenção desta enumeração é evitar que o sistema espalhe textos
 * livres como "vendedor errado", "sem desconto", etc.
 *
 * O motivo passa a ser um código estável do domínio, que poderá ser
 * utilizado posteriormente por:
 *
 * - DealEvaluation;
 * - filtros;
 * - auditoria;
 * - persistência;
 * - relatórios;
 * - testes automatizados.
 *
 * Importante:
 * esta enumeração não conhece a Amazon, PostgreSQL, HTML, Excel ou
 * qualquer canal de publicação. Ela representa somente o vocabulário
 * do domínio.
 */
public enum RejectionReason {

    /**
     * A oferta não possui evidência suficiente sobre o vendedor.
     *
     * A política do projeto é "fail closed": quando não é possível
     * confirmar o vendedor, a oferta não deve ser considerada elegível.
     */
    SELLER_UNKNOWN,

    /**
     * A oferta é vendida por um terceiro.
     */
    SELLER_THIRD_PARTY,

    /**
     * Não foi possível determinar com segurança quem realiza a entrega.
     */
    DELIVERY_UNKNOWN,

    /**
     * A entrega é realizada por um terceiro.
     */
    DELIVERY_THIRD_PARTY,

    /**
     * A oferta não possui os dados mínimos necessários para avaliação.
     *
     * A definição exata desses campos será formalizada quando
     * OfferSnapshot e DealEvaluation forem implementados.
     */
    INSUFFICIENT_DATA
}