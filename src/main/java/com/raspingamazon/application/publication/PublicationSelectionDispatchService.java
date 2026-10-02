package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.application.publication.scheduling.PublicationCadencePlan;
import com.raspingamazon.application.publication.scheduling.PublicationCadenceSlotPlanner;
import com.raspingamazon.application.publication.scheduling.port.PublicationCadenceProfileProvider;
import com.raspingamazon.application.publication.scheduling.port.PublicationCadenceReservationQueryPort;
import com.raspingamazon.application.publication.selection.PublicationSelectionExecution;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;
import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaSnapshot;
import com.raspingamazon.domain.publication.selection.PublicationSelectionCandidate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Conecta uma execução persistida de seleção ao fluxo automático
 * de geração, liberação e reserva das Publications escolhidas.
 *
 * <p>No fluxo com cadência:</p>
 *
 * <pre>
 * PublicationSelectionExecution
 *          |
 *          v
 * selectedCandidates()
 *          |
 *          v
 * perfil ativo de cadência
 *          |
 *          v
 * último availableAt persistido
 *          |
 *          v
 * próximo slot
 *          |
 *          v
 * PublicationGenerationUseCase
 *          |
 *          v
 * PublicationReadinessUseCase
 *          |
 *          v
 * PublicationOutboxEnqueuePort
 * </pre>
 *
 * <p>A outbox continua sendo a autoridade final para quota e
 * cadência. O planejamento realizado aqui não constitui reserva.</p>
 *
 * <p>Se a outbox detectar mudança concorrente e devolver
 * STALE_SELECTION ou QUOTA_EXHAUSTED, o lote é interrompido.
 * Uma futura seleção observará o novo estado persistido.</p>
 *
 * <p>Não existe aprovação humana neste fluxo.</p>
 */
public final class PublicationSelectionDispatchService {

    private final PublicationGenerationUseCase
        publicationGenerationUseCase;

    private final PublicationReadinessUseCase
        publicationReadinessUseCase;

    private final PublicationOutboxEnqueuePort
        publicationOutboxEnqueuePort;

    private final PublicationCadenceProfileProvider
        cadenceProfileProvider;

    private final PublicationCadenceReservationQueryPort
        cadenceReservationQueryPort;

    private final PublicationCadenceSlotPlanner
        cadenceSlotPlanner;

    private final Clock clock;

