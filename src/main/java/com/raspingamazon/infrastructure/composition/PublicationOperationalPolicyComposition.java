package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.infrastructure.config.PublicationOperationalPolicyConfig;
import com.raspingamazon.infrastructure.config.PublicationOperationalPolicyEnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOperationalPolicySynchronizer;

import java.sql.Connection;
import java.util.Objects;

/**
 * Composition root da configuração operacional de publicação.
 *
 * <p>Responsabilidade:</p>
 *
 * <pre>
 * environment
 *      |
 *      v
 * PublicationOperationalPolicyConfig
 *      |
 *      v
 * JdbcPublicationOperationalPolicySynchronizer
 *      |
 *      v
 * PostgreSQL
 * </pre>
 *
 * <p>Este componente não abre Connection própria porque o runtime
 * contínuo já possui uma Connection que deve ser compartilhada
 * pelos adapters pertencentes ao mesmo processo.</p>
 */
public final class PublicationOperationalPolicyComposition {

    private PublicationOperationalPolicyComposition() {
    }

    /**
     * Sincroniza usando System.getenv().
     *
     * <p>Este será o entry point utilizado posteriormente durante
     * a inicialização do runtime contínuo.</p>
     */
    public static PublicationOperationalPolicyConfig synchronize(
        Connection connection
    ) {

        return synchronize(
            connection,
            PublicationOperationalPolicyEnvironmentConfigProvider
                .load()
        );
    }

    /**
     * Variante explícita para testes e composição determinística.
     */
    public static PublicationOperationalPolicyConfig synchronize(
        Connection connection,
        PublicationOperationalPolicyConfig desiredConfig
    ) {

        Objects.requireNonNull(
            connection,
            "connection must not be null"
        );

        Objects.requireNonNull(
            desiredConfig,
            "desiredConfig must not be null"
        );

        JdbcPublicationOperationalPolicySynchronizer synchronizer =
            new JdbcPublicationOperationalPolicySynchronizer(
                connection
            );

        return synchronizer.synchronize(
            desiredConfig
        );
    }
}
