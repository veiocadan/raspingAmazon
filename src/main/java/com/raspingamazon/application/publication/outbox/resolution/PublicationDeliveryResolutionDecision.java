package com.raspingamazon.application.publication.outbox.resolution;

import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;

/**
 * Decisão operacional explícita sobre uma publication_outbox cujo
 * efeito externo terminou como DELIVERY_UNKNOWN.
 *
 * <p>Nenhuma decisão representa retry automático.</p>
 */
public enum PublicationDeliveryResolutionDecision {

    /**
     * Evidência posterior confirmou que a entrega ocorreu.
     *
     * <p>A outbox pode ser consolidada como SUCCEEDED sem executar
     * novamente o canal.</p>
     */
    CONFIRMED_DELIVERED(
        PublicationOutboxStatus.SUCCEEDED
    ),

    /**
     * Evidência posterior confirmou que nenhuma entrega ocorreu.
     *
     * <p>Somente este caso autoriza reabrir a mesma outbox como
     * PENDING para uma nova tentativa.</p>
     */
    CONFIRMED_NOT_DELIVERED(
        PublicationOutboxStatus.PENDING
    ),

    /**
     * A revisão não conseguiu eliminar a ambiguidade.
     *
     * <p>A outbox permanece DELIVERY_UNKNOWN e não volta à fila.</p>
     */
    REMAINS_UNKNOWN(
        PublicationOutboxStatus.DELIVERY_UNKNOWN
    );

    private final PublicationOutboxStatus resultingStatus;

    PublicationDeliveryResolutionDecision(
        PublicationOutboxStatus resultingStatus
    ) {

        this.resultingStatus =
            resultingStatus;
    }

    public PublicationOutboxStatus resultingStatus() {

        return resultingStatus;
    }

    public boolean allowsRedelivery() {

        return this
            == CONFIRMED_NOT_DELIVERED;
    }
}
