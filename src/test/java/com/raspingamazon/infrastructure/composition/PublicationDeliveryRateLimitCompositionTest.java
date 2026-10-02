package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitPolicy;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitRule;
import com.raspingamazon.infrastructure.config.PublicationChannelActivationConfig;
import com.raspingamazon.infrastructure.config.PublicationRateLimitConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationDeliveryRateLimitCompositionTest {

    @Test
    void telegramAndManualWhatsAppShouldShareTelegramBotApiLimiter() {

        PublicationChannelActivationConfig activation =
            new PublicationChannelActivationConfig(
                true,
                true,
                false
            );

        PublicationRateLimitPolicy policy =
            PublicationDeliveryComposition
                .createRateLimitPolicy(
                    activation,
                    new PublicationRateLimitConfig(
                        Duration.ofMillis(
                            750
                        ),
                        Duration.ofSeconds(
                            2
                        )
                    )
                );

        PublicationRateLimitRule telegram =
            policy.findRule(
                "TELEGRAM"
            ).orElseThrow();

        PublicationRateLimitRule manual =
            policy.findRule(
                "WHATSAPP_MANUAL"
            ).orElseThrow();

        assertEquals(
            PublicationDeliveryComposition
                .TELEGRAM_BOT_API_INTEGRATION,
            telegram.integrationKey()
        );

        assertEquals(
            telegram,
            manual
        );

        assertEquals(
            Duration.ofMillis(
                750
            ),
            telegram.minimumInterval()
        );

        assertTrue(
            policy.findRule(
                "WHATSAPP"
            ).isEmpty()
        );
    }

    @Test
    void officialWhatsAppShouldUseIndependentCloudApiLimiter() {

        PublicationChannelActivationConfig activation =
            new PublicationChannelActivationConfig(
                false,
                false,
                true
            );

        PublicationRateLimitPolicy policy =
            PublicationDeliveryComposition
                .createRateLimitPolicy(
                    activation,
                    new PublicationRateLimitConfig(
                        Duration.ofSeconds(
                            1
                        ),
                        Duration.ofSeconds(
                            3
                        )
                    )
                );

        PublicationRateLimitRule whatsApp =
            policy.findRule(
                "WHATSAPP"
            ).orElseThrow();

        assertEquals(
            PublicationDeliveryComposition
                .WHATSAPP_CLOUD_API_INTEGRATION,
            whatsApp.integrationKey()
        );

        assertEquals(
            Duration.ofSeconds(
                3
            ),
            whatsApp.minimumInterval()
        );

        assertTrue(
            policy.findRule(
                "TELEGRAM"
            ).isEmpty()
        );

        assertTrue(
            policy.findRule(
                "WHATSAPP_MANUAL"
            ).isEmpty()
        );
    }

    @Test
    void disabledChannelsShouldNotReceiveRateLimitRules() {

        PublicationRateLimitPolicy policy =
            PublicationDeliveryComposition
                .createRateLimitPolicy(
                    new PublicationChannelActivationConfig(
                        false,
                        false,
                        false
                    ),
                    new PublicationRateLimitConfig(
                        Duration.ofSeconds(
                            1
                        ),
                        Duration.ofSeconds(
                            1
                        )
                    )
                );

        assertTrue(
            policy.findRule(
                "TELEGRAM"
            ).isEmpty()
        );

        assertTrue(
            policy.findRule(
                "WHATSAPP_MANUAL"
            ).isEmpty()
        );

        assertTrue(
            policy.findRule(
                "WHATSAPP"
            ).isEmpty()
        );
    }
}
