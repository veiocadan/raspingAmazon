package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentMethod;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Repository responsável pela persistência de PaymentCondition.
 *
 * Esta classe pertence à infraestrutura e traduz a estrutura de domínio
 * PaymentCondition para as tabelas PostgreSQL:
 *
 * - offer_payment_condition
 * - offer_payment_condition_method
 *
 * O repository não contém regras de negócio.
 *
 * Ele não decide qual condição deve ser escolhida, não calcula descontos
 * e não seleciona a quantidade de parcelas. Essas decisões pertencem às
 * camadas responsáveis pela interpretação e avaliação comercial.
 */
public final class OfferPaymentConditionRepository {

    private final Connection connection;

    /**
     * Cria um repository utilizando uma conexão JDBC existente.
     *
     * @param connection conexão utilizada para executar as operações SQL
     */
    public OfferPaymentConditionRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Persiste uma condição comercial associada a um OfferSnapshot.
     *
     * A operação possui duas etapas:
     *
     * 1. insere a condição em offer_payment_condition;
     * 2. insere os métodos de pagamento associados em
     *    offer_payment_condition_method.
     *
     * @param offerSnapshotId identificador persistente do snapshot
     * @param condition condição comercial que será persistida
     * @return identificador gerado para a condição
     * @throws SQLException quando ocorre um erro durante a operação JDBC
     */
    public long insert(
            long offerSnapshotId,
            PaymentCondition condition
    ) throws SQLException {

        String sql = """
                INSERT INTO offer_payment_condition (
                    offer_snapshot_id,
                    condition_type,
                    price,
                    discount_percentage,
                    installment_count,
                    installment_amount,
                    installment_total,
                    interest
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        long paymentConditionId;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, offerSnapshotId);
            statement.setString(2, condition.type().name());

            if (condition.price() == null) {
                statement.setObject(3, null);
            } else {
                statement.setBigDecimal(
                        3,
                        condition.price().amount()
                );
            }

            if (condition.discountPercentage() == null) {
                statement.setObject(4, null);
            } else {
                statement.setBigDecimal(
                        4,
                        condition.discountPercentage().value()
                );
            }

            if (condition.installmentCount() == null) {
                statement.setObject(5, null);
            } else {
                statement.setInt(
                        5,
                        condition.installmentCount()
                );
            }

            if (condition.installmentAmount() == null) {
                statement.setObject(6, null);
            } else {
                statement.setBigDecimal(
                        6,
                        condition.installmentAmount().amount()
                );
            }

            if (condition.installmentTotal() == null) {
                statement.setObject(7, null);
            } else {
                statement.setBigDecimal(
                        7,
                        condition.installmentTotal().amount()
                );
            }

            if (condition.interest() == null) {
                statement.setObject(8, null);
            } else {
                statement.setBigDecimal(
                        8,
                        condition.interest().value()
                );
            }

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                            "Payment condition insert did not return an id."
                    );
                }

                paymentConditionId = resultSet.getLong("id");
            }
        }

        insertPaymentMethods(
                paymentConditionId,
                condition
        );

        return paymentConditionId;
    }

    /**
     * Persiste os métodos de pagamento associados à condição.
     *
     * Cada método recebe uma linha própria na tabela
     * offer_payment_condition_method.
     */
    private void insertPaymentMethods(
            long paymentConditionId,
            PaymentCondition condition
    ) throws SQLException {

        String sql = """
                INSERT INTO offer_payment_condition_method (
                    payment_condition_id,
                    payment_method
                )
                VALUES (?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            for (PaymentMethod paymentMethod : condition.paymentMethods()) {

                statement.setLong(
                        1,
                        paymentConditionId
                );

                statement.setString(
                        2,
                        paymentMethod.name()
                );

                statement.addBatch();
            }

            statement.executeBatch();
        }
    }
}