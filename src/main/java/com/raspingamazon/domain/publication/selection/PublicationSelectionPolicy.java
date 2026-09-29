package com.raspingamazon.domain.publication.selection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Política operacional de priorização e seleção de publicações.
 *
 * <p>Esta política não altera score, não recalcula ranking
 * comercial e não acessa persistência.</p>
 *
 * <p>A PUBLICATION_SELECTION_V1 considera:</p>
 *
 * <ol>
 *     <li>hard cooldown;</li>
 *     <li>nunca publicados;</li>
 *     <li>preferred cooldown;</li>
 *     <li>antiguidade da última publicação bem-sucedida;</li>
 *     <li>score;</li>
 *     <li>desempate determinístico;</li>
 *     <li>capacidade observada no snapshot de quota.</li>
 * </ol>
 *
 * <p>A decisão SELECTED ainda não reserva definitivamente uma
 * vaga. A reserva concorrente pertence à transação de enqueue
 * da outbox.</p>
 */
public final class PublicationSelectionPolicy {

    public static final String VERSION =
        "PUBLICATION_SELECTION_V1";

    /**
     * Prioriza somente os candidatos atualmente elegíveis.
     *
     * <p>Candidatos dentro do hard cooldown são removidos do
     * resultado.</p>
     *
     * <p>Este método permanece disponível como operação pura de
     * ordenação e é utilizado internamente pela seleção completa.</p>
     *
     * @param candidates candidatos operacionais
     * @param profile configuração temporal da seleção
     * @param currentTime instante lógico da decisão
     * @return candidatos elegíveis em ordem de prioridade
     */
    public List<PublicationSelectionCandidate> prioritize(
        List<PublicationSelectionCandidate> candidates,
        PublicationSelectionProfile profile,
        Instant currentTime
    ) {

        validateBaseInputs(
            candidates,
            profile,
            currentTime
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        validateSingleOperationalScope(
            candidates
        );

        validateHistoricalTimes(
            candidates,
            currentTime
        );

        Comparator<PublicationSelectionCandidate>
            priorityComparator =
            priorityComparator(
                profile,
                currentTime
            );

        return candidates.stream()
            .filter(
                candidate ->
                    classifyRecency(
                        candidate,
                        profile,
                        currentTime
                    )
                        != PublicationSelectionRecency
                        .INSIDE_HARD_COOLDOWN
            )
            .sorted(
                priorityComparator
            )
            .toList();
    }

    /**
     * Executa a decisão completa de seleção.
     *
     * <p>Todos os candidatos recebem uma decisão auditável:</p>
     *
     * <ul>
     *     <li>SELECTED;</li>
     *     <li>DEFERRED_DUE_TO_HARD_COOLDOWN;</li>
     *     <li>NOT_SELECTED_DUE_TO_QUOTA.</li>
     * </ul>
     *
     * <p>O snapshot de quota representa a capacidade observada no
     * momento da seleção. A outbox deverá revalidar essa capacidade
     * de forma concorrente antes de efetivamente reservar a vaga.</p>
     *
     * @param candidates candidatos avaliados
     * @param profile perfil temporal utilizado
     * @param quotaSnapshot capacidade observada
     * @param currentTime instante lógico da decisão
     * @return resultado completo e auditável
     */
    public PublicationSelectionResult select(
        List<PublicationSelectionCandidate> candidates,
        PublicationSelectionProfile profile,
        PublicationQuotaSnapshot quotaSnapshot,
        Instant currentTime
    ) {

        validateBaseInputs(
            candidates,
            profile,
            currentTime
        );

        Objects.requireNonNull(
            quotaSnapshot,
            "quotaSnapshot must not be null"
        );

        if (candidates.isEmpty()) {

            return new PublicationSelectionResult(
                currentTime,
                profile,
                quotaSnapshot,
                List.of()
            );
        }

        validateSingleOperationalScope(
            candidates
        );

        validateQuotaScope(
            candidates,
            quotaSnapshot
        );

        validateHistoricalTimes(
            candidates,
            currentTime
        );

        List<PublicationSelectionCandidate>
            prioritizedCandidates =
            prioritize(
                candidates,
                profile,
                currentTime
            );

        int availableSlots =
            quotaSnapshot.availableSlots();

        List<PublicationSelectionDecision> decisions =
            new ArrayList<>();

        for (int index = 0;
             index < prioritizedCandidates.size();
             index++) {

            PublicationSelectionCandidate candidate =
                prioritizedCandidates.get(
                    index
                );

            int priorityPosition =
                index + 1;

            PublicationSelectionDecisionStatus status =
                priorityPosition <= availableSlots
                    ? PublicationSelectionDecisionStatus.SELECTED
                    : PublicationSelectionDecisionStatus
                    .NOT_SELECTED_DUE_TO_QUOTA;

            decisions.add(
                new PublicationSelectionDecision(
                    candidate,
                    status,
                    classifyRecency(
                        candidate,
                        profile,
                        currentTime
                    ),
                    priorityPosition
                )
            );
        }

        candidates.stream()
            .filter(
                candidate ->
                    classifyRecency(
                        candidate,
                        profile,
                        currentTime
                    )
                        == PublicationSelectionRecency
                        .INSIDE_HARD_COOLDOWN
            )
            .sorted(
                deferredCandidateComparator()
            )
            .map(
                candidate ->
                    new PublicationSelectionDecision(
                        candidate,
                        PublicationSelectionDecisionStatus
                            .DEFERRED_DUE_TO_HARD_COOLDOWN,
                        PublicationSelectionRecency
                            .INSIDE_HARD_COOLDOWN,
                        null
                    )
            )
            .forEach(
                decisions::add
            );

        return new PublicationSelectionResult(
            currentTime,
            profile,
            quotaSnapshot,
            decisions
        );
    }

    private Comparator<PublicationSelectionCandidate>
    priorityComparator(
        PublicationSelectionProfile profile,
        Instant currentTime
    ) {

        return Comparator
            .<PublicationSelectionCandidate>comparingInt(
                candidate ->
                    priorityBucket(
                        candidate,
                        profile,
                        currentTime
                    )
            )
            .thenComparing(
                PublicationSelectionPolicy
                    ::lastSuccessfulPublicationAt,
                Comparator.nullsFirst(
                    Comparator.naturalOrder()
                )
            )
            .thenComparing(
                PublicationSelectionCandidate::score,
                Comparator.reverseOrder()
            )
            .thenComparing(
                candidate ->
                    candidate.asin()
                        .value()
            )
            .thenComparingLong(
                PublicationSelectionCandidate
                    ::dealEvaluationId
            );
    }

    private Comparator<PublicationSelectionCandidate>
    deferredCandidateComparator() {

        return Comparator
            .comparing(
                (PublicationSelectionCandidate candidate) ->
                    candidate.asin()
                        .value()
            )
            .thenComparingLong(
                PublicationSelectionCandidate
                    ::dealEvaluationId
            );
    }

    private int priorityBucket(
        PublicationSelectionCandidate candidate,
        PublicationSelectionProfile profile,
        Instant currentTime
    ) {

        PublicationSelectionRecency recency =
            classifyRecency(
                candidate,
                profile,
                currentTime
            );

        return switch (recency) {

            case NEVER_SUCCESSFULLY_PUBLISHED ->
                0;

            case OUTSIDE_PREFERRED_COOLDOWN ->
                1;

            case INSIDE_PREFERRED_COOLDOWN ->
                2;

            case INSIDE_HARD_COOLDOWN ->
                3;
        };
    }

    private PublicationSelectionRecency classifyRecency(
        PublicationSelectionCandidate candidate,
        PublicationSelectionProfile profile,
        Instant currentTime
    ) {

        if (candidate.neverSuccessfullyPublished()) {

            return PublicationSelectionRecency
                .NEVER_SUCCESSFULLY_PUBLISHED;
        }

        Duration elapsed =
            elapsedSinceLastSuccessfulPublication(
                candidate,
                currentTime
            );

        if (elapsed.compareTo(
            profile.hardCooldown()
        ) < 0) {

            return PublicationSelectionRecency
                .INSIDE_HARD_COOLDOWN;
        }

        if (elapsed.compareTo(
            profile.preferredCooldown()
        ) < 0) {

            return PublicationSelectionRecency
                .INSIDE_PREFERRED_COOLDOWN;
        }

        return PublicationSelectionRecency
            .OUTSIDE_PREFERRED_COOLDOWN;
    }

    private Duration elapsedSinceLastSuccessfulPublication(
        PublicationSelectionCandidate candidate,
        Instant currentTime
    ) {

        Instant lastSuccessfulPublicationAt =
            candidate.successfulHistory()
                .orElseThrow()
                .lastSuccessfulPublicationAt();

        return Duration.between(
            lastSuccessfulPublicationAt,
            currentTime
        );
    }

    private static Instant lastSuccessfulPublicationAt(
        PublicationSelectionCandidate candidate
    ) {

        return candidate.successfulHistory()
            .map(
                SuccessfulPublicationHistory
                    ::lastSuccessfulPublicationAt
            )
            .orElse(
                null
            );
    }

    private void validateBaseInputs(
        List<PublicationSelectionCandidate> candidates,
        PublicationSelectionProfile profile,
        Instant currentTime
    ) {

        validateCandidates(
            candidates
        );

        Objects.requireNonNull(
            profile,
            "profile must not be null"
        );

        Objects.requireNonNull(
            currentTime,
            "currentTime must not be null"
        );

        validateProfileVersion(
            profile
        );
    }

    private void validateCandidates(
        List<PublicationSelectionCandidate> candidates
    ) {

        Objects.requireNonNull(
            candidates,
            "candidates must not be null"
        );

        candidates.forEach(
            candidate ->
                Objects.requireNonNull(
                    candidate,
                    "candidates must not contain null"
                )
        );
    }

    private void validateProfileVersion(
        PublicationSelectionProfile profile
    ) {

        if (!VERSION.equals(
            profile.version()
        )) {

            throw new IllegalArgumentException(
                "PublicationSelectionPolicy "
                    + VERSION
                    + " cannot use profile version "
                    + profile.version()
            );
        }
    }

    private void validateSingleOperationalScope(
        List<PublicationSelectionCandidate> candidates
    ) {

        PublicationSelectionCandidate first =
            candidates.getFirst();

        String expectedChannel =
            first.channel();

        String expectedDestination =
            first.destination();

        boolean containsDifferentScope =
            candidates.stream()
                .anyMatch(
                    candidate ->
                        !expectedChannel.equals(
                            candidate.channel()
                        )
                            || !expectedDestination.equals(
                            candidate.destination()
                        )
                );

        if (containsDifferentScope) {

            throw new IllegalArgumentException(
                "all publication selection candidates "
                    + "must belong to the same channel "
                    + "and destination"
            );
        }
    }

    private void validateQuotaScope(
        List<PublicationSelectionCandidate> candidates,
        PublicationQuotaSnapshot quotaSnapshot
    ) {

        PublicationSelectionCandidate first =
            candidates.getFirst();

        if (!first.channel()
            .equals(
                quotaSnapshot.channel()
            )
            || !first.destination()
            .equals(
                quotaSnapshot.destination()
            )) {

            throw new IllegalArgumentException(
                "quota snapshot must belong to candidate "
                    + "channel and destination"
            );
        }
    }

    private void validateHistoricalTimes(
        List<PublicationSelectionCandidate> candidates,
        Instant currentTime
    ) {

        boolean containsFutureHistory =
            candidates.stream()
                .flatMap(
                    candidate ->
                        candidate.successfulHistory()
                            .stream()
                )
                .anyMatch(
                    history ->
                        history.lastSuccessfulPublicationAt()
                            .isAfter(
                                currentTime
                            )
                );

        if (containsFutureHistory) {

            throw new IllegalArgumentException(
                "last successful publication time "
                    + "must not be after currentTime"
            );
        }
    }
}
