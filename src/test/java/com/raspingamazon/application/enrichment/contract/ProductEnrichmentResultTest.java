package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do contrato de resultado do enriquecimento.
 */
class ProductEnrichmentResultTest {

    private static final OffsetDateTime ENRICHED_AT =
            OffsetDateTime.parse("2026-09-17T10:00:00-03:00");

    @Test
    void shouldCreateAmazonAmazonResult() {

        ProductEnrichmentResult result =
                new ProductEnrichmentResult(
                        "B012345678",
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature",
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature",
                        "https://www.amazon.com.br/dp/B012345678",
                        ENRICHED_AT
                );

        assertEquals("B012345678", result.asin());

        assertEquals(
                "Amazon.com.br",
                result.rawSellerValue()
        );

        assertEquals(
                SellerType.AMAZON,
                result.sellerType()
        );

        assertEquals(
                "merchantInfoFeature",
                result.sellerEvidenceSource()
        );

        assertEquals(
                "Amazon",
                result.rawDeliveryValue()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryType()
        );

        assertEquals(
                "fulfillerInfoFeature",
                result.deliveryEvidenceSource()
        );
    }

    @Test
    void shouldPreserveAmazonGlobalRawValueWhileNormalizingToAmazon() {

        ProductEnrichmentResult result =
                new ProductEnrichmentResult(
                        "B012345678",
                        "Amazon Global",
                        SellerType.AMAZON,
                        "merchantInfoFeature",
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature",
                        "https://www.amazon.com.br/dp/B012345678",
                        ENRICHED_AT
                );

        /*
         * O texto original continua disponível para auditoria.
         */
        assertEquals(
                "Amazon Global",
                result.rawSellerValue()
        );

        /*
         * A classificação de domínio permanece AMAZON.
         *
         * Não existe AMAZON_GLOBAL no domínio.
         */
        assertEquals(
                SellerType.AMAZON,
                result.sellerType()
        );
    }

    @Test
    void shouldAllowThirdPartySellerWithAmazonDelivery() {

        ProductEnrichmentResult result =
                new ProductEnrichmentResult(
                        "B012345678",
                        "Imagem Hitech FULL",
                        SellerType.THIRD_PARTY,
                        "merchantInfoFeature",
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature",
                        "https://www.amazon.com.br/dp/B012345678",
                        ENRICHED_AT
                );

        /*
         * A FASE 7 apenas registra as evidências.
         *
         * Não existe aqui uma propriedade "eligible".
         *
         * A decisão pertence à FASE 8.
         */
        assertEquals(
                SellerType.THIRD_PARTY,
                result.sellerType()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryType()
        );
    }

    @Test
    void shouldAllowUnknownClassification() {

        ProductEnrichmentResult result =
                new ProductEnrichmentResult(
                        "B012345678",
                        null,
                        SellerType.UNKNOWN,
                        null,
                        null,
                        DeliveryType.UNKNOWN,
                        null,
                        "https://www.amazon.com.br/dp/B012345678",
                        ENRICHED_AT
                );

        assertEquals(
                SellerType.UNKNOWN,
                result.sellerType()
        );

        assertEquals(
                DeliveryType.UNKNOWN,
                result.deliveryType()
        );
    }

    @Test
    void shouldRejectNullAsin() {

        assertThrows(
                NullPointerException.class,
                () -> new ProductEnrichmentResult(
                        null,
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature",
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature",
                        "source",
                        ENRICHED_AT
                )
        );
    }

    @Test
    void shouldRejectNullSellerType() {

        assertThrows(
                NullPointerException.class,
                () -> new ProductEnrichmentResult(
                        "B012345678",
                        "Amazon.com.br",
                        null,
                        "merchantInfoFeature",
                        "Amazon",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature",
                        "source",
                        ENRICHED_AT
                )
        );
    }

    @Test
    void shouldRejectNullDeliveryType() {

        assertThrows(
                NullPointerException.class,
                () -> new ProductEnrichmentResult(
                        "B012345678",
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature",
                        "Amazon",
                        null,
                        "fulfillerInfoFeature",
                        "source",
                        ENRICHED_AT
                )
        );
    }
}