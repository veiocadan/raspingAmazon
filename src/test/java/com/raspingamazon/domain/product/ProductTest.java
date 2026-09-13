package com.raspingamazon.domain.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa as invariantes básicas da entidade Product.
 *
 * Estes testes verificam somente o comportamento do domínio.
 * Não utilizamos PostgreSQL, JDBC ou qualquer componente externo.
 *
 * Isso mantém a FASE 3 independente da infraestrutura construída
 * anteriormente.
 */
class ProductTest {

    @Test
    void shouldCreateValidProduct() {
        Asin asin = new Asin("B0FN4BK3V7");

        Product product = new Product(
                1L,
                asin,
                "Produto de teste",
                "https://example.com/image.jpg",
                "https://www.amazon.com.br/dp/B0FN4BK3V7"
        );

        assertEquals(1L, product.id());
        assertEquals(asin, product.asin());
        assertEquals("Produto de teste", product.title());
        assertEquals(
                "https://example.com/image.jpg",
                product.imageUrl()
        );
        assertEquals(
                "https://www.amazon.com.br/dp/B0FN4BK3V7",
                product.productUrl()
        );
    }

    @Test
    void shouldAllowNullIdBeforePersistence() {
        /*
         * O domínio não deve exigir que o identificador interno já exista
         * no momento em que o produto é criado.
         */
        Product product = new Product(
                null,
                new Asin("B0FN4BK3V7"),
                "Produto de teste",
                null,
                "https://www.amazon.com.br/dp/B0FN4BK3V7"
        );

        assertNull(product.id());
    }

    @Test
    void shouldAllowNullImageUrl() {
        /*
         * image_url foi definido como opcional na modelagem do projeto.
         */
        Product product = new Product(
                1L,
                new Asin("B0FN4BK3V7"),
                "Produto de teste",
                null,
                "https://www.amazon.com.br/dp/B0FN4BK3V7"
        );

        assertNull(product.imageUrl());
    }

    @Test
    void shouldRejectNullAsin() {
        assertThrows(
                NullPointerException.class,
                () -> new Product(
                        1L,
                        null,
                        "Produto de teste",
                        null,
                        "https://www.amazon.com.br/dp/B0FN4BK3V7"
                )
        );
    }

    @Test
    void shouldRejectNullTitle() {
        assertThrows(
                NullPointerException.class,
                () -> new Product(
                        1L,
                        new Asin("B0FN4BK3V7"),
                        null,
                        null,
                        "https://www.amazon.com.br/dp/B0FN4BK3V7"
                )
        );
    }

    @Test
    void shouldRejectBlankTitle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Product(
                        1L,
                        new Asin("B0FN4BK3V7"),
                        "   ",
                        null,
                        "https://www.amazon.com.br/dp/B0FN4BK3V7"
                )
        );
    }

    @Test
    void shouldRejectNullProductUrl() {
        assertThrows(
                NullPointerException.class,
                () -> new Product(
                        1L,
                        new Asin("B0FN4BK3V7"),
                        "Produto de teste",
                        null,
                        null
                )
        );
    }

    @Test
    void shouldRejectBlankProductUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Product(
                        1L,
                        new Asin("B0FN4BK3V7"),
                        "Produto de teste",
                        null,
                        "   "
                )
        );
    }
}