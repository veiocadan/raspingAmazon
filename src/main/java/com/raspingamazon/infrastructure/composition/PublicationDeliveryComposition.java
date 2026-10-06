package com.raspingamazon.infrastructure.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;
import com.raspingamazon.application.publication.outbox.retry.BoundedExponentialPublicationOutboxRetryPolicy;
import com.raspingamazon.application.publication.outbox.retry.PublicationOutboxRetryPolicy;
import com.raspingamazon.application.publication.outbox.worker.PublicationOutboxWorker;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitPolicy;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitRule;
import com.raspingamazon.application.publication.ratelimit.port.PublicationRateLimitReservationPort;
import com.raspingamazon.infrastructure.config.PublicationChannelActivationConfig;
import com.raspingamazon.infrastructure.config.PublicationChannelActivationConfigProvider;
import com.raspingamazon.infrastructure.config.PublicationOutboxRetryConfig;
import com.raspingamazon.infrastructure.config.PublicationOutboxRetryConfigProvider;
import com.raspingamazon.infrastructure.config.PublicationRateLimitConfig;
import com.raspingamazon.infrastructure.config.PublicationRateLimitConfigProvider;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
import com.raspingamazon.infrastructure.config.TelegramChannelConfigProvider;
import com.raspingamazon.infrastructure.config.WhatsAppChannelConfig;
import com.raspingamazon.infrastructure.config.WhatsAppChannelConfigProvider;
import com.raspingamazon.infrastructure.config.WhatsAppManualStagingConfig;
import com.raspingamazon.infrastructure.config.WhatsAppManualStagingConfigProvider;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationAttemptCompletionAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationAttemptStartAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxQueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationRateLimitReservationAdapter;
import com.raspingamazon.infrastructure.publication.channel.DisabledPublicationChannel;
import com.raspingamazon.infrastructure.publication.channel.MapPublicationChannelResolver;
import com.raspingamazon.infrastructure.publication.channel.TelegramChannel;
import com.raspingamazon.infrastructure.publication.channel.WhatsAppChannel;
import com.raspingamazon.infrastructure.publication.channel.WhatsAppManualStagingChannel;
import com.raspingamazon.infrastructure.publication.http.JavaPublicationHttpTransport;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransport;
import com.raspingamazon.infrastructure.publication.ratelimit.MapPublicationRateLimitPolicy;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Composition root da entrega externa de publicações.
 *
 * <p>Identidades operacionais conhecidas:</p>
 *
 * <pre>
 * TELEGRAM
 *     -> TelegramChannel
 *     -> HTML
 *     -> Telegram Bot API
 *
 * WHATSAPP_MANUAL
 *     -> WhatsAppManualStagingChannel
 *     -> marcação WhatsApp
 *     -> TelegramChannel PLAIN
 *     -> staging privado
 *     -> cópia/publicação humana no WhatsApp
 *
 * WHATSAPP
 *     -> WhatsAppChannel
 *     -> Meta WhatsApp Cloud API
 * </pre>
 *
 * <p>A ativação de cada rota é independente.</p>
 *
 * <p>O runtime real injeta:</p>
 *
 * <ul>
 *     <li>outbox persistente;</li>
 *     <li>PublicationAttempt STARTED antes da chamada externa;</li>
 *     <li>conclusão da mesma tentativa depois do provider;</li>
 *     <li>retry/backoff persistente;</li>
 *     <li>rate limiting preventivo persistente por integração física.</li>
 * </ul>
 *
 * <p>TELEGRAM e WHATSAPP_MANUAL compartilham TELEGRAM_BOT_API porque
 * ambos atravessam a mesma integração física.</p>
 */
public final class PublicationDeliveryComposition {

    public static final String TELEGRAM_CHANNEL =
        "TELEGRAM";

    public static final String WHATSAPP_MANUAL_CHANNEL =
        "WHATSAPP_MANUAL";

    public static final String WHATSAPP_CHANNEL =
        "WHATSAPP";

    public static final String TELEGRAM_BOT_API_INTEGRATION =
        "TELEGRAM_BOT_API";

    public static final String WHATSAPP_CLOUD_API_INTEGRATION =
        "WHATSAPP_CLOUD_API";

    public static final String TELEGRAM_DISABLED_ERROR =
        "TELEGRAM_DISABLED";

    public static final String WHATSAPP_MANUAL_DISABLED_ERROR =
        "WHATSAPP_MANUAL_DISABLED";

    public static final String WHATSAPP_DISABLED_ERROR =
        "WHATSAPP_DISABLED";

    private PublicationDeliveryComposition() {
    }

