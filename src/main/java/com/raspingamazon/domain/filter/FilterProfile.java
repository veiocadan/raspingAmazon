package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa uma versão imutável da configuração dos filtros
 * comerciais aplicáveis às ofertas.
 *
 * O perfil define somente critérios comerciais eliminatórios.
 *
 * Não pertencem a este perfil:
 *
 * - validação de vendedor Amazon;
 * - validação de entrega Amazon;
 * - soldPercentage;
 * - score;
 * - momentum;
 * - regras de apresentação/publicação.
 *
 * A validação de vendedor e entrega pertence à política estrutural
 * de elegibilidade.
 *
 * soldPercentage permanece disponível como dado da oferta, mas foi
 * reservado para uso futuro no score e não deve eliminar ofertas.
 *
 * O desconto mínimo representa exclusivamente desconto à vista
 * explicitamente informado em uma PaymentCondition reconhecida.
 *
 * Esta classe não define de onde a configuração é carregada.
 * Persistência, variáveis de ambiente ou outra fonte de configuração
 * pertencem às camadas externas ao domínio.
 */
public record FilterProfile(
    String version,
    Percentage minCashDiscountPercentage,
    BigDecimal minRating,
    long minReviewCount
) {

    private static final BigDecimal MIN_RATING =
        BigDecimal.ZERO;

    private static final BigDecimal MAX_RATING =
        new BigDecimal("5");

    /**
     * Valida as invariantes do perfil.
     *
     * Todos os filtros existentes nesta versão possuem um limite
     * explícito. Um limite igual a zero é válido e, na prática,
     * permite configurar um critério sem torná-lo restritivo.
     */
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

        Objects.requireNonNull(
            minCashDiscountPercentage,
            "minCashDiscountPercentage must not be null"
        );

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
}
