package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.outbox.PublicationOutboxDerivedTarget;
import com.raspingamazon.application.publication.outbox.PublicationOutboxFanoutEnqueueService;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.infrastructure.config.PublicationChannelActivationConfig;
import com.raspingamazon.infrastructure.config.PublicationChannelActivationConfigProvider;
import com.raspingamazon.infrastructure.config.WhatsAppManualStagingConfig;
import com.raspingamazon.infrastructure.config.WhatsAppManualStagingConfigProvider;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxDerivedEnqueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxEnqueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcTransactionAdapter;

import java.sql.Connection;
import java.util.List;
import java.util.Objects;

/**
 * Composition root do enqueue de publicações cuja seleção primária
 * pertence ao canal TELEGRAM.
 *
 * <p>Esta composição não executa seleção, aprovação ou entrega.</p>
 *
 * <p>Sua responsabilidade é escolher a estratégia de reserva da
 * publication_outbox para uma Publication já selecionada para
 * Telegram.</p>
 *
 * <p>Quando WHATSAPP_MANUAL está desabilitado:</p>
 *
 * <pre>
 * Selection TELEGRAM
 *      |
 *      v
 * publication_outbox TELEGRAM
 * </pre>
 *
 * <p>Quando WHATSAPP_MANUAL está habilitado:</p>
 *
 * <pre>
 * Selection TELEGRAM
 *      |
 *      +-----------------------------+
 *      |                             |
 *      v                             v
 * outbox TELEGRAM            outbox WHATSAPP_MANUAL
 * reserva quota              não reserva nova quota
 * </pre>
 *
 * <p>A segunda entrega utiliza o destino privado de staging
 * configurado para o fluxo manual do WhatsApp.</p>
 *
 * <p>WHATSAPP automático não participa desta composição.</p>
 */
public final class TelegramPublicationOutboxEnqueueComposition {

    public static final String WHATSAPP_MANUAL_CHANNEL =
        "WHATSAPP_MANUAL";

    private TelegramPublicationOutboxEnqueueComposition() {
    }

    /**
     * Cria a porta usando configuração operacional real.
     *
     * <p>O staging config somente é carregado quando o fluxo
     * WHATSAPP_MANUAL está efetivamente habilitado.</p>
     */
    public static PublicationOutboxEnqueuePort create(
        Connection connection
    ) {

        Objects.requireNonNull(
            connection,
            "connection must not be null"
        );

        PublicationChannelActivationConfig activationConfig =
            PublicationChannelActivationConfigProvider.load();

        WhatsAppManualStagingConfig stagingConfig =
            activationConfig.whatsAppManualEnabled()
                ? WhatsAppManualStagingConfigProvider.load()
                : null;

        return create(
            connection,
            activationConfig,
            stagingConfig
        );
    }

    /**
     * Variante explicitamente injetável para testes e outros
     * composition roots.
     *
     * <p>Este método deve ser utilizado somente para enqueues cuja
     * SelectionRun primária seja do canal TELEGRAM.</p>
     */
    static PublicationOutboxEnqueuePort create(
        Connection connection,
        PublicationChannelActivationConfig activationConfig,
        WhatsAppManualStagingConfig stagingConfig
    ) {

        Objects.requireNonNull(
            connection,
            "connection must not be null"
        );

        Objects.requireNonNull(
            activationConfig,
            "activationConfig must not be null"
        );

        JdbcPublicationOutboxEnqueueAdapter primaryEnqueue =
            new JdbcPublicationOutboxEnqueueAdapter(
                connection
            );

        /*
         * Sem staging manual, preservamos exatamente o comportamento
         * original da FASE 18.
         */
        if (!activationConfig.whatsAppManualEnabled()) {

            return primaryEnqueue;
        }

        WhatsAppManualStagingConfig validatedStagingConfig =
            Objects.requireNonNull(
                stagingConfig,
                "stagingConfig must not be null "
                    + "when WhatsApp manual staging is enabled"
            );

        JdbcPublicationOutboxDerivedEnqueueAdapter derivedEnqueue =
            new JdbcPublicationOutboxDerivedEnqueueAdapter(
                connection
            );

        JdbcTransactionAdapter transactionAdapter =
            new JdbcTransactionAdapter(
                connection
            );

        PublicationOutboxDerivedTarget manualWhatsAppTarget =
            new PublicationOutboxDerivedTarget(
                WHATSAPP_MANUAL_CHANNEL,
                validatedStagingConfig.telegramDestination()
            );

        return new PublicationOutboxFanoutEnqueueService(
            primaryEnqueue,
            derivedEnqueue,
            transactionAdapter,
            List.of(
                manualWhatsAppTarget
            )
        );
    }
}
