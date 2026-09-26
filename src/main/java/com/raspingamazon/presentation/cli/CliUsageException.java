package com.raspingamazon.presentation.cli;

import java.util.Objects;

/**
 * Indica erro sintático ou semântico nos argumentos de um comando.
 *
 * <p>É diferente de falha operacional. Argumentos inválidos devem
 * resultar em CliExitCode.USAGE_ERROR e não em erro de
 * infraestrutura.</p>
 */
public final class CliUsageException
    extends RuntimeException {

    public CliUsageException(
        String message
    ) {

        super(
            requireMessage(
                message
            )
        );
    }

    private static String requireMessage(
        String message
    ) {

        Objects.requireNonNull(
            message,
            "message must not be null"
        );

        if (message.isBlank()) {
            throw new IllegalArgumentException(
                "message must not be blank"
            );
        }

        return message;
    }
}
