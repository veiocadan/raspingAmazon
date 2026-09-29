package com.raspingamazon.domain.publication.selection;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuração versionada da política operacional de seleção
 * de publicações.
 *
 * <p>Os períodos de cooldown são configuração funcional e não
 * constantes internas da política.</p>
 *
 * <p>Semântica:</p>
 *
 * <pre>
 * elapsed < hardCooldown
 *     -> candidato temporariamente indisponível
 *
 * hardCooldown <= elapsed < preferredCooldown
 *     -> candidato elegível com prioridade reduzida
 *
 * elapsed >= preferredCooldown
 *     -> candidato elegível com prioridade normal
 * </pre>
 *
 * <p>Um valor zero desabilita a respectiva janela temporal.</p>
 *
 * <p>A precisão persistente da FASE 18 é de segundos inteiros.
 * Portanto durações com fração de segundo são rejeitadas para
 * impedir perda silenciosa de precisão durante auditoria.</p>
 */
public record PublicationSelectionProfile(
    String version,
    Duration hardCooldown,
    Duration preferredCooldown
) {

    public PublicationSelectionProfile {

        version =
            requireText(
                version,
                "version"
            );

        hardCooldown =
            requireNonNegativeWholeSecondDuration(
                hardCooldown,
                "hardCooldown"
            );

        preferredCooldown =
            requireNonNegativeWholeSecondDuration(
                preferredCooldown,
                "preferredCooldown"
            );

        if (preferredCooldown.compareTo(
            hardCooldown
        ) < 0) {

            throw new IllegalArgumentException(
                "preferredCooldown must not be shorter "
                    + "than hardCooldown"
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

    private static Duration requireNonNegativeWholeSecondDuration(
        Duration value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isNegative()) {
            throw new IllegalArgumentException(
                fieldName + " must not be negative"
            );
        }

        if (value.getNano() != 0) {
            throw new IllegalArgumentException(
                fieldName
                    + " must use whole-second precision"
            );
        }

        return value;
    }
}
