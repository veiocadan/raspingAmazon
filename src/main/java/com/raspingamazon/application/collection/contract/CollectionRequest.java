package com.raspingamazon.application.collection.contract;

import java.net.URI;
import java.util.Objects;

/**
 * Representa o comando mínimo para solicitar uma coleta.
 *
 * <p>O contrato não conhece Amazon, HTML, navegador ou biblioteca HTTP.
 * A implementação concreta da coleta poderá utilizar qualquer uma dessas
 * tecnologias sem alterar o contrato consumido pelo restante da aplicação.</p>
 */
public record CollectionRequest(URI source) {

    public CollectionRequest {
        Objects.requireNonNull(source, "Collection source must not be null");

        // Exigimos uma URI absoluta para que o contrato não dependa
        // de regras diferentes para resolver caminhos relativos.
        if (!source.isAbsolute()) {
            throw new IllegalArgumentException(
                    "Collection source must be absolute"
            );
        }
    }
}