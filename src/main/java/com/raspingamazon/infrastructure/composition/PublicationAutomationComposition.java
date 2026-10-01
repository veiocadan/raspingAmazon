package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.PublicationGenerationUseCase;
import com.raspingamazon.application.publication.PublicationProcessingRunDispatchService;
import com.raspingamazon.application.publication.PublicationReadinessUseCase;
import com.raspingamazon.application.publication.PublicationSelectionDispatchService;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.application.publication.selection.PublicationSelectionService;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionSourceQueryPort;
import com.raspingamazon.infrastructure.config.AmazonAffiliateConfig;
import com.raspingamazon.infrastructure.config.AmazonAffiliateConfigProvider;
import com.raspingamazon.infrastructure.config.PublicationAutomationPolicyConfig;
import com.raspingamazon.infrastructure.config.PublicationAutomationPolicyEnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationAutomationPolicySynchronizer;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationSelectionSourceQueryAdapter;

import java.sql.Connection;
import java.time.Clock;
import java.util.Objects;

/**
 * Composition root completo do fluxo automático de publicação
 * originado por uma ProcessingRun.
 *
 * <p>Fluxo de composição:</p>
 *
 * <pre>
 * environment
 *      |
 *      v
 * PublicationAutomationPolicyConfig
 *      |
 *      v
 * sincronização atômica:
 *      selection + quota + cadence
 *      |
 *      v
 * source query da ProcessingRun
 *      |
 *      v
 * PublicationSelectionService
 *      |
 *      v
 * geração Amazon
 *      |
 *      v
 * READY automático
 *      |
 *      v
 * dispatch com cadência
 *      |
 *      v
 * TELEGRAM primary outbox
 *      |
 *      v
 * fan-out WHATSAPP_MANUAL quando habilitado
 * </pre>
 *
 * <p>O runtime recebe como resultado um serviço cuja API
 * operacional é simplesmente:</p>
 *
 * <pre>
 * service.process(processingRunId)
 * </pre>
 *
 * <p>Este composition root é específico para o fluxo cujo
 * canal primário é TELEGRAM.</p>
 */
public final class PublicationAutomationComposition {

    private static final String SUPPORTED_PRIMARY_CHANNEL =
        "TELEGRAM";

    private PublicationAutomationComposition() {
    }

    /**
     * Composition root de produção.
     *
     * <p>A configuração é carregada do environment, sincronizada
     * com o PostgreSQL e somente depois os casos de uso são
     * construídos.</p>
     */
    public static PublicationProcessingRunDispatchService create(
        Connection connection
    ) {

        Connection validatedConnection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        Clock clock =
            Clock.systemUTC();

        PublicationAutomationPolicyConfig desiredConfig =
            PublicationAutomationPolicyEnvironmentConfigProvider
                .load();

        validatePrimaryChannel(
            desiredConfig
        );

        /*
         * ---------------------------------------------------------
         * CONFIGURAÇÃO OPERACIONAL
         * ---------------------------------------------------------
         *
         * Primeiro sincronizamos:
         *
         * selection
         * quota
         * cadence
         *
         * como uma única mudança atômica.
         */
        synchronizePolicies(
            validatedConnection,
            desiredConfig
        );

        /*
         * ---------------------------------------------------------
         * GERAÇÃO
         * ---------------------------------------------------------
         *
         * AmazonPublicationComposition permanece autoridade sobre:
         *
         * - apresentação comercial;
         * - template;
         * - link de associado;
         * - persistência idempotente.
         */
        AmazonAffiliateConfig affiliateConfig =
            AmazonAffiliateConfigProvider.load();

        PublicationGenerationUseCase generationUseCase =
            AmazonPublicationComposition.create(
                validatedConnection,
                affiliateConfig,
                clock
            );

        /*
         * ---------------------------------------------------------
         * READINESS AUTOMÁTICO
         * ---------------------------------------------------------
         */
        PublicationReadinessUseCase readinessUseCase =
            PublicationReadinessComposition.create(
                validatedConnection
            );

        /*
         * ---------------------------------------------------------
         * OUTBOX PRIMÁRIA + FAN-OUT
         * ---------------------------------------------------------
         *
         * Esta composição já conhece:
         *
         * TELEGRAM
         * WHATSAPP_MANUAL
         * WHATSAPP oficial desabilitado quando configurado assim.
         */
        PublicationOutboxEnqueuePort outboxEnqueuePort =
            TelegramPublicationOutboxEnqueueComposition.create(
                validatedConnection
            );

        return createAfterSynchronization(
            validatedConnection,
            desiredConfig,
            generationUseCase,
            readinessUseCase,
            outboxEnqueuePort,
            clock
        );
    }

