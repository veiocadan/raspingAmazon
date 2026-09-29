package com.raspingamazon.infrastructure.publication.channel;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapPublicationChannelResolverTest {

    @Test
    void shouldResolveConfiguredChannelByExactIdentity() {

        PublicationChannel fake =
            command ->
                PublicationResult.success(
                    "provider-reference"
                );

        MapPublicationChannelResolver resolver =
            new MapPublicationChannelResolver(
                Map.of(
                    "FAKE",
                    fake
                )
            );

        assertSame(
            fake,
            resolver.resolve(
                "FAKE"
            )
        );
    }

    @Test
    void shouldRejectUnknownChannel() {

        MapPublicationChannelResolver resolver =
            new MapPublicationChannelResolver(
                Map.of(
                    "FAKE",
                    command ->
                        PublicationResult.success(
                            null
                        )
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    "UNKNOWN"
                )
        );
    }

    @Test
    void shouldNotNormalizeChannelIdentitySilently() {

        MapPublicationChannelResolver resolver =
            new MapPublicationChannelResolver(
                Map.of(
                    "FAKE",
                    command ->
                        PublicationResult.success(
                            null
                        )
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    "fake"
                )
        );
    }

    @Test
    void shouldRejectBlankConfiguredChannel() {

        Map<String, PublicationChannel> channels =
            new HashMap<>();

        channels.put(
            "   ",
            command ->
                PublicationResult.success(
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new MapPublicationChannelResolver(
                    channels
                )
        );
    }

    @Test
    void shouldRejectNullConfiguredAdapter() {

        Map<String, PublicationChannel> channels =
            new HashMap<>();

        channels.put(
            "FAKE",
            null
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new MapPublicationChannelResolver(
                    channels
                )
        );
    }
}
