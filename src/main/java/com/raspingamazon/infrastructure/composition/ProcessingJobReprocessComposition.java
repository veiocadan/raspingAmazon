package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessService;
import com.raspingamazon.application.orchestration.reprocess.port.ProcessingJobReprocessPort;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobReprocessAdapter;

import java.sql.Connection;
import java.time.Clock;
import java.util.Objects;

/**
 * Composition root do reprocessamento operacional controlado de
 * ProcessingJob em dead-letter lógico.
 *
 * <p>A interface operacional deve consumir o caso de uso retornado
 * por esta composition. Ela não deve conhecer JDBC nem manipular
 * diretamente a tabela processing_job.</p>
 *
 * <p>O reprocessamento continua separado do retry automático do
 * worker. Esta composition apenas conecta:</p>
 *
 * <pre>
 * Connection
 *     ↓
 * JdbcProcessingJobReprocessAdapter
 *     ↓
 * ProcessingJobReprocessService
 * </pre>
 */
public final class ProcessingJobReprocessComposition {

    private ProcessingJobReprocessComposition() {
    }

    public static ProcessingJobReprocessService create(
        Connection connection,
        Clock clock
    ) {

        Connection validatedConnection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        Clock validatedClock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        ProcessingJobReprocessPort reprocessPort =
            new JdbcProcessingJobReprocessAdapter(
                validatedConnection
            );

        return new ProcessingJobReprocessService(
            reprocessPort,
            validatedClock
        );
    }
}
