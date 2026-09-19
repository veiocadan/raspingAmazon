package com.raspingamazon.domain.evaluation;

/**
 * Representa os motivos controlados pelos quais uma oferta pode
 * ser considerada inelegível ou rejeitada pelo domínio.
 *
 * A intenção desta enumeração é evitar que o sistema espalhe textos
 * livres como "vendedor errado", "sem desconto", "rating baixo" etc.
 *
 * O motivo passa a ser um código estável do domínio, utilizado por:
 *
 * - DealEvaluation;
 * - regras de elegibilidade;
 * - filtros comerciais;
 * - auditoria;
 * - persistência;
 * - relatórios;
 * - testes automatizados.
 *
 * Importante:
 * esta enumeração não conhece Amazon, PostgreSQL, HTML, Excel ou
 * qualquer canal de publicação. Ela representa somente o vocabulário
 * controlado de rejeições do domínio.
 */
public enum RejectionReason {

    /**
     * A oferta não possui evidência suficiente sobre o vendedor.
     *
     * A política estrutural é fail closed: quando não é possível
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
     * Motivo genérico preservado para situações em que a avaliação não
     * dispõe dos dados mínimos necessários e não existe um motivo mais
     * específico no vocabulário do domínio.
     *
     * Os filtros comerciais da FASE 9 devem preferir seus motivos
     * específicos de indisponibilidade.
     */
    INSUFFICIENT_DATA,

    /**
     * Não existe desconto à vista explicitamente informado em uma
     * condição de pagamento reconhecida pelo filtro.
     */
    CASH_DISCOUNT_UNAVAILABLE,

    /**
     * Existe desconto à vista explicitamente informado, mas o maior
     * desconto elegível está abaixo do mínimo configurado.
     */
    CASH_DISCOUNT_BELOW_MINIMUM,

    /**
     * A avaliação agregada do produto não está disponível.
     */
    RATING_UNAVAILABLE,

    /**
     * A avaliação agregada está disponível, mas abaixo do mínimo
     * configurado.
     */
    RATING_BELOW_MINIMUM,

    /**
     * A quantidade agregada de avaliações do produto não está
     * disponível.
     */
    REVIEW_COUNT_UNAVAILABLE,

    /**
     * A quantidade agregada de avaliações está disponível, mas abaixo
     * do mínimo configurado.
     */
    REVIEW_COUNT_BELOW_MINIMUM
}
