package com.raspingamazon.application.deal.port;

import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.product.Product;

/**
 * Porta de persistência de Product usada pelo fluxo de aplicação.
 *
 * <p>A aplicação não deve conhecer JDBC, SQL ou PostgreSQL.</p>
 *
 * <p>A operação é definida como upsert porque o mesmo ASIN pode
 * aparecer em diferentes coletas. Product representa a identidade
 * durável do produto, não uma ocorrência temporal da oferta.</p>
 */
public interface ProductPersistencePort {

    /**
     * Insere ou atualiza a identidade conhecida do produto.
     *
     * @param parsedDeal dados atuais observados pelo parser
     * @return Product persistido, sempre com id
     */
    Product upsert(ParsedDeal parsedDeal);
}