package com.raspingamazon.infrastructure.publication.ratelimit;

import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitPolicy;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitRule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação imutável da resolução canal lógico -> integração
 * física sujeita a rate limiting.
 *
 * <p>A identidade do canal permanece exata e case-sensitive.</p>
 */
public final class MapPublicationRateLimitPolicy
    implements PublicationRateLimitPolicy {

    private final Map<String, PublicationRateLimitRule> rules;

    public MapPublicationRateLimitPolicy(
        Map<String, PublicationRateLimitRule> rules
    ) {

        Objects.requireNonNull(
            rules,
            "rules must not be null"
        );

        Map<String, PublicationRateLimitRule> validated =
            new LinkedHashMap<>();

        rules.forEach(
            (channel, rule) -> {

                String validatedChannel =
                    requireText(
                        channel,
                        "channel"
                    );

                PublicationRateLimitRule validatedRule =
                    Objects.requireNonNull(
                        rule,
                        "rate-limit rule must not be null"
                    );

                validated.put(
                    validatedChannel,
                    validatedRule
                );
            }
        );

        this.rules =
            Map.copyOf(
                validated
            );
    }

    @Override
    public Optional<PublicationRateLimitRule> findRule(
        String channel
    ) {

        String validatedChannel =
            requireText(
                channel,
                "channel"
            );

        return Optional.ofNullable(
            rules.get(
                validatedChannel
            )
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

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }
}
