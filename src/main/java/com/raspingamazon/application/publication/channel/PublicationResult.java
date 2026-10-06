package com.raspingamazon.application.publication.channel;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado estruturado devolvido por um PublicationChannel.
 *
 * <p>O resultado separa quatro conceitos:</p>
 *
 * <ul>
 *     <li>classificação operacional;</li>
 *     <li>referência eventualmente devolvida pelo provider;</li>
 *     <li>código estável de erro quando houver falha ou ambiguidade;</li>
 *     <li>limite temporal mínimo eventualmente imposto pelo provider
 *         para um retry transitório.</li>
 * </ul>
 *
 * <p>{@code retryNotBefore} é somente um hint de piso temporal. Ele
 * não cria orçamento adicional de retry e só é válido para
 * {@link PublicationResultStatus#FAILED_TRANSIENT}.</p>
 *
 * <p>Exceções específicas de bibliotecas externas não atravessam
 * este contrato.</p>
 */
public record PublicationResult(
    PublicationResultStatus status,
    String providerReference,
    String errorCode,
    OffsetDateTime retryNotBefore
) {

    public PublicationResult {

        Objects.requireNonNull(
            status,
            "status must not be null"
        );

        providerReference =
            optionalText(
                providerReference,
                "providerReference"
            );

        errorCode =
            optionalText(
                errorCode,
                "errorCode"
            );

        validateConsistency(
            status,
            errorCode,
            retryNotBefore
        );
    }

    /**
     * Construtor de compatibilidade para callers anteriores ao hint
     * temporal de retry da FASE 20-G.
     */
    public PublicationResult(
        PublicationResultStatus status,
        String providerReference,
        String errorCode
    ) {

        this(
            status,
            providerReference,
            errorCode,
            null
        );
    }

    public static PublicationResult success(
        String providerReference
    ) {

        return new PublicationResult(
            PublicationResultStatus.SUCCESS,
            providerReference,
            null,
            null
        );
    }

    public static PublicationResult failedTransient(
        String errorCode
    ) {

        return failedTransient(
            errorCode,
            null,
            null
        );
    }

    public static PublicationResult failedTransient(
        String errorCode,
        String providerReference
    ) {

        return failedTransient(
            errorCode,
            providerReference,
            null
        );
    }

    /**
     * Falha transitória com piso temporal imposto pelo provider.
     */
    public static PublicationResult failedTransientWithRetryNotBefore(
        String errorCode,
        OffsetDateTime retryNotBefore
    ) {

        return failedTransient(
            errorCode,
            null,
            Objects.requireNonNull(
                retryNotBefore,
                "retryNotBefore must not be null"
            )
        );
    }

    public static PublicationResult failedTransient(
        String errorCode,
        String providerReference,
        OffsetDateTime retryNotBefore
    ) {

        return new PublicationResult(
            PublicationResultStatus.FAILED_TRANSIENT,
            providerReference,
            errorCode,
            retryNotBefore
        );
    }

    public static PublicationResult failedPermanent(
        String errorCode
    ) {

        return failedPermanent(
            errorCode,
            null
        );
    }

    public static PublicationResult failedPermanent(
        String errorCode,
        String providerReference
    ) {

        return new PublicationResult(
            PublicationResultStatus.FAILED_PERMANENT,
            providerReference,
            errorCode,
            null
        );
    }

    public static PublicationResult deliveryUnknown(
        String errorCode
    ) {

        return deliveryUnknown(
            errorCode,
            null
        );
    }

    public static PublicationResult deliveryUnknown(
        String errorCode,
        String providerReference
    ) {

        return new PublicationResult(
            PublicationResultStatus.DELIVERY_UNKNOWN,
            providerReference,
            errorCode,
            null
        );
    }

    public boolean successful() {

        return status
            == PublicationResultStatus.SUCCESS;
    }

    public boolean transientFailure() {

        return status
            == PublicationResultStatus.FAILED_TRANSIENT;
    }

    public boolean permanentFailure() {

        return status
            == PublicationResultStatus.FAILED_PERMANENT;
    }

    public boolean deliveryUnknown() {

        return status
            == PublicationResultStatus.DELIVERY_UNKNOWN;
    }

    public Optional<String> providerReferenceValue() {

        return Optional.ofNullable(
            providerReference
        );
    }

    public Optional<String> errorCodeValue() {

        return Optional.ofNullable(
            errorCode
        );
    }

    public Optional<OffsetDateTime> retryNotBeforeValue() {

        return Optional.ofNullable(
            retryNotBefore
        );
    }

    private static void validateConsistency(
        PublicationResultStatus status,
        String errorCode,
        OffsetDateTime retryNotBefore
    ) {

        if (status == PublicationResultStatus.SUCCESS) {

            if (errorCode != null) {

                throw new IllegalArgumentException(
                    "successful publication result "
                        + "must not contain errorCode"
                );
            }

            if (retryNotBefore != null) {

                throw new IllegalArgumentException(
                    "successful publication result "
                        + "must not contain retryNotBefore"
                );
            }

            return;
        }

        if (errorCode == null) {

            throw new IllegalArgumentException(
                "non-success publication result "
                    + "must contain errorCode"
            );
        }

        if (retryNotBefore != null
            && status
            != PublicationResultStatus.FAILED_TRANSIENT) {

            throw new IllegalArgumentException(
                "retryNotBefore is only valid for FAILED_TRANSIENT"
            );
        }
    }

    private static String optionalText(
        String value,
        String fieldName
    ) {

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank when present"
            );
        }

        return value;
    }
}
