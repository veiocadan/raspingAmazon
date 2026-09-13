package com.raspingamazon.domain.shared;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa um percentual no domínio.
 *
 * O valor é armazenado como número entre 0 e 100.
 *
 * Exemplos:
 *
 *     0    = 0%
 *     48   = 48%
 *     83.5 = 83,5%
 *     100  = 100%
 *
 * A classe não sabe se o percentual representa desconto, vendas,
 * evolução ou qualquer outra métrica. Essa interpretação pertence
 * ao conceito de negócio que utilizará o Percentage.
 */
public record Percentage(BigDecimal value) {

    public Percentage {
        Objects.requireNonNull(value, "Percentage value must not be null");

        /*
         * Um percentual válido para o domínio não pode ser negativo.
         */
        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Percentage value must not be negative"
            );
        }

        /*
         * O domínio trabalha com percentuais de 0% a 100%.
         */
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Percentage value must not exceed 100"
            );
        }
    }

    /**
     * Cria um percentual a partir de uma representação decimal.
     *
     * Exemplos:
     *
     *     Percentage.of("48")
     *     Percentage.of("83.5")
     */
    public static Percentage of(String value) {
        Objects.requireNonNull(value, "Percentage value must not be null");

        return new Percentage(new BigDecimal(value));
    }
}