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

        quotaProfileVersion =
            requireText(
                quotaProfileVersion,
                "quotaProfileVersion"
            );

        Objects.requireNonNull(
            quotaDate,
            "quotaDate must not be null"
        );

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

    public boolean processing() {

        return status
            == PublicationOutboxStatus.PROCESSING;
    }

    public boolean finished() {

        return status
            == PublicationOutboxStatus.SUCCEEDED
            || status
            == PublicationOutboxStatus.FAILED_TRANSIENT
            || status
            == PublicationOutboxStatus.FAILED_PERMANENT;
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

        if (lockedAt != null || lockedBy != null) {

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
                == PublicationOutboxStatus.FAILED_PERMANENT;

        if (terminal && finishedAt == null) {

            throw new IllegalArgumentException(
                "Terminal outbox item requires finishedAt"
            );
        }

        if (!terminal && finishedAt != null) {

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