    /**
     * Monta o worker utilizando configuração real do ambiente.
     */
    public static PublicationOutboxWorker create(
        Connection connection,
        String workerId
    ) {

        Objects.requireNonNull(
            connection,
            "connection must not be null"
        );

        PublicationChannelActivationConfig activationConfig =
            PublicationChannelActivationConfigProvider.load();

        boolean telegramTransportRequired =
            activationConfig.telegramEnabled()
                || activationConfig.whatsAppManualEnabled();

        TelegramChannelConfig telegramConfig =
            telegramTransportRequired
                ? TelegramChannelConfigProvider.load()
                : null;

        WhatsAppManualStagingConfig whatsAppManualStagingConfig =
            activationConfig.whatsAppManualEnabled()
                ? WhatsAppManualStagingConfigProvider.load()
                : null;

        WhatsAppChannelConfig whatsAppConfig =
            activationConfig.whatsAppEnabled()
                ? WhatsAppChannelConfigProvider.load()
                : null;

        PublicationOutboxRetryConfig retryConfig =
            PublicationOutboxRetryConfigProvider.load();

        PublicationOutboxRetryPolicy retryPolicy =
            new BoundedExponentialPublicationOutboxRetryPolicy(
                retryConfig.maxAttempts(),
                retryConfig.initialBackoff(),
                retryConfig.maxBackoff()
            );

        PublicationRateLimitConfig rateLimitConfig =
            PublicationRateLimitConfigProvider.load();

        PublicationRateLimitPolicy rateLimitPolicy =
            createRateLimitPolicy(
                activationConfig,
                rateLimitConfig
            );

        PublicationRateLimitReservationPort
            rateLimitReservationPort =
            new JdbcPublicationRateLimitReservationAdapter(
                connection
            );

        HttpClient httpClient =
            HttpClient.newBuilder()
                .followRedirects(
                    HttpClient.Redirect.NEVER
                )
                .build();

        PublicationHttpTransport transport =
            new JavaPublicationHttpTransport(
                httpClient
            );

        return create(
            connection,
            workerId,
            activationConfig,
            telegramConfig,
            whatsAppManualStagingConfig,
            whatsAppConfig,
            transport,
            new ObjectMapper(),
            Clock.systemUTC(),
            retryPolicy,
            rateLimitPolicy,
            rateLimitReservationPort
        );
    }

    /**
     * Variante histórica para Telegram e WhatsApp automáticos.
     */
    public static PublicationOutboxWorker create(
        Connection connection,
        String workerId,
        TelegramChannelConfig telegramConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper,
        Clock clock
    ) {

        return create(
            connection,
            workerId,
            new PublicationChannelActivationConfig(
                true,
                false,
                true
            ),
            telegramConfig,
            null,
            whatsAppConfig,
            transport,
            objectMapper,
            clock,
            PublicationOutboxRetryPolicy.noRetry()
        );
    }

    /**
     * Variante compatível com a primeira implementação das flags.
     */
    public static PublicationOutboxWorker create(
        Connection connection,
        String workerId,
        PublicationChannelActivationConfig activationConfig,
        TelegramChannelConfig telegramConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper,
        Clock clock
    ) {

        return create(
            connection,
            workerId,
            activationConfig,
            telegramConfig,
            null,
            whatsAppConfig,
            transport,
            objectMapper,
            clock,
            PublicationOutboxRetryPolicy.noRetry()
        );
    }

    /**
     * Variante injetável incluindo WHATSAPP_MANUAL.
     */
    public static PublicationOutboxWorker create(
        Connection connection,
        String workerId,
        PublicationChannelActivationConfig activationConfig,
        TelegramChannelConfig telegramConfig,
        WhatsAppManualStagingConfig whatsAppManualStagingConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper,
        Clock clock
    ) {

        return create(
            connection,
            workerId,
            activationConfig,
            telegramConfig,
            whatsAppManualStagingConfig,
            whatsAppConfig,
            transport,
            objectMapper,
            clock,
            PublicationOutboxRetryPolicy.noRetry()
        );
    }

    /**
     * Variante completamente injetável de retry, mantendo rate limit
     * desabilitado para compatibilidade dos testes anteriores.
     */
    public static PublicationOutboxWorker create(
        Connection connection,
        String workerId,
        PublicationChannelActivationConfig activationConfig,
        TelegramChannelConfig telegramConfig,
        WhatsAppManualStagingConfig whatsAppManualStagingConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper,
        Clock clock,
        PublicationOutboxRetryPolicy retryPolicy
    ) {

        return create(
            connection,
            workerId,
            activationConfig,
            telegramConfig,
            whatsAppManualStagingConfig,
            whatsAppConfig,
            transport,
            objectMapper,
            clock,
            retryPolicy,
            PublicationRateLimitPolicy.disabled(),
            disabledRateLimitReservationPort()
        );
    }

