package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.PublicationGenerationUseCase;
import com.raspingamazon.application.publication.PublicationReadinessUseCase;
import com.raspingamazon.application.publication.PublicationSelectionDispatchService;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.application.publication.scheduling.PublicationCadenceSlotPlanner;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationCadenceProfileProvider;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationCadenceReservationQueryAdapter;

import java.sql.Connection;
import java.time.Clock;
import java.util.Objects;

/**
 * Composition root do dispatch automático de Publications
 * selecionadas com cadência persistente.
 *
 * <p>Responsabilidades desta composição:</p>
 *
 * <pre>
 * JDBC cadence profile
 *          +
 * JDBC reservation history
 *          +
 * cadence slot planner
 *          ↓
 * PublicationSelectionDispatchService
 * </pre>
 *
 * <p>Esta classe deliberadamente não constrói:</p>
 *
 * <ul>
 *     <li>PublicationGenerator;</li>
 *     <li>PublicationReadinessUseCase;</li>
 *     <li>PublicationOutboxEnqueuePort.</li>
 * </ul>
 *
 * <p>Essas dependências pertencem a composições próprias e são
 * recebidas explicitamente para evitar duplicação de wiring.</p>
 *
 * <p>A mesma Connection é compartilhada pelos adapters JDBC.</p>
 */
public final class PublicationSelectionDispatchComposition {

    private PublicationSelectionDispatchComposition() {
    }

    /**
     * Composição utilizando relógio de produção.
     */
    public static PublicationSelectionDispatchService create(
        Connection connection,
        PublicationGenerationUseCase publicationGenerationUseCase,
        PublicationReadinessUseCase publicationReadinessUseCase,
        PublicationOutboxEnqueuePort publicationOutboxEnqueuePort
    ) {

        return create(
            connection,
            publicationGenerationUseCase,
            publicationReadinessUseCase,
            publicationOutboxEnqueuePort,
            Clock.systemUTC()
        );
    }

    /**
     * Variante com Clock explícito para testes e composition roots
     * superiores.
     */
    public static PublicationSelectionDispatchService create(
        Connection connection,
        PublicationGenerationUseCase publicationGenerationUseCase,
        PublicationReadinessUseCase publicationReadinessUseCase,
        PublicationOutboxEnqueuePort publicationOutboxEnqueuePort,
        Clock clock
    ) {

        Connection validatedConnection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        PublicationGenerationUseCase validatedGenerationUseCase =
            Objects.requireNonNull(
                publicationGenerationUseCase,
                "publicationGenerationUseCase must not be null"
            );

        PublicationReadinessUseCase validatedReadinessUseCase =
            Objects.requireNonNull(
                publicationReadinessUseCase,
                "publicationReadinessUseCase must not be null"
            );

        PublicationOutboxEnqueuePort validatedOutboxEnqueuePort =
            Objects.requireNonNull(
                publicationOutboxEnqueuePort,
                "publicationOutboxEnqueuePort must not be null"
            );

        Clock validatedClock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        JdbcPublicationCadenceProfileProvider
            cadenceProfileProvider =
            new JdbcPublicationCadenceProfileProvider(
                validatedConnection
            );

        JdbcPublicationCadenceReservationQueryAdapter
            cadenceReservationQueryAdapter =
            new JdbcPublicationCadenceReservationQueryAdapter(
                validatedConnection
            );

        PublicationCadenceSlotPlanner cadenceSlotPlanner =
            new PublicationCadenceSlotPlanner();

        return new PublicationSelectionDispatchService(
            validatedGenerationUseCase,
            validatedReadinessUseCase,
            validatedOutboxEnqueuePort,
            cadenceProfileProvider,
            cadenceReservationQueryAdapter,
            cadenceSlotPlanner,
            validatedClock
        );
    }
}
