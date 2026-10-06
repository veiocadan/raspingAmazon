package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.channel.PublicationResultStatus;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramChannelRetryAfterTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-10-06T21:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    @Test
    void http429ShouldCarryRetryAfterAsProviderFloor() {

        TelegramChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    429,
                    "{\"ok\":false}",
                    Map.of(
                        "Retry-After",
                        List.of(
                            "120"
                        )
                    )
                )
            );

        PublicationResult result =
            channel.publish(
                command()
            );

        assertEquals(
            PublicationResultStatus.FAILED_TRANSIENT,
            result.status()
        );

        assertEquals(
            "TELEGRAM_RATE_LIMITED",
            result.errorCode()
        );

        assertEquals(
            OffsetDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC
            ).plusSeconds(
                120
            ),
            result.retryNotBeforeValue()
                .orElseThrow()
        );
    }

    @Test
    void http429WithoutValidRetryAfterShouldRemainTransientWithoutFloor() {

        TelegramChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    429,
                    "{\"ok\":false}",
                    Map.of(
                        "Retry-After",
                        List.of(
                            "invalid"
                        )
                    )
                )
            );

        PublicationResult result =
            channel.publish(
                command()
            );

        assertEquals(
            PublicationResultStatus.FAILED_TRANSIENT,
            result.status()
        );

        assertTrue(
            result.retryNotBeforeValue()
                .isEmpty()
        );
    }

    @Test
    void explicitTelegram429ShouldAlsoUseRetryAfterHeader() {

        TelegramChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    200,
                    "{\"ok\":false,\"error_code\":429}",
                    Map.of(
                        "Retry-After",
                        List.of(
                            "60"
                        )
                    )
                )
            );

        PublicationResult result =
            channel.publish(
                command()
            );

        assertEquals(
            PublicationResultStatus.FAILED_TRANSIENT,
            result.status()
        );

        assertEquals(
            OffsetDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC
            ).plusSeconds(
                60
            ),
            result.retryNotBeforeValue()
                .orElseThrow()
        );
    }

    private TelegramChannel channelReturning(
        PublicationHttpResponse response
    ) {

        return new TelegramChannel(
            new TelegramChannelConfig(
                URI.create(
                    "https://api.telegram.example"
                ),
                "12345:test-token",
                Duration.ofSeconds(
                    5
                )
            ),
            request -> response,
            new ObjectMapper(),
            TelegramChannel.MessageFormat.PLAIN,
            CLOCK
        );
    }

    private PublicationCommand command() {

        return new PublicationCommand(
            1L,
            "TELEGRAM",
            "@test_channel",
            "Oferta https://example.com/product"
        );
    }
}
