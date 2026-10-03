package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.orchestration.ProcessingFailureType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado normalizado da classificação de uma falha operacional.
 *
 * <p>A classificação mantém dimensões semanticamente independentes:</p>
 *
 * <pre>
 * type
 *     semântica de retry: TRANSIENT ou PERMANENT
 *
 * origin
 *     origem operacional: INTERNAL ou EXTERNAL
 *
 * category
 *     natureza operacional da falha
 *
 * handlingActions
 *     ações operacionais associadas à classificação
 *
 * code
 *     código diagnóstico estável e específico
 *
 * message
 *     mensagem segura para diagnóstico
 * </pre>
 *
 * <p>Este objeto não altera diretamente o estado de jobs. Ele descreve
 * a falha para que políticas de orquestração, observabilidade e
 * recuperação possam tomar decisões sem misturar essas dimensões.</p>
 */
public record FailureClassification(
    ProcessingFailureType type,
    OperationalFailureOrigin origin,
    FailureCategory category,
    Set<FailureHandlingAction> handlingActions,
    String code,
    String message
) {

    public FailureClassification {

        Objects.requireNonNull(
            type,
            "type must not be null"
        );

        Objects.requireNonNull(
            origin,
            "origin must not be null"
        );

        Objects.requireNonNull(
            category,
            "category must not be null"
        );

        Objects.requireNonNull(
            handlingActions,
            "handlingActions must not be null"
        );

        if (handlingActions.isEmpty()) {

            throw new IllegalArgumentException(
                "handlingActions must not be empty"
            );
        }

        EnumSet<FailureHandlingAction> normalizedActions =
            EnumSet.copyOf(
                handlingActions
            );

        if (type == ProcessingFailureType.TRANSIENT
            && !normalizedActions.contains(
            FailureHandlingAction.RETRY
        )) {

            throw new IllegalArgumentException(
                "transient classification must include RETRY action"
            );
        }

        if (type == ProcessingFailureType.PERMANENT
            && normalizedActions.contains(
            FailureHandlingAction.RETRY
        )) {

            throw new IllegalArgumentException(
                "permanent classification must not include RETRY action"
            );
        }

        handlingActions =
            Collections.unmodifiableSet(
                normalizedActions
            );

        code =
            requireText(
                code,
                "code must not be blank"
            );

        message =
            requireText(
                message,
                "message must not be blank"
            );
    }

    /**
     * Construtor de compatibilidade para classificadores ou testes que
     * ainda utilizem o contrato anterior da FASE 12.
     *
     * <p>Novos classificadores devem preferir o construtor completo,
     * informando explicitamente origem, categoria e ações.</p>
     *
     * <p>A ausência histórica dessas dimensões é representada como
     * INTERNAL + UNKNOWN, mantendo a semântica de retry existente.</p>
     */
    public FailureClassification(
        ProcessingFailureType type,
        String code,
        String message
    ) {

        this(
            type,
            OperationalFailureOrigin.INTERNAL,
            FailureCategory.UNKNOWN,
            defaultActions(
                type
            ),
            code,
            message
        );
    }

    /**
     * Mantém a semântica histórica usada pela orquestração.
     *
     * @return true somente quando a classificação admite retry
     */
    public boolean retryable() {

        return type
            == ProcessingFailureType.TRANSIENT;
    }

    /**
     * Verifica se uma ação operacional pertence à classificação.
     */
    public boolean requires(
        FailureHandlingAction action
    ) {

        Objects.requireNonNull(
            action,
            "action must not be null"
        );

        return handlingActions.contains(
            action
        );
    }

    private static Set<FailureHandlingAction> defaultActions(
        ProcessingFailureType type
    ) {

        Objects.requireNonNull(
            type,
            "type must not be null"
        );

        return switch (type) {

            case TRANSIENT ->
                EnumSet.of(
                    FailureHandlingAction.RETRY
                );

            case PERMANENT ->
                EnumSet.of(
                    FailureHandlingAction.REJECT
                );
        };
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
