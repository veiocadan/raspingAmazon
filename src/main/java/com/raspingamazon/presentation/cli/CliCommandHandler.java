package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.List;

/**
 * Handler de um comando top-level da interface operacional.
 *
 * <p>Recebe somente os argumentos restantes depois do nome
 * do comando.</p>
 *
 * <p>A interface deliberadamente não conhece JDBC,
 * Connection ou adapters concretos.</p>
 */
@FunctionalInterface
public interface CliCommandHandler {

    CliExitCode execute(
        List<String> arguments,
        PrintWriter out,
        PrintWriter err
    );
}
