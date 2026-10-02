package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLoadPort;
import com.raspingamazon.application.publication.PublicationReadinessService;
import com.raspingamazon.application.publication.PublicationReadinessUseCase;
import com.raspingamazon.application.publication.PublicationStatusRepository;
import com.raspingamazon.application.publication.port.PublicationDataQueryPort;
import com.raspingamazon.application.publication.port.PublicationQueryPort;
import com.raspingamazon.infrastructure.persistence.PublicationJdbcRepository;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcOfferSnapshotEvaluationLoadAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationDataQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationQueryAdapter;

import java.sql.Connection;
import java.util.Objects;

/**
 * Composition root da liberação automática CREATED -> READY.
 *
 * <p>O serviço não representa aprovação humana.</p>
 *
 * <p>READY significa que as regras automáticas aplicáveis
 * liberaram a Publication para seguir ao dispatch.</p>
 */
public final class PublicationReadinessComposition {

    private PublicationReadinessComposition() {
    }

    public static PublicationReadinessUseCase create(
        Connection connection
    ) {

        Connection validatedConnection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        OfferSnapshotEvaluationLoadPort
            offerSnapshotEvaluationLoadPort =
            new JdbcOfferSnapshotEvaluationLoadAdapter(
                validatedConnection
            );

        PublicationDataQueryPort publicationDataQueryPort =
            new JdbcPublicationDataQueryAdapter(
                validatedConnection,
                offerSnapshotEvaluationLoadPort
            );

        PublicationQueryPort publicationQueryPort =
            new JdbcPublicationQueryAdapter(
                validatedConnection,
                publicationDataQueryPort
            );

        PublicationStatusRepository publicationStatusRepository =
            new PublicationJdbcRepository(
                validatedConnection
            );

        return new PublicationReadinessService(
            publicationQueryPort,
            publicationStatusRepository
        );
    }
}
