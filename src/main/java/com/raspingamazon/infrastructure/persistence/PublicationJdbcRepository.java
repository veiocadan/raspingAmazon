package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.publication.PublicationRepository;
import com.raspingamazon.application.publication.PublicationStatusRepository;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Implementação JDBC da persistência de Publication.
 *
 * <p>Esta implementação atende dois contratos independentes:</p>
 *
 * <ul>
 *     <li>PublicationRepository para criação idempotente;</li>
 *     <li>PublicationStatusRepository para transições de estado.</li>
 * </ul>
 *
 * <p>A criação permanece protegida pela identidade única de geração
 * introduzida na migration V14.</p>
 *
 * <p>Transições posteriores utilizam compare-and-set:</p>
 *
 * <pre>
 * UPDATE publication
 * SET status = novoEstado
 * WHERE id = publicationId
 *   AND status = estadoEsperado
 * </pre>
 *
 * <p>Dessa forma, alterações concorrentes não são sobrescritas
 * silenciosamente.</p>
 */
public final class PublicationJdbcRepository
    implements PublicationRepository,
    PublicationStatusRepository {

    private final Connection connection;

    public PublicationJdbcRepository(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Publication save(
        Publication publication
    ) {

        Objects.requireNonNull(
            publication,
            "publication must not be null"
        );

        if (publication.id() != null) {

            throw new IllegalArgumentException(
                "Only new Publication instances can be persisted"
            );
        }

        Long insertedId =
            tryInsert(
                publication
            );

        if (insertedId != null) {

            return copyWithId(
                insertedId,
                publication
            );
        }

        return findExisting(
            publication
        );
    }

    @Override
    public void updateStatus(
        Publication publication,
        PublicationStatus expectedStatus
    ) {

        Objects.requireNonNull(
            publication,
            "publication must not be null"
        );

        Objects.requireNonNull(
            expectedStatus,
            "expectedStatus must not be null"
        );

        Long publicationId =
            publication.id();

        if (publicationId == null
            || publicationId <= 0L) {

            throw new IllegalArgumentException(
                "Publication must be persisted before status update"
            );
        }

        PublicationStatus targetStatus =
            Objects.requireNonNull(
                publication.status(),
                "publication status must not be null"
            );

        if (targetStatus == expectedStatus) {

            throw new IllegalArgumentException(
                "Publication target status must differ "
                    + "from expected status"
            );
        }

        String sql =
            """
            UPDATE publication
            SET status = ?
            WHERE id = ?
              AND status = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                targetStatus.name()
            );

            statement.setLong(
                2,
                publicationId
            );

            statement.setString(
                3,
                expectedStatus.name()
            );

            int updatedRows =
                statement.executeUpdate();

            if (updatedRows != 1) {

                throw new IllegalStateException(
                    "Publication status transition conflict "
                        + "for publication "
                        + publicationId
                        + ": expected "
                        + expectedStatus
                        + ", target "
                        + targetStatus
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to update Publication status "
                    + publicationId,
                exception
            );
        }
    }

    /**
     * Tenta criar a publicação.
     *
     * <p>Quando a identidade de geração já existe,
     * ON CONFLICT não cria uma segunda linha.</p>
     */
    private Long tryInsert(
        Publication publication
    ) {

        String sql =
            """
            INSERT INTO publication (
                deal_evaluation_id,
                template_version,
                commercial_presentation_version,
                affiliate_link_version,
                generated_text,
                affiliate_url,
                status,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (
                deal_evaluation_id,
                template_version,
                commercial_presentation_version,
                affiliate_link_version
            )
            DO NOTHING
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                publication.dealEvaluation()
                    .id()
            );

            statement.setString(
                2,
                publication.templateVersion()
            );

            statement.setString(
                3,
                publication.commercialPresentationVersion()
            );

            statement.setString(
                4,
                publication.affiliateLinkVersion()
            );

            statement.setString(
                5,
                publication.generatedText()
            );

            statement.setString(
                6,
                publication.affiliateUrl()
            );

            statement.setString(
                7,
                publication.status()
                    .name()
            );

            statement.setObject(
                8,
                publication.createdAt()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                return resultSet.getLong(
                    "id"
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to persist Publication",
                exception
            );
        }
    }

    /**
     * Carrega a publicação que já possuía a mesma identidade
     * idempotente de geração.
     *
     * <p>A DealEvaluation já está disponível no objeto de entrada,
     * portanto esta operação não reconstrói o grafo histórico.</p>
     */
    private Publication findExisting(
        Publication publication
    ) {

        String sql =
            """
            SELECT
                id,
                generated_text,
                affiliate_url,
                status,
                created_at
            FROM publication
            WHERE deal_evaluation_id = ?
              AND template_version = ?
              AND commercial_presentation_version = ?
              AND affiliate_link_version = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                publication.dealEvaluation()
                    .id()
            );

            statement.setString(
                2,
                publication.templateVersion()
            );

            statement.setString(
                3,
                publication.commercialPresentationVersion()
            );

            statement.setString(
                4,
                publication.affiliateLinkVersion()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication conflict occurred but "
                            + "existing row could not be found"
                    );
                }

                return new Publication(
                    resultSet.getLong(
                        "id"
                    ),
                    publication.dealEvaluation(),
                    publication.templateVersion(),
                    publication.commercialPresentationVersion(),
                    publication.affiliateLinkVersion(),
                    resultSet.getString(
                        "generated_text"
                    ),
                    resultSet.getString(
                        "affiliate_url"
                    ),
                    PublicationStatus.valueOf(
                        resultSet.getString(
                            "status"
                        )
                    ),
                    resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                    )
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load existing Publication",
                exception
            );
        }
    }

    private Publication copyWithId(
        long id,
        Publication publication
    ) {

        return new Publication(
            id,
            publication.dealEvaluation(),
            publication.templateVersion(),
            publication.commercialPresentationVersion(),
            publication.affiliateLinkVersion(),
            publication.generatedText(),
            publication.affiliateUrl(),
            publication.status(),
            publication.createdAt()
        );
    }
}
