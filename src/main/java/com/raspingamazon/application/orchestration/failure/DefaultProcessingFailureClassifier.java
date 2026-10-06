package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.SourceChangedException;
import com.raspingamazon.application.collection.contract.SourceDataUnavailableException;
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
 * Classificação operacional padrão das falhas conhecidas pela aplicação.
 *
 * <p>As dimensões permanecem independentes:</p>
 *
 * <ul>
 *     <li>semântica de retry;</li>
 *     <li>origem;</li>
 *     <li>categoria operacional;</li>
 *     <li>ações recomendadas;</li>
 *     <li>código diagnóstico.</li>
 * </ul>
 *
 * <p>A política é conservadora e fail-closed. Falhas desconhecidas,
 * mudanças estruturais e restrições explícitas não recebem retry
 * automático.</p>
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
         * As três exceções abaixo também derivam de
         * CollectionException.
         *
         * Portanto precisam ser classificadas antes da regra genérica
         * de collection.
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

        SourceChangedException sourceChanged =
            findCause(
                failure,
                SourceChangedException.class
            );

        if (sourceChanged != null) {

            return permanentFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.SOURCE_CHANGED,
                sourceChanged.errorCode(),
                sourceChanged,
                FailureHandlingAction.REJECT,
                FailureHandlingAction.PAUSE,
                FailureHandlingAction.ALERT,
                FailureHandlingAction.OPERATOR_INTERVENTION
            );
        }

        SourceDataUnavailableException dataUnavailable =
            findCause(
                failure,
                SourceDataUnavailableException.class
            );

        if (dataUnavailable != null) {

            return permanentFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.DATA_UNAVAILABLE,
                dataUnavailable.errorCode(),
                dataUnavailable,
                FailureHandlingAction.REJECT
            );
        }

        PublicationDispatchJobException
            publicationDispatchFailure =
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

        SQLException sqlFailure =
            findCause(
                failure,
                SQLException.class
            );

        if (sqlFailure != null) {

            return classifySql(
                sqlFailure
            );
        }

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

        if (statusCode == null) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "COLLECTION_TRANSPORT",
                failure
            );
        }

        if (statusCode == 429) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.RATE_LIMIT,
                "COLLECTION_HTTP_429",
                failure
            );
        }

        if (statusCode == 408
            || statusCode == 425) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "COLLECTION_HTTP_" + statusCode,
                failure
            );
        }

        if (statusCode >= 500
            && statusCode <= 599) {

            return transientFailure(
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.NETWORK,
                "COLLECTION_HTTP_" + statusCode,
                failure
            );
        }

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

        return permanentFailure(
            OperationalFailureOrigin.EXTERNAL,
            FailureCategory.UNKNOWN,
            "COLLECTION_HTTP_UNEXPECTED",
            failure,
            FailureHandlingAction.REJECT,
            FailureHandlingAction.ALERT
        );
    }

    private FailureClassification classifySql(
        SQLException failure
    ) {

        String sqlState =
            normalizeSqlState(
                failure.getSQLState()
            );

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

        if (failure instanceof SQLTransientException
            || failure instanceof SQLRecoverableException) {

            return transientFailure(
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.DATABASE,
                "DATABASE_TRANSIENT",
                failure
            );
        }

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

    private String normalizeSqlState(
        String sqlState
    ) {

        if (sqlState == null) {
            return null;
        }

        String normalized =
            sqlState
                .trim()
                .toUpperCase(
                    Locale.ROOT
                );

        return normalized.isEmpty()
            ? null
            : normalized;
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
