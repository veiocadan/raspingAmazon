package com.raspingamazon.presentation.cli;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Ajuda específica dos comandos operacionais de ProcessingJob.
 */
public final class JobsCliUsage {

    private static final String TEXT =
        """
        Uso:
          rasping-amazon jobs list [opcoes]

        Opcoes de list:
          --type <COLLECT_DEALS|ENRICH_DEAL|EVALUATE_DEAL>
          --status <PENDING|RUNNING|RETRY_WAIT|SUCCEEDED|DEAD>
          --failure-type <TRANSIENT|PERMANENT>
          --run-id <id>
          --candidate-id <id>
          --snapshot-id <id>
          --from <ISO-8601 offset date-time>
          --until <ISO-8601 offset date-time>
          --after-at <ISO-8601 offset date-time>
          --after-id <job-id>
          --limit <1..200>

        Paginacao:
          --after-at e --after-id devem ser informados juntos.

        Observacao:
          --run-id filtra somente o processing_run_id persistido
          diretamente no job. Ele nao executa rastreamento indireto
          de toda a linhagem do run.

        Exemplos:
          rasping-amazon jobs list
          rasping-amazon jobs list --status RETRY_WAIT
          rasping-amazon jobs list --type EVALUATE_DEAL --limit 25
          rasping-amazon jobs list --failure-type TRANSIENT
        """;

    private JobsCliUsage() {
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
