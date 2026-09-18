package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do contrato de enriquecimento.
 */
class ProductEnrichmentResultTest {

    private static final OffsetDateTime ENRICHED_AT =
            OffsetDateTime.parse(
                    "2026-09-17T10:00:00-03:00"
            );

    @Test
    void shouldCreateCompleteAmazonAmazonResult() {

        SellerEvidence sellerEvidence =
                new SellerEvidence(
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature"
                );

        DeliveryEvidence deliveryEvidence =
                new DeliveryEvidence(
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature"
                );

        ProductEnrichmentResult result =
                new ProductEnrichmentResult(
                        "B012345678",
                        sellerEvidence,
                        deliveryEvidence,
                        "AMAZON_PRODUCT_PAGE",
                        "https://www.amazon.com.br/dp/B012345678",
                        ENRICHED_AT
                );

        /*
         * O teste verifica campo a campo.
         *
         * Isso é importante porque foi justamente a ausência
         * dessas verificações que permitiu o bug anterior.
         */
        assertEquals(
                "B012345678",
                result.asin()
        );

        assertEquals(
                "Amazon.com.br",
                result.sellerEvidence().rawValue()
        );

        assertEquals(
                SellerType.AMAZON,
                result.sellerEvidence().sellerType()
        );

        assertEquals(
                "merchantInfoFeature",
                result.sellerEvidence().source()
        );

        assertEquals(
                "Amazon",
                result.deliveryEvidence().rawValue()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryEvidence().deliveryType()
        );

        assertEquals(
                "fulfillerInfoFeature",
                result.deliveryEvidence().source()
        );

        assertEquals(
                "AMAZON_PRODUCT_PAGE",
                result.source()
        );

        assertEquals(
                "https://www.amazon.com.br/dp/B012345678",
                result.productUrl()
        );

        assertEquals(
                ENRICHED_AT,
                result.enrichedAt()
        );
    }

    @Test
    void shouldPreserveAmazonGlobalEvidence() {

        SellerEvidence sellerEvidence =
                new SellerEvidence(
                        "Amazon Global",
                        SellerType.AMAZON,
                        "merchantInfoFeature"
                );

        assertEquals(
                "Amazon Global",
                sellerEvidence.rawValue()
        );

        assertEquals(
                SellerType.AMAZON,
                sellerEvidence.sellerType()
        );
    }

    @Test
    void shouldAllowUnknownEvidence() {

        SellerEvidence sellerEvidence =
                new SellerEvidence(
                        null,
                        SellerType.UNKNOWN,
                        null
                );

        DeliveryEvidence deliveryEvidence =
                new DeliveryEvidence(
                        null,
                        DeliveryType.UNKNOWN,
                        null
                );

        ProductEnrichmentResult result =
                new ProductEnrichmentResult(
                        "B012345678",
                        sellerEvidence,
                        deliveryEvidence,
                        "AMAZON_PRODUCT_PAGE",
                        "https://www.amazon.com.br/dp/B012345678",
                        ENRICHED_AT
                );

        assertEquals(
                SellerType.UNKNOWN,
                result.sellerEvidence().sellerType()
        );

        assertEquals(
                DeliveryType.UNKNOWN,
                result.deliveryEvidence().deliveryType()
        );
    }

    @Test
    void shouldRejectNullSellerEvidence() {

        DeliveryEvidence deliveryEvidence =
                new DeliveryEvidence(
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature"
                );

        assertThrows(
                NullPointerException.class,
                () -> new ProductEnrichmentResult(
                        "B012345678",
                        null,
                        deliveryEvidence,
                        "AMAZON_PRODUCT_PAGE",
                        "https://www.amazon.com.br/dp/B012345678",
                        ENRICHED_AT
                )
        );
    }

    @Test
    void shouldRejectNullDeliveryEvidence() {

        SellerEvidence sellerEvidence =
                new SellerEvidence(
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature"
                );

        assertThrows(
                NullPointerException.class,
                () -> new ProductEnrichmentResult(
                        "B012345678",
                        sellerEvidence,
                        null,
                        "AMAZON_PRODUCT_PAGE",
                        "https://www.amazon.com.br/dp/B012345678",
                        ENRICHED_AT
                )
        );
    }

    @Test
    void shouldRejectBlankProductUrl() {

        SellerEvidence sellerEvidence =
                new SellerEvidence(
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature"
                );

        DeliveryEvidence deliveryEvidence =
                new DeliveryEvidence(
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProductEnrichmentResult(
                        "B012345678",
                        sellerEvidence,
                        deliveryEvidence,
                        "AMAZON_PRODUCT_PAGE",
                        "   ",
                        ENRICHED_AT
                )
        );
    }
}