package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Ajuda específica dos comandos de avaliações.
 */
public final class EvaluationsCliUsage {

    private static final String TEXT =
        """
        Uso:
          rasping-amazon evaluations list [opcoes]
          rasping-amazon evaluations show <evaluation-id>

        Opcoes de list:
          --eligible <true|false>
          --asin <ASIN>
          --min-score <numero>
          --max-score <numero>
          --from <ISO-8601 offset date-time>
          --until <ISO-8601 offset date-time>
          --after-at <ISO-8601 offset date-time>
          --after-id <evaluation-id>
          --limit <1..200>

        Paginacao:
          --after-at e --after-id devem ser informados juntos.

        Exemplos:
          rasping-amazon evaluations list --eligible true --min-score 70 --limit 50
          rasping-amazon evaluations show 123
        """;

    private EvaluationsCliUsage() {
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
