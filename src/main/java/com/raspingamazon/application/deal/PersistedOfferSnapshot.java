package com.raspingamazon.application.deal;

import com.raspingamazon.domain.deal.OfferSnapshot;

import java.util.Objects;

/**
 * Resultado da persistência idempotente de um OfferSnapshot.
 *
 * <p>created=true significa que esta execução criou a observação.</p>
 *
 * <p>created=false significa que a mesma observação já havia sido
 * processada anteriormente.</p>
 */
public record PersistedOfferSnapshot(
        OfferSnapshot snapshot,
        boolean created
) {

    public PersistedOfferSnapshot {

        Objects.requireNonNull(
                snapshot,
                "snapshot must not be null"
        );

        if (snapshot.id() == null) {
            throw new IllegalArgumentException(
                    "Persisted snapshot must have an id"
            );
        }
    }
}