package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;

import java.util.Map;
import java.util.Objects;

/**
 * Carrega do ambiente toda a configuração necessária para
 * o fluxo automático de seleção/publicação.
 *
 * <p>Este provider apenas interpreta o estado desejado.</p>
 *
 * <p>A ativação persistente é responsabilidade do synchronizer
 * transacional correspondente.</p>
 */
public final class
PublicationAutomationPolicyEnvironmentConfigProvider {

    private PublicationAutomationPolicyEnvironmentConfigProvider() {
    }

    public static PublicationAutomationPolicyConfig load() {

        return load(
            System.getenv()
        );
    }

    /**
     * Variante determinística para testes.
     */
    public static PublicationAutomationPolicyConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        PublicationOperationalPolicyConfig operationalPolicy =
            PublicationOperationalPolicyEnvironmentConfigProvider
                .load(
                    environment
                );

        PublicationSelectionProfile selectionProfile =
            PublicationSelectionEnvironmentConfigProvider.load(
                environment
            );

        return new PublicationAutomationPolicyConfig(
            operationalPolicy,
            selectionProfile
        );
    }
}
