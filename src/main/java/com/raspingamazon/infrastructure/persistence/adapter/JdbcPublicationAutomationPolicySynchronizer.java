package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.infrastructure.config.PublicationAutomationPolicyConfig;

import java.sql.Connection;
import java.util.Objects;

/**
 * Sincroniza atomicamente toda a configuração operacional
 * necessária ao fluxo automático de publicação:
 *
 * <pre>
 * selection profile
 * quota profile
 * cadence profile
 * </pre>
 *
 * <p>Os sincronizadores especializados permanecem responsáveis
 * pela imutabilidade e pelo histórico de suas respectivas
 * configurações.</p>
 *
 * <p>Esta classe é responsável somente pela atomicidade conjunta.</p>
 *
 * <p>Fluxo:</p>
 *
 * <pre>
 * BEGIN
 *
 * selectionSynchronizer
 *
 * operationalPolicySynchronizer
 *      |
 *      +-- quotaSynchronizer
 *      |
 *      +-- cadenceSynchronizer
 *
 * COMMIT
 * </pre>
 *
 * <p>Se qualquer etapa falhar, a transação externa desfaz
 * integralmente as alterações realizadas pelas etapas anteriores.</p>
 */
public final class JdbcPublicationAutomationPolicySynchronizer {

    private final JdbcTransactionAdapter transactionAdapter;

    private final JdbcPublicationSelectionProfileSynchronizer
        selectionSynchronizer;

    private final JdbcPublicationOperationalPolicySynchronizer
        operationalPolicySynchronizer;

    public JdbcPublicationAutomationPolicySynchronizer(
        Connection connection
    ) {

        Connection validatedConnection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.transactionAdapter =
            new JdbcTransactionAdapter(
                validatedConnection
            );

        this.selectionSynchronizer =
            new JdbcPublicationSelectionProfileSynchronizer(
                validatedConnection
            );

        this.operationalPolicySynchronizer =
            new JdbcPublicationOperationalPolicySynchronizer(
                validatedConnection
            );
    }

    public PublicationAutomationPolicyConfig synchronize(
        PublicationAutomationPolicyConfig desiredConfig
    ) {

        Objects.requireNonNull(
            desiredConfig,
            "desiredConfig must not be null"
        );

        return transactionAdapter.execute(
            () -> {

                selectionSynchronizer.synchronize(
                    desiredConfig.channel(),
                    desiredConfig.destination(),
                    desiredConfig.selectionProfile()
                );

                operationalPolicySynchronizer.synchronize(
                    desiredConfig.operationalPolicy()
                );

                return desiredConfig;
            }
        );
    }
}