    /**
     * Variante integralmente injetável.
     *
     * <p>Esta é a fronteira preferencial para testes de integração
     * entre retry, rate limiting, canais e outbox.</p>
     *
     * <p>A FASE 20 substitui a conclusão histórica pós-provider por
     * duas fronteiras explícitas:</p>
     *
     * <pre>
     * JdbcPublicationAttemptStartAdapter
     *     ↓
     * provider
     *     ↓
     * JdbcPublicationAttemptCompletionAdapter
     * </pre>
     */
    public static PublicationOutboxWorker create(
        Connection connection,
        String workerId,
        PublicationChannelActivationConfig activationConfig,
        TelegramChannelConfig telegramConfig,
        WhatsAppManualStagingConfig whatsAppManualStagingConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper,
        Clock clock,
        PublicationOutboxRetryPolicy retryPolicy,
        PublicationRateLimitPolicy rateLimitPolicy,
        PublicationRateLimitReservationPort rateLimitReservationPort
    ) {

        Objects.requireNonNull(
            connection,
            "connection must not be null"
        );

        String validatedWorkerId =
            requireText(
                workerId,
                "workerId"
            );

        Objects.requireNonNull(
            activationConfig,
            "activationConfig must not be null"
        );

        Objects.requireNonNull(
            transport,
            "transport must not be null"
        );

        Objects.requireNonNull(
            objectMapper,
            "objectMapper must not be null"
        );

        Objects.requireNonNull(
            clock,
            "clock must not be null"
        );

        Objects.requireNonNull(
            retryPolicy,
            "retryPolicy must not be null"
        );

        Objects.requireNonNull(
            rateLimitPolicy,
            "rateLimitPolicy must not be null"
        );

        Objects.requireNonNull(
            rateLimitReservationPort,
            "rateLimitReservationPort must not be null"
        );

        PublicationChannelResolver channelResolver =
            createChannelResolver(
                activationConfig,
                telegramConfig,
                whatsAppManualStagingConfig,
                whatsAppConfig,
                transport,
                objectMapper
            );

        JdbcPublicationOutboxQueueAdapter queue =
            new JdbcPublicationOutboxQueueAdapter(
                connection
            );

        /*
         * STARTED precisa usar a mesma conexão operacional, porém seu
         * próprio adapter exige autoCommit=true e produz commit antes
         * de retornar ao worker.
         */
        JdbcPublicationAttemptStartAdapter attemptStart =
            new JdbcPublicationAttemptStartAdapter(
                connection
            );

        JdbcPublicationAttemptCompletionAdapter attemptCompletion =
            new JdbcPublicationAttemptCompletionAdapter(
                connection,
                retryPolicy
            );

        return new PublicationOutboxWorker(
            validatedWorkerId,
            queue,
            channelResolver,
            attemptStart,
            attemptCompletion,
            clock,
            rateLimitPolicy,
            rateLimitReservationPort
        );
    }

    /**
     * Mapeia canais lógicos habilitados para integrações físicas.
     */
    static PublicationRateLimitPolicy createRateLimitPolicy(
        PublicationChannelActivationConfig activationConfig,
        PublicationRateLimitConfig rateLimitConfig
    ) {

        Objects.requireNonNull(
            activationConfig,
            "activationConfig must not be null"
        );

        Objects.requireNonNull(
            rateLimitConfig,
            "rateLimitConfig must not be null"
        );

        Map<String, PublicationRateLimitRule> rules =
            new LinkedHashMap<>();

        PublicationRateLimitRule telegramRule =
            new PublicationRateLimitRule(
                TELEGRAM_BOT_API_INTEGRATION,
                rateLimitConfig.telegramBotApiMinimumInterval()
            );

        if (activationConfig.telegramEnabled()) {

            rules.put(
                TELEGRAM_CHANNEL,
                telegramRule
            );
        }

        if (activationConfig.whatsAppManualEnabled()) {

            /*
             * WHATSAPP_MANUAL também utiliza Telegram Bot API.
             *
             * Portanto deve compartilhar exatamente a mesma
             * integração física do canal TELEGRAM.
             */
            rules.put(
                WHATSAPP_MANUAL_CHANNEL,
                telegramRule
            );
        }

        if (activationConfig.whatsAppEnabled()) {

            rules.put(
                WHATSAPP_CHANNEL,
                new PublicationRateLimitRule(
                    WHATSAPP_CLOUD_API_INTEGRATION,
                    rateLimitConfig.whatsAppCloudApiMinimumInterval()
                )
            );
        }

        return new MapPublicationRateLimitPolicy(
            rules
        );
    }

    /**
     * Variante histórica para Telegram e WhatsApp concretos.
     */
    static PublicationChannelResolver createChannelResolver(
        TelegramChannelConfig telegramConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        return createChannelResolver(
            new PublicationChannelActivationConfig(
                true,
                false,
                true
            ),
            telegramConfig,
            null,
            whatsAppConfig,
            transport,
            objectMapper
        );
    }

    /**
     * Variante compatível com a composição anterior às rotas manuais.
     */
    static PublicationChannelResolver createChannelResolver(
        PublicationChannelActivationConfig activationConfig,
        TelegramChannelConfig telegramConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        return createChannelResolver(
            activationConfig,
            telegramConfig,
            null,
            whatsAppConfig,
            transport,
            objectMapper
        );
    }

    /**
     * Monta o registry completo dos canais da FASE 19.
     */
    static PublicationChannelResolver createChannelResolver(
        PublicationChannelActivationConfig activationConfig,
        TelegramChannelConfig telegramConfig,
        WhatsAppManualStagingConfig whatsAppManualStagingConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        Objects.requireNonNull(
            activationConfig,
            "activationConfig must not be null"
        );

        Objects.requireNonNull(
            transport,
            "transport must not be null"
        );

        Objects.requireNonNull(
            objectMapper,
            "objectMapper must not be null"
        );

        PublicationChannel telegramChannel =
            createTelegramChannel(
                activationConfig,
                telegramConfig,
                transport,
                objectMapper
            );

        PublicationChannel whatsAppManualChannel =
            createWhatsAppManualChannel(
                activationConfig,
                telegramConfig,
                whatsAppManualStagingConfig,
                transport,
                objectMapper
            );

        PublicationChannel whatsAppChannel =
            createWhatsAppChannel(
                activationConfig,
                whatsAppConfig,
                transport,
                objectMapper
            );

        return new MapPublicationChannelResolver(
            Map.of(
                TELEGRAM_CHANNEL,
                telegramChannel,
                WHATSAPP_MANUAL_CHANNEL,
                whatsAppManualChannel,
                WHATSAPP_CHANNEL,
                whatsAppChannel
            )
        );
    }

    private static PublicationChannel createTelegramChannel(
        PublicationChannelActivationConfig activationConfig,
        TelegramChannelConfig telegramConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        if (!activationConfig.telegramEnabled()) {

            return new DisabledPublicationChannel(
                TELEGRAM_DISABLED_ERROR
            );
        }

        TelegramChannelConfig validatedConfig =
            Objects.requireNonNull(
                telegramConfig,
                "telegramConfig must not be null when Telegram is enabled"
            );

        return new TelegramChannel(
            validatedConfig,
            transport,
            objectMapper,
            TelegramChannel.MessageFormat.HTML
        );
    }

    private static PublicationChannel createWhatsAppManualChannel(
        PublicationChannelActivationConfig activationConfig,
        TelegramChannelConfig telegramConfig,
        WhatsAppManualStagingConfig whatsAppManualStagingConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        if (!activationConfig.whatsAppManualEnabled()) {

            return new DisabledPublicationChannel(
                WHATSAPP_MANUAL_DISABLED_ERROR
            );
        }

        TelegramChannelConfig validatedTelegramConfig =
            Objects.requireNonNull(
                telegramConfig,
                "telegramConfig must not be null when WhatsApp manual staging is enabled"
            );

        WhatsAppManualStagingConfig validatedStagingConfig =
            Objects.requireNonNull(
                whatsAppManualStagingConfig,
                "whatsAppManualStagingConfig must not be null when WhatsApp manual staging is enabled"
            );

        return new WhatsAppManualStagingChannel(
            validatedStagingConfig,
            validatedTelegramConfig,
            transport,
            objectMapper
        );
    }

    private static PublicationChannel createWhatsAppChannel(
        PublicationChannelActivationConfig activationConfig,
        WhatsAppChannelConfig whatsAppConfig,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        if (!activationConfig.whatsAppEnabled()) {

            return new DisabledPublicationChannel(
                WHATSAPP_DISABLED_ERROR
            );
        }

        WhatsAppChannelConfig validatedConfig =
            Objects.requireNonNull(
                whatsAppConfig,
                "whatsAppConfig must not be null when WhatsApp is enabled"
            );

        return new WhatsAppChannel(
            validatedConfig,
            transport,
            objectMapper
        );
    }

    private static PublicationRateLimitReservationPort
    disabledRateLimitReservationPort() {

        return (
            integrationKey,
            minimumInterval,
            requestedAt
        ) -> {
            throw new IllegalStateException(
                "Rate-limit reservation port must not be called "
                    + "when rate limiting is disabled"
            );
        };
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
