package com.raspingamazon.infrastructure.publication.channel;

import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.channel.PublicationResultStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePublicationChannelTest {

    @Test
    void shouldReturnConfiguredSuccessAndRecordCommand() {

        PublicationResult configuredResult =
            PublicationResult.success(
                "fake-provider-123"
            );

        FakePublicationChannel channel =
            new FakePublicationChannel(
                configuredResult
            );

        PublicationCommand command =
            command(
                1L,
                "Oferta A"
            );

        PublicationResult result =
            channel.publish(
                command
            );

        assertSame(
            configuredResult,
            result
        );

        assertEquals(
            PublicationResultStatus.SUCCESS,
            result.status()
        );

        assertEquals(
            1,
            channel.callCount()
        );

        assertSame(
            command,
            channel.lastCommand()
                .orElseThrow()
        );

        assertEquals(
            List.of(
                command
            ),
            channel.publishedCommands()
        );
    }

    @Test
    void shouldReturnConfiguredTransientFailure() {

        PublicationResult configuredResult =
            PublicationResult.failedTransient(
                "FAKE_TEMPORARY_FAILURE"
            );

        FakePublicationChannel channel =
            new FakePublicationChannel(
                configuredResult
            );

        PublicationResult result =
            channel.publish(
                command(
                    1L,
                    "Oferta temporariamente indisponível"
                )
            );

        assertSame(
            configuredResult,
            result
        );

        assertTrue(
            result.transientFailure()
        );

        assertEquals(
            "FAKE_TEMPORARY_FAILURE",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            1,
            channel.callCount()
        );
    }

    @Test
    void shouldReturnConfiguredPermanentFailure() {

        PublicationResult configuredResult =
            PublicationResult.failedPermanent(
                "FAKE_INVALID_DESTINATION"
            );

        FakePublicationChannel channel =
            new FakePublicationChannel(
                configuredResult
            );

        PublicationResult result =
            channel.publish(
                command(
                    1L,
                    "Oferta com falha permanente"
                )
            );

        assertSame(
            configuredResult,
            result
        );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "FAKE_INVALID_DESTINATION",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            1,
            channel.callCount()
        );
    }

    @Test
    void shouldPreservePublishedCommandOrder() {

        FakePublicationChannel channel =
            new FakePublicationChannel(
                PublicationResult.success(
                    "fake-provider"
                )
            );

        PublicationCommand first =
            command(
                1L,
                "Primeira oferta"
            );

        PublicationCommand second =
            command(
                2L,
                "Segunda oferta"
            );

        PublicationCommand third =
            command(
                3L,
                "Terceira oferta"
            );

        channel.publish(
            first
        );

        channel.publish(
            second
        );

        channel.publish(
            third
        );

        assertEquals(
            3,
            channel.callCount()
        );

        assertEquals(
            List.of(
                first,
                second,
                third
            ),
            channel.publishedCommands()
        );

        assertSame(
            third,
            channel.lastCommand()
                .orElseThrow()
        );
    }

    @Test
    void shouldRejectNullCommandWithoutRecordingCall() {

        FakePublicationChannel channel =
            new FakePublicationChannel(
                PublicationResult.success(
                    null
                )
            );

        assertThrows(
            NullPointerException.class,
            () ->
                channel.publish(
                    null
                )
        );

        assertEquals(
            0,
            channel.callCount()
        );

        assertTrue(
            channel.publishedCommands()
                .isEmpty()
        );

        assertTrue(
            channel.lastCommand()
                .isEmpty()
        );
    }

    @Test
    void shouldRejectNullConfiguredResult() {

        assertThrows(
            NullPointerException.class,
            () ->
                new FakePublicationChannel(
                    null
                )
        );
    }

    private PublicationCommand command(
        long publicationId,
        String content
    ) {

        return new PublicationCommand(
            publicationId,
            "FAKE",
            "fake-destination",
            content
        );
    }
}
