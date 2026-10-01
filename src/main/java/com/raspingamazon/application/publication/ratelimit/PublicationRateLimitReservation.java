package com.raspingamazon.application.publication.ratelimit;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Resultado de uma tentativa atômica de admissão para uma integração
 * externa de publicação.
 *
 * <p>Há dois resultados possíveis:</p>
 *
 * <pre>
 * IMEDIATAMENTE PERMITIDO
 *
 * requestedAt == allowedAt
 * nextAllowedAt > allowedAt
 *
 * O slot atual foi adquirido e o estado persistente foi avançado.
 *
 *
 * ADIADO
 *
 * requestedAt < allowedAt
 * nextAllowedAt == allowedAt
 *
 * O provider ainda não pode ser chamado.
 *
 * Nenhum slot futuro foi reservado e o estado persistente NÃO foi
 * avançado. allowedAt informa somente o primeiro instante em que uma
 * nova tentativa de admissão pode ser feita.
 * </pre>
 *
 * <p>Não reservar slots futuros é importante para o worker
 * não bloqueante: a outbox pode voltar para PENDING e tentar
 * novamente quando availableAt chegar.</p>
 */
public record PublicationRateLimitReservation(
    String integrationKey,
    OffsetDateTime requestedAt,
    OffsetDateTime allowedAt,
    OffsetDateTime nextAllowedAt
) {

    public PublicationRateLimitReservation {

        integrationKey =
            requireText(
                integrationKey,
                "integrationKey"
            );

        Objects.requireNonNull(
            requestedAt,
            "requestedAt must not be null"
        );

        Objects.requireNonNull(
            allowedAt,
            "allowedAt must not be null"
        );

        Objects.requireNonNull(
            nextAllowedAt,
            "nextAllowedAt must not be null"
        );

        if (allowedAt.isBefore(
            requestedAt
        )) {

            throw new IllegalArgumentException(
                "allowedAt must not be before requestedAt"
            );
        }

        if (nextAllowedAt.isBefore(
            allowedAt
        )) {

            throw new IllegalArgumentException(
                "nextAllowedAt must not be before allowedAt"
            );
        }

        boolean immediatelyAllowed =
            allowedAt.equals(
                requestedAt
            );

        if (immediatelyAllowed
            && !nextAllowedAt.isAfter(
            allowedAt
        )) {

            throw new IllegalArgumentException(
                "Immediately allowed reservation must advance "
                    + "nextAllowedAt"
            );
        }

        boolean deferred =
            allowedAt.isAfter(
                requestedAt
            );

        if (deferred
            && !nextAllowedAt.equals(
            allowedAt
        )) {

            throw new IllegalArgumentException(
                "Deferred reservation must not reserve "
                    + "a future slot"
            );
        }
    }

    /**
     * Retorna true quando esta chamada adquiriu o slot atual.
     *
     * <p>Nesse caso o provider pode ser chamado imediatamente.</p>
     */
    public boolean immediatelyAllowed() {

        return allowedAt.equals(
            requestedAt
        );
    }

    /**
     * Retorna true quando o provider ainda não pode ser chamado.
     *
     * <p>allowedAt informa quando a outbox pode tentar adquirir
     * admissão novamente.</p>
     *
     * <p>O horário futuro não pertence exclusivamente a esta
     * outbox.</p>
     */
    public boolean deferred() {

        return allowedAt.isAfter(
            requestedAt
        );
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
