package com.raspingamazon.application.collection.contract;

import java.util.Objects;

/**
 * Indica que a fonte respondeu, porém o conteúdo obtido representa uma
 * restrição operacional em vez do recurso solicitado.
 *
 * <p>A exceção pertence ao contrato de coleta, e não à infraestrutura
 * Amazon, porque a aplicação precisa classificar a falha sem depender
 * de adapters concretos.</p>
 *
 * <p>Ela deliberadamente não transporta HTML, challenge tokens,
 * cookies ou qualquer informação destinada a superar a restrição.</p>
 */
public final class SourceRestrictionException
    extends CollectionException {

    private final SourceRestrictionType restrictionType;

    public SourceRestrictionException(
        SourceRestrictionType restrictionType
    ) {

        super(
            "Source restriction detected: "
                + Objects.requireNonNull(
                restrictionType,
                "restrictionType must not be null"
            ).name()
        );

        this.restrictionType =
            restrictionType;
    }

    public SourceRestrictionType restrictionType() {

        return restrictionType;
    }
}
