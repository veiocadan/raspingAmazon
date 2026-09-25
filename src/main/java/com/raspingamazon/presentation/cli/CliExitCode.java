package com.raspingamazon.presentation.cli;

/**
 * Códigos de saída públicos da interface operacional.
 *
 * <p>O motor da CLI trabalha com este enum em vez de inteiros
 * espalhados pelos handlers.</p>
 */
public enum CliExitCode {

    SUCCESS(0),

    OPERATIONAL_ERROR(1),

    USAGE_ERROR(2),

    NOT_FOUND(3);

    private final int code;

    CliExitCode(
        int code
    ) {

        this.code =
            code;
    }

    public int code() {
        return code;
    }
}
