package com.raspingamazon.domain.commercial;

import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;

import java.util.List;
import java.util.Objects;

/**
 * Representa uma condição comercial de pagamento de uma oferta.
 *
 * Uma condição pode representar:
 *
 * - pagamento à vista;
 * - pagamento parcelado no crédito.
 *
 * A classe pertence ao domínio e não possui conhecimento sobre PostgreSQL,
 * JDBC, HTML, Amazon ou qualquer outra infraestrutura externa.
 *
 * Os valores desta classe devem representar somente informações que já
 * tenham sido identificadas pelas camadas responsáveis pela coleta e
 * interpretação comercial.
 */
public final class PaymentCondition {

    private final PaymentConditionType type;
    private final Money price;
    private final Percentage discountPercentage;
    private final Integer installmentCount;
    private final Money installmentAmount;
    private final Money installmentTotal;
    private final Percentage interest;
    private final List<PaymentMethod> paymentMethods;

    /**
     * Cria uma condição comercial.
     *
     * @param type tipo da condição comercial
     * @param price preço associado à condição, quando informado
     * @param discountPercentage percentual de desconto, quando informado
     * @param installmentCount quantidade de parcelas, quando aplicável
     * @param installmentAmount valor de cada parcela, quando aplicável
     * @param installmentTotal total do parcelamento, quando aplicável
     * @param interest percentual de juros, quando informado
     * @param paymentMethods métodos de pagamento explicitamente identificados
     */
    public PaymentCondition(
            PaymentConditionType type,
            Money price,
            Percentage discountPercentage,
            Integer installmentCount,
            Money installmentAmount,
            Money installmentTotal,
            Percentage interest,
            List<PaymentMethod> paymentMethods
    ) {
        this.type = Objects.requireNonNull(
                type,
                "type must not be null"
        );

        this.price = price;
        this.discountPercentage = discountPercentage;
        this.installmentCount = installmentCount;
        this.installmentAmount = installmentAmount;
        this.installmentTotal = installmentTotal;
        this.interest = interest;

        Objects.requireNonNull(
                paymentMethods,
                "paymentMethods must not be null"
        );

        this.paymentMethods = List.copyOf(paymentMethods);

        validateInstallmentFields();
    }

    /**
     * Valida a coerência estrutural dos campos de parcelamento.
     *
     * A condição CASH não deve carregar dados de parcelamento.
     *
     * A condição CREDIT_INSTALLMENT precisa possuir quantidade positiva
     * de parcelas, valor da parcela e total do parcelamento.
     */
    private void validateInstallmentFields() {

        if (type == PaymentConditionType.CASH) {

            if (installmentCount != null) {
                throw new IllegalArgumentException(
                        "CASH condition must not have installment count."
                );
            }

            if (installmentAmount != null) {
                throw new IllegalArgumentException(
                        "CASH condition must not have installment amount."
                );
            }

            if (installmentTotal != null) {
                throw new IllegalArgumentException(
                        "CASH condition must not have installment total."
                );
            }

            return;
        }

        if (type == PaymentConditionType.CREDIT_INSTALLMENT) {

            if (installmentCount == null || installmentCount <= 0) {
                throw new IllegalArgumentException(
                        "CREDIT_INSTALLMENT condition must have a positive installment count."
                );
            }

            if (installmentAmount == null) {
                throw new IllegalArgumentException(
                        "CREDIT_INSTALLMENT condition must have installment amount."
                );
            }

            if (installmentTotal == null) {
                throw new IllegalArgumentException(
                        "CREDIT_INSTALLMENT condition must have installment total."
                );
            }
        }
    }

    /**
     * Retorna o tipo da condição comercial.
     */
    public PaymentConditionType type() {
        return type;
    }

    /**
     * Retorna o preço associado à condição, quando disponível.
     */
    public Money price() {
        return price;
    }

    /**
     * Retorna o percentual de desconto, quando disponível.
     */
    public Percentage discountPercentage() {
        return discountPercentage;
    }

    /**
     * Retorna a quantidade de parcelas, quando aplicável.
     */
    public Integer installmentCount() {
        return installmentCount;
    }

    /**
     * Retorna o valor de cada parcela, quando aplicável.
     */
    public Money installmentAmount() {
        return installmentAmount;
    }

    /**
     * Retorna o total do parcelamento, quando aplicável.
     */
    public Money installmentTotal() {
        return installmentTotal;
    }

    /**
     * Retorna o percentual de juros, quando disponível.
     */
    public Percentage interest() {
        return interest;
    }

    /**
     * Retorna os métodos de pagamento explicitamente identificados.
     *
     * A lista retornada é imutável.
     */
    public List<PaymentMethod> paymentMethods() {
        return paymentMethods;
    }
}