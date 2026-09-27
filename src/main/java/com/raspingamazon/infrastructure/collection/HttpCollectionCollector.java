package com.raspingamazon.infrastructure.collection;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.collection.contract.HttpTransport;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;
import com.raspingamazon.application.observability.IntegrationObservation;
import com.raspingamazon.application.observability.IntegrationObservationOutcome;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.observability.port.IntegrationObservationRecorder;
import com.raspingamazon.application.orchestration.failure.DefaultProcessingFailureClassifier;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Implementação do coletor baseado em HTTP.
 *
 * <p>Esta classe adapta o transporte HTTP ao contrato de coleta
 * definido pela aplicação.</p>
 *
 * <p>A classe permanece deliberadamente genérica: não conhece Amazon,
 * HTML, JSON, ASIN, ofertas ou regras de negócio.</p>
 *
 * <p>Quando observabilidade explícita é configurada, o collector também
 * registra uma observação durável da interação HTTP. O tempo medido
 * compreende somente a chamada ao HttpTransport, e não parsing,
 * persistência, classificação, logging ou gravação da própria
 * observabilidade.</p>
 *
 * <p>A observabilidade permanece best-effort: nenhuma falha do recorder
 * ou do classificador utilizado exclusivamente para a observação pode
 * alterar o resultado funcional da coleta.</p>
 */
public final class HttpCollectionCollector
    implements CollectionCollector {

    private static final int MAX_ERROR_BODY_EXCERPT_LENGTH =
        2000;

    private static final String DEFAULT_INTEGRATION =
        "http-collection";

    private static final String OBSERVED_OPERATION =
        "GET";

    private static final IntegrationObservationRecorder
        NO_OP_OBSERVATION_RECORDER =
        observation -> {
        };

    private final HttpTransport httpTransport;

    private final Clock clock;

    private final String integration;

    private final IntegrationObservationRecorder
        observationRecorder;

    private final ProcessingFailureClassifier
        failureClassifier;

    private final LongSupplier nanoTime;

    /**
     * Construtor histórico, mantido para compatibilidade.
     *
     * <p>Nesta forma nenhuma observação é persistida. Isso evita que
     * consumidores existentes comecem a produzir telemetria sem que o
     * composition root configure explicitamente a infraestrutura
     * correspondente.</p>
     */
    public HttpCollectionCollector(
        HttpTransport httpTransport,
        Clock clock
    ) {

        this(
            httpTransport,
            clock,
            DEFAULT_INTEGRATION,
            NO_OP_OBSERVATION_RECORDER,
            new DefaultProcessingFailureClassifier(),
            System::nanoTime
        );
    }

    /**
     * Construtor com observabilidade explícita.
     *
     * @param httpTransport transporte HTTP real
     * @param clock relógio de aquisição e observação
     * @param integration identificador estável da integração
     * @param observationRecorder destino best-effort das observações
     * @param failureClassifier classificação já utilizada pelo pipeline
     */
    public HttpCollectionCollector(
        HttpTransport httpTransport,
        Clock clock,
        String integration,
        IntegrationObservationRecorder observationRecorder,
        ProcessingFailureClassifier failureClassifier
    ) {

        this(
            httpTransport,
            clock,
            integration,
            observationRecorder,
            failureClassifier,
            System::nanoTime
        );
    }

    /**
     * Variante package-private com fonte monotônica de tempo injetável.
     *
     * <p>Clock continua responsável pelo instante civil da observação.
     * nanoTime é utilizado exclusivamente para duração.</p>
     */
    HttpCollectionCollector(
        HttpTransport httpTransport,
        Clock clock,
        String integration,
        IntegrationObservationRecorder observationRecorder,
        ProcessingFailureClassifier failureClassifier,
        LongSupplier nanoTime
    ) {

        this.httpTransport =
            Objects.requireNonNull(
                httpTransport,
                "HTTP transport must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "Clock must not be null"
            );

        this.integration =
            requireText(
                integration,
                "Integration must not be blank"
            );

        this.observationRecorder =
            Objects.requireNonNull(
                observationRecorder,
                "Observation recorder must not be null"
            );

        this.failureClassifier =
            Objects.requireNonNull(
                failureClassifier,
                "Failure classifier must not be null"
            );

        this.nanoTime =
            Objects.requireNonNull(
                nanoTime,
                "nanoTime must not be null"
            );
    }

    @Override
    public CollectionResult collect(
        CollectionRequest request
    ) {

        return collect(
            request,
            OperationalLogContext.empty()
        );
    }

    @Override
    public CollectionResult collect(
        CollectionRequest request,
        OperationalLogContext context
    ) {

        Objects.requireNonNull(
            request,
            "Collection request must not be null"
        );

        Objects.requireNonNull(
            context,
            "Operational log context must not be null"
        );

        OperationalLogContext observationContext =
            observationContext(
                context
            );

        /*
         * O marcador inicial é obtido imediatamente antes da chamada
         * à fronteira externa.
         */
        long startedAtNanos =
            nanoTime.getAsLong();

        HttpTransportResponse response;

        try {

            response =
                httpTransport.get(
                    request.source()
                );

        } catch (CollectionException exception) {

            long durationMs =
                elapsedMilliseconds(
                    startedAtNanos
                );

            /*
             * JavaHttpTransport utiliza CollectionException para falhas
             * de transporte como conexão, timeout ou interrupção.
             *
             * A mesma exceção funcional precisa continuar atravessando
             * esta fronteira sem substituição.
             */
            recordFailureBestEffort(
                exception,
                OperationalFailureOrigin.EXTERNAL,
                durationMs,
                exception.httpStatusCode(),
                observationContext
            );

            throw exception;

        } catch (Exception exception) {

            long durationMs =
                elapsedMilliseconds(
                    startedAtNanos
                );

            /*
             * Preserva a semântica anterior do collector: uma exceção
             * inesperada do contrato de transporte é encapsulada como
             * CollectionException.
             *
             * Como não é uma CollectionException produzida pela
             * integração conhecida, a origem operacional é INTERNAL.
             */
            CollectionException wrapped =
                new CollectionException(
                    "Collection failed",
                    exception
                );

            recordFailureBestEffort(
                wrapped,
                OperationalFailureOrigin.INTERNAL,
                durationMs,
                null,
                observationContext
            );

            throw wrapped;
        }

        /*
         * O marcador final é capturado imediatamente depois do retorno
         * do transporte. Tudo abaixo fica fora da medição de latência
         * da chamada externa.
         */
        long durationMs =
            elapsedMilliseconds(
                startedAtNanos
            );

        if (response == null) {

            CollectionException failure =
                new CollectionException(
                    "Collection failed",
                    new NullPointerException(
                        "HTTP transport must not return null"
                    )
                );

            recordFailureBestEffort(
                failure,
                OperationalFailureOrigin.INTERNAL,
                durationMs,
                null,
                observationContext
            );

            throw failure;
        }

        if (!isSuccessful(
            response.statusCode()
        )) {

            CollectionException failure =
                new CollectionException(
                    "HTTP response status indicates collection failure: "
                        + response.statusCode(),
                    response.statusCode(),
                    createBodyExcerpt(
                        response.body()
                    )
                );

            /*
             * A chamada HTTP ocorreu e o sistema remoto respondeu com
             * um status que a coleta considera falha.
             *
             * O classificador existente decide TRANSIENT/PERMANENT
             * exatamente como já ocorre no pipeline.
             */
            recordFailureBestEffort(
                failure,
                OperationalFailureOrigin.EXTERNAL,
                durationMs,
                response.statusCode(),
                observationContext
            );

            throw failure;
        }

        final CollectionResult result;

        try {

            /*
             * CollectionResult continua sendo responsável por validar
             * se o conteúdo é utilizável.
             *
             * Não duplicamos essa regra aqui.
             */
            result =
                new CollectionResult(
                    response.body(),
                    OffsetDateTime.now(
                        clock
                    ),
                    request.source()
                        .toString()
                );

        } catch (Exception exception) {

            /*
             * A chamada HTTP terminou, mas uma regra interna posterior
             * à fronteira de transporte não conseguiu produzir o
             * contrato de coleta.
             *
             * Mantemos exatamente a semântica funcional anterior:
             * "Collection failed" com a causa original preservada.
             */
            CollectionException wrapped =
                new CollectionException(
                    "Collection failed",
                    exception
                );

            recordFailureBestEffort(
                wrapped,
                OperationalFailureOrigin.INTERNAL,
                durationMs,
                response.statusCode(),
                observationContext
            );

            throw wrapped;
        }

        recordSuccessBestEffort(
            durationMs,
            response.statusCode(),
            observationContext
        );

        return result;
    }

    /**
     * Registra uma chamada aceita como sucesso pela coleta.
     *
     * <p>Falhas na construção ou persistência da própria observação são
     * ignoradas neste ponto. Quando o recorder concreto for
     * BestEffortIntegrationObservationRecorder, ele tentará explicar
     * sua própria falha por log estruturado antes de retornar.</p>
     */
    private void recordSuccessBestEffort(
        long durationMs,
        int httpStatusCode,
        OperationalLogContext context
    ) {

        try {

            observationRecorder.record(
                new IntegrationObservation(
                    null,
                    OffsetDateTime.now(
                        clock
                    ),
                    integration,
                    OBSERVED_OPERATION,
                    IntegrationObservationOutcome.SUCCESS,
                    durationMs,
                    context,
                    null,
                    null,
                    null,
                    httpStatusCode
                )
            );

        } catch (RuntimeException ignored) {

            /*
             * O contrato permite implementações alternativas do recorder.
             *
             * Mesmo que uma implementação não respeite best-effort, este
             * adapter de integração não permite que a telemetria altere
             * o resultado funcional.
             */
        }
    }

    /**
     * Registra uma falha observada sem criar uma taxonomia paralela.
     *
     * <p>TRANSIENT/PERMANENT e errorCode são obtidos do mesmo
     * ProcessingFailureClassifier utilizado pela orquestração.</p>
     */
    private void recordFailureBestEffort(
        Throwable failure,
        OperationalFailureOrigin failureOrigin,
        long durationMs,
        Integer httpStatusCode,
        OperationalLogContext context
    ) {

        try {

            FailureClassification classification =
                Objects.requireNonNull(
                    failureClassifier.classify(
                        failure
                    ),
                    "Failure classifier must not return null"
                );

            observationRecorder.record(
                new IntegrationObservation(
                    null,
                    OffsetDateTime.now(
                        clock
                    ),
                    integration,
                    OBSERVED_OPERATION,
                    IntegrationObservationOutcome.FAILURE,
                    durationMs,
                    context,
                    failureOrigin,
                    classification.type(),
                    classification.code(),
                    httpStatusCode
                )
            );

        } catch (RuntimeException ignored) {

            /*
             * Classificação e persistência desta observação são
             * complementares.
             *
             * A exceção funcional original continua sendo a verdade da
             * execução e nunca é substituída por uma falha de telemetria.
             */
        }
    }

    /**
     * Preserva a correlação fornecida pela aplicação e acrescenta o
     * identificador da integração pertencente a esta fronteira.
     *
     * <p>Nenhuma identidade ausente é inferida. Em particular, ASIN,
     * candidateId e jobId permanecem null quando o chamador não os
     * conhece.</p>
     */
    private OperationalLogContext observationContext(
        OperationalLogContext context
    ) {

        return new OperationalLogContext(
            context.runId(),
            context.jobId(),
            context.jobType(),
            context.candidateId(),
            context.snapshotId(),
            context.evaluationId(),
            context.publicationId(),
            context.asin(),
            integration
        );
    }

    /**
     * Calcula a duração utilizando fonte monotônica.
     *
     * <p>Clock não deve ser utilizado para medir duração porque relógios
     * civis podem ser ajustados durante a execução.</p>
     */
    private long elapsedMilliseconds(
        long startedAtNanos
    ) {

        long finishedAtNanos =
            nanoTime.getAsLong();

        long elapsedNanos =
            finishedAtNanos
                - startedAtNanos;

        if (elapsedNanos <= 0L) {
            return 0L;
        }

        return TimeUnit.NANOSECONDS
            .toMillis(
                elapsedNanos
            );
    }

    private boolean isSuccessful(
        int statusCode
    ) {

        return statusCode >= 200
            && statusCode < 300;
    }

    private String createBodyExcerpt(
        String body
    ) {

        if (body == null
            || body.isBlank()) {

            return null;
        }

        if (body.length()
            <= MAX_ERROR_BODY_EXCERPT_LENGTH) {

            return body;
        }

        return body.substring(
            0,
            MAX_ERROR_BODY_EXCERPT_LENGTH
        );
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
