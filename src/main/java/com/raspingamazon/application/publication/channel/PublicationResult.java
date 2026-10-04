package com.raspingamazon.application.publication.channel;

import java.util.Objects;
import java.util.Optional;

/**
 * Resultado estruturado devolvido por um PublicationChannel.
 *
 * <p>O resultado separa três conceitos:</p>
 *
 * <ul>
 *     <li>classificação operacional;</li>
 *     <li>referência eventualmente devolvida pelo provider;</li>
 *     <li>código estável de erro quando houver falha ou ambiguidade.</li>
 * </ul>
 *
 * <p>Exceções específicas de bibliotecas externas não atravessam
 * este contrato.</p>
 */
public record PublicationResult(
    PublicationResultStatus status,
    String providerReference,
    String errorCode
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
            errorCode
        );
    }

    public static PublicationResult success(
        String providerReference
    ) {

        return new PublicationResult(
            PublicationResultStatus.SUCCESS,
            providerReference,
            null
        );
    }

    public static PublicationResult failedTransient(
        String errorCode
    ) {

        return failedTransient(
            errorCode,
            null
        );
    }

    public static PublicationResult failedTransient(
        String errorCode,
        String providerReference
    ) {

        return new PublicationResult(
            PublicationResultStatus.FAILED_TRANSIENT,
            providerReference,
            errorCode
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
            errorCode
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
            errorCode
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

    private static void validateConsistency(
        PublicationResultStatus status,
        String errorCode
    ) {

        if (status == PublicationResultStatus.SUCCESS) {

            if (errorCode != null) {

                throw new IllegalArgumentException(
                    "successful publication result "
                        + "must not contain errorCode"
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
