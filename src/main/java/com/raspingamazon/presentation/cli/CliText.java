package com.raspingamazon.presentation.cli;

import com.raspingamazon.domain.shared.Money;

import java.math.BigDecimal;

/**
 * Formatação textual compartilhada pela CLI operacional.
 *
 * <p>Não contém regra de negócio. Apenas transforma valores já
 * calculados pela aplicação em células seguras para saída textual.</p>
 */
public final class CliText {

    private static final String EMPTY_VALUE = "-";

    private CliText() {
    }

    public static String text(
        String value
    ) {

        if (value == null) {
            return EMPTY_VALUE;
        }

        return sanitize(
            value
        );
    }

    public static String decimal(
        BigDecimal value
    ) {

        if (value == null) {
            return EMPTY_VALUE;
        }

        return value.toPlainString();
    }

    public static String money(
        Money value
    ) {

        if (value == null) {
            return EMPTY_VALUE;
        }

        return value.amount()
            .toPlainString();
    }

    public static String enumName(
        Enum<?> value
    ) {

        if (value == null) {
            return EMPTY_VALUE;
        }

        return value.name();
    }

    private static String sanitize(
        String value
    ) {

        return value
            .replace(
                '\t',
                ' '
            )
            .replace(
                '\r',
                ' '
            )
            .replace(
                '\n',
                ' '
            );
    }
}
