package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationContentFormatter;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
import com.raspingamazon.infrastructure.config.WhatsAppManualStagingConfig;
import com.raspingamazon.infrastructure.publication.format.WhatsAppManualPublicationFormatter;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransport;

import java.util.Objects;

/**
 * Canal lógico utilizado no fluxo manual de publicação para WhatsApp.
 *
 * <p>Este adapter NÃO publica no WhatsApp e NÃO utiliza a API da Meta.</p>
 *
 * <p>O conteúdo canônico da Publication é formatado para WhatsApp e
 * entregue a um destino privado do Telegram. A publicação final no
 * WhatsApp permanece uma ação humana.</p>
 *
 * <p>A identidade lógica permanece WHATSAPP_MANUAL, enquanto o
 * transporte técnico utilizado pelo staging é o Telegram Bot API.</p>
 */
public final class WhatsAppManualStagingChannel
    implements PublicationChannel {

    private final WhatsAppManualStagingConfig stagingConfig;

    private final PublicationContentFormatter contentFormatter;

    private final TelegramChannel telegramChannel;

    /**
     * Construtor padrão utilizado pela composição de produção.
     */
    public WhatsAppManualStagingChannel(
        WhatsAppManualStagingConfig stagingConfig,
        TelegramChannelConfig telegramConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        this(
            stagingConfig,
            telegramConfig,
            transport,
            objectMapper,
            new WhatsAppManualPublicationFormatter()
        );
    }

    /**
     * Variante injetável para testes e futuras estratégias
     * de apresentação.
     */
    public WhatsAppManualStagingChannel(
        WhatsAppManualStagingConfig stagingConfig,
        TelegramChannelConfig telegramConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper,
        PublicationContentFormatter contentFormatter
    ) {

        this.stagingConfig =
            Objects.requireNonNull(
                stagingConfig,
                "stagingConfig must not be null"
            );

        Objects.requireNonNull(
            telegramConfig,
            "telegramConfig must not be null"
        );

        Objects.requireNonNull(
            transport,
            "transport must not be null"
        );

        Objects.requireNonNull(
            objectMapper,
            "objectMapper must not be null"
        );

        this.contentFormatter =
            Objects.requireNonNull(
                contentFormatter,
                "contentFormatter must not be null"
            );

        /*
         * No staging queremos receber texto pronto para copiar.
         *
         * Portanto:
         *
         * - preview Telegram desabilitado;
         * - parse_mode Telegram desabilitado;
         * - * e ~ permanecem literais.
         */
        TelegramChannelConfig stagingTelegramConfig =
            new TelegramChannelConfig(
                telegramConfig.apiBaseUri(),
                telegramConfig.botToken(),
                telegramConfig.requestTimeout(),
                false,
                telegramConfig.linkPreviewPosition(),
                telegramConfig.linkPreviewSize()
            );

        this.telegramChannel =
            new TelegramChannel(
                stagingTelegramConfig,
                transport,
                objectMapper,
                TelegramChannel.MessageFormat.PLAIN
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

        if (!stagingConfig
            .telegramDestination()
            .equals(
                command.destination()
            )) {

            return PublicationResult.failedPermanent(
                "WHATSAPP_MANUAL_INVALID_DESTINATION"
            );
        }

        String formattedContent =
            contentFormatter.format(
                command.content()
            );

        PublicationCommand stagingCommand =
            new PublicationCommand(
                command.publicationId(),
                command.channel(),
                command.destination(),
                formattedContent
            );

        return telegramChannel.publish(
            stagingCommand
        );
    }
}
