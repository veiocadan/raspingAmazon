package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.publication.PublicationRepository;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Implementação JDBC da persistência idempotente de Publication.
 *
 * <p>A proteção definitiva contra duplicidade pertence ao PostgreSQL,
 * por meio da constraint criada na migration V14.</p>
 *
 * <p>O repository tenta inserir a publicação. Quando a identidade de
 * geração já existe, o INSERT não cria uma nova linha e a publicação
 * existente é lida e devolvida.</p>
 */
public final class PublicationJdbcRepository
    implements PublicationRepository {

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

    /**
     * Tenta criar a linha.
     *
     * <p>ON CONFLICT referencia implicitamente qualquer violação
     * da chave única de identidade da publicação. Como este INSERT
     * preenche todos os campos obrigatórios e a FK já deve existir,
     * o conflito esperado aqui é a identidade de geração já
     * persistida.</p>
     */
    private Long tryInsert(
        Publication publication
    ) {

        String sql = """
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
     * Carrega a linha que ganhou a disputa de idempotência.
     *
     * <p>A DealEvaluation já está presente no objeto de entrada
     * e corresponde ao mesmo id usado na identidade da linha,
     * portanto não reconstruímos novamente todo o grafo histórico
     * nesta operação de escrita.</p>
     */
    private Publication findExisting(
        Publication publication
    ) {

        String sql = """
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
                        "Publication conflict occurred but existing row could not be found"
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
