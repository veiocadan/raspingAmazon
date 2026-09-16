package com.raspingamazon.domain.publication.contract;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationRequestTest {

    @Test
    void shouldCreateValidRequest() {

        DealEvaluation evaluation = createEvaluation();

        PublicationRequest request = new PublicationRequest(
                evaluation,
                "template-v1"
        );

        assertSame(
                evaluation,
                request.dealEvaluation()
        );

        assertEquals(
                "template-v1",
                request.templateVersion()
        );
    }

    @Test
    void shouldRejectNullDealEvaluation() {

        assertThrows(
                NullPointerException.class,
                () -> new PublicationRequest(
                        null,
                        "template-v1"
                )
        );
    }

    @Test
    void shouldRejectNullTemplateVersion() {

        assertThrows(
                NullPointerException.class,
                () -> new PublicationRequest(
                        createEvaluation(),
                        null
                )
        );
    }

    @Test
    void shouldRejectBlankTemplateVersion() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PublicationRequest(
                        createEvaluation(),
                        ""
                )
        );
    }

    @Test
    void shouldRejectWhitespaceOnlyTemplateVersion() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new PublicationRequest(
                        createEvaluation(),
                        "   "
                )
        );
    }

    private DealEvaluation createEvaluation() {

        Product product = new Product(
                1L,
                new Asin("B000000001"),
                "Produto de teste",
                "https://example.com/image.jpg",
                "https://example.com/product"
        );

        OfferSnapshot snapshot = new OfferSnapshot(
                1L,
                product,
                OffsetDateTime.now(),
                Money.of("100.00"),
                Money.of("120.00"),
                null,
                null,
                4.5,
                100L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST",
                List.of()
        );

        return new DealEvaluation(
                1L,
                snapshot,
                true,
                null,
                "v1",
                null,
                null,
                OffsetDateTime.now()
        );
    }
}
