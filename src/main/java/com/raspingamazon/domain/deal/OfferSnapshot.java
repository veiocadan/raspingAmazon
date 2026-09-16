package com.raspingamazon.domain.deal;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Representa uma fotografia de uma oferta em um determinado momento.
 *
 * Diferentemente de Product, que representa a identidade do produto,
 * OfferSnapshot representa uma ocorrência temporal dos dados observados
 * durante uma coleta.
 *
 * A entidade pertence exclusivamente ao domínio:
 *
 * - não conhece PostgreSQL;
 * - não conhece JDBC;
 * - não conhece HTML;
 * - não conhece a API da Amazon;
 * - não conhece Excel;
 * - não conhece canais de publicação.
 *
 * As condições comerciais de pagamento pertencem ao snapshot, mas são
 * representadas por PaymentCondition e persistidas separadamente.
 */
public final class OfferSnapshot {

    private final Long id;

    private final Product product;

    private final OffsetDateTime collectedAt;

    /**
     * Principal preço comercial observado na coleta.
     */
    private final Money currentPrice;

    /**
     * Preço-base/lista observado na fonte, quando disponível.
     *
     * Não deve ser automaticamente tratado como previousPrice.
     */
    private final Money basisPrice;

    /**
     * Preço anterior, quando houver evidência explícita dessa informação.
     */
    private final Money previousPrice;

    /**
     * Percentual vendido/reivindicado, quando disponível.
     *
     * null significa que a fonte não forneceu informação suficiente.
     */
    private final Percentage soldPercentage;

    private final Double rating;

    private final Long reviewCount;

    private final String sellerName;

    private final String deliveryProvider;

    private final SellerType sellerType;

    private final DeliveryType deliveryType;

    /**
     * Identifica a origem dos dados coletados.
     */
    private final String source;

    /**
     * Condições comerciais observadas para o snapshot.
     *
     * A lista pode ser vazia quando nenhuma condição de pagamento
     * foi observada ou quando essa informação ainda não está disponível.
     */
    private final List<PaymentCondition> paymentConditions;

    public OfferSnapshot(
            Long id,
            Product product,
            OffsetDateTime collectedAt,
            Money currentPrice,
            Money basisPrice,
            Money previousPrice,
            Percentage soldPercentage,
            Double rating,
            Long reviewCount,
            String sellerName,
            String deliveryProvider,
            SellerType sellerType,
            DeliveryType deliveryType,
            String source,
            List<PaymentCondition> paymentConditions
    ) {
        this.id = id;

        this.product = Objects.requireNonNull(
                product,
                "OfferSnapshot product must not be null"
        );

        this.collectedAt = Objects.requireNonNull(
                collectedAt,
                "OfferSnapshot collectedAt must not be null"
        );

        this.currentPrice = Objects.requireNonNull(
                currentPrice,
                "OfferSnapshot currentPrice must not be null"
        );

        this.basisPrice = basisPrice;

        this.previousPrice = previousPrice;

        this.soldPercentage = soldPercentage;

        this.rating = rating;

        this.reviewCount = reviewCount;

        this.sellerName = sellerName;

        this.deliveryProvider = deliveryProvider;

        this.sellerType = Objects.requireNonNull(
                sellerType,
                "OfferSnapshot sellerType must not be null"
        );

        this.deliveryType = Objects.requireNonNull(
                deliveryType,
                "OfferSnapshot deliveryType must not be null"
        );

        this.source = requireText(
                source,
                "OfferSnapshot source must not be blank"
        );

        Objects.requireNonNull(
                paymentConditions,
                "OfferSnapshot paymentConditions must not be null"
        );

        this.paymentConditions = List.copyOf(paymentConditions);
    }

    private static String requireText(
            String value,
            String message
    ) {
        Objects.requireNonNull(value, message);

        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    public Long id() {
        return id;
    }

    public Product product() {
        return product;
    }

    public OffsetDateTime collectedAt() {
        return collectedAt;
    }

    public Money currentPrice() {
        return currentPrice;
    }

    public Money basisPrice() {
        return basisPrice;
    }

    public Money previousPrice() {
        return previousPrice;
    }

    public Percentage soldPercentage() {
        return soldPercentage;
    }

    public Double rating() {
        return rating;
    }

    public Long reviewCount() {
        return reviewCount;
    }

    public String sellerName() {
        return sellerName;
    }

    public String deliveryProvider() {
        return deliveryProvider;
    }

    public SellerType sellerType() {
        return sellerType;
    }

    public DeliveryType deliveryType() {
        return deliveryType;
    }

    public String source() {
        return source;
    }

    public List<PaymentCondition> paymentConditions() {
        return paymentConditions;
    }
}