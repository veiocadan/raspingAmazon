package com.raspingamazon.application.deal;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do transporte ParsedDeal + enrichment -> OfferSnapshot.
 */
class OfferSnapshotFactoryTest {

    private static final String ASIN =
            "B087WLJH8Y";

    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse(
                    "2026-09-18T12:00:00Z"
            );

    private static final OffsetDateTime ENRICHED_AT =
            OffsetDateTime.parse(
                    "2026-09-18T12:01:00Z"
            );

    private final OfferSnapshotFactory factory =
            new OfferSnapshotFactory();

    @Test
    void shouldTransportParsedDealFieldsToOfferSnapshot() {

        Product product =
                createProduct(
                        ASIN
                );

        ParsedDeal parsedDeal =
                new ParsedDeal(
                        ASIN,
                        "https://www.amazon.com.br/dp/" + ASIN,
                        "Produto de teste",
                        "https://example.com/image.jpg",
                        new BigDecimal("99.90"),
                        new BigDecimal("129.90"),
                        null,
                        new BigDecimal("48"),
                        4.7,
                        1520L,
                        COLLECTED_AT,
                        "https://www.amazon.com.br/deals"
                );

        ProductEnrichmentResult enrichment =
                createAmazonEnrichment(
                        ASIN
                );

        OfferSnapshot snapshot =
                factory.create(
                        product,
                        parsedDeal,
                        enrichment
                );

        /*
         * Snapshot ainda não foi persistido.
         */
        assertNull(
                snapshot.id()
        );

        assertEquals(
                product,
                snapshot.product()
        );

        assertEquals(
                COLLECTED_AT,
                snapshot.collectedAt()
        );

        assertEquals(
                Money.of("99.90"),
                snapshot.currentPrice()
        );

        assertEquals(
                Money.of("129.90"),
                snapshot.basisPrice()
        );

        assertNull(
                snapshot.previousPrice()
        );

        assertEquals(
                Percentage.of("48"),
                snapshot.soldPercentage()
        );

        /*
         * Campos fechados na D2.
         */
        assertEquals(
                4.7,
                snapshot.rating()
        );

        assertEquals(
                1520L,
                snapshot.reviewCount()
        );

        /*
         * Evidências de enrichment.
         */
        assertEquals(
                "Amazon.com.br",
                snapshot.sellerName()
        );

        assertEquals(
                "Amazon.com.br",
                snapshot.deliveryProvider()
        );

        assertEquals(
                SellerType.AMAZON,
                snapshot.sellerType()
        );

        assertEquals(
                DeliveryType.AMAZON,
                snapshot.deliveryType()
        );

        assertEquals(
                "https://www.amazon.com.br/deals",
                snapshot.source()
        );

        assertEquals(
                List.of(),
                snapshot.paymentConditions()
        );
    }

    @Test
    void shouldPreserveMissingOptionalCommercialFields() {

        Product product =
                createProduct(
                        ASIN
                );

        ParsedDeal parsedDeal =
                new ParsedDeal(
                        ASIN,
                        "https://www.amazon.com.br/dp/" + ASIN,
                        "Produto sem dados opcionais",
                        null,
                        new BigDecimal("99.90"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        COLLECTED_AT,
                        "https://www.amazon.com.br/deals"
                );

        OfferSnapshot snapshot =
                factory.create(
                        product,
                        parsedDeal,
                        createAmazonEnrichment(
                                ASIN
                        )
                );

        assertNull(
                snapshot.basisPrice()
        );

        assertNull(
                snapshot.previousPrice()
        );

        assertNull(
                snapshot.soldPercentage()
        );

        assertNull(
                snapshot.rating()
        );

        assertNull(
                snapshot.reviewCount()
        );
    }

    @Test
    void shouldRejectDifferentParsedDealAsin() {

        Product product =
                createProduct(
                        ASIN
                );

        ParsedDeal parsedDeal =
                new ParsedDeal(
                        "B000000002",
                        "https://www.amazon.com.br/dp/B000000002",
                        "Outro produto",
                        null,
                        new BigDecimal("10.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        COLLECTED_AT,
                        "https://www.amazon.com.br/deals"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        product,
                        parsedDeal,
                        createAmazonEnrichment(
                                ASIN
                        )
                )
        );
    }

    @Test
    void shouldRejectDifferentEnrichmentAsin() {

        Product product =
                createProduct(
                        ASIN
                );

        ParsedDeal parsedDeal =
                new ParsedDeal(
                        ASIN,
                        "https://www.amazon.com.br/dp/" + ASIN,
                        "Produto de teste",
                        null,
                        new BigDecimal("10.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        COLLECTED_AT,
                        "https://www.amazon.com.br/deals"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(
                        product,
                        parsedDeal,
                        createAmazonEnrichment(
                                "B000000002"
                        )
                )
        );
    }

    private Product createProduct(
            String asin
    ) {
        return new Product(
                1L,
                new Asin(
                        asin
                ),
                "Produto de teste",
                "https://example.com/image.jpg",
                "https://www.amazon.com.br/dp/" + asin
        );
    }

    private ProductEnrichmentResult createAmazonEnrichment(
            String asin
    ) {
        return new ProductEnrichmentResult(
                asin,

                new SellerEvidence(
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature"
                ),

                new DeliveryEvidence(
                        "Amazon.com.br",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature"
                ),

                "AMAZON_PRODUCT_PAGE",

                "https://www.amazon.com.br/dp/" + asin,

                ENRICHED_AT
        );
    }
}