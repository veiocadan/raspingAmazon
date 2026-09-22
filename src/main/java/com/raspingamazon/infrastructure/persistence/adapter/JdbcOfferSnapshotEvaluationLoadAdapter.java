package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.port.OfferSnapshotEvaluationLoadPort;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reconstrói um OfferSnapshot completo para a etapa EVALUATE_DEAL.
 *
 * <p>O snapshot assíncrono precisa ser reconstruído exclusivamente
 * a partir do estado persistente, pois a avaliação pode ocorrer em
 * outro worker ou depois de reinício da aplicação.</p>
 */
public final class JdbcOfferSnapshotEvaluationLoadAdapter
    implements OfferSnapshotEvaluationLoadPort {

    private final Connection connection;

    public JdbcOfferSnapshotEvaluationLoadAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public Optional<OfferSnapshot> findById(
        long offerSnapshotId
    ) {

        if (offerSnapshotId <= 0) {
            throw new IllegalArgumentException(
                "offerSnapshotId must be positive"
            );
        }

        try {

            Optional<SnapshotRow> snapshotRow =
                findSnapshotRow(
                    offerSnapshotId
                );

            if (snapshotRow.isEmpty()) {
                return Optional.empty();
            }

            EvidenceTypes evidenceTypes =
                loadEvidenceTypes(
                    offerSnapshotId
                );

            List<PaymentCondition> paymentConditions =
                loadPaymentConditions(
                    offerSnapshotId
                );

            return Optional.of(
                toDomain(
                    snapshotRow.get(),
                    evidenceTypes,
                    paymentConditions
                )
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Could not load OfferSnapshot for evaluation: "
                    + offerSnapshotId,
                exception
            );
        }
    }

    private Optional<SnapshotRow> findSnapshotRow(
        long offerSnapshotId
    ) throws SQLException {

        String sql =
            """
            SELECT
                snapshot.id,
                snapshot.product_id,
                snapshot.collected_at,
                snapshot.current_price,
                snapshot.basis_price,
                snapshot.previous_price,
                snapshot.sold_percentage,
                snapshot.rating,
                snapshot.review_count,
                snapshot.seller_name,
                snapshot.delivery_provider,
                snapshot.source,
                product.asin,
                product.title,
                product.image_url,
                product.product_url
            FROM offer_snapshot AS snapshot
            INNER JOIN product
                ON product.id = snapshot.product_id
            WHERE snapshot.id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                offerSnapshotId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    new SnapshotRow(
                        resultSet.getLong(
                            "id"
                        ),
                        resultSet.getLong(
                            "product_id"
                        ),
                        resultSet.getString(
                            "asin"
                        ),
                        resultSet.getString(
                            "title"
                        ),
                        resultSet.getString(
                            "image_url"
                        ),
                        resultSet.getString(
                            "product_url"
                        ),
                        resultSet.getObject(
                            "collected_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getBigDecimal(
                            "current_price"
                        ),
                        resultSet.getBigDecimal(
                            "basis_price"
                        ),
                        resultSet.getBigDecimal(
                            "previous_price"
                        ),
                        resultSet.getBigDecimal(
                            "sold_percentage"
                        ),
                        getNullableDouble(
                            resultSet,
                            "rating"
                        ),
                        getNullableLong(
                            resultSet,
                            "review_count"
                        ),
                        resultSet.getString(
                            "seller_name"
                        ),
                        resultSet.getString(
                            "delivery_provider"
                        ),
                        resultSet.getString(
                            "source"
                        )
                    )
                );
            }
        }
    }

    /**
     * Carrega as classificações normalizadas gravadas pelo enrichment.
     *
     * <p>Quando existir mais de uma evidência histórica para o mesmo
     * tipo, a de maior id é considerada a mais recente.</p>
     */
    private EvidenceTypes loadEvidenceTypes(
        long offerSnapshotId
    ) throws SQLException {

        String sql =
            """
            SELECT
                evidence_type,
                normalized_value
            FROM offer_evidence
            WHERE offer_snapshot_id = ?
              AND evidence_type IN (
                  'SELLER',
                  'DELIVERY'
              )
            ORDER BY id DESC
            """;

        SellerType sellerType =
            null;

        DeliveryType deliveryType =
            null;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                offerSnapshotId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    String evidenceType =
                        resultSet.getString(
                            "evidence_type"
                        );

                    String normalizedValue =
                        resultSet.getString(
                            "normalized_value"
                        );

                    if ("SELLER".equals(
                        evidenceType
                    )
                        && sellerType == null) {

                        sellerType =
                            SellerType.valueOf(
                                normalizedValue
                            );
                    }

                    if ("DELIVERY".equals(
                        evidenceType
                    )
                        && deliveryType == null) {

                        deliveryType =
                            DeliveryType.valueOf(
                                normalizedValue
                            );
                    }
                }
            }
        }

        if (sellerType == null) {
            throw new IllegalStateException(
                "SELLER evidence not found for OfferSnapshot "
                    + offerSnapshotId
            );
        }

        if (deliveryType == null) {
            throw new IllegalStateException(
                "DELIVERY evidence not found for OfferSnapshot "
                    + offerSnapshotId
            );
        }

        return new EvidenceTypes(
            sellerType,
            deliveryType
        );
    }

    /**
     * Reconstrói condições comerciais e métodos de pagamento usando
     * uma única consulta.
     */
    private List<PaymentCondition> loadPaymentConditions(
        long offerSnapshotId
    ) throws SQLException {

        String sql =
            """
            SELECT
                condition.id AS condition_id,
                condition.condition_type,
                condition.price,
                condition.discount_percentage,
                condition.installment_count,
                condition.installment_amount,
                condition.installment_total,
                condition.interest,
                method.payment_method
            FROM offer_payment_condition AS condition
            LEFT JOIN offer_payment_condition_method AS method
                ON method.payment_condition_id = condition.id
            WHERE condition.offer_snapshot_id = ?
            ORDER BY
                condition.id ASC,
                method.id ASC
            """;

        Map<Long, PaymentConditionRow> rows =
            new LinkedHashMap<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                offerSnapshotId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    long conditionId =
                        resultSet.getLong(
                            "condition_id"
                        );

                    PaymentConditionRow row =
                        rows.get(
                            conditionId
                        );

                    if (row == null) {

                        row =
                            new PaymentConditionRow(
                                PaymentConditionType.valueOf(
                                    resultSet.getString(
                                        "condition_type"
                                    )
                                ),
                                resultSet.getBigDecimal(
                                    "price"
                                ),
                                resultSet.getBigDecimal(
                                    "discount_percentage"
                                ),
                                getNullableInteger(
                                    resultSet,
                                    "installment_count"
                                ),
                                resultSet.getBigDecimal(
                                    "installment_amount"
                                ),
                                resultSet.getBigDecimal(
                                    "installment_total"
                                ),
                                resultSet.getBigDecimal(
                                    "interest"
                                )
                            );

                        rows.put(
                            conditionId,
                            row
                        );
                    }

                    String paymentMethod =
                        resultSet.getString(
                            "payment_method"
                        );

                    if (paymentMethod != null) {

                        row.paymentMethods.add(
                            PaymentMethod.valueOf(
                                paymentMethod
                            )
                        );
                    }
                }
            }
        }

        List<PaymentCondition> result =
            new ArrayList<>(
                rows.size()
            );

        for (PaymentConditionRow row
            : rows.values()) {

            result.add(
                new PaymentCondition(
                    row.type,
                    toMoney(
                        row.price
                    ),
                    toPercentage(
                        row.discountPercentage
                    ),
                    row.installmentCount,
                    toMoney(
                        row.installmentAmount
                    ),
                    toMoney(
                        row.installmentTotal
                    ),
                    toPercentage(
                        row.interest
                    ),
                    row.paymentMethods
                )
            );
        }

        return List.copyOf(
            result
        );
    }

    private OfferSnapshot toDomain(
        SnapshotRow row,
        EvidenceTypes evidenceTypes,
        List<PaymentCondition> paymentConditions
    ) {

        Product product =
            new Product(
                row.productId,
                new Asin(
                    row.asin
                ),
                row.title,
                row.imageUrl,
                row.productUrl
            );

        return new OfferSnapshot(
            row.snapshotId,
            product,
            row.collectedAt,
            new Money(
                row.currentPrice
            ),
            toMoney(
                row.basisPrice
            ),
            toMoney(
                row.previousPrice
            ),
            toPercentage(
                row.soldPercentage
            ),
            row.rating,
            row.reviewCount,
            row.sellerName,
            row.deliveryProvider,
            evidenceTypes.sellerType,
            evidenceTypes.deliveryType,
            row.source,
            paymentConditions
        );
    }

    private Money toMoney(
        BigDecimal value
    ) {

        if (value == null) {
            return null;
        }

        return new Money(
            value
        );
    }

    private Percentage toPercentage(
        BigDecimal value
    ) {

        if (value == null) {
            return null;
        }

        return new Percentage(
            value
        );
    }

    private Double getNullableDouble(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        double value =
            resultSet.getDouble(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private Long getNullableLong(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        long value =
            resultSet.getLong(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private Integer getNullableInteger(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        int value =
            resultSet.getInt(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private record SnapshotRow(
        long snapshotId,
        long productId,
        String asin,
        String title,
        String imageUrl,
        String productUrl,
        OffsetDateTime collectedAt,
        BigDecimal currentPrice,
        BigDecimal basisPrice,
        BigDecimal previousPrice,
        BigDecimal soldPercentage,
        Double rating,
        Long reviewCount,
        String sellerName,
        String deliveryProvider,
        String source
    ) {
    }

    private record EvidenceTypes(
        SellerType sellerType,
        DeliveryType deliveryType
    ) {
    }

    private static final class PaymentConditionRow {

        private final PaymentConditionType type;

        private final BigDecimal price;

        private final BigDecimal discountPercentage;

        private final Integer installmentCount;

        private final BigDecimal installmentAmount;

        private final BigDecimal installmentTotal;

        private final BigDecimal interest;

        private final List<PaymentMethod>
            paymentMethods =
            new ArrayList<>();

        private PaymentConditionRow(
            PaymentConditionType type,
            BigDecimal price,
            BigDecimal discountPercentage,
            Integer installmentCount,
            BigDecimal installmentAmount,
            BigDecimal installmentTotal,
            BigDecimal interest
        ) {

            this.type =
                type;

            this.price =
                price;

            this.discountPercentage =
                discountPercentage;

            this.installmentCount =
                installmentCount;

            this.installmentAmount =
                installmentAmount;

            this.installmentTotal =
                installmentTotal;

            this.interest =
                interest;
        }
    }
}
