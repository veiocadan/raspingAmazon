package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.publication.selection.PublicationSelectionService;
import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationQuotaProfileProvider;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationQuotaUsageQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationSelectionAuditRepository;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationSelectionProfileProvider;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcSuccessfulPublicationHistoryQueryAdapter;

import java.sql.Connection;
import java.util.Objects;

/**
 * Composition root do caso de uso de seleção operacional
 * de publicações.
 *
 * <p>Responsabilidades:</p>
 *
 * <pre>
 * perfil temporal persistido
 *          +
 * quota persistida
 *          +
 * ocupação da quota
 *          +
 * histórico de publicação bem-sucedida
 *          +
 * auditoria da SelectionRun
 *          +
 * PublicationSelectionPolicy
 *          ↓
 * PublicationSelectionService
 * </pre>
 *
 * <p>Nenhuma regra de seleção é implementada aqui.</p>
 */
public final class PublicationSelectionComposition {

    private PublicationSelectionComposition() {
    }

    public static PublicationSelectionService create(
        Connection connection
    ) {

        Connection validatedConnection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        return new PublicationSelectionService(
            new JdbcPublicationSelectionProfileProvider(
                validatedConnection
            ),
            new JdbcPublicationQuotaProfileProvider(
                validatedConnection
            ),
            new JdbcPublicationQuotaUsageQueryAdapter(
                validatedConnection
            ),
            new JdbcSuccessfulPublicationHistoryQueryAdapter(
                validatedConnection
            ),
            new JdbcPublicationSelectionAuditRepository(
                validatedConnection
            ),
            new PublicationSelectionPolicy()
        );
    }
}