    /**
     * Construtor legado mantido temporariamente para compatibilidade
     * com compositions ainda não migradas para cadência.
     *
     * <p>Neste modo:</p>
     *
     * <pre>
     * availableAt = enqueuedAt
     * </pre>
     *
     * <p>O fluxo automático de produção será composto posteriormente
     * usando o construtor completo.</p>
     */
    public PublicationSelectionDispatchService(
        PublicationGenerationUseCase publicationGenerationUseCase,
        PublicationReadinessUseCase publicationReadinessUseCase,
        PublicationOutboxEnqueuePort publicationOutboxEnqueuePort,
        Clock clock
    ) {

        this.publicationGenerationUseCase =
            Objects.requireNonNull(
                publicationGenerationUseCase,
                "publicationGenerationUseCase must not be null"
            );

        this.publicationReadinessUseCase =
            Objects.requireNonNull(
                publicationReadinessUseCase,
                "publicationReadinessUseCase must not be null"
            );

        this.publicationOutboxEnqueuePort =
            Objects.requireNonNull(
                publicationOutboxEnqueuePort,
                "publicationOutboxEnqueuePort must not be null"
            );

        this.cadenceProfileProvider =
            null;

        this.cadenceReservationQueryPort =
            null;

        this.cadenceSlotPlanner =
            null;

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Construtor do fluxo automático gerenciado por cadência.
     */
    public PublicationSelectionDispatchService(
        PublicationGenerationUseCase publicationGenerationUseCase,
        PublicationReadinessUseCase publicationReadinessUseCase,
        PublicationOutboxEnqueuePort publicationOutboxEnqueuePort,
        PublicationCadenceProfileProvider cadenceProfileProvider,
        PublicationCadenceReservationQueryPort
            cadenceReservationQueryPort,
        PublicationCadenceSlotPlanner cadenceSlotPlanner,
        Clock clock
    ) {

        this.publicationGenerationUseCase =
            Objects.requireNonNull(
                publicationGenerationUseCase,
                "publicationGenerationUseCase must not be null"
            );

        this.publicationReadinessUseCase =
            Objects.requireNonNull(
                publicationReadinessUseCase,
                "publicationReadinessUseCase must not be null"
            );

        this.publicationOutboxEnqueuePort =
            Objects.requireNonNull(
                publicationOutboxEnqueuePort,
                "publicationOutboxEnqueuePort must not be null"
            );

        this.cadenceProfileProvider =
            Objects.requireNonNull(
                cadenceProfileProvider,
                "cadenceProfileProvider must not be null"
            );

        this.cadenceReservationQueryPort =
            Objects.requireNonNull(
                cadenceReservationQueryPort,
                "cadenceReservationQueryPort must not be null"
            );

        this.cadenceSlotPlanner =
            Objects.requireNonNull(
                cadenceSlotPlanner,
                "cadenceSlotPlanner must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    public PublicationSelectionDispatchResult dispatch(
        PublicationSelectionExecution selectionExecution
    ) {

        Objects.requireNonNull(
            selectionExecution,
            "selectionExecution must not be null"
        );

        if (cadenceProfileProvider == null) {

            return dispatchWithoutCadence(
                selectionExecution
            );
        }

        return dispatchWithCadence(
            selectionExecution
        );
    }

    private PublicationSelectionDispatchResult dispatchWithCadence(
        PublicationSelectionExecution selectionExecution
    ) {

        List<PublicationSelectionCandidate> selectedCandidates =
            selectionExecution.result()
                .selectedCandidates();

        if (selectedCandidates.isEmpty()) {

            return new PublicationSelectionDispatchResult(
                selectionExecution.auditRunId(),
                0,
                0,
                false,
                List.of()
            );
        }

        PublicationQuotaSnapshot quotaSnapshot =
            selectionExecution.result()
                .quotaSnapshot();

        PublicationCadenceProfile cadenceProfile =
            Objects.requireNonNull(
                cadenceProfileProvider.activeProfile(
                    quotaSnapshot.channel(),
                    quotaSnapshot.destination()
                ),
                "cadenceProfileProvider returned null"
            );

        Instant dispatchInstant =
            clock.instant();

        OffsetDateTime enqueuedAt =
            OffsetDateTime.now(
                clock
            );

        List<PublicationSelectionDispatchResult.Item> items =
            new ArrayList<>(
                selectedCandidates.size()
            );

        int cadenceUnavailableCount =
            0;

        boolean interruptedByReservationRevalidation =
            false;

        for (int index = 0;
             index < selectedCandidates.size();
             index++) {

            PublicationSelectionCandidate candidate =
                selectedCandidates.get(
                    index
                );

            Optional<OffsetDateTime> lastReservedAvailableAt =
                Objects.requireNonNull(
                    cadenceReservationQueryPort
                        .findLastReservedAvailableAt(
                            quotaSnapshot.channel(),
                            quotaSnapshot.destination(),
                            quotaSnapshot.quotaDate()
                        ),
                    "cadenceReservationQueryPort returned null"
                );

            PublicationCadencePlan cadencePlan =
                cadenceSlotPlanner.plan(
                    quotaSnapshot,
                    cadenceProfile,
                    dispatchInstant,
                    1,
                    lastReservedAvailableAt
                );

            if (cadencePlan.availableAtSlots()
                .isEmpty()) {

                cadenceUnavailableCount =
                    selectedCandidates.size()
                        - index;

                break;
            }

            OffsetDateTime availableAt =
                cadencePlan.availableAtSlots()
                    .getFirst();

            Publication generated =
                Objects.requireNonNull(
                    publicationGenerationUseCase.generate(
                        candidate.dealEvaluationId()
                    ),
                    "publicationGenerationUseCase returned null"
                );

            long publicationId =
                validateGeneratedPublication(
                    candidate,
                    generated
                );

            Publication ready =
                Objects.requireNonNull(
                    publicationReadinessUseCase.ensureReady(
                        publicationId
                    ),
                    "publicationReadinessUseCase returned null"
                );

            validateReadyPublication(
                candidate,
                publicationId,
                ready
            );

            PublicationOutboxEnqueueResult enqueueResult =
                Objects.requireNonNull(
                    publicationOutboxEnqueuePort.enqueue(
                        new PublicationOutboxEnqueueRequest(
                            publicationId,
                            selectionExecution.auditRunId(),
                            cadenceProfile.version(),
                            availableAt,
                            enqueuedAt
                        )
                    ),
                    "publicationOutboxEnqueuePort returned null"
                );

            items.add(
                new PublicationSelectionDispatchResult.Item(
                    candidate.dealEvaluationId(),
                    publicationId,
                    enqueueResult
                )
            );

            /*
             * Estes resultados significam que o snapshot ou o
             * planejamento já não representa o estado definitivo
             * observado pela outbox.
             *
             * Não tentamos reconstruir os horários seguintes a
             * partir de uma fotografia obsoleta.
             *
             * Uma execução futura refará seleção e planejamento.
             */
            if (enqueueResult.staleSelection()
                || enqueueResult.quotaExhausted()) {

                interruptedByReservationRevalidation =
                    true;

                break;
            }
        }

        return new PublicationSelectionDispatchResult(
            selectionExecution.auditRunId(),
            selectedCandidates.size(),
            cadenceUnavailableCount,
            interruptedByReservationRevalidation,
            items
        );
    }

    /**
     * Semântica anterior preservada temporariamente para que
     * compositions ainda não migradas continuem funcionais.
     */
    private PublicationSelectionDispatchResult dispatchWithoutCadence(
        PublicationSelectionExecution selectionExecution
    ) {

        List<PublicationSelectionCandidate> selectedCandidates =
            selectionExecution.result()
                .selectedCandidates();

        OffsetDateTime dispatchAt =
            OffsetDateTime.now(
                clock
            );

        List<PublicationSelectionDispatchResult.Item> items =
            new ArrayList<>(
                selectedCandidates.size()
            );

        for (PublicationSelectionCandidate candidate
            : selectedCandidates) {

            Publication generated =
                Objects.requireNonNull(
                    publicationGenerationUseCase.generate(
                        candidate.dealEvaluationId()
                    ),
                    "publicationGenerationUseCase returned null"
                );

            long publicationId =
                validateGeneratedPublication(
                    candidate,
                    generated
                );

            Publication ready =
                Objects.requireNonNull(
                    publicationReadinessUseCase.ensureReady(
                        publicationId
                    ),
                    "publicationReadinessUseCase returned null"
                );

            validateReadyPublication(
                candidate,
                publicationId,
                ready
            );

            PublicationOutboxEnqueueResult enqueueResult =
                Objects.requireNonNull(
                    publicationOutboxEnqueuePort.enqueue(
                        new PublicationOutboxEnqueueRequest(
                            publicationId,
                            selectionExecution.auditRunId(),
                            dispatchAt,
                            dispatchAt
                        )
                    ),
                    "publicationOutboxEnqueuePort returned null"
                );

            items.add(
                new PublicationSelectionDispatchResult.Item(
                    candidate.dealEvaluationId(),
                    publicationId,
                    enqueueResult
                )
            );
        }

        return new PublicationSelectionDispatchResult(
            selectionExecution.auditRunId(),
            selectedCandidates.size(),
            0,
            false,
            items
        );
    }

    private long validateGeneratedPublication(
        PublicationSelectionCandidate candidate,
        Publication publication
    ) {

        Long publicationId =
            publication.id();

        if (publicationId == null
            || publicationId <= 0L) {

            throw new IllegalStateException(
                "Generated Publication must have a positive "
                    + "persisted identity"
            );
        }

        validateDealEvaluationIdentity(
            candidate,
            publication,
            "generated"
        );

        return publicationId;
    }

    private void validateReadyPublication(
        PublicationSelectionCandidate candidate,
        long expectedPublicationId,
        Publication publication
    ) {

        Long publicationId =
            publication.id();

        if (publicationId == null
            || publicationId != expectedPublicationId) {

            throw new IllegalStateException(
                "publicationReadinessUseCase returned a Publication "
                    + "with unexpected identity"
            );
        }

        validateDealEvaluationIdentity(
            candidate,
            publication,
            "ready"
        );

        if (publication.status()
            != PublicationStatus.READY) {

            throw new IllegalStateException(
                "publicationReadinessUseCase must return "
                    + "Publication READY"
            );
        }
    }

    private void validateDealEvaluationIdentity(
        PublicationSelectionCandidate candidate,
        Publication publication,
        String stage
    ) {

        Long dealEvaluationId =
            publication.dealEvaluation()
                .id();

        if (dealEvaluationId == null
            || dealEvaluationId
            != candidate.dealEvaluationId()) {

            throw new IllegalStateException(
                stage
                    + " Publication does not belong to selected "
                    + "DealEvaluation "
                    + candidate.dealEvaluationId()
            );
        }
    }
}
