package com.raspingamazon.infrastructure.publication.channel;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry simples e imutável de PublicationChannel.
 *
 * <p>A identidade do canal é comparada exatamente como persistida.
 * Nenhuma normalização silenciosa de maiúsculas/minúsculas é
 * realizada.</p>
 *
 * <p>Uma composição futura poderá registrar:</p>
 *
 * <pre>
 * TELEGRAM -> TelegramChannel
 * WHATSAPP -> WhatsAppChannel
 * </pre>
 *
 * <p>Na FASE 18 utilizaremos FakePublicationChannel.</p>
 */
public final class MapPublicationChannelResolver
    implements PublicationChannelResolver {

    private final Map<String, PublicationChannel> channels;

    public MapPublicationChannelResolver(
        Map<String, PublicationChannel> channels
    ) {

        Objects.requireNonNull(
            channels,
            "channels must not be null"
        );

        Map<String, PublicationChannel> validated =
            new LinkedHashMap<>();

        for (Map.Entry<String, PublicationChannel> entry
            : channels.entrySet()) {

            String channel =
                requireText(
                    entry.getKey(),
                    "channel key"
                );

            PublicationChannel adapter =
                Objects.requireNonNull(
                    entry.getValue(),
                    "PublicationChannel must not be null for "
                        + channel
                );

            validated.put(
                channel,
                adapter
            );
        }

        this.channels =
            Map.copyOf(
                validated
            );
    }

    @Override
    public PublicationChannel resolve(
        String channel
    ) {

        String validatedChannel =
            requireText(
                channel,
                "channel"
            );

        PublicationChannel resolved =
            channels.get(
                validatedChannel
            );

        if (resolved == null) {

            throw new IllegalStateException(
                "No PublicationChannel configured for channel "
                    + validatedChannel
            );
        }

        return resolved;
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
