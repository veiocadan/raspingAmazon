package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Ajuda específica dos comandos operacionais de ProcessingRun.
 */
public final class RunsCliUsage {

    private static final String TEXT =
        """
        Uso:
          rasping-amazon runs list [opcoes]

        Opcoes de list:
          --status <PENDING|RUNNING|COMPLETED|FAILED>
          --from <ISO-8601 offset date-time>
          --until <ISO-8601 offset date-time>
          --after-at <ISO-8601 offset date-time>
          --after-id <run-id>
          --limit <1..200>

        Paginacao:
          --after-at e --after-id devem ser informados juntos.

        Exemplos:
          rasping-amazon runs list
          rasping-amazon runs list --status FAILED
          rasping-amazon runs list --from 2026-09-24T00:00:00-03:00 --limit 25
        """;

    private RunsCliUsage() {
    }

    public static void print(
        PrintWriter writer
    ) {

        Objects.requireNonNull(
            writer,
            "writer must not be null"
        );

        writer.print(
            TEXT
        );

        writer.flush();
    }
}
