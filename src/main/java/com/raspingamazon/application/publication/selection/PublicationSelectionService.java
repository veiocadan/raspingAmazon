package com.raspingamazon.application.publication.selection;

import com.raspingamazon.application.publication.selection.port.PublicationQuotaProfileProvider;
import com.raspingamazon.application.publication.selection.port.PublicationQuotaUsageQueryPort;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionAuditRepository;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionProfileProvider;
import com.raspingamazon.application.publication.selection.port.SuccessfulPublicationHistoryQueryPort;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaSnapshot;
import com.raspingamazon.domain.publication.selection.PublicationSelectionCandidate;
import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionResult;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Orquestra a seleção operacional de publicações.
 *
 * <p>O serviço coordena portas e domínio, mas não implementa
 * regras de prioridade.</p>
 *
 * <p>Responsabilidades:</p>
 *
 * <ol>
 *     <li>validar o lote recebido;</li>
 *     <li>carregar os perfis ativos;</li>
 *     <li>determinar o dia operacional da quota;</li>
 *     <li>consultar ocupação persistente da quota;</li>
 *     <li>consultar histórico de sucesso em lote;</li>
 *     <li>montar candidatos do domínio;</li>
 *     <li>delegar a decisão à PublicationSelectionPolicy;</li>
 *     <li>persistir a auditoria produzida;</li>
 *     <li>retornar resultado e identidade auditável.</li>
 * </ol>
 *
 * <p>Este serviço não:</p>
 *
 * <ul>
 *     <li>recalcula elegibilidade;</li>
 *     <li>recalcula score;</li>
 *     <li>consulta HTML ou Amazon;</li>
 *     <li>envia publicação;</li>
 *     <li>reserva definitivamente quota;</li>
 *     <li>consulta a listagem operacional paginada para inventar
 *         um lote de publicação.</li>
 * </ul>
 */
public final class PublicationSelectionService {

    private final PublicationSelectionProfileProvider
        selectionProfileProvider;

    private final PublicationQuotaProfileProvider
        quotaProfileProvider;

    private final PublicationQuotaUsageQueryPort
        quotaUsageQueryPort;

    private final SuccessfulPublicationHistoryQueryPort
        successfulHistoryQueryPort;

    private final PublicationSelectionAuditRepository
        auditRepository;

    private final PublicationSelectionPolicy
        selectionPolicy;

    public PublicationSelectionService(
        PublicationSelectionProfileProvider selectionProfileProvider,
        PublicationQuotaProfileProvider quotaProfileProvider,
        PublicationQuotaUsageQueryPort quotaUsageQueryPort,
        SuccessfulPublicationHistoryQueryPort successfulHistoryQueryPort,
        PublicationSelectionAuditRepository auditRepository,
        PublicationSelectionPolicy selectionPolicy
    ) {

        this.selectionProfileProvider =
            Objects.requireNonNull(
                selectionProfileProvider,
                "selectionProfileProvider must not be null"
            );

        this.quotaProfileProvider =
            Objects.requireNonNull(
                quotaProfileProvider,
                "quotaProfileProvider must not be null"
            );

        this.quotaUsageQueryPort =
            Objects.requireNonNull(
                quotaUsageQueryPort,
                "quotaUsageQueryPort must not be null"
            );

        this.successfulHistoryQueryPort =
            Objects.requireNonNull(
                successfulHistoryQueryPort,
                "successfulHistoryQueryPort must not be null"
            );

        this.auditRepository =
            Objects.requireNonNull(
                auditRepository,
                "auditRepository must not be null"
            );

        this.selectionPolicy =
            Objects.requireNonNull(
                selectionPolicy,
                "selectionPolicy must not be null"
            );
    }

    /**
     * Executa um ciclo de seleção operacional.
     *
     * <p>O lote deve possuir no máximo uma DealEvaluation para
     * cada ASIN. A política de recorrência trabalha por ASIN e,
     * portanto, duas avaliações do mesmo produto no mesmo ciclo
     * seriam ambíguas e poderiam consumir múltiplas vagas.</p>
     *
     * @param sourceCandidates candidatos já qualificados e pontuados
     * @param channel canal lógico
     * @param destination destino operacional
     * @param currentTime instante lógico da seleção
     * @return execução auditável persistida
     */
    public PublicationSelectionExecution execute(
        List<PublicationSelectionSourceCandidate> sourceCandidates,
        String channel,
        String destination,
        Instant currentTime
    ) {

        validateSourceCandidates(
            sourceCandidates
        );

        String validatedChannel =
            requireText(
                channel,
                "channel"
            );

        String validatedDestination =
            requireText(
                destination,
                "destination"
            );

        Objects.requireNonNull(
            currentTime,
            "currentTime must not be null"
        );

        validateUniqueCandidates(
            sourceCandidates
        );

        PublicationSelectionProfile selectionProfile =
            selectionProfileProvider.activeProfile(
                validatedChannel,
                validatedDestination
            );

        Objects.requireNonNull(
            selectionProfile,
            "selectionProfileProvider returned null"
        );

        PublicationQuotaProfile quotaProfile =
            quotaProfileProvider.activeProfile(
                validatedChannel,
                validatedDestination
            );

        Objects.requireNonNull(
            quotaProfile,
            "quotaProfileProvider returned null"
        );

        LocalDate quotaDate =
            quotaProfile.quotaDateAt(
                currentTime
            );

        long occupiedSlots =
            quotaUsageQueryPort.occupiedSlots(
                validatedChannel,
                validatedDestination,
                quotaDate
            );

        if (occupiedSlots < 0L) {

            throw new IllegalStateException(
                "quotaUsageQueryPort returned negative occupiedSlots"
            );
        }

        PublicationQuotaSnapshot quotaSnapshot =
            new PublicationQuotaSnapshot(
                validatedChannel,
                validatedDestination,
                quotaDate,
                quotaProfile.version(),
                quotaProfile.maxPublicationsPerDay(),
                occupiedSlots
            );

        Set<Asin> requestedAsins =
            requestedAsins(
                sourceCandidates
            );

        Map<Asin, SuccessfulPublicationHistory> histories =
            loadSuccessfulHistories(
                requestedAsins,
                validatedChannel,
                validatedDestination
            );

        List<PublicationSelectionCandidate> domainCandidates =
            buildDomainCandidates(
                sourceCandidates,
                histories,
                validatedChannel,
                validatedDestination
            );

        PublicationSelectionResult result =
            selectionPolicy.select(
                domainCandidates,
                selectionProfile,
                quotaSnapshot,
                currentTime
            );

        long auditRunId =
            auditRepository.save(
                result
            );

        if (auditRunId <= 0L) {

            throw new IllegalStateException(
                "auditRepository returned non-positive auditRunId"
            );
        }

        return new PublicationSelectionExecution(
            auditRunId,
            result
        );
    }

