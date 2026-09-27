package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Ajuda específica dos comandos operacionais de scheduling.
 */
public final class SchedulesCliUsage {

    private static final String TEXT =
        """
        Uso:
          rasping-amazon schedules status <schedule-key>
          rasping-amazon schedules pause <schedule-key>
          rasping-amazon schedules resume <schedule-key>
          rasping-amazon schedules interval <schedule-key> <duration>
          rasping-amazon schedules help

        Acoes:
          status
              Exibe o estado persistido do agendamento.

          pause
              Suspende novas execucoes automaticas.
              Uma execucao que ja possui lease nao e cancelada.

          resume
              Retoma o agendamento.
              A proxima janela torna-se elegivel imediatamente.

          interval
              Altera a frequencia do agendamento.
              A proxima janela sera calculada a partir do instante
              da alteracao.

        Duracao:
          Utilize o formato ISO-8601 de java.time.Duration.

        Exemplos:
          PT30S
          PT15M
          PT2H
          P1D
        """;

    private SchedulesCliUsage() {
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
