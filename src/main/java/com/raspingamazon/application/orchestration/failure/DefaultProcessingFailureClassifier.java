package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.publication.PublicationDispatchJobException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;

/**
 * Classificação padrão das falhas conhecidas pela aplicação.
 *
 * <p>A política é deliberadamente conservadora e separa:</p>
 *
 * <ul>
 *     <li>semântica de retry;</li>
 *     <li>origem operacional;</li>
 *     <li>categoria da falha;</li>
 *     <li>ações operacionais;</li>
 *     <li>código diagnóstico específico.</li>
 * </ul>
 *
 * <p>Falhas desconhecidas permanecem permanentes até receberem
 * classificação explícita. Isso evita loops automáticos causados por
 * bugs, dados inválidos ou estados que exigem investigação.</p>
 */
public final class DefaultProcessingFailureClassifier
    implements ProcessingFailureClassifier {

    @Override
    public FailureClassification classify(
        Throwable failure
    ) {

        Objects.requireNonNull(
            failure,
            "failure must not be null"
        );

        /*
         * SourceRestrictionException também é CollectionException.
         *
         * Portanto a classificação explícita de restrição da fonte
         * precisa ocorrer antes da regra genérica de CollectionException.
         */
        SourceRestrictionException sourceRestriction =
            findCause(
                failure,
                SourceRestrictionException.class
            );

        if (sourceRestriction != null) {

            return permanentFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.SOURCE_RESTRICTION,
                "SOURCE_RESTRICTION_"
                    + sourceRestriction
                    .restrictionType()
                    .name(),
                sourceRestriction,
                FailureHandlingAction.REJECT,
                FailureHandlingAction.ALERT,
                FailureHandlingAction.OPERATOR_INTERVENTION
            );
        }

        /*
         * PUBLICATION_DISPATCH possui condições funcionais terminais
         * próprias.
         *
         * Elas representam estado da aplicação e permanecem separadas
         * de falhas dos canais externos.
         */
        PublicationDispatchJobException publicationDispatchFailure =
            findCause(
                failure,
                PublicationDispatchJobException.class
            );

        if (publicationDispatchFailure != null) {

            return permanentFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.PROCESSING,
                publicationDispatchFailure.errorCode(),
                publicationDispatchFailure,
                FailureHandlingAction.REJECT
            );
        }

        /*
         * CollectionException preserva a semântica técnica da coleta,
         * inclusive status HTTP quando uma resposta foi recebida.
         */
        CollectionException collectionException =
            findCause(
                failure,
                CollectionException.class
            );

        if (collectionException != null) {

            return classifyCollection(
                collectionException
            );
        }

        /*
         * Timeouts de infraestrutura geral permanecem retryable.
         *
         * A distinção mais fina de timeout antes/depois de possíveis
         * efeitos externos será aplicada à publicação na FASE 20-D.
         */
        HttpTimeoutException httpTimeout =
            findCause(
                failure,
                HttpTimeoutException.class
            );

        if (httpTimeout != null) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "NETWORK_TIMEOUT",
                httpTimeout
            );
        }

        SocketTimeoutException socketTimeout =
            findCause(
                failure,
                SocketTimeoutException.class
            );

        if (socketTimeout != null) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "NETWORK_TIMEOUT",
                socketTimeout
            );
        }

        ConnectException connectionFailure =
            findCause(
                failure,
                ConnectException.class
            );

        if (connectionFailure != null) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "NETWORK_CONNECTION_FAILED",
                connectionFailure
            );
        }

        /*
         * Toda SQLException conhecida chega a um classificador próprio.
         *
         * Isso permite usar SQLState além das subclasses Java.
         */
        SQLException databaseFailure =
            findCause(
                failure,
                SQLException.class
            );

        if (databaseFailure != null) {

            return classifyDatabase(
                databaseFailure
            );
        }

        /*
         * Entradas inválidas são falhas internas permanentes para o
         * trabalho corrente.
         */
        IllegalArgumentException invalidInput =
            findCause(
                failure,
                IllegalArgumentException.class
            );

        if (invalidInput != null) {

            return permanentFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.PROCESSING,
                "INVALID_PROCESSING_INPUT",
                invalidInput,
                FailureHandlingAction.REJECT
            );
        }

        /*
         * Estado interno impossível ou incompatível merece sinalização
         * operacional além do encerramento do job.
         */
        IllegalStateException invalidState =
            findCause(
                failure,
                IllegalStateException.class
            );

        if (invalidState != null) {

            return permanentFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.PROCESSING,
                "INVALID_PROCESSING_STATE",
                invalidState,
                FailureHandlingAction.REJECT,
                FailureHandlingAction.ALERT
            );
        }

        /*
         * Fallback fail closed.
         *
         * Falha não reconhecida não entra em retry automático.
         */
        return permanentFailure(
            OperationalFailureOrigin.INTERNAL,
            FailureCategory.UNKNOWN,
            "UNCLASSIFIED_FAILURE",
            failure,
            FailureHandlingAction.REJECT,
            FailureHandlingAction.ALERT
        );
    }

    private FailureClassification classifyCollection(
        CollectionException failure
    ) {

        Integer statusCode =
            failure.httpStatusCode();

        /*
         * Ausência de resposta HTTP utilizável indica falha de
         * transporte.
         *
         * SourceRestrictionException já foi tratada anteriormente.
         */
        if (statusCode == null) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "COLLECTION_TRANSPORT",
                failure
            );
        }

        /*
         * Rate limit é uma categoria própria.
         */
        if (statusCode == 429) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.RATE_LIMIT,
                "COLLECTION_HTTP_429",
                failure
            );
        }

        /*
         * Request timeout e Too Early admitem nova tentativa.
         */
        if (statusCode == 408
            || statusCode == 425) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "COLLECTION_HTTP_" + statusCode,
                failure
            );
        }

        /*
         * Falhas 5xx representam indisponibilidade técnica da origem
         * remota para esta tentativa.
         */
        if (statusCode >= 500
            && statusCode <= 599) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "COLLECTION_HTTP_" + statusCode,
                failure
            );
        }

        /*
         * Falha explícita de autenticação/autorização.
         */
        if (statusCode == 401) {

            return permanentFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.AUTHENTICATION,
                "COLLECTION_HTTP_401",
                failure,
                FailureHandlingAction.REJECT,
                FailureHandlingAction.PAUSE,
                FailureHandlingAction.ALERT,
                FailureHandlingAction.OPERATOR_INTERVENTION
            );
        }

        /*
         * Para a coleta pública atual, HTTP 403 é tratado de forma
         * conservadora como restrição da fonte.
         *
         * A FASE 20-C refinará a semântica Amazon específica usando
         * evidência de challenge/CAPTCHA/layout.
         */
        if (statusCode == 403) {

            return permanentFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.SOURCE_RESTRICTION,
                "COLLECTION_HTTP_403",
                failure,
                FailureHandlingAction.REJECT,
                FailureHandlingAction.ALERT,
                FailureHandlingAction.OPERATOR_INTERVENTION
            );
        }

        /*
         * Recurso ausente ou removido.
         */
        if (statusCode == 404
            || statusCode == 410) {

            return permanentFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.DATA_UNAVAILABLE,
                "COLLECTION_HTTP_" + statusCode,
                failure,
                FailureHandlingAction.REJECT
            );
        }

        /*
         * Outros 4xx indicam que repetir exatamente o mesmo trabalho
         * não possui expectativa razoável de corrigir a requisição.
         */
        if (statusCode >= 400
            && statusCode <= 499) {

            return permanentFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.PROCESSING,
                "COLLECTION_HTTP_" + statusCode,
                failure,
                FailureHandlingAction.REJECT
            );
        }

        /*
         * Uma CollectionException associada a status fora das faixas
         * esperadas representa contrato inesperado e permanece fail
         * closed.
         */
        return permanentFailure(
            OperationalFailureOrigin.EXTERNAL,
            FailureCategory.UNKNOWN,
            "COLLECTION_HTTP_UNEXPECTED",
            failure,
            FailureHandlingAction.REJECT,
            FailureHandlingAction.ALERT
        );
    }

    private FailureClassification classifyDatabase(
        SQLException failure
    ) {

        String sqlState =
            normalizeSqlState(
                failure.getSQLState()
            );

        /*
         * SQLState classe 08:
         * connection exception.
         */
        if (sqlState != null
            && sqlState.startsWith(
            "08"
        )) {

            return transientFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.DATABASE,
                "DATABASE_CONNECTION",
                failure
            );
        }

        /*
         * PostgreSQL:
         *
         * 40001 = serialization_failure
         * 40P01 = deadlock_detected
         *
         * Ambos permitem retry da transação/trabalho.
         */
        if ("40001".equals(
            sqlState
        )
            || "40P01".equals(
            sqlState
        )) {

            return transientFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.DATABASE,
                "DATABASE_TRANSACTION_RETRY",
                failure
            );
        }

        /*
         * PostgreSQL:
         *
         * 55P03 = lock_not_available
         */
        if ("55P03".equals(
            sqlState
        )) {

            return transientFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.DATABASE,
                "DATABASE_LOCK_NOT_AVAILABLE",
                failure
            );
        }

        /*
         * PostgreSQL:
         *
         * 57P01 = admin_shutdown
         * 57P02 = crash_shutdown
         * 57P03 = cannot_connect_now
         */
        if ("57P01".equals(
            sqlState
        )
            || "57P02".equals(
            sqlState
        )
            || "57P03".equals(
            sqlState
        )) {

            return transientFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.DATABASE,
                "DATABASE_UNAVAILABLE",
                failure
            );
        }

        /*
         * Mantém compatibilidade com a semântica Java já adotada antes
         * da FASE 20 para subclasses explicitamente transitórias ou
         * recuperáveis.
         */
        if (failure
            instanceof SQLTransientException
            || failure
            instanceof SQLRecoverableException) {

            return transientFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.DATABASE,
                "DATABASE_TRANSIENT",
                failure
            );
        }

        /*
         * SQLState conhecido, mas não pertencente ao conjunto
         * conservador de estados seguros para retry.
         */
        if (sqlState != null) {

            return permanentFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.DATABASE,
                "DATABASE_SQLSTATE_" + sqlState,
                failure,
                FailureHandlingAction.REJECT,
                FailureHandlingAction.ALERT
            );
        }

        /*
         * SQLException sem SQLState e sem semântica transitória
         * explícita permanece permanente.
         */
        return permanentFailure(
            OperationalFailureOrigin.INTERNAL,
            FailureCategory.DATABASE,
            "DATABASE_FAILURE",
            failure,
            FailureHandlingAction.REJECT,
            FailureHandlingAction.ALERT
        );
    }

    private FailureClassification transientFailure(
        OperationalFailureOrigin origin,
        FailureCategory category,
        String code,
        Throwable failure
    ) {

        return new FailureClassification(
            ProcessingFailureType.TRANSIENT,
            origin,
            category,
            EnumSet.of(
                FailureHandlingAction.RETRY
            ),
            code,
            safeMessage(
                failure
            )
        );
    }

    private FailureClassification permanentFailure(
        OperationalFailureOrigin origin,
        FailureCategory category,
        String code,
        Throwable failure,
        FailureHandlingAction firstAction,
        FailureHandlingAction... additionalActions
    ) {

        return new FailureClassification(
            ProcessingFailureType.PERMANENT,
            origin,
            category,
            EnumSet.of(
                firstAction,
                additionalActions
            ),
            code,
            safeMessage(
                failure
            )
        );
    }

    private String normalizeSqlState(
        String sqlState
    ) {

        if (sqlState == null
            || sqlState.isBlank()) {

            return null;
        }

        return sqlState
            .trim()
            .toUpperCase(
                Locale.ROOT
            );
    }

    private String safeMessage(
        Throwable failure
    ) {

        String message =
            failure.getMessage();

        if (message == null
            || message.isBlank()) {

            return failure
                .getClass()
                .getSimpleName();
        }

        return message;
    }

    private <T extends Throwable> T findCause(
        Throwable failure,
        Class<T> type
    ) {

        Throwable current =
            failure;

        while (current != null) {

            if (type.isInstance(
                current
            )) {

                return type.cast(
                    current
                );
            }

            current =
                current.getCause();
        }

        return null;
    }
}
