package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Objects;

/** Ajuda dos comandos operacionais de agendamento. */
public final class SchedulesCliUsage {
    private static final String TEXT = """
        Uso:
          rasping-amazon schedules status <schedule-key>
          rasping-amazon schedules pause <schedule-key> [--reason <text>]
          rasping-amazon schedules resume <schedule-key> [--reason <text>]
          rasping-amazon schedules interval <schedule-key> <duration> [--reason <text>]
          rasping-amazon schedules help

        Acoes:
          status: consulta o estado persistido.
          pause: suspende novas execucoes sem cancelar leases existentes.
          resume: retoma e torna a proxima janela elegivel imediatamente.
          interval: altera a frequencia a partir do instante da mudanca.

        Auditoria:
          --reason registra o motivo ou referencia da alteracao.
          Sem --reason, o comando historico e registrado como referencia.
          O ator local e informativo, nao representa autenticacao forte.

        Duracao: ISO-8601 (PT30S, PT15M, PT2H, P1D).
        """;

    private SchedulesCliUsage() { }

    public static String text() { return TEXT; }

    public static void print(PrintWriter writer) {
        Objects.requireNonNull(writer, "writer must not be null");
        writer.print(TEXT);
        writer.flush();
    }
}
