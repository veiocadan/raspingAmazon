package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.application.publication.port.PublicationDataQueryPort;
import com.raspingamazon.application.publication.port.PublicationQueryPort;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação JDBC da leitura de Publication por id.
 *
 * <p>Esta classe lê somente os campos pertencentes diretamente
 * à tabela publication.</p>
 *
 * <p>A reconstrução da DealEvaluation é delegada ao
 * PublicationDataQueryPort já existente. Dessa forma não
 * duplicamos a reconstrução de:</p>
 *
 * <ul>
 *     <li>DealEvaluation;</li>
 *     <li>OfferSnapshot;</li>
 *     <li>Product;</li>
 *     <li>evidências;</li>
 *     <li>regras de avaliação;</li>
 *     <li>fatores de score.</li>
 * </ul>
 */
public final class JdbcPublicationQueryAdapter
    implements PublicationQueryPort {

    private final Connection connection;

    private final PublicationDataQueryPort
        publicationDataQueryPort;

    public JdbcPublicationQueryAdapter(
        Connection connection,
        PublicationDataQueryPort publicationDataQueryPort
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.publicationDataQueryPort =
            Objects.requireNonNull(
                publicationDataQueryPort,
                "publicationDataQueryPort must not be null"
            );
    }

    @Override
    public Optional<Publication> findById(
        long publicationId
    ) {

        if (publicationId <= 0L) {

            throw new IllegalArgumentException(
                "publicationId must be positive"
            );
        }

        try {

            Optional<PublicationRow> row =
                findPublicationRow(
                    publicationId
                );

            if (row.isEmpty()) {
                return Optional.empty();
            }

            PublicationRow publicationRow =
                row.get();

            PublicationData publicationData =
                publicationDataQueryPort
                    .findByDealEvaluationId(
                        publicationRow.dealEvaluationId()
                    )
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "DealEvaluation not found for "
                                    + "Publication "
                                    + publicationId
                                    + ": "
                                    + publicationRow
                                    .dealEvaluationId()
                            )
                    );

            if (publicationData.dealEvaluationId()
                != publicationRow.dealEvaluationId()) {

                throw new IllegalStateException(
                    "PublicationDataQueryPort returned "
                        + "unexpected DealEvaluation identity"
                );
            }

            Publication publication =
                new Publication(
                    publicationId,
                    publicationData.dealEvaluation(),
                    publicationRow.templateVersion(),
                    publicationRow
                        .commercialPresentationVersion(),
                    publicationRow.affiliateLinkVersion(),
                    publicationRow.generatedText(),
                    publicationRow.affiliateUrl(),
                    toPublicationStatus(
                        publicationRow.status(),
                        publicationId
                    ),
                    publicationRow.createdAt()
                );

            return Optional.of(
                publication
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load Publication "
                    + publicationId,
                exception
            );
        }
    }

    private Optional<PublicationRow> findPublicationRow(
        long publicationId
    ) throws SQLException {

        String sql =
            """
            SELECT
                deal_evaluation_id,
                template_version,
                commercial_presentation_version,
                affiliate_link_version,
                generated_text,
                affiliate_url,
                status,
                created_at
            FROM publication
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                publicationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    new PublicationRow(
                        resultSet.getLong(
                            "deal_evaluation_id"
                        ),
                        resultSet.getString(
                            "template_version"
                        ),
                        resultSet.getString(
                            "commercial_presentation_version"
                        ),
                        resultSet.getString(
                            "affiliate_link_version"
                        ),
                        resultSet.getString(
                            "generated_text"
                        ),
                        resultSet.getString(
                            "affiliate_url"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "created_at",
                            OffsetDateTime.class
                        )
                    )
                );
            }
        }
    }

    private PublicationStatus toPublicationStatus(
        String status,
        long publicationId
    ) {

        try {

            return PublicationStatus.valueOf(
                status
            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                "Publication "
                    + publicationId
                    + " contains unknown persisted status: "
                    + status,
                exception
            );
        }
    }

    private record PublicationRow(
        long dealEvaluationId,
        String templateVersion,
        String commercialPresentationVersion,
        String affiliateLinkVersion,
        String generatedText,
        String affiliateUrl,
        String status,
        OffsetDateTime createdAt
    ) {
    }
}
