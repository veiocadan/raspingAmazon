package com.raspingamazon.application.enrichment.model;

/**
 * Tipos semânticos de evidência produzidos durante o enrichment.
 *
 * <p>O enum evita Strings livres na aplicação. A tabela
 * offer_evidence utiliza TEXT deliberadamente para permitir evolução
 * incremental dos tipos sem reescrever migrations históricas.</p>
 */
public enum EvidenceType {

    /**
     * Evidência referente ao vendedor da oferta.
     */
    SELLER,

    /**
     * Evidência referente ao responsável pela entrega.
     */
    DELIVERY,

    /**
     * Evidência de rating observada na página individual.
     */
    RATING,

    /**
     * Evidência da quantidade de avaliações observada na página individual.
     */
    REVIEW_COUNT
}
