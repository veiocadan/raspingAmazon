package com.raspingamazon.application.enrichment.model;

/**
 * Tipos de evidência atualmente produzidos durante o enriquecimento.
 *
 * <p>Este enum evita espalhar Strings como "SELLER" e "DELIVERY"
 * pelo código. Dessa forma, erros de digitação passam a ser detectados
 * pelo compilador.</p>
 */
public enum EvidenceType {

    /**
     * Evidência referente ao vendedor da oferta.
     */
    SELLER,

    /**
     * Evidência referente ao responsável pela entrega.
     */
    DELIVERY
}