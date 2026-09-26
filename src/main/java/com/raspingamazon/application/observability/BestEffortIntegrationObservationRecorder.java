package com.raspingamazon.application.observability;

import com.raspingamazon.application.observability.port.IntegrationObservationPersistencePort;
import com.raspingamazon.application.observability.port.IntegrationObservationRecorder;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;

import java.util.Objects;

/**
 * Recorder best-effort das observações duráveis de integração.
 *
 * <p>A persistência das métricas é importante para operação, mas não
 * participa da decisão funcional do pipeline. Portanto, uma falha ao
 * persistir uma IntegrationObservation não deve:</p>
 *
 * <ul>
 *     <li>transformar uma integração bem-sucedida em falha;</li>
 *     <li>criar retry funcional;</li>
 *     <li>transformar retry em DEAD;</li>
 *     <li>alterar regras comerciais;</li>
 *     <li>substituir a exceção original da integração.</li>
 * </ul>
 *
 * <p>Quando a persistência da observabilidade falha, o recorder tenta
 * emitir um log estruturado de falha INTERNAL. Essa segunda tentativa
 * também é best-effort e nunca provoca logging recursivo.</p>
 */
public final class BestEffortIntegrationObservationRecorder
    implements IntegrationObservationRecorder {

    private static final String LOG_EVENT =
        "integration.observation.persistence-failed";

    private static final String LOG_COMPONENT =
        "integration-observation-recorder";

    private static final String LOG_OPERATION =
        "persist-observation";

    private final IntegrationObservationPersistencePort
        persistencePort;

    private final ProcessingFailureClassifier
        failureClassifier;

    private final StructuredOperationalLogPort
        operationalLog;

    public BestEffortIntegrationObservationRecorder(
        IntegrationObservationPersistencePort persistencePort,
        ProcessingFailureClassifier failureClassifier,
        StructuredOperationalLogPort operationalLog
    ) {

        this.persistencePort =
            Objects.requireNonNull(
                persistencePort,
                "persistencePort must not be null"
            );

        this.failureClassifier =
            Objects.requireNonNull(
                failureClassifier,
                "failureClassifier must not be null"
            );

        this.operationalLog =
            Objects.requireNonNull(
                operationalLog,
                "operationalLog must not be null"
            );
    }

    /**
     * Registra a observação.
     *
     * <p>Argumentos inválidos continuam sendo erro de programação e,
     * portanto, são rejeitados. Somente falhas ocorridas depois que uma
     * observação válida foi entregue à infraestrutura são isoladas como
     * best-effort.</p>
     */
    @Override
    public void record(
        IntegrationObservation observation
    ) {

        Objects.requireNonNull(
            observation,
            "observation must not be null"
        );

        try {

            persistencePort.save(
                observation
            );

        } catch (RuntimeException persistenceFailure) {

            reportPersistenceFailureBestEffort(
                observation,
                persistenceFailure
            );
        }
    }

    /**
     * Tenta explicar uma falha da própria observabilidade.
     *
     * <p>O failureOrigin é INTERNAL porque o erro ocorreu no mecanismo
     * interno responsável por armazenar a observação, e não na
     * integração externa que estava sendo observada.</p>
     */
    private void reportPersistenceFailureBestEffort(
        IntegrationObservation observation,
        RuntimeException persistenceFailure
    ) {

        try {

            FailureClassification classification =
                Objects.requireNonNull(
                    failureClassifier.classify(
                        persistenceFailure
                    ),
                    "failureClassifier must not return null"
                );

            OperationalLogEvent event =
                new OperationalLogEvent(
                    OperationalLogLevel.ERROR,
                    LOG_EVENT,
                    LOG_COMPONENT,
                    LOG_OPERATION,
                    contextForPersistenceFailure(
                        observation
                    ),
                    "FAILURE",
                    null,
                    OperationalFailureOrigin.INTERNAL,
                    classification.type(),
                    classification.code()
                );

            operationalLog.log(
                event
            );

        } catch (RuntimeException ignored) {

            /*
             * Não existe terceira tentativa.
             *
             * Se a persistência da observabilidade falhou e o mecanismo
             * complementar de logging também falhou, o fluxo funcional
             * ainda precisa continuar.
             *
             * Tentar registrar esta segunda falha por meio do mesmo
             * mecanismo criaria recursão de observabilidade.
             */
        }
    }

    /**
     * Preserva toda correlação conhecida da observação original e
     * garante que o log diagnóstico também identifique a integração
     * cuja observação não pôde ser persistida.
     */
    private OperationalLogContext contextForPersistenceFailure(
        IntegrationObservation observation
    ) {

        OperationalLogContext original =
            observation.context();

        return new OperationalLogContext(
            original.runId(),
            original.jobId(),
            original.jobType(),
            original.candidateId(),
            original.snapshotId(),
            original.evaluationId(),
            original.publicationId(),
            original.asin(),
            observation.integration()
        );
    }
}
