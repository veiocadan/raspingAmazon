package com.raspingamazon.application.publication.outbox;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Solicitação mínima para reservar uma Publication selecionada
 * na outbox.
 *
 * <p>Canal, destino, conteúdo, posição e quota não são recebidos
 * do chamador. Eles são reconstruídos a partir da SelectionRun,
 * SelectionDecision e Publication persistidas.</p>
 *
 * <p>cadenceProfileVersion identifica a política de cadência
 * utilizada para calcular availableAt.</p>
 *
 * <p>O valor pode ser null somente para compatibilidade com
 * reservas históricas/caminhos anteriores à introdução da
 * cadência. O fluxo automático novo deve sempre informar a
 * versão da cadência.</p>
 */
public record PublicationOutboxEnqueueRequest(
    long publicationId,
    long selectionRunId,
    String cadenceProfileVersion,
    OffsetDateTime availableAt,
    OffsetDateTime enqueuedAt
) {

    public PublicationOutboxEnqueueRequest {

        if (publicationId <= 0L) {

            throw new IllegalArgumentException(
                "publicationId must be positive"
            );
        }

        if (selectionRunId <= 0L) {

            throw new IllegalArgumentException(
                "selectionRunId must be positive"
            );
        }

        if (cadenceProfileVersion != null) {

            cadenceProfileVersion =
                cadenceProfileVersion.trim();

            if (cadenceProfileVersion.isEmpty()) {

                throw new IllegalArgumentException(
                    "cadenceProfileVersion must not be blank"
                );
            }
        }

        Objects.requireNonNull(
            availableAt,
            "availableAt must not be null"
        );

        Objects.requireNonNull(
            enqueuedAt,
            "enqueuedAt must not be null"
        );
    }

    /**
     * Construtor de compatibilidade com os testes e chamadas
     * anteriores à introdução da cadência.
     *
     * <p>Novos fluxos automáticos devem utilizar o construtor
     * canônico e informar cadenceProfileVersion.</p>
     */
    public PublicationOutboxEnqueueRequest(
        long publicationId,
        long selectionRunId,
        OffsetDateTime availableAt,
        OffsetDateTime enqueuedAt
    ) {

        this(
            publicationId,
            selectionRunId,
            null,
            availableAt,
            enqueuedAt
        );
    }

    public boolean cadenceManaged() {

        return cadenceProfileVersion != null;
    }
}
