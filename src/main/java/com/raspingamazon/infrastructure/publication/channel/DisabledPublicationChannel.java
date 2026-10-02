package com.raspingamazon.infrastructure.publication.channel;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;

import java.util.Objects;

/**
 * PublicationChannel utilizado quando um canal conhecido existe
 * na aplicação, mas está operacionalmente desabilitado.
 *
 * <p>Um canal desabilitado não executa I/O externo.</p>
 *
 * <p>A tentativa termina como falha permanente porque repetir a
 * mesma operação enquanto a configuração continuar desabilitada
 * não produzirá resultado diferente.</p>
 *
 * <p>A reativação futura do canal é uma decisão explícita de
 * configuração e não uma política automática de retry.</p>
 */
public final class DisabledPublicationChannel
    implements PublicationChannel {

    private final String errorCode;

    public DisabledPublicationChannel(
        String errorCode
    ) {

        this.errorCode =
            requireText(
                errorCode,
                "errorCode"
            );
    }

    @Override
    public PublicationResult publish(
        PublicationCommand command
    ) {

        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        return PublicationResult.failedPermanent(
            errorCode
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
