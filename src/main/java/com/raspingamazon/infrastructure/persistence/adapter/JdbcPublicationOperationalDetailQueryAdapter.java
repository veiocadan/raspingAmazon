package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.publication.PublicationDetail;
import com.raspingamazon.application.operation.publication.PublicationSummary;
import com.raspingamazon.application.operation.publication.port.PublicationOperationalDetailQueryPort;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Consulta JDBC do detalhe operacional de uma Publication.
 *
 * <p>Este adapter é estritamente somente leitura.</p>
 *
 * <p>Transições CREATED, READY, PUBLISHED e FAILED não pertencem
 * à interface operacional desta fase.</p>
 */
public final class JdbcPublicationOperationalDetailQueryAdapter
    implements PublicationOperationalDetailQueryPort {

    private final Connection connection;

    public JdbcPublicationOperationalDetailQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Optional<PublicationDetail> findById(
        long publicationId
    ) {

        if (publicationId <= 0L) {
            throw new IllegalArgumentException(
                "publicationId must be positive"
            );
        }

        String sql =
            """
            SELECT
                pub.id AS publication_id,
                pub.deal_evaluation_id,
                p.id AS product_id,
                p.asin,
                p.title,
                pub.status,
                pub.template_version,
                pub.commercial_presentation_version,
                pub.affiliate_link_version,
                pub.generated_text,
                pub.affiliate_url,
                pub.created_at
            FROM publication pub
            INNER JOIN deal_evaluation de
                ON de.id = pub.deal_evaluation_id
            INNER JOIN offer_snapshot os
                ON os.id = de.offer_snapshot_id
            INNER JOIN product p
                ON p.id = os.product_id
            WHERE pub.id = ?
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

                PublicationDetail detail =
                    readDetail(
                        resultSet
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one row found for Publication "
                            + publicationId
                    );
                }

                return Optional.of(
                    detail
                );
            }

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                "Could not load operational detail "
                    + "for Publication "
                    + publicationId,
                exception
            );
        }
    }

    private PublicationDetail readDetail(
        ResultSet resultSet
    ) throws SQLException {

        PublicationSummary summary =
            new PublicationSummary(
                resultSet.getLong(
                    "publication_id"
                ),
                resultSet.getLong(
                    "deal_evaluation_id"
                ),
                resultSet.getLong(
                    "product_id"
                ),
                new Asin(
                    resultSet.getString(
                        "asin"
                    )
                ),
                resultSet.getString(
                    "title"
                ),
                resultSet.getString(
                    "status"
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
                resultSet.getObject(
                    "created_at",
                    OffsetDateTime.class
                )
            );

        return new PublicationDetail(
            summary,
            resultSet.getString(
                "generated_text"
            ),
            resultSet.getString(
                "affiliate_url"
            )
        );
    }
}
