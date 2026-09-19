package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.deal.port.PaymentConditionPersistencePort;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.infrastructure.persistence.OfferPaymentConditionRepository;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Adapter JDBC da porta de persistência de condições comerciais.
 *
 * <p>A camada de aplicação trabalha com uma lista de PaymentCondition.
 * O repository JDBC atual persiste cada condição individualmente.
 * Este adapter faz essa tradução.</p>
 */
public final class PaymentConditionJdbcPersistenceAdapter
        implements PaymentConditionPersistencePort {

    private final OfferPaymentConditionRepository repository;

    public PaymentConditionJdbcPersistenceAdapter(
            OfferPaymentConditionRepository repository
    ) {
        this.repository =
                Objects.requireNonNull(
                        repository,
                        "repository must not be null"
                );
    }

    @Override
    public void saveAll(
            long offerSnapshotId,
            List<PaymentCondition> conditions
    ) {
        Objects.requireNonNull(
                conditions,
                "conditions must not be null"
        );

        if (offerSnapshotId <= 0) {
            throw new IllegalArgumentException(
                    "offerSnapshotId must be positive"
            );
        }

        try {
            for (PaymentCondition condition : conditions) {

                Objects.requireNonNull(
                        condition,
                        "Payment condition must not be null"
                );

                repository.insert(
                        offerSnapshotId,
                        condition
                );
            }

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                    "Failed to persist payment conditions for OfferSnapshot "
                            + offerSnapshotId,
                    exception
            );
        }
    }
}