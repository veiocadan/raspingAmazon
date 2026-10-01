package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.selection.PublicationSelectionExecution;
import com.raspingamazon.application.publication.selection.PublicationSelectionService;
import com.raspingamazon.application.publication.selection.PublicationSelectionSourceCandidate;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionSourceQueryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Fachada de aplicação que conecta uma ProcessingRun persistida
 * ao fluxo automático de seleção e dispatch de publicações.
 *
 * <p>O modo preferencial do fluxo automático recebe o escopo
 * operacional durante a composição:</p>
 *
 * <pre>
 * channel
 * +
 * destination
 * </pre>
 *
 * <p>Depois disso o chamador informa somente:</p>
 *
 * <pre>
 * processingRunId
 * </pre>
 *
 * <p>Fluxo:</p>
 *
 * <pre>
 * ProcessingRun
 *      |
 *      v
 * candidatos elegíveis e pontuados persistidos
 *      |
 *      v
 * PublicationSelectionService
 *      |
 *      v
 * PublicationSelectionExecution auditável
 *      |
 *      v
 * PublicationSelectionDispatchService
 *      |
 *      v
 * geração
 *      |
 *      v
 * READY automático
 *      |
 *      v
 * outbox
 * </pre>
 *
 * <p>Este serviço não implementa regra própria de:</p>
 *
 * <ul>
 *     <li>elegibilidade;</li>
 *     <li>score;</li>
 *     <li>ranking;</li>
 *     <li>quota;</li>
 *     <li>cooldown;</li>
 *     <li>cadência;</li>
 *     <li>geração de conteúdo;</li>
 *     <li>entrega por canal.</li>
 * </ul>
 *
 * <p>Ele somente coordena componentes especializados.</p>
 *
 * <p>Não existe aprovação humana neste fluxo.</p>
 *
 * <p>Durante a migração para o escopo configurado pela composição,
 * dois contratos permanecem disponíveis:</p>
 *
 * <pre>
 * NOVO
 *
 * process(processingRunId)
 *     -> PublicationSelectionDispatchResult
 *
 *
 * LEGADO
 *
 * process(processingRunId, channel, destination)
 *     -> PublicationProcessingRunDispatchResult
 * </pre>
 *
 * <p>O contrato legado preserva informações adicionais da
 * ProcessingRun e da execução de seleção para que callers e testes
 * anteriores possam migrar sem quebra abrupta.</p>
 */
public final class PublicationProcessingRunDispatchService {

    private final PublicationSelectionSourceQueryPort
        sourceQueryPort;

    private final PublicationSelectionService
        selectionService;

    private final PublicationSelectionDispatchService
        selectionDispatchService;

    /**
     * Null somente quando a instância foi criada pelo
     * construtor legado.
     */
    private final String configuredChannel;

    /**
     * Null somente quando a instância foi criada pelo
     * construtor legado.
     */
    private final String configuredDestination;

    private final Clock clock;

