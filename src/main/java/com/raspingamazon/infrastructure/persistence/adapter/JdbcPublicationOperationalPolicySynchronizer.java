package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.infrastructure.config.PublicationOperationalPolicyConfig;

import java.sql.Connection;
import java.util.Objects;

/**
 * Sincroniza atomicamente quota e cadência desejadas
 * para um mesmo escopo operacional.
 *
 * <p>Atomicidade:</p>
 *
 * <pre>
 * BEGIN
 *
 * sincronizar quota
 * sincronizar cadência
 *
 * COMMIT
 * </pre>
 *
 * <p>Se qualquer uma das sincronizações falhar:</p>
 *
 * <pre>
 * ROLLBACK
 * </pre>
 *
 * <p>Os sincronizadores internos utilizam a mesma Connection.
 * Quando encontram uma transação externa ativa, seus
 * JdbcTransactionAdapter trabalham com savepoints e não realizam
 * commit independente.</p>
 */
public final class JdbcPublicationOperationalPolicySynchronizer {

    private final JdbcTransactionAdapter transactionAdapter;

    private final JdbcPublicationQuotaProfileSynchronizer
        quotaSynchronizer;

    private final JdbcPublicationCadenceProfileSynchronizer
        cadenceSynchronizer;

    public JdbcPublicationOperationalPolicySynchronizer(
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

        this.quotaSynchronizer =
            new JdbcPublicationQuotaProfileSynchronizer(
                validatedConnection
            );

        this.cadenceSynchronizer =
            new JdbcPublicationCadenceProfileSynchronizer(
                validatedConnection
            );
    }

    /**
     * Ativa quota e cadência como uma única mudança operacional.
     *
     * @return a configuração desejada após sincronização bem-sucedida
     */
    public PublicationOperationalPolicyConfig synchronize(
        PublicationOperationalPolicyConfig desiredConfig
    ) {

        Objects.requireNonNull(
            desiredConfig,
            "desiredConfig must not be null"
        );

        return transactionAdapter.execute(
            () -> {

                quotaSynchronizer.synchronize(
                    desiredConfig.channel(),
                    desiredConfig.destination(),
                    desiredConfig.quotaProfile()
                );

                cadenceSynchronizer.synchronize(
                    desiredConfig.channel(),
                    desiredConfig.destination(),
                    desiredConfig.cadenceProfile()
                );

                return desiredConfig;
            }
        );
    }
}