    /**
     * Variante package-private utilizada por testes do composition
     * root.
     *
     * <p>Permite testar a montagem completa da seleção e cadência
     * sem depender de credenciais/configuração externa de Amazon
     * ou Telegram.</p>
     */
    static PublicationProcessingRunDispatchService create(
        Connection connection,
        PublicationAutomationPolicyConfig desiredConfig,
        PublicationGenerationUseCase generationUseCase,
        PublicationReadinessUseCase readinessUseCase,
        PublicationOutboxEnqueuePort outboxEnqueuePort,
        Clock clock
    ) {

        Connection validatedConnection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        Objects.requireNonNull(
            desiredConfig,
            "desiredConfig must not be null"
        );

        Objects.requireNonNull(
            generationUseCase,
            "generationUseCase must not be null"
        );

        Objects.requireNonNull(
            readinessUseCase,
            "readinessUseCase must not be null"
        );

        Objects.requireNonNull(
            outboxEnqueuePort,
            "outboxEnqueuePort must not be null"
        );

        Objects.requireNonNull(
            clock,
            "clock must not be null"
        );

        validatePrimaryChannel(
            desiredConfig
        );

        synchronizePolicies(
            validatedConnection,
            desiredConfig
        );

        return createAfterSynchronization(
            validatedConnection,
            desiredConfig,
            generationUseCase,
            readinessUseCase,
            outboxEnqueuePort,
            clock
        );
    }

    /**
     * Constrói a cadeia somente depois que a configuração já foi
     * sincronizada com sucesso.
     */
    private static PublicationProcessingRunDispatchService
    createAfterSynchronization(
        Connection connection,
        PublicationAutomationPolicyConfig desiredConfig,
        PublicationGenerationUseCase generationUseCase,
        PublicationReadinessUseCase readinessUseCase,
        PublicationOutboxEnqueuePort outboxEnqueuePort,
        Clock clock
    ) {

        PublicationSelectionSourceQueryPort sourceQueryPort =
            new JdbcPublicationSelectionSourceQueryAdapter(
                connection
            );

        PublicationSelectionService selectionService =
            PublicationSelectionComposition.create(
                connection
            );

        PublicationSelectionDispatchService selectionDispatchService =
            PublicationSelectionDispatchComposition.create(
                connection,
                generationUseCase,
                readinessUseCase,
                outboxEnqueuePort,
                clock
            );

        return new PublicationProcessingRunDispatchService(
            sourceQueryPort,
            selectionService,
            selectionDispatchService,
            desiredConfig.channel(),
            desiredConfig.destination(),
            clock
        );
    }

    private static void synchronizePolicies(
        Connection connection,
        PublicationAutomationPolicyConfig desiredConfig
    ) {

        JdbcPublicationAutomationPolicySynchronizer synchronizer =
            new JdbcPublicationAutomationPolicySynchronizer(
                connection
            );

        synchronizer.synchronize(
            desiredConfig
        );
    }

    private static void validatePrimaryChannel(
        PublicationAutomationPolicyConfig config
    ) {

        if (!SUPPORTED_PRIMARY_CHANNEL.equals(
            config.channel()
        )) {

            throw new IllegalArgumentException(
                "PublicationAutomationComposition supports only "
                    + SUPPORTED_PRIMARY_CHANNEL
                    + " as primary channel, but received "
                    + config.channel()
            );
        }
    }
}
