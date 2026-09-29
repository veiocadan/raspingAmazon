package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.selection.port.SuccessfulPublicationHistoryQueryPort;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementação JDBC da leitura agregada do histórico de
 * publicações externas concluídas com sucesso.
 *
 * <p>O histórico é reconstruído a partir da cadeia persistida:</p>
 *
 * <pre>
 * publication_attempt
 *     -> publication
 *     -> deal_evaluation
 *     -> offer_snapshot
 *     -> product
 * </pre>
 *
 * <p>Somente tentativas com status SUCCESS participam da
 * recorrência.</p>
 *
 * <p>A consulta recebe todos os ASINs candidatos em lote para
 * evitar uma consulta individual por candidato.</p>
 */
public final class JdbcSuccessfulPublicationHistoryQueryAdapter
    implements SuccessfulPublicationHistoryQueryPort {

    private final Connection connection;

    public JdbcSuccessfulPublicationHistoryQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Map<Asin, SuccessfulPublicationHistory>
    findSuccessfulByAsins(
        Set<Asin> asins,
        String channel,
        String destination
    ) {

        validateAsins(
            asins
        );

        String validatedChannel =
            requireText(
                channel,
                "channel"
            );

        String validatedDestination =
            requireText(
                destination,
                "destination"
            );

        if (asins.isEmpty()) {
            return Map.of();
        }

        String[] asinValues =
            asins.stream()
                .map(
                    Asin::value
                )
                .sorted()
                .toArray(
                    String[]::new
                );

        String sql =
            """
            SELECT
                product.asin,
                MAX(publication_attempt.created_at)
                    AS last_successful_publication_at,
                COUNT(DISTINCT publication.id)
                    AS successful_publication_count
            FROM publication_attempt
            INNER JOIN publication
                ON publication.id =
                    publication_attempt.publication_id
            INNER JOIN deal_evaluation
                ON deal_evaluation.id =
                    publication.deal_evaluation_id
            INNER JOIN offer_snapshot
                ON offer_snapshot.id =
                    deal_evaluation.offer_snapshot_id
            INNER JOIN product
                ON product.id =
                    offer_snapshot.product_id
            WHERE publication_attempt.status = 'SUCCESS'
              AND publication_attempt.channel = ?
              AND publication_attempt.target = ?
              AND product.asin = ANY (?)
            GROUP BY product.asin
            ORDER BY product.asin ASC
            """;

        Array asinArray =
            null;

        try {

            asinArray =
                connection.createArrayOf(
                    "varchar",
                    asinValues
                );

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         sql
                     )) {

                statement.setString(
                    1,
                    validatedChannel
                );

                statement.setString(
                    2,
                    validatedDestination
                );

                statement.setArray(
                    3,
                    asinArray
                );

                try (ResultSet resultSet =
                         statement.executeQuery()) {

                    Map<Asin, SuccessfulPublicationHistory>
                        histories =
                        new HashMap<>();

                    while (resultSet.next()) {

                        Asin asin =
                            new Asin(
                                resultSet.getString(
                                    "asin"
                                )
                            );

                        OffsetDateTime
                            lastSuccessfulPublicationAt =
                            resultSet.getObject(
                                "last_successful_publication_at",
                                OffsetDateTime.class
                            );

                        long successfulPublicationCount =
                            resultSet.getLong(
                                "successful_publication_count"
                            );

                        histories.put(
                            asin,
                            new SuccessfulPublicationHistory(
                                asin,
                                lastSuccessfulPublicationAt
                                    .toInstant(),
                                successfulPublicationCount
                            )
                        );
                    }

                    return Map.copyOf(
                        histories
                    );
                }
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Could not load successful publication history "
                    + "for channel "
                    + validatedChannel
                    + " and destination "
                    + validatedDestination,
                exception
            );

        } finally {

            freeArray(
                asinArray
            );
        }
    }

    private void validateAsins(
        Set<Asin> asins
    ) {

        Objects.requireNonNull(
            asins,
            "asins must not be null"
        );

        asins.forEach(
            asin ->
                Objects.requireNonNull(
                    asin,
                    "asins must not contain null"
                )
        );
    }

    private String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }

    private void freeArray(
        Array array
    ) {

        if (array == null) {
            return;
        }

        try {

            array.free();

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Could not release JDBC ASIN array",
                exception
            );
        }
    }
}
