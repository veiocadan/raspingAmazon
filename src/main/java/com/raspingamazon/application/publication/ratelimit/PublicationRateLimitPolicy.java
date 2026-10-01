package com.raspingamazon.application.publication.ratelimit;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolve a regra de rate limiting de um canal lógico.
 *
 * <p>A ausência de regra significa que aquele canal não está sujeito
 * a rate limiting nesta composição.</p>
 *
 * <p>A decisão sobre quais canais compartilham uma integração física
 * pertence à composição da infraestrutura, não ao worker.</p>
 */
@FunctionalInterface
public interface PublicationRateLimitPolicy {

    Optional<PublicationRateLimitRule> findRule(
        String channel
    );

    /**
     * Política sem rate limiting.
     *
     * <p>É utilizada pelos construtores de compatibilidade e por
     * testes que não exercitam essa responsabilidade.</p>
     */
    static PublicationRateLimitPolicy disabled() {

        return channel -> {

            Objects.requireNonNull(
                channel,
                "channel must not be null"
            );

            return Optional.empty();
        };
    }
}
