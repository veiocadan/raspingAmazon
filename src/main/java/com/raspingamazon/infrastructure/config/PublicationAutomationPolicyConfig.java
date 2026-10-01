package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;

import java.util.Objects;

/**
 * Agregado completo da configuração operacional necessária
 * ao fluxo automático de seleção e publicação.
 *
 * <p>A configuração de quota + cadência permanece encapsulada em
 * PublicationOperationalPolicyConfig.</p>
 *
 * <p>Este agregado acrescenta a política temporal de seleção:</p>
 *
 * <pre>
 * PublicationAutomationPolicyConfig
 *      |
 *      +-- selection profile
 *      |
 *      +-- PublicationOperationalPolicyConfig
 *              |
 *              +-- quota profile
 *              |
 *              +-- cadence profile
 * </pre>
 */
public record PublicationAutomationPolicyConfig(
    PublicationOperationalPolicyConfig operationalPolicy,
    PublicationSelectionProfile selectionProfile
) {

    public PublicationAutomationPolicyConfig {

        Objects.requireNonNull(
            operationalPolicy,
            "operationalPolicy must not be null"
        );

        Objects.requireNonNull(
            selectionProfile,
            "selectionProfile must not be null"
        );

        /*
         * O fluxo automático desta aplicação utiliza atualmente
         * PUBLICATION_SELECTION_V1.
         *
         * Persistir/configurar outra revisão de parâmetros é
         * permitido, desde que continue pertencendo ao mesmo
         * algoritmo.
         */
        if (!PublicationSelectionPolicy.supportsProfileVersion(
            selectionProfile.version()
        )) {

            throw new IllegalArgumentException(
                "selectionProfile version "
                    + selectionProfile.version()
                    + " is not supported by policy "
                    + PublicationSelectionPolicy.VERSION
            );
        }
    }

    public String channel() {

        return operationalPolicy.channel();
    }

    public String destination() {

        return operationalPolicy.destination();
    }
}
