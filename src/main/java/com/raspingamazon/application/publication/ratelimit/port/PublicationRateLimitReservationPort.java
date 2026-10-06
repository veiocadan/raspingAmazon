package com.raspingamazon.application.publication.ratelimit.port;

import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitReservation;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Estado persistente de admissão para uma integração externa de
 * publicação.
 *
 * <p>A operação principal reserva atomicamente o slot atual.</p>
 *
 * <p>A FASE 20-G também permite elevar o piso temporal quando o
 * próprio provider informa um {@code Retry-After}. Esse piso pertence
 * à integração física compartilhada, não somente à outbox que recebeu
 * a resposta.</p>
 *
 * <p>A implementação deve ser segura para múltiplos workers e
 * múltiplas instâncias da aplicação.</p>
 */
@FunctionalInterface
public interface PublicationRateLimitReservationPort {

    /**
     * Reserva atomicamente um slot de chamada.
     */
    PublicationRateLimitReservation reserve(
        String integrationKey,
        Duration minimumInterval,
        OffsetDateTime requestedAt
    );

    /**
     * Eleva o primeiro instante permitido para toda a integração.
     *
     * <p>A implementação deve aplicar semanticamente:</p>
     *
     * <pre>
     * nextAllowedAt =
     *     max(
     *         nextAllowedAt persistido,
     *         notBefore,
     *         observedAt
     *     )
     * </pre>
     *
     * <p>O método nunca deve reduzir o limite já persistido e não
     * reserva um slot futuro para uma outbox específica.</p>
     *
     * <p>O método default preserva implementações históricas baseadas
     * em lambda. Quando um worker com rate limiting habilitado recebe
     * um provider floor, a implementação operacional deve
     * sobrescrevê-lo; falhar explicitamente é mais seguro do que
     * ignorar silenciosamente o limite do provider.</p>
     *
     * @param integrationKey integração física compartilhada
     * @param notBefore piso temporal informado pelo provider
     * @param observedAt instante em que o resultado foi observado
     * @return nextAllowedAt efetivo depois da operação
     */
    default OffsetDateTime extendNotBefore(
        String integrationKey,
        OffsetDateTime notBefore,
        OffsetDateTime observedAt
    ) {

        Objects.requireNonNull(
            integrationKey,
            "integrationKey must not be null"
        );

        Objects.requireNonNull(
            notBefore,
            "notBefore must not be null"
        );

        Objects.requireNonNull(
            observedAt,
            "observedAt must not be null"
        );

        throw new UnsupportedOperationException(
            "PublicationRateLimitReservationPort does not support "
                + "provider rate-limit floors"
        );
    }
}
