package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.history.OfferHistoryQueryPort;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.history.HistoricalOfferObservation;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementação JDBC da consulta histórica de ofertas.
 *
 * <p>Esta classe reconstrói fatos persistidos. Ela não executa
 * interpretação de negócio.</p>
 *
 * <p>Consequentemente, este repository não:</p>
 *
 * <ul>
 *     <li>calcula variações;</li>
 *     <li>seleciona o melhor desconto;</li>
 *     <li>calcula momentum;</li>
 *     <li>decide elegibilidade;</li>
 *     <li>calcula score.</li>
 * </ul>
 *
 * <p>As condições comerciais são reconstruídas porque o desconto
 * histórico continua sendo contextual. A escolha da melhor condição
 * permanece responsabilidade do domínio.</p>
 */
public final class OfferHistoryJdbcRepository
    implements OfferHistoryQueryPort {

    private final Connection connection;

    public OfferHistoryJdbcRepository(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public List<HistoricalOfferObservation> findHistoryByAsin(
        Asin asin
    ) {

        Objects.requireNonNull(
            asin,
            "asin must not be null"
        );

        String sql = """
            SELECT
                os.id AS snapshot_id,
                p.asin,
                os.collected_at,
                os.current_price,
                os.sold_percentage,
                os.source
            FROM product p
            JOIN offer_snapshot os
              ON os.product_id = p.id
            WHERE p.asin = ?
            ORDER BY
                os.collected_at ASC,
                os.id ASC
            """;

        List<SnapshotRow> rows =
            querySnapshotRows(
                sql,
                statement ->
                    statement.setString(
                        1,
                        asin.value()
                    )
            );

        return hydrate(
            rows
        );
    }

    @Override
    public java.util.Optional<HistoricalOfferObservation>
    findFirstByAsin(
        Asin asin
    ) {

        Objects.requireNonNull(
            asin,
            "asin must not be null"
        );

        String sql = """
            SELECT
                os.id AS snapshot_id,
                p.asin,
                os.collected_at,
                os.current_price,
                os.sold_percentage,
                os.source
            FROM product p
            JOIN offer_snapshot os
              ON os.product_id = p.id
            WHERE p.asin = ?
            ORDER BY
                os.collected_at ASC,
                os.id ASC
            LIMIT 1
            """;

        List<HistoricalOfferObservation> observations =
            hydrate(
                querySnapshotRows(
                    sql,
                    statement ->
                        statement.setString(
                            1,
                            asin.value()
                        )
                )
            );

        return observations.stream()
            .findFirst();
    }

    @Override
    public java.util.Optional<HistoricalOfferObservation>
    findLatestByAsin(
        Asin asin
    ) {

        Objects.requireNonNull(
            asin,
            "asin must not be null"
        );

        String sql = """
            SELECT
                os.id AS snapshot_id,
                p.asin,
                os.collected_at,
                os.current_price,
                os.sold_percentage,
                os.source
            FROM product p
            JOIN offer_snapshot os
              ON os.product_id = p.id
            WHERE p.asin = ?
            ORDER BY
                os.collected_at DESC,
                os.id DESC
            LIMIT 1
            """;

        List<HistoricalOfferObservation> observations =
            hydrate(
                querySnapshotRows(
                    sql,
                    statement ->
                        statement.setString(
                            1,
                            asin.value()
                        )
                )
            );

        return observations.stream()
            .findFirst();
    }

    @Override
    public java.util.Optional<HistoricalOfferObservation>
    findPreviousByAsin(
        Asin asin,
        OffsetDateTime collectedAt
    ) {

        Objects.requireNonNull(
            asin,
            "asin must not be null"
        );

        Objects.requireNonNull(
            collectedAt,
            "collectedAt must not be null"
        );

        String sql = """
            SELECT
                os.id AS snapshot_id,
                p.asin,
                os.collected_at,
                os.current_price,
                os.sold_percentage,
                os.source
            FROM product p
            JOIN offer_snapshot os
              ON os.product_id = p.id
            WHERE p.asin = ?
              AND os.collected_at < ?
            ORDER BY
                os.collected_at DESC,
                os.id DESC
            LIMIT 1
            """;

        List<HistoricalOfferObservation> observations =
            hydrate(
                querySnapshotRows(
                    sql,
                    statement -> {

                        statement.setString(
                            1,
                            asin.value()
                        );

                        statement.setObject(
                            2,
                            collectedAt
                        );
                    }
                )
            );

        return observations.stream()
            .findFirst();
    }

    @Override
    public long countByAsin(
        Asin asin
    ) {

        Objects.requireNonNull(
            asin,
            "asin must not be null"
        );

        String sql = """
            SELECT COUNT(*) AS snapshot_count
            FROM product p
            JOIN offer_snapshot os
              ON os.product_id = p.id
            WHERE p.asin = ?
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
                    throw new SQLException(
                        "Historical snapshot count query returned no row"
                    );
                }

                return resultSet.getLong(
                    "snapshot_count"
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to count historical offer snapshots",
                exception
            );
        }
    }

    /**
     * Executa uma consulta de cabeçalhos históricos.
     *
     * <p>As condições comerciais são carregadas separadamente em lote,
     * evitando multiplicar as linhas dos snapshots pelo JOIN com
     * condições e métodos de pagamento.</p>
     */
    private List<SnapshotRow> querySnapshotRows(
        String sql,
        StatementBinder binder
    ) {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            binder.bind(
                statement
            );

            List<SnapshotRow> rows =
                new ArrayList<>();

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    BigDecimal soldPercentageValue =
                        resultSet.getBigDecimal(
                            "sold_percentage"
                        );

                    Percentage soldPercentage =
                        soldPercentageValue == null
                            ? null
                            : new Percentage(
                            soldPercentageValue
                        );

                    rows.add(
                        new SnapshotRow(
                            resultSet.getLong(
                                "snapshot_id"
                            ),
                            new Asin(
                                resultSet.getString(
                                    "asin"
                                )
                            ),
                            resultSet.getObject(
                                "collected_at",
                                OffsetDateTime.class
                            ),
                            new Money(
                                resultSet.getBigDecimal(
                                    "current_price"
                                )
                            ),
                            soldPercentage,
                            resultSet.getString(
                                "source"
                            )
                        )
                    );
                }
            }

            return List.copyOf(
                rows
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to query historical offer snapshots",
                exception
            );
        }
    }

    /**
     * Reconstrói as observações completas carregando as condições
     * comerciais de todos os snapshots em uma única consulta adicional.
     */
    private List<HistoricalOfferObservation> hydrate(
        List<SnapshotRow> rows
    ) {

        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> snapshotIds =
            rows.stream()
                .map(
                    SnapshotRow::snapshotId
                )
                .toList();

        Map<Long, List<PaymentCondition>>
            conditionsBySnapshot =
            loadPaymentConditions(
                snapshotIds
            );

        List<HistoricalOfferObservation> observations =
            new ArrayList<>();

        for (SnapshotRow row : rows) {

            observations.add(
                new HistoricalOfferObservation(
                    row.snapshotId(),
                    row.asin(),
                    row.collectedAt(),
                    row.currentPrice(),
                    row.soldPercentage(),
                    row.source(),
                    conditionsBySnapshot.getOrDefault(
                        row.snapshotId(),
                        List.of()
                    )
                )
            );
        }

        return List.copyOf(
            observations
        );
    }

    /**
     * Carrega condições comerciais em lote.
     *
     * <p>Para N snapshots, o repository executa duas consultas
     * principais:</p>
     *
     * <pre>
     * 1. consulta dos snapshots
     * 2. consulta das condições e métodos
     * </pre>
     *
     * <p>Isso evita o problema de N+1 queries.</p>
     */
    private Map<Long, List<PaymentCondition>>
    loadPaymentConditions(
        List<Long> snapshotIds
    ) {

        if (snapshotIds.isEmpty()) {
            return Map.of();
        }

        String placeholders =
            snapshotIds.stream()
                .map(
                    ignored -> "?"
                )
                .collect(
                    Collectors.joining(
                        ", "
                    )
                );

        String sql = """
            SELECT
                opc.offer_snapshot_id,
                opc.id AS payment_condition_id,
                opc.condition_type,
                opc.price,
                opc.discount_percentage,
                opc.installment_count,
                opc.installment_amount,
                opc.installment_total,
                opc.interest,
                opcm.payment_method
            FROM offer_payment_condition opc
            LEFT JOIN offer_payment_condition_method opcm
              ON opcm.payment_condition_id = opc.id
            WHERE opc.offer_snapshot_id IN (%s)
            ORDER BY
                opc.offer_snapshot_id ASC,
                opc.id ASC,
                opcm.id ASC
            """.formatted(
            placeholders
        );

        Map<
            Long,
            LinkedHashMap<
                Long,
                MutablePaymentCondition
                >
            > mutableConditions =
            new LinkedHashMap<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            for (
                int index = 0;
                index < snapshotIds.size();
                index++
            ) {

                statement.setLong(
                    index + 1,
                    snapshotIds.get(
                        index
                    )
                );
            }

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    long snapshotId =
                        resultSet.getLong(
                            "offer_snapshot_id"
                        );

                    long conditionId =
                        resultSet.getLong(
                            "payment_condition_id"
                        );

                    LinkedHashMap<
                        Long,
                        MutablePaymentCondition
                        > snapshotConditions =
                        mutableConditions.computeIfAbsent(
                            snapshotId,
                            ignored ->
                                new LinkedHashMap<>()
                        );

                    MutablePaymentCondition condition =
                        snapshotConditions.get(
                            conditionId
                        );

                    if (condition == null) {

                        condition =
                            readPaymentCondition(
                                resultSet
                            );

                        snapshotConditions.put(
                            conditionId,
                            condition
                        );
                    }

                    String paymentMethodValue =
                        resultSet.getString(
                            "payment_method"
                        );

                    if (paymentMethodValue != null) {

                        condition.addPaymentMethod(
                            PaymentMethod.valueOf(
                                paymentMethodValue
                            )
                        );
                    }
                }
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to query historical payment conditions",
                exception
            );
        }

        Map<Long, List<PaymentCondition>> result =
            new HashMap<>();

        for (
            Map.Entry<
                Long,
                LinkedHashMap<
                    Long,
                    MutablePaymentCondition
                    >
                > snapshotEntry
            : mutableConditions.entrySet()
        ) {

            List<PaymentCondition> conditions =
                snapshotEntry.getValue()
                    .values()
                    .stream()
                    .map(
                        MutablePaymentCondition::toDomain
                    )
                    .toList();

            result.put(
                snapshotEntry.getKey(),
                conditions
            );
        }

        return Map.copyOf(
            result
        );
    }

    /**
     * Reconstrói os campos próprios da condição comercial.
     *
     * <p>Os métodos de pagamento são adicionados posteriormente,
     * porque uma única condição pode produzir várias linhas no
     * resultado do LEFT JOIN.</p>
     */
    private MutablePaymentCondition readPaymentCondition(
        ResultSet resultSet
    ) throws SQLException {

        return new MutablePaymentCondition(
            PaymentConditionType.valueOf(
                resultSet.getString(
                    "condition_type"
                )
            ),
            nullableMoney(
                resultSet.getBigDecimal(
                    "price"
                )
            ),
            nullablePercentage(
                resultSet.getBigDecimal(
                    "discount_percentage"
                )
            ),
            resultSet.getObject(
                "installment_count",
                Integer.class
            ),
            nullableMoney(
                resultSet.getBigDecimal(
                    "installment_amount"
                )
            ),
            nullableMoney(
                resultSet.getBigDecimal(
                    "installment_total"
                )
            ),
            nullablePercentage(
                resultSet.getBigDecimal(
                    "interest"
                )
            )
        );
    }

    private Money nullableMoney(
        BigDecimal value
    ) {

        return value == null
            ? null
            : new Money(
            value
        );
    }

    private Percentage nullablePercentage(
        BigDecimal value
    ) {

        return value == null
            ? null
            : new Percentage(
            value
        );
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(
            PreparedStatement statement
        ) throws SQLException;
    }

    /**
     * Linha mínima obtida diretamente da consulta de snapshots.
     */
    private record SnapshotRow(
        long snapshotId,
        Asin asin,
        OffsetDateTime collectedAt,
        Money currentPrice,
        Percentage soldPercentage,
        String source
    ) {
    }

    /**
     * Estrutura mutável exclusivamente interna ao processo de hidratação
     * JDBC.
     *
     * <p>Ela existe porque uma condição com dois métodos de pagamento
     * produz duas linhas no resultado SQL.</p>
     */
    private static final class MutablePaymentCondition {

        private final PaymentConditionType type;

        private final Money price;

        private final Percentage discountPercentage;

        private final Integer installmentCount;

        private final Money installmentAmount;

        private final Money installmentTotal;

        private final Percentage interest;

        private final Set<PaymentMethod> paymentMethods =
            new LinkedHashSet<>();

        private MutablePaymentCondition(
            PaymentConditionType type,
            Money price,
            Percentage discountPercentage,
            Integer installmentCount,
            Money installmentAmount,
            Money installmentTotal,
            Percentage interest
        ) {

            this.type = type;
            this.price = price;
            this.discountPercentage =
                discountPercentage;
            this.installmentCount =
                installmentCount;
            this.installmentAmount =
                installmentAmount;
            this.installmentTotal =
                installmentTotal;
            this.interest = interest;
        }

        private void addPaymentMethod(
            PaymentMethod paymentMethod
        ) {

            paymentMethods.add(
                paymentMethod
            );
        }

        private PaymentCondition toDomain() {

            return new PaymentCondition(
                type,
                price,
                discountPercentage,
                installmentCount,
                installmentAmount,
                installmentTotal,
                interest,
                new ArrayList<>(
                    paymentMethods
                )
            );
        }
    }
}
