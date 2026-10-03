package com.raspingamazon.application.publication.outbox;

import com.raspingamazon.application.publication.channel.PublicationCommand;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa uma unidade persistente de trabalho da outbox.
 *
 * <p>A outbox contém o conteúdo final necessário para a entrega.
 * Portanto o worker não precisa reconstruir DealEvaluation,
 * OfferSnapshot ou Publication para publicar.</p>
 *
 * <p>Uma entrada pode representar:</p>
 *
 * <ul>
 *     <li>
 *         entrega primária, que possui quotaProfileVersion
 *         e quotaDate;
 *     </li>
 *     <li>
 *         entrega derivada, que reutiliza uma seleção já aprovada
 *         e não reserva quota adicional.
 *     </li>
 * </ul>
 *
 * <p>Quota profile e quota date formam uma unidade semântica:
 * ambos devem estar presentes ou ambos devem estar ausentes.</p>
 *
 * <p>DELIVERY_UNKNOWN é terminal do ponto de vista do processamento
 * automático. O sistema não pode transformar esse estado em retry
 * somente porque um lease expirou.</p>
 */
public record PublicationOutboxItem(
    long id,
    long publicationId,
    long selectionRunId,
    int selectionPosition,
    String channel,
    String destination,
    String content,
    String quotaProfileVersion,
    LocalDate quotaDate,
    PublicationOutboxStatus status,
    OffsetDateTime availableAt,
    OffsetDateTime lockedAt,
    String lockedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime finishedAt
) {

    public PublicationOutboxItem {

        requirePositive(
            id,
            "id"
        );

        requirePositive(
            publicationId,
            "publicationId"
        );

        requirePositive(
            selectionRunId,
            "selectionRunId"
        );

        if (selectionPosition <= 0) {

            throw new IllegalArgumentException(
                "selectionPosition must be positive"
            );
        }

        channel =
            requireText(
                channel,
                "channel"
            );

        destination =
            requireText(
                destination,
                "destination"
            );

        content =
            requireText(
                content,
                "content"
            );

        validateQuotaReservation(
            quotaProfileVersion,
            quotaDate
        );

        if (quotaProfileVersion != null) {

            quotaProfileVersion =
                requireText(
                    quotaProfileVersion,
                    "quotaProfileVersion"
                );
        }

        Objects.requireNonNull(
            status,
            "status must not be null"
        );

        Objects.requireNonNull(
            availableAt,
            "availableAt must not be null"
        );

        Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );

        Objects.requireNonNull(
            updatedAt,
            "updatedAt must not be null"
        );

        validateLock(
            status,
            lockedAt,
            lockedBy
        );

        validateFinishedAt(
            status,
            finishedAt
        );
    }

    /**
     * Constrói diretamente o comando autocontido esperado pelo canal.
     */
    public PublicationCommand command() {

        return new PublicationCommand(
            publicationId,
            channel,
            destination,
            content
        );
    }

    /**
     * Informa se esta entrada representa a reserva real de uma
     * posição da quota operacional.
     */
    public boolean reservesQuota() {

        return quotaProfileVersion != null;
    }

    /**
     * Informa se esta entrada é uma entrega derivada da seleção,
     * sem consumo de uma segunda vaga de quota.
     */
    public boolean derivedDelivery() {

        return !reservesQuota();
    }

    public boolean processing() {

        return status
            == PublicationOutboxStatus.PROCESSING;
    }

    /**
     * Indica que o processamento automático desta unidade terminou.
     *
     * <p>DELIVERY_UNKNOWN também é terminal. Ele não significa sucesso,
     * mas exige decisão explícita antes de qualquer reprocessamento.</p>
     */
    public boolean finished() {

        return status
            == PublicationOutboxStatus.SUCCEEDED
            || status
            == PublicationOutboxStatus.FAILED_TRANSIENT
            || status
            == PublicationOutboxStatus.FAILED_PERMANENT
            || status
            == PublicationOutboxStatus.DELIVERY_UNKNOWN;
    }

    private static void validateQuotaReservation(
        String quotaProfileVersion,
        LocalDate quotaDate
    ) {

        boolean hasQuotaProfile =
            quotaProfileVersion != null;

        boolean hasQuotaDate =
            quotaDate != null;

        if (hasQuotaProfile != hasQuotaDate) {

            throw new IllegalArgumentException(
                "quotaProfileVersion and quotaDate "
                    + "must both be present or both be null"
            );
        }
    }

    private static void validateLock(
        PublicationOutboxStatus status,
        OffsetDateTime lockedAt,
        String lockedBy
    ) {

        if (status == PublicationOutboxStatus.PROCESSING) {

            Objects.requireNonNull(
                lockedAt,
                "PROCESSING outbox item requires lockedAt"
            );

            requireText(
                lockedBy,
                "lockedBy"
            );

            return;
        }

        if (lockedAt != null
            || lockedBy != null) {

            throw new IllegalArgumentException(
                "Only PROCESSING outbox item may hold a worker lock"
            );
        }
    }

    private static void validateFinishedAt(
        PublicationOutboxStatus status,
        OffsetDateTime finishedAt
    ) {

        boolean terminal =
            status == PublicationOutboxStatus.SUCCEEDED
                || status
                == PublicationOutboxStatus.FAILED_TRANSIENT
                || status
                == PublicationOutboxStatus.FAILED_PERMANENT
                || status
                == PublicationOutboxStatus.DELIVERY_UNKNOWN;

        if (terminal
            && finishedAt == null) {

            throw new IllegalArgumentException(
                "Terminal outbox item requires finishedAt"
            );
        }

        if (!terminal
            && finishedAt != null) {

            throw new IllegalArgumentException(
                "Non-terminal outbox item must not have finishedAt"
            );
        }
    }

    private static void requirePositive(
        long value,
        String fieldName
    ) {

        if (value <= 0L) {

            throw new IllegalArgumentException(
                fieldName + " must be positive"
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

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }
}