    /**
     * Construtor preferencial do fluxo automático.
     *
     * <p>Channel e destination são fixados na composição e não
     * precisam ser fornecidos em cada execução.</p>
     */
    public PublicationProcessingRunDispatchService(
        PublicationSelectionSourceQueryPort sourceQueryPort,
        PublicationSelectionService selectionService,
        PublicationSelectionDispatchService selectionDispatchService,
        String channel,
        String destination,
        Clock clock
    ) {

        this.sourceQueryPort =
            Objects.requireNonNull(
                sourceQueryPort,
                "sourceQueryPort must not be null"
            );

        this.selectionService =
            Objects.requireNonNull(
                selectionService,
                "selectionService must not be null"
            );

        this.selectionDispatchService =
            Objects.requireNonNull(
                selectionDispatchService,
                "selectionDispatchService must not be null"
            );

        this.configuredChannel =
            requireText(
                channel,
                "channel"
            );

        this.configuredDestination =
            requireText(
                destination,
                "destination"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Construtor legado temporário.
     *
     * <p>Neste modo channel e destination são fornecidos para
     * {@link #process(long, String, String)}.</p>
     */
    public PublicationProcessingRunDispatchService(
        PublicationSelectionSourceQueryPort sourceQueryPort,
        PublicationSelectionService selectionService,
        PublicationSelectionDispatchService selectionDispatchService,
        Clock clock
    ) {

        this.sourceQueryPort =
            Objects.requireNonNull(
                sourceQueryPort,
                "sourceQueryPort must not be null"
            );

        this.selectionService =
            Objects.requireNonNull(
                selectionService,
                "selectionService must not be null"
            );

        this.selectionDispatchService =
            Objects.requireNonNull(
                selectionDispatchService,
                "selectionDispatchService must not be null"
            );

        this.configuredChannel =
            null;

        this.configuredDestination =
            null;

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * API preferencial do fluxo automático.
     *
     * <p>O chamador fornece somente a ProcessingRun.</p>
     *
     * @param processingRunId identidade persistida da ProcessingRun
     * @return resultado do dispatch das publicações selecionadas
     */
    public PublicationSelectionDispatchResult process(
        long processingRunId
    ) {

        if (configuredChannel == null
            || configuredDestination == null) {

            throw new IllegalStateException(
                "PublicationProcessingRunDispatchService was "
                    + "constructed without configured channel and "
                    + "destination. Use process(processingRunId, "
                    + "channel, destination) or construct the "
                    + "service with a fixed operational scope."
            );
        }

        PublicationProcessingRunDispatchResult detailedResult =
            processDetailed(
                processingRunId,
                configuredChannel,
                configuredDestination
            );

        return detailedResult.dispatchResult();
    }

    /**
     * API de compatibilidade com o contrato anterior.
     *
     * <p>Preserva o resultado rico contendo:</p>
     *
     * <ul>
     *     <li>processingRunId;</li>
     *     <li>quantidade de candidatos da fonte;</li>
     *     <li>PublicationSelectionExecution;</li>
     *     <li>PublicationSelectionDispatchResult.</li>
     * </ul>
     *
     * <p>Novos composition roots devem preferir
     * {@link #process(long)}.</p>
     */
    public PublicationProcessingRunDispatchResult process(
        long processingRunId,
        String channel,
        String destination
    ) {

        return processDetailed(
            processingRunId,
            requireText(
                channel,
                "channel"
            ),
            requireText(
                destination,
                "destination"
            )
        );
    }

    /**
     * Alias da API nova.
     */
    public PublicationSelectionDispatchResult dispatch(
        long processingRunId
    ) {

        return process(
            processingRunId
        );
    }

    /**
     * Alias do contrato legado.
     */
    public PublicationProcessingRunDispatchResult dispatch(
        long processingRunId,
        String channel,
        String destination
    ) {

        return process(
            processingRunId,
            channel,
            destination
        );
    }

    /**
     * Implementação comum aos contratos novo e legado.
     *
     * <p>Aqui é construída a representação mais rica da execução.
     * A API nova apenas projeta seu dispatchResult.</p>
     */
    private PublicationProcessingRunDispatchResult processDetailed(
        long processingRunId,
        String channel,
        String destination
    ) {

        validateProcessingRunId(
            processingRunId
        );

        List<PublicationSelectionSourceCandidate> loadedCandidates =
            Objects.requireNonNull(
                sourceQueryPort
                    .findEligibleScoredByProcessingRunId(
                        processingRunId
                    ),
                "sourceQueryPort returned null"
            );

        /*
         * Fronteira imutável entre persistência e seleção.
         *
         * List.copyOf também rejeita elementos null.
         */
        List<PublicationSelectionSourceCandidate> sourceCandidates =
            List.copyOf(
                loadedCandidates
            );

        /*
         * Uma única fotografia temporal é utilizada para a decisão
         * desta execução de seleção.
         */
        Instant selectionTime =
            clock.instant();

        PublicationSelectionExecution selectionExecution =
            Objects.requireNonNull(
                selectionService.execute(
                    sourceCandidates,
                    channel,
                    destination,
                    selectionTime
                ),
                "selectionService returned null"
            );

        PublicationSelectionDispatchResult dispatchResult =
            Objects.requireNonNull(
                selectionDispatchService.dispatch(
                    selectionExecution
                ),
                "selectionDispatchService returned null"
            );

        return new PublicationProcessingRunDispatchResult(
            processingRunId,
            sourceCandidates.size(),
            selectionExecution,
            dispatchResult
        );
    }

    private void validateProcessingRunId(
        long processingRunId
    ) {

        if (processingRunId <= 0L) {

            throw new IllegalArgumentException(
                "processingRunId must be positive"
            );
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

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
