package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.shared.Percentage;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Representa a melhor observação de desconto à vista encontrada
 * nas condições comerciais de uma oferta.
 *
 * A observação preserva:
 *
 * - o percentual de desconto;
 * - todos os meios de pagamento reconhecidos que atingem esse
 *   mesmo melhor percentual.
 *
 * Exemplo:
 *
 * Pix   = 10%
 * NuPay = 15%
 *
 * Resultado:
 *
 * discountPercentage = 15%
 * paymentMethods      = [NUPAY_ADDITIONAL_LIMIT]
 *
 * Outro exemplo:
 *
 * Pix   = 15%
 * NuPay = 15%
 *
 * Resultado:
 *
 * discountPercentage = 15%
 * paymentMethods      = [PIX, NUPAY_ADDITIONAL_LIMIT]
 *
 * Esta classe não decide o que será publicado. Ela representa
 * somente o fato comercial observado.
 */
public record CashDiscountObservation(
    Percentage discountPercentage,
    List<PaymentMethod> paymentMethods
) {

    public CashDiscountObservation {

        Objects.requireNonNull(
            discountPercentage,
            "discountPercentage must not be null"
        );

        Objects.requireNonNull(
            paymentMethods,
            "paymentMethods must not be null"
        );

        if (paymentMethods.isEmpty()) {
            throw new IllegalArgumentException(
                "paymentMethods must not be empty"
            );
        }

        paymentMethods = List.copyOf(
            paymentMethods
        );

        for (PaymentMethod paymentMethod : paymentMethods) {

            if (paymentMethod != PaymentMethod.PIX
                && paymentMethod != PaymentMethod.NUPAY_ADDITIONAL_LIMIT) {

                throw new IllegalArgumentException(
                    "Cash discount observation supports only recognized cash payment methods"
                );
            }
        }
    }

    /**
     * Produz uma representação textual estável para auditoria.
     *
     * Exemplos:
     *
     * 15|PIX
     *
     * 15|NUPAY_ADDITIONAL_LIMIT
     *
     * 15|PIX,NUPAY_ADDITIONAL_LIMIT
     */
    public String auditValue() {

        String discount =
            discountPercentage
                .value()
                .stripTrailingZeros()
                .toPlainString();

        String methods =
            paymentMethods
                .stream()
                .map(Enum::name)
                .collect(
                    Collectors.joining(",")
                );

        return discount + "|" + methods;
    }
}
