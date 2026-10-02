package com.raspingamazon.domain.publication.scheduling;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Configuração versionada da cadência operacional de publicação.
 *
 * <p>Quota e cadência são conceitos distintos:</p>
 *
 * <pre>
 * quota
 *     = quantas publicações podem ocupar um dia operacional
 *
 * cadência
 *     = em quais intervalos e janela essas publicações
 *       podem ser disponibilizadas
 * </pre>
 *
 * <p>O perfil não executa scheduling e não conhece PostgreSQL,
 * environment variables ou canais externos.</p>
 *
 * <p>Uma versão histórica não deve ser reutilizada para representar
 * uma nova decisão operacional, mesmo quando os valores voltarem a
 * ser iguais a uma versão antiga.</p>
 *
 * <p>Nesta versão do contrato, a janela precisa começar e terminar
 * dentro da mesma data operacional. Janelas que atravessam a
 * meia-noite exigiriam redefinir também a semântica de quotaDate
 * da outbox.</p>
 */
public record PublicationCadenceProfile(
    String version,
    Duration interval,
    LocalTime windowStart,
    LocalTime windowEnd,
    ZoneId zone
) {

    public PublicationCadenceProfile {

        version =
            requireText(
                version,
                "version"
            );

        Objects.requireNonNull(
            interval,
            "interval must not be null"
        );

        Objects.requireNonNull(
            windowStart,
            "windowStart must not be null"
        );

        Objects.requireNonNull(
            windowEnd,
            "windowEnd must not be null"
        );

        Objects.requireNonNull(
            zone,
            "zone must not be null"
        );

        if (interval.isZero()
            || interval.isNegative()) {

            throw new IllegalArgumentException(
                "interval must be positive"
            );
        }

        /*
         * O contrato de persistência utiliza segundos inteiros.
         *
         * Uma duração como PT1.5S não pode ser silenciosamente
         * arredondada para PT1S.
         */
        if (interval.getNano() != 0) {

            throw new IllegalArgumentException(
                "interval must use whole seconds"
            );
        }

        /*
         * A janela precisa pertencer integralmente ao mesmo
         * quotaDate.
         */
        if (!windowEnd.isAfter(
            windowStart
        )) {

            throw new IllegalArgumentException(
                "windowEnd must be after windowStart "
                    + "within the same operational day"
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
