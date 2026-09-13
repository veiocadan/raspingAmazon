package com.raspingamazon.domain.shared;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa um valor monetário dentro do domínio.
 *
 * O domínio utiliza BigDecimal para evitar os problemas de precisão
 * associados à representação de valores monetários com float ou double.
 *
 * A classe não conhece banco de dados, moeda específica da infraestrutura
 * ou qualquer detalhe de persistência.
 */
public record Money(BigDecimal amount) {

    public Money {
        Objects.requireNonNull(amount, "Money amount must not be null");

        /*
         * Valores monetários negativos não fazem sentido para os preços
         * tratados pelo projeto. A validação pertence ao domínio para que
         * entidades futuras não dependam exclusivamente do banco.
         */
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount must not be negative");
        }
    }

    /**
     * Cria um valor monetário a partir de uma representação decimal.
     *
     * Este método facilita a criação dos objetos nos testes e nos casos
     * de uso sem espalhar a construção de BigDecimal pelo código.
     */
    public static Money of(String value) {
        Objects.requireNonNull(value, "Money value must not be null");

        return new Money(new BigDecimal(value));
    }
}