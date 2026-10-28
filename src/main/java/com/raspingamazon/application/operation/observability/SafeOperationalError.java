package com.raspingamazon.application.operation.observability;

import java.util.Objects;

/**
 * Representação segura de falhas operacionais em interfaces externas.
 *
 * <p>Mensagens arbitrárias de exceções podem incorporar credenciais JDBC,
 * tokens, URLs autenticadas ou informações de fornecedores. Esta classe
 * deliberadamente não consulta getMessage(), getLocalizedMessage(),
 * toString() nem a cadeia de causas.</p>
 *
 * <p>O tipo Java de uma exceção é apenas um indício diagnóstico seguro;
 * não substitui a taxonomia persistida de falhas da aplicação.</p>
 */
public final class SafeOperationalError {

    private static final String DETAILS_SUPPRESSED =
        " (details suppressed)";

    private static final String UNKNOWN_FAILURE_TYPE =
        "RuntimeException";

    private SafeOperationalError() {
    }

    public static String describe(Throwable failure) {

        Objects.requireNonNull(
            failure,
            "failure must not be null"
        );

        String typeName =
            failure.getClass().getSimpleName();

        if (!isSafeTypeName(typeName)) {
            typeName = UNKNOWN_FAILURE_TYPE;
        }

        return typeName + DETAILS_SUPPRESSED;
    }

    private static boolean isSafeTypeName(String name) {

        if (name == null || name.isBlank() || name.length() > 100) {
            return false;
        }

        for (int index = 0; index < name.length(); index++) {

            char character = name.charAt(index);

            boolean valid = Character.isLetterOrDigit(character)
                || character == '_'
                || character == '$';

            if (!valid) {
                return false;
            }
        }

        return Character.isLetter(name.charAt(0))
            || name.charAt(0) == '_';
    }
}
