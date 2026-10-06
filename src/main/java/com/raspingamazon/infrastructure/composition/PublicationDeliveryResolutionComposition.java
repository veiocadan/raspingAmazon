package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionService;
import com.raspingamazon.application.publication.outbox.resolution.port.PublicationDeliveryResolutionPort;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationDeliveryResolutionAdapter;

import java.sql.Connection;
import java.time.Clock;
import java.util.Objects;

/**
 * Composition root da resolução operacional de publication_outbox
 * em DELIVERY_UNKNOWN.
 *
 * <p>A interface operacional deve consumir o caso de uso retornado
 * por esta composition. Ela não deve conhecer JDBC nem alterar
 * diretamente publication_outbox ou publication_attempt.</p>
 *
 * <p>A resolução permanece separada do retry automático e do worker.
 * Esta composition apenas conecta:</p>
 *
 * <pre>
 * Connection
 *     ↓
 * JdbcPublicationDeliveryResolutionAdapter
 *     ↓
 * PublicationDeliveryResolutionService
 * </pre>
 */
public final class PublicationDeliveryResolutionComposition {

    private PublicationDeliveryResolutionComposition() {
    }

    public static PublicationDeliveryResolutionService create(
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

        PublicationDeliveryResolutionPort resolutionPort =
            new JdbcPublicationDeliveryResolutionAdapter(
                validatedConnection
            );

        return new PublicationDeliveryResolutionService(
            resolutionPort,
            validatedClock
        );
    }
}
