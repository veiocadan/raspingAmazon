package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;
import com.raspingamazon.application.orchestration.ProcessingFailureType;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Objects;

/**
 * Classificação padrão das falhas conhecidas pela aplicação.
 *
 * <p>A política é deliberadamente conservadora:</p>
 *
 * <ul>
 *     <li>falhas claramente transitórias recebem retry;</li>
 *     <li>restrições explícitas da fonte não recebem retry automático;</li>
 *     <li>falhas de entrada/regra/programação não recebem retry;</li>
 *     <li>falhas desconhecidas são permanentes até serem classificadas
 *     explicitamente.</li>
 * </ul>
 *
 * <p>Isso evita loops automáticos de retry para bugs, dados inválidos
 * ou páginas de proteção de uma fonte externa.</p>
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
         * Portanto ela precisa ser localizada antes da regra genérica
         * de CollectionException sem status HTTP.
         */
        SourceRestrictionException sourceRestriction =
            findCause(
                failure,
                SourceRestrictionException.class
            );

        if (sourceRestriction != null) {

            return permanentFailure(
                "SOURCE_RESTRICTION_"
                    + sourceRestriction.restrictionType()
                    .name(),
                sourceRestriction
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

        if (hasCause(
            failure,
            HttpTimeoutException.class
        )
            || hasCause(
            failure,
            HttpConnectTimeoutException.class
        )
            || hasCause(
            failure,
            SocketTimeoutException.class
        )) {

            return transientFailure(
                "NETWORK_TIMEOUT",
                failure
            );
        }

        if (hasCause(
            failure,
            ConnectException.class
        )) {

            return transientFailure(
                "NETWORK_CONNECTION_FAILED",
                failure
            );
        }

        if (hasCause(
            failure,
            SQLTransientException.class
        )
            || hasCause(
            failure,
            SQLRecoverableException.class
        )) {

            return transientFailure(
                "DATABASE_TRANSIENT",
                failure
            );
        }

        if (failure
            instanceof IllegalArgumentException) {

            return permanentFailure(
                "INVALID_PROCESSING_INPUT",
                failure
            );
        }

        if (failure
            instanceof IllegalStateException) {

            return permanentFailure(
                "INVALID_PROCESSING_STATE",
                failure
            );
        }

        return permanentFailure(
            "UNCLASSIFIED_FAILURE",
            failure
        );
    }

    private FailureClassification classifyCollection(
        CollectionException failure
    ) {

        Integer statusCode =
            failure.httpStatusCode();

        /*
         * Ausência de status em uma CollectionException genérica
         * significa que a coleta falhou antes de obter uma resposta
         * HTTP utilizável.
         *
         * SourceRestrictionException já foi tratada anteriormente.
         */
        if (statusCode == null) {

            return transientFailure(
                "COLLECTION_TRANSPORT",
                failure
            );
        }

        /*
         * Request timeout, too early e rate limiting são
         * explicitamente retryable.
         */
        if (statusCode == 408
            || statusCode == 425
            || statusCode == 429) {

            return transientFailure(
                "COLLECTION_HTTP_" + statusCode,
                failure
            );
        }

        /*
         * Falhas 5xx pertencem ao servidor remoto.
         */
        if (statusCode >= 500
            && statusCode <= 599) {

            return transientFailure(
                "COLLECTION_HTTP_" + statusCode,
                failure
            );
        }

        /*
         * Outros 4xx normalmente representam uma requisição que
         * não será corrigida repetindo exatamente o mesmo job.
         *
         * Isso inclui HTTP 403.
         */
        if (statusCode >= 400
            && statusCode <= 499) {

            return permanentFailure(
                "COLLECTION_HTTP_" + statusCode,
                failure
            );
        }

        return permanentFailure(
            "COLLECTION_HTTP_UNEXPECTED",
            failure
        );
    }

    private FailureClassification transientFailure(
        String code,
        Throwable failure
    ) {

        return new FailureClassification(
            ProcessingFailureType.TRANSIENT,
            code,
            safeMessage(
                failure
            )
        );
    }

    private FailureClassification permanentFailure(
        String code,
        Throwable failure
    ) {

        return new FailureClassification(
            ProcessingFailureType.PERMANENT,
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

    private boolean hasCause(
        Throwable failure,
        Class<? extends Throwable> type
    ) {

        return findCause(
            failure,
            type
        ) != null;
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