    private Map<Asin, SuccessfulPublicationHistory>
    loadSuccessfulHistories(
        Set<Asin> requestedAsins,
        String channel,
        String destination
    ) {

        if (requestedAsins.isEmpty()) {
            return Map.of();
        }

        Map<Asin, SuccessfulPublicationHistory> histories =
            successfulHistoryQueryPort.findSuccessfulByAsins(
                Set.copyOf(
                    requestedAsins
                ),
                channel,
                destination
            );

        validateSuccessfulHistories(
            histories,
            requestedAsins
        );

        return histories;
    }

    private void validateSuccessfulHistories(
        Map<Asin, SuccessfulPublicationHistory> histories,
        Set<Asin> requestedAsins
    ) {

        if (histories == null) {

            throw new IllegalStateException(
                "successfulHistoryQueryPort returned null"
            );
        }

        for (Map.Entry<Asin, SuccessfulPublicationHistory> entry
            : histories.entrySet()) {

            Asin mapAsin =
                entry.getKey();

            SuccessfulPublicationHistory history =
                entry.getValue();

            if (mapAsin == null) {

                throw new IllegalStateException(
                    "successful history map contains null ASIN"
                );
            }

            if (history == null) {

                throw new IllegalStateException(
                    "successful history map contains null history"
                );
            }

            if (!requestedAsins.contains(
                mapAsin
            )) {

                throw new IllegalStateException(
                    "successful history query returned "
                        + "an ASIN that was not requested"
                );
            }

            if (!mapAsin.equals(
                history.asin()
            )) {

                throw new IllegalStateException(
                    "successful history map key does not "
                        + "match history ASIN"
                );
            }
        }
    }

    private List<PublicationSelectionCandidate>
    buildDomainCandidates(
        List<PublicationSelectionSourceCandidate> sourceCandidates,
        Map<Asin, SuccessfulPublicationHistory> histories,
        String channel,
        String destination
    ) {

        List<PublicationSelectionCandidate> candidates =
            new ArrayList<>(
                sourceCandidates.size()
            );

        for (PublicationSelectionSourceCandidate source
            : sourceCandidates) {

            candidates.add(
                new PublicationSelectionCandidate(
                    source.dealEvaluationId(),
                    source.asin(),
                    source.score(),
                    channel,
                    destination,
                    histories.get(
                        source.asin()
                    )
                )
            );
        }

        return List.copyOf(
            candidates
        );
    }

    private Set<Asin> requestedAsins(
        List<PublicationSelectionSourceCandidate> candidates
    ) {

        Set<Asin> asins =
            new HashSet<>();

        for (PublicationSelectionSourceCandidate candidate
            : candidates) {

            asins.add(
                candidate.asin()
            );
        }

        return asins;
    }

    private void validateSourceCandidates(
        List<PublicationSelectionSourceCandidate> sourceCandidates
    ) {

        Objects.requireNonNull(
            sourceCandidates,
            "sourceCandidates must not be null"
        );

        for (PublicationSelectionSourceCandidate candidate
            : sourceCandidates) {

            Objects.requireNonNull(
                candidate,
                "sourceCandidates must not contain null"
            );
        }
    }

    private void validateUniqueCandidates(
        List<PublicationSelectionSourceCandidate> candidates
    ) {

        Set<Long> evaluationIds =
            new HashSet<>();

        Set<Asin> asins =
            new HashSet<>();

        for (PublicationSelectionSourceCandidate candidate
            : candidates) {

            if (!evaluationIds.add(
                candidate.dealEvaluationId()
            )) {

                throw new IllegalArgumentException(
                    "sourceCandidates must not contain "
                        + "duplicate dealEvaluationId"
                );
            }

            if (!asins.add(
                candidate.asin()
            )) {

                throw new IllegalArgumentException(
                    "sourceCandidates must contain at most "
                        + "one candidate per ASIN"
                );
            }
        }
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }
}
