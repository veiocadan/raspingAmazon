package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Ajuda específica dos comandos operacionais de Publication.
 */
public final class PublicationsCliUsage {

    private static final String TEXT =
        """
        Uso:
          rasping-amazon publications list [opcoes]

        Opcoes de list:
          --status <CREATED|READY|PUBLISHED|FAILED>
          --asin <ASIN>
          --evaluation-id <id>
          --from <ISO-8601 offset date-time>
          --until <ISO-8601 offset date-time>
          --after-at <ISO-8601 offset date-time>
          --after-id <publication-id>
          --limit <1..200>

        Paginacao:
          --after-at e --after-id devem ser informados juntos.

        Exemplos:
          rasping-amazon publications list
          rasping-amazon publications list --status CREATED
          rasping-amazon publications list --asin B0XXXXXXXX --limit 25
        """;

    private PublicationsCliUsage() {
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
