package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Texto de ajuda top-level da interface operacional.
 */
public final class OperationalCliUsage {

    private static final String TEXT =
        """
        Rasping Amazon - interface operacional

        Uso:
          rasping-amazon <recurso> <acao> [opcoes]
          rasping-amazon help

        Recursos:
          evaluations
              list
              show <evaluation-id>

          runs
              list
              show <run-id>

          jobs
              list

          publications
              list
              show <publication-id>

          alerts
              list

        Ajuda:
          help
          --help
          -h

        Observacao:
          A interface operacional observa o pipeline.
          Ela nao autoriza o pipeline a funcionar e nao executa
          aprovacao humana obrigatoria.

          alerts list realiza apenas uma avaliacao do estado
          persistido. Ele nao inicia monitoramento continuo.
        """;

    private OperationalCliUsage() {
    }

    public static String text() {
        return TEXT;
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
