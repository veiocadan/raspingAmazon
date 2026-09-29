package com.raspingamazon.application.publication.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PublicationChannelTest {

    @Test
    void shouldAcceptSelfContainedCommandAndReturnStructuredResult() {

        PublicationCommand command =
            new PublicationCommand(
                100L,
                "TEST",
                "destination-1",
                "Conteúdo completamente pronto"
            );

        PublicationChannel channel =
            receivedCommand -> {

                assertSame(
                    command,
                    receivedCommand
                );

                return PublicationResult.success(
                    "fake-provider-reference"
                );
            };

        PublicationResult result =
            channel.publish(
                command
            );

        assertEquals(
            PublicationResultStatus.SUCCESS,
            result.status()
        );

        assertEquals(
            "fake-provider-reference",
            result.providerReferenceValue()
                .orElseThrow()
        );
    }
}
