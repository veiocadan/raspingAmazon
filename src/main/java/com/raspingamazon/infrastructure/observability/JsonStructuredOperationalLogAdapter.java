package com.raspingamazon.infrastructure.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.observability.OperationalLogEvent;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;

import java.io.PrintWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Adapter de logging operacional estruturado em JSON Lines.
 *
 * <p>Cada chamada produz exatamente um objeto JSON em uma linha.</p>
 *
 * <p>O formato foi mantido deliberadamente simples e independente
 * de frameworks de logging. Isso permite substituir o destino
 * posteriormente sem alterar a aplicação ou o domínio.</p>
 *
 * <p>Campos opcionais ausentes não são serializados como null.
 * Isso reduz ruído sem mudar a semântica do evento.</p>
 */
public final class JsonStructuredOperationalLogAdapter
    implements StructuredOperationalLogPort {

    private final PrintWriter writer;

    private final Clock clock;

    private final ObjectMapper objectMapper;

    public JsonStructuredOperationalLogAdapter(
        PrintWriter writer,
        Clock clock
    ) {

        this.writer =
            Objects.requireNonNull(
                writer,
                "writer must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.objectMapper =
            new ObjectMapper();
    }

    /**
     * Serializa uma unidade lógica completa de log.
     *
     * <p>O método é synchronized para impedir que múltiplos workers
     * concorrentes produzam campos da mesma linha de maneira
     * intercalada.</p>
     */
    @Override
    public synchronized void log(
        OperationalLogEvent event
    ) {

        Objects.requireNonNull(
            event,
            "event must not be null"
        );

        ObjectNode json =
            objectMapper.createObjectNode();

        json.put(
            "timestamp",
            OffsetDateTime.now(
                clock
            ).toString()
        );

        json.put(
            "level",
            event.level()
                .name()
        );

        json.put(
            "event",
            event.event()
        );

        json.put(
            "component",
            event.component()
        );

        json.put(
            "operation",
            event.operation()
        );

        appendContext(
            json,
            event.context()
        );

        json.put(
            "outcome",
            event.outcome()
        );

        if (event.durationMs() != null) {

            json.put(
                "durationMs",
                event.durationMs()
            );
        }

        if (event.failureOrigin() != null) {

            json.put(
                "failureOrigin",
                event.failureOrigin()
                    .name()
            );

            json.put(
                "failureType",
                event.failureType()
                    .name()
            );

            json.put(
                "errorCode",
                event.errorCode()
            );
        }

        writer.println(
            json
        );
    }

    private void appendContext(
        ObjectNode json,
        OperationalLogContext context
    ) {

        if (context.runId() != null) {

            json.put(
                "runId",
                context.runId()
            );
        }

        if (context.jobId() != null) {

            json.put(
                "jobId",
                context.jobId()
            );
        }

        if (context.jobType() != null) {

            json.put(
                "jobType",
                context.jobType()
                    .name()
            );
        }

        if (context.candidateId() != null) {

            json.put(
                "candidateId",
                context.candidateId()
            );
        }

        if (context.snapshotId() != null) {

            json.put(
                "snapshotId",
                context.snapshotId()
            );
        }

        if (context.evaluationId() != null) {

            json.put(
                "evaluationId",
                context.evaluationId()
            );
        }

        if (context.publicationId() != null) {

            json.put(
                "publicationId",
                context.publicationId()
            );
        }

        if (context.asin() != null) {

            json.put(
                "asin",
                context.asin()
            );
        }

        if (context.integration() != null) {

            json.put(
                "integration",
                context.integration()
            );
        }
    }
}
