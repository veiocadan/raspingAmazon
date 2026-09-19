package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.application.enrichment.port.OfferEvidenceRepository;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa a fronteira entre a porta limpa da aplicação e o
 * repository antigo que ainda declara SQLException.
 */
class OfferEvidenceJdbcPersistenceAdapterTest {

    @Test
    void shouldDelegateEvidencePersistence() {

        AtomicLong capturedSnapshotId =
                new AtomicLong();

        AtomicReference<ProductEnrichmentResult> capturedResult =
                new AtomicReference<>();

        OfferEvidenceRepository repository =
                (
                        offerSnapshotId,
                        enrichmentResult
                ) -> {

                    capturedSnapshotId.set(
                            offerSnapshotId
                    );

                    capturedResult.set(
                            enrichmentResult
                    );
                };

        OfferEvidenceJdbcPersistenceAdapter adapter =
                new OfferEvidenceJdbcPersistenceAdapter(
                        repository
                );

        ProductEnrichmentResult enrichmentResult =
                createEnrichmentResult();

        adapter.save(
                123L,
                enrichmentResult
        );

        assertEquals(
                123L,
                capturedSnapshotId.get()
        );

        assertSame(
                enrichmentResult,
                capturedResult.get()
        );
    }

    @Test
    void shouldTranslateSQLExceptionToPersistenceOperationException() {

        OfferEvidenceRepository repository =
                (
                        offerSnapshotId,
                        enrichmentResult
                ) -> {
                    throw new SQLException(
                            "controlled database failure"
                    );
                };

        OfferEvidenceJdbcPersistenceAdapter adapter =
                new OfferEvidenceJdbcPersistenceAdapter(
                        repository
                );

        assertThrows(
                PersistenceOperationException.class,
                () -> adapter.save(
                        123L,
                        createEnrichmentResult()
                )
        );
    }

    private ProductEnrichmentResult createEnrichmentResult() {

        return new ProductEnrichmentResult(
                "B087WLJH8Y",

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

                "https://www.amazon.com.br/dp/B087WLJH8Y",

                OffsetDateTime.parse(
                        "2026-09-18T20:00:00Z"
                )
        );
    }
}