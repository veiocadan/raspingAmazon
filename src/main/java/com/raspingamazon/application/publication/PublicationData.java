package com.raspingamazon.application.publication;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.product.Product;

import java.util.List;
import java.util.Objects;

/**
 * Representa os dados persistidos que podem alimentar
 * a geração de uma Publication.
 *
 * <p>PublicationData não duplica os campos já existentes no domínio.
 * Em vez disso, preserva o grafo:</p>
 *
 * <pre>
 * DealEvaluation
 *      ↓
 * OfferSnapshot
 *      ↓
 * Product
 *      ↓
 * PaymentCondition[]
 * </pre>
 *
 * <p>Esse objeto pertence à camada de aplicação. Sua responsabilidade
 * é declarar que a geração de publicação trabalha somente com uma
 * avaliação e seus dados de origem já persistidos.</p>
 *
 * <p>Ele não conhece:</p>
 *
 * <ul>
 *     <li>SQL;</li>
 *     <li>JDBC;</li>
 *     <li>Amazon HTML;</li>
 *     <li>templates concretos;</li>
 *     <li>links de associado;</li>
 *     <li>Telegram;</li>
 *     <li>WhatsApp.</li>
 * </ul>
 */
public record PublicationData(
    DealEvaluation dealEvaluation
) {

    /**
     * Valida que todo o núcleo auditável da publicação já possui
     * identidade persistente.
     *
     * <p>A FASE 13 não deve gerar publicação a partir de objetos
     * transitórios que ainda não possam ser relacionados de forma
     * inequívoca ao histórico armazenado.</p>
     */
    public PublicationData {

        Objects.requireNonNull(
            dealEvaluation,
            "dealEvaluation must not be null"
        );

        requirePersistedId(
            dealEvaluation.id(),
            "DealEvaluation must be persisted before publication generation"
        );

        OfferSnapshot offerSnapshot =
            Objects.requireNonNull(
                dealEvaluation.offerSnapshot(),
                "DealEvaluation offerSnapshot must not be null"
            );

        requirePersistedId(
            offerSnapshot.id(),
            "OfferSnapshot must be persisted before publication generation"
        );

        Product product =
            Objects.requireNonNull(
                offerSnapshot.product(),
                "OfferSnapshot product must not be null"
            );

        requirePersistedId(
            product.id(),
            "Product must be persisted before publication generation"
        );
    }

    /**
     * Retorna a identidade persistente da avaliação.
     */
    public long dealEvaluationId() {
        return dealEvaluation.id();
    }

    /**
     * Retorna o snapshot que originou a avaliação.
     */
    public OfferSnapshot offerSnapshot() {
        return dealEvaluation.offerSnapshot();
    }

    /**
     * Retorna a identidade persistente do snapshot.
     */
    public long offerSnapshotId() {
        return offerSnapshot().id();
    }

    /**
     * Retorna o produto associado ao snapshot.
     */
    public Product product() {
        return offerSnapshot().product();
    }

    /**
     * Retorna a identidade persistente do produto.
     */
    public long productId() {
        return product().id();
    }

    /**
     * Retorna as condições comerciais observadas para o snapshot.
     *
     * <p>A lista permanece imutável porque OfferSnapshot já protege
     * essa invariável no domínio.</p>
     */
    public List<PaymentCondition> paymentConditions() {
        return offerSnapshot().paymentConditions();
    }

    /**
     * Garante que uma identidade represente uma entidade já persistida.
     */
    private static void requirePersistedId(
        Long id,
        String message
    ) {

        if (id == null || id <= 0L) {
            throw new IllegalArgumentException(
                message
            );
        }
    }
}
