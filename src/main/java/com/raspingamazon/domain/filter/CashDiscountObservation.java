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
 * <p>A observação preserva:</p>
 *
 * <ul>
 *     <li>o percentual de desconto;</li>
 *     <li>todos os meios de pagamento reconhecidos que atingem esse
 *         mesmo melhor percentual.</li>
 * </ul>
 *
 * <p>São reconhecidos como meios à vista:</p>
 *
 * <ul>
 *     <li>Pix;</li>
 *     <li>NuPay;</li>
 *     <li>NuPay Limite Adicional.</li>
 * </ul>
 *
 * <p>Esta classe não decide o que será publicado. Ela representa
 * somente o fato comercial observado.</p>
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

        paymentMethods =
            List.copyOf(
                paymentMethods
            );

        for (PaymentMethod paymentMethod : paymentMethods) {

            if (!isRecognizedCashMethod(
                paymentMethod
            )) {

                throw new IllegalArgumentException(
                    "Cash discount observation supports only recognized cash payment methods"
                );
            }
        }
    }

    /**
     * Produz uma representação textual estável para auditoria.
     *
     * <p>Exemplos:</p>
     *
     * <pre>
     * 15|PIX
     * 15|NUPAY
     * 15|NUPAY_ADDITIONAL_LIMIT
     * 15|PIX,NUPAY
     * </pre>
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
                .map(
                    Enum::name
                )
                .collect(
                    Collectors.joining(
                        ","
                    )
                );

        return discount
            + "|"
            + methods;
    }

    private static boolean isRecognizedCashMethod(
        PaymentMethod paymentMethod
    ) {

        return paymentMethod
            == PaymentMethod.PIX
            || paymentMethod
            == PaymentMethod.NUPAY
            || paymentMethod
            == PaymentMethod.NUPAY_ADDITIONAL_LIMIT;
    }
}
