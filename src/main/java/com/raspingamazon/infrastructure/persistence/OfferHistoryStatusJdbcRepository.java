package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.history.OfferHistoryStatus;
import com.raspingamazon.application.history.OfferHistoryStatusQueryPort;
import com.raspingamazon.domain.product.Asin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação JDBC do read model histórico operacional.
 *
 * <p>A consulta agrega em uma única ida ao PostgreSQL:</p>
 *
 * <ul>
 *     <li>quantidade de snapshots;</li>
 *     <li>primeira detecção;</li>
 *     <li>última atualização;</li>
 *     <li>existência de publicação concluída.</li>
 * </ul>
 *
 * <p>A publicação é considerada concluída somente quando
 * publication.status = 'PUBLISHED'.</p>
 */
public final class OfferHistoryStatusJdbcRepository
    implements OfferHistoryStatusQueryPort {

    private final Connection connection;

    public OfferHistoryStatusJdbcRepository(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Optional<OfferHistoryStatus> findByAsin(
        Asin asin
    ) {

        Objects.requireNonNull(
            asin,
            "asin must not be null"
        );

        /*
         * A query principal não faz JOIN direto com Publication.
         *
         * Se fizéssemos isso, uma avaliação com múltiplas publicações
         * poderia multiplicar as linhas de OfferSnapshot e distorcer
         * COUNT/MIN/MAX.
         *
         * O EXISTS correlacionado mantém a agregação histórica
         * independente da cardinalidade das publicações.
         */
        String sql = """
            SELECT
                p.asin,
                COUNT(os.id) AS snapshot_count,
                MIN(os.collected_at) AS first_detected_at,
                MAX(os.collected_at) AS last_updated_at,

                EXISTS (
                    SELECT 1
                    FROM offer_snapshot published_snapshot
                    JOIN deal_evaluation de
                      ON de.offer_snapshot_id =
                         published_snapshot.id
                    JOIN publication pub
                      ON pub.deal_evaluation_id = de.id
                    WHERE published_snapshot.product_id = p.id
                      AND pub.status = 'PUBLISHED'
                ) AS published_before

            FROM product p

            LEFT JOIN offer_snapshot os
              ON os.product_id = p.id

            WHERE p.asin = ?

            GROUP BY
                p.id,
                p.asin
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                asin.value()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                long snapshotCount =
                    resultSet.getLong(
                        "snapshot_count"
                    );

                OffsetDateTime firstDetectedAt =
                    resultSet.getObject(
                        "first_detected_at",
                        OffsetDateTime.class
                    );

                OffsetDateTime lastUpdatedAt =
                    resultSet.getObject(
                        "last_updated_at",
                        OffsetDateTime.class
                    );

                boolean publishedBefore =
                    resultSet.getBoolean(
                        "published_before"
                    );

                return Optional.of(
                    new OfferHistoryStatus(
                        new Asin(
                            resultSet.getString(
                                "asin"
                            )
                        ),
                        snapshotCount,
                        firstDetectedAt,
                        lastUpdatedAt,
                        publishedBefore
                    )
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to query OfferHistoryStatus",
                exception
            );
        }
    }
}
