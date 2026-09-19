package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.deal.port.ProductPersistencePort;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;
import com.raspingamazon.infrastructure.persistence.ProductRepository;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Adapter JDBC da porta ProductPersistencePort.
 *
 * <p>Traduz o contrato da aplicação para o repository de infraestrutura
 * e impede que SQLException atravesse a fronteira da aplicação.</p>
 */
public final class ProductJdbcPersistenceAdapter
        implements ProductPersistencePort {

    private final ProductRepository repository;

    public ProductJdbcPersistenceAdapter(
            ProductRepository repository
    ) {
        this.repository =
                Objects.requireNonNull(
                        repository,
                        "repository must not be null"
                );
    }

    @Override
    public Product upsert(
            ParsedDeal parsedDeal
    ) {
        Objects.requireNonNull(
                parsedDeal,
                "parsedDeal must not be null"
        );

        try {
            return repository.upsert(
                    parsedDeal.asin(),
                    parsedDeal.title(),
                    parsedDeal.imageUrl(),
                    parsedDeal.productUrl()
            );

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                    "Failed to upsert Product for ASIN "
                            + parsedDeal.asin(),
                    exception
            );
        }
    }
}