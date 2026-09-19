package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.deal.port.OfferSnapshotPersistencePort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.infrastructure.persistence.OfferSnapshotRepository;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Adapter JDBC da persistência de OfferSnapshot.
 *
 * <p>O repository atual retorna apenas o id gerado. A porta da aplicação,
 * por outro lado, trabalha com a entidade persistida. O adapter faz essa
 * tradução reconstruindo o snapshot com o novo id.</p>
 */
public final class OfferSnapshotJdbcPersistenceAdapter
        implements OfferSnapshotPersistencePort {

    private final OfferSnapshotRepository repository;

    public OfferSnapshotJdbcPersistenceAdapter(
            OfferSnapshotRepository repository
    ) {
        this.repository =
                Objects.requireNonNull(
                        repository,
                        "repository must not be null"
                );
    }

    @Override
    public OfferSnapshot save(
            OfferSnapshot snapshot
    ) {
        Objects.requireNonNull(
                snapshot,
                "snapshot must not be null"
        );

        if (snapshot.id() != null) {
            throw new IllegalArgumentException(
                    "New OfferSnapshot must not already have an id"
            );
        }

        try {
            long id =
                    repository.insert(
                            snapshot
                    );

            return copyWithId(
                    snapshot,
                    id
            );

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                    "Failed to persist OfferSnapshot for ASIN "
                            + snapshot.product()
                            .asin()
                            .value(),
                    exception
            );
        }
    }

    /**
     * Como OfferSnapshot é imutável, criamos uma nova representação
     * contendo o id retornado pelo banco.
     */
    private OfferSnapshot copyWithId(
            OfferSnapshot snapshot,
            long id
    ) {
        return new OfferSnapshot(
                id,
                snapshot.product(),
                snapshot.collectedAt(),
                snapshot.currentPrice(),
                snapshot.basisPrice(),
                snapshot.previousPrice(),
                snapshot.soldPercentage(),
                snapshot.rating(),
                snapshot.reviewCount(),
                snapshot.sellerName(),
                snapshot.deliveryProvider(),
                snapshot.sellerType(),
                snapshot.deliveryType(),
                snapshot.source(),
                snapshot.paymentConditions()
        );
    }
}