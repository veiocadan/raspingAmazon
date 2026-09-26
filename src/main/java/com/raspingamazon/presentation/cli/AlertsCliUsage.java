package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Ajuda especifica do recurso de alertas operacionais.
 */
public final class AlertsCliUsage {

    private static final String TEXT =
        """
        Uso:
          rasping-amazon alerts list

        Comportamento:
          Avalia uma unica vez os fatos operacionais persistidos
          e imprime os alertas ativos.

        Alertas atuais:
          REPEATED_EXTERNAL_FAILURES
          DEAD_JOBS
          ZERO_CANDIDATES
          SUSPICIOUS_COLLECTION_DROP

        Configuracao obrigatoria para alerts list:
          ALERT_REPEATED_EXTERNAL_FAILURE_THRESHOLD
          ALERT_REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS
          ALERT_SUSPICIOUS_COLLECTION_LOOKBACK_RUNS
          ALERT_SUSPICIOUS_COLLECTION_DROP_FRACTION
          ALERT_SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES

        Observacao:
          Nenhum limiar numerico possui default implicito.
          O comando nao executa polling, scheduler ou monitoramento
          continuo.

        Exemplos:
          rasping-amazon alerts list
          rasping-amazon alerts help
        """;

    private AlertsCliUsage() {
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
