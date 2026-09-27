package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.presentation.cli.OperationalCliUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalCliSchedulesBootstrapContractTest {

    @Test
    void shouldRequireOperationalCompositionForSchedules() {

        assertTrue(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "schedules",
                    "status",
                    "amazon-deals"
                }
            )
        );

        assertTrue(
            OperationalCliBootstrap.requiresComposition(
                new String[]{
                    "schedules",
                    "pause",
                    "amazon-deals"
                }
            )
        );
    }

    @Test
    void shouldDocumentSchedulesInGlobalUsage() {

        String usage =
            OperationalCliUsage.text();

        assertTrue(
            usage.contains(
                "schedules"
            )
        );

        assertTrue(
            usage.contains(
                "status <schedule-key>"
            )
        );

        assertTrue(
            usage.contains(
                "pause <schedule-key>"
            )
        );

        assertTrue(
            usage.contains(
                "resume <schedule-key>"
            )
        );

        assertTrue(
            usage.contains(
                "interval <schedule-key> <duration>"
            )
        );
    }
}
