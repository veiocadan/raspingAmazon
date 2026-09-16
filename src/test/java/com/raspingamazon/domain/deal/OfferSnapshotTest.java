package com.raspingamazon.domain.deal;

import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa as invariantes estruturais de OfferSnapshot.
 *
 * O objetivo aqui não é testar elegibilidade ou scoring.
 * Esses comportamentos pertencem a conceitos que serão implementados
 * posteriormente na FASE 3.
 */
class OfferSnapshotTest {

    private static final Product PRODUCT = new Product(
            1L,
            new Asin("B0FN4BK3V7"),
            "Produto de teste",
            null,
            "https://www.amazon.com.br/dp/B0FN4BK3V7"
    );

    @Test
    void shouldCreateValidOfferSnapshot() {
        OffsetDateTime collectedAt = OffsetDateTime.parse(
                "2026-09-13T19:00:00-03:00"
        );

        OfferSnapshot snapshot = new OfferSnapshot(
                10L,
                PRODUCT,
                collectedAt,
                Money.of("199.90"),
                Money.of("299.90"),
                Money.of("249.90"),
                Percentage.of("48"),
                4.7,
                1520L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "amazon-deals",
                List.of()
        );

        assertEquals(10L, snapshot.id());
        assertEquals(PRODUCT, snapshot.product());
        assertEquals(collectedAt, snapshot.collectedAt());
        assertEquals(Money.of("199.90"), snapshot.currentPrice());
        assertEquals(Money.of("299.90"), snapshot.basisPrice());
        assertEquals(Money.of("249.90"), snapshot.previousPrice());
        assertEquals(Percentage.of("48"), snapshot.soldPercentage());
        assertEquals(4.7, snapshot.rating());
        assertEquals(1520L, snapshot.reviewCount());
        assertEquals("Amazon.com.br", snapshot.sellerName());
        assertEquals("Amazon", snapshot.deliveryProvider());
        assertEquals(SellerType.AMAZON, snapshot.sellerType());
        assertEquals(DeliveryType.AMAZON, snapshot.deliveryType());
        assertEquals("amazon-deals", snapshot.source());
        assertEquals(List.of(), snapshot.paymentConditions());
    }

    @Test
    void shouldAllowNullIdBeforePersistence() {
        OfferSnapshot snapshot = createValidSnapshot(
                null,
                null,
                null,
                null
        );

        assertNull(snapshot.id());
    }

    @Test
    void shouldAllowOptionalOfferFieldsToBeNull() {
        /*
         * A ausência desses dados não significa zero.
         * O snapshot simplesmente registra que a fonte não forneceu
         * a informação naquele momento.
         */
        OfferSnapshot snapshot = createValidSnapshot(
                null,
                null,
                null,
                null
        );

        assertNull(snapshot.basisPrice());
        assertNull(snapshot.previousPrice());
        assertNull(snapshot.soldPercentage());
        assertNull(snapshot.rating());
        assertNull(snapshot.reviewCount());
    }

    @Test
    void shouldRejectNullProduct() {
        assertThrows(
                NullPointerException.class,
                () -> new OfferSnapshot(
                        null,
                        null,
                        OffsetDateTime.now(),
                        Money.of("199.90"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        SellerType.UNKNOWN,
                        DeliveryType.UNKNOWN,
                        "amazon-deals",
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullCollectedAt() {
        assertThrows(
                NullPointerException.class,
                () -> new OfferSnapshot(
                        null,
                        PRODUCT,
                        null,
                        Money.of("199.90"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        SellerType.UNKNOWN,
                        DeliveryType.UNKNOWN,
                        "amazon-deals",
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullCurrentPrice() {
        assertThrows(
                NullPointerException.class,
                () -> new OfferSnapshot(
                        null,
                        PRODUCT,
                        OffsetDateTime.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        SellerType.UNKNOWN,
                        DeliveryType.UNKNOWN,
                        "amazon-deals",
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullSellerType() {
        assertThrows(
                NullPointerException.class,
                () -> new OfferSnapshot(
                        null,
                        PRODUCT,
                        OffsetDateTime.now(),
                        Money.of("199.90"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        DeliveryType.UNKNOWN,
                        "amazon-deals",
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullDeliveryType() {
        assertThrows(
                NullPointerException.class,
                () -> new OfferSnapshot(
                        null,
                        PRODUCT,
                        OffsetDateTime.now(),
                        Money.of("199.90"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        SellerType.UNKNOWN,
                        null,
                        "amazon-deals",
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectNullSource() {
        assertThrows(
                NullPointerException.class,
                () -> new OfferSnapshot(
                        null,
                        PRODUCT,
                        OffsetDateTime.now(),
                        Money.of("199.90"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        SellerType.UNKNOWN,
                        DeliveryType.UNKNOWN,
                        null,
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectBlankSource() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OfferSnapshot(
                        null,
                        PRODUCT,
                        OffsetDateTime.now(),
                        Money.of("199.90"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        SellerType.UNKNOWN,
                        DeliveryType.UNKNOWN,
                        "   ",
                        List.of()
                )
        );
    }

    /**
     * Cria um snapshot mínimo para os testes que não precisam verificar
     * individualmente todos os campos.
     *
     * Os parâmetros opcionais tornam explícito quais informações estão
     * sendo deliberadamente omitidas no cenário do teste.
     */
    private static OfferSnapshot createValidSnapshot(
            Long id,
            Money basisPrice,
            Money previousPrice,
            Percentage soldPercentage
    ) {
        return new OfferSnapshot(
                id,
                PRODUCT,
                OffsetDateTime.parse("2026-09-13T19:00:00-03:00"),
                Money.of("199.90"),
                basisPrice,
                previousPrice,
                soldPercentage,
                null,
                null,
                null,
                null,
                SellerType.UNKNOWN,
                DeliveryType.UNKNOWN,
                "amazon-deals",
                List.of()
        );
    }
}