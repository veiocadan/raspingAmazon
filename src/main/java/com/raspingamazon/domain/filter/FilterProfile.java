package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa uma versão imutável da configuração dos filtros
 * comerciais aplicáveis às ofertas.
 *
 * <p>Um perfil utiliza exatamente uma semântica de desconto:</p>
 *
 * <ul>
 *     <li>minCashDiscountPercentage para COMMERCIAL_FILTER_V1;</li>
 *     <li>minBasisDiscountPercentage para perfis da ADR-0005.</li>
 * </ul>
 */
public record FilterProfile(
    String version,
    Percentage minCashDiscountPercentage,
    Percentage minBasisDiscountPercentage,
    BigDecimal minRating,
    long minReviewCount
) {

    private static final BigDecimal MIN_RATING =
        BigDecimal.ZERO;

    private static final BigDecimal MAX_RATING =
        new BigDecimal("5");

    /**
     * Construtor compatível com COMMERCIAL_FILTER_V1.
     */
    public FilterProfile(
        String version,
        Percentage minCashDiscountPercentage,
        BigDecimal minRating,
        long minReviewCount
    ) {
        this(
            version,
            requireLegacyCashThreshold(
                minCashDiscountPercentage
            ),
            null,
            minRating,
            minReviewCount
        );
    }

    public FilterProfile {

        Objects.requireNonNull(
            version,
            "version must not be null"
        );

        if (version.isBlank()) {
            throw new IllegalArgumentException(
                "version must not be blank"
            );
        }

        boolean hasCashThreshold =
            minCashDiscountPercentage != null;

        boolean hasBasisThreshold =
            minBasisDiscountPercentage != null;

        if (hasCashThreshold == hasBasisThreshold) {
            throw new IllegalArgumentException(
                "FilterProfile must define exactly one discount threshold"
            );
        }

        Objects.requireNonNull(
            minRating,
            "minRating must not be null"
        );

        if (minRating.compareTo(MIN_RATING) < 0) {
            throw new IllegalArgumentException(
                "minRating must not be lower than 0"
            );
        }

        if (minRating.compareTo(MAX_RATING) > 0) {
            throw new IllegalArgumentException(
                "minRating must not be greater than 5"
            );
        }

        if (minReviewCount < 0) {
            throw new IllegalArgumentException(
                "minReviewCount must not be negative"
            );
        }
    }

    public static FilterProfile forBasisDiscount(
        String version,
        Percentage minBasisDiscountPercentage,
        BigDecimal minRating,
        long minReviewCount
    ) {
        return new FilterProfile(
            version,
            null,
            Objects.requireNonNull(
                minBasisDiscountPercentage,
                "minBasisDiscountPercentage must not be null"
            ),
            minRating,
            minReviewCount
        );
    }

    public boolean usesCashDiscountRule() {
        return minCashDiscountPercentage != null;
    }

    public boolean usesBasisDiscountRule() {
        return minBasisDiscountPercentage != null;
    }

    private static Percentage requireLegacyCashThreshold(
        Percentage value
    ) {
        return Objects.requireNonNull(
            value,
            "minCashDiscountPercentage must not be null"
        );
    }
}
