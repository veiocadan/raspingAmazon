package com.raspingamazon.infrastructure.publication.ratelimit;

import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitRule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPublicationRateLimitPolicyTest {

    @Test
    void shouldResolveExactLogicalChannel() {

        MapPublicationRateLimitPolicy policy =
            new MapPublicationRateLimitPolicy(
                Map.of(
                    "TELEGRAM",
                    new PublicationRateLimitRule(
                        "TELEGRAM_BOT_API",
                        Duration.ofSeconds(
                            1
                        )
                    )
                )
            );

        PublicationRateLimitRule rule =
            policy.findRule(
                "TELEGRAM"
            ).orElseThrow();

        assertEquals(
            "TELEGRAM_BOT_API",
            rule.integrationKey()
        );
    }

    @Test
    void shouldKeepChannelIdentityCaseSensitive() {

        MapPublicationRateLimitPolicy policy =
            new MapPublicationRateLimitPolicy(
                Map.of(
                    "TELEGRAM",
                    new PublicationRateLimitRule(
                        "TELEGRAM_BOT_API",
                        Duration.ofSeconds(
                            1
                        )
                    )
                )
            );

        assertTrue(
            policy.findRule(
                "telegram"
            ).isEmpty()
        );
    }

    @Test
    void unknownChannelShouldHaveNoRateLimitRule() {

        MapPublicationRateLimitPolicy policy =
            new MapPublicationRateLimitPolicy(
                Map.of()
            );

        assertTrue(
            policy.findRule(
                "UNKNOWN"
            ).isEmpty()
        );
    }
}
