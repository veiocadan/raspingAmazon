package com.raspingamazon.domain.publication;

import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do ciclo de vida da Publication.
 *
 * <p>O objetivo principal desta classe é garantir que as transições
 * permitidas pelo domínio funcionem e que transições inválidas sejam
 * rejeitadas.</p>
 */
class PublicationTest {

    /**
     * Uma Publication recém-criada deve iniciar no estado informado
     * pelo seu construtor.
     */
    @Test
    void shouldCreatePublicationWithCreatedStatus() {
        Publication publication = createPublication(
                PublicationStatus.CREATED
        );

        assertEquals(
                PublicationStatus.CREATED,
                publication.status()
        );
    }

    /**
     * CREATED -> READY é uma transição válida.
     */
    @Test
    void shouldMoveFromCreatedToReady() {
        Publication publication = createPublication(
                PublicationStatus.CREATED
        );

        publication.markReady();

        assertEquals(
                PublicationStatus.READY,
                publication.status()
        );
    }

    /**
     * READY -> PUBLISHED representa uma publicação concluída.
     */
    @Test
    void shouldMoveFromReadyToPublished() {
        Publication publication = createPublication(
                PublicationStatus.READY
        );

        publication.markPublished();

        assertEquals(
                PublicationStatus.PUBLISHED,
                publication.status()
        );
    }

    /**
     * READY -> FAILED representa uma falha durante o processo
     * de publicação.
     */
    @Test
    void shouldMoveFromReadyToFailed() {
        Publication publication = createPublication(
                PublicationStatus.READY
        );

        publication.markFailed();

        assertEquals(
                PublicationStatus.FAILED,
                publication.status()
        );
    }

    /**
     * FAILED -> READY representa o retorno ao fluxo para um
     * eventual retry autorizado por uma camada posterior.
     */
    @Test
    void shouldRetryFailedPublication() {
        Publication publication = createPublication(
                PublicationStatus.FAILED
        );

        publication.retry();

        assertEquals(
                PublicationStatus.READY,
                publication.status()
        );
    }

    /**
     * Uma Publication já publicada não pode voltar para READY.
     */
    @Test
    void shouldRejectReadyTransitionFromPublished() {
        Publication publication = createPublication(
                PublicationStatus.PUBLISHED
        );

        assertThrows(
                IllegalStateException.class,
                publication::markReady
        );

        assertEquals(
                PublicationStatus.PUBLISHED,
                publication.status()
        );
    }

    /**
     * Uma Publication já publicada não pode ser marcada novamente
     * como publicada.
     */
    @Test
    void shouldRejectPublishingAlreadyPublishedPublication() {
        Publication publication = createPublication(
                PublicationStatus.PUBLISHED
        );

        assertThrows(
                IllegalStateException.class,
                publication::markPublished
        );

        assertEquals(
                PublicationStatus.PUBLISHED,
                publication.status()
        );
    }

    /**
     * Uma Publication FAILED não pode ser marcada diretamente como
     * PUBLISHED. Primeiro ela precisa retornar para READY.
     */
    @Test
    void shouldRejectPublishingFailedPublicationDirectly() {
        Publication publication = createPublication(
                PublicationStatus.FAILED
        );

        assertThrows(
                IllegalStateException.class,
                publication::markPublished
        );

        assertEquals(
                PublicationStatus.FAILED,
                publication.status()
        );
    }

    /**
     * Uma Publication CREATED não pode ser publicada diretamente.
     */
    @Test
    void shouldRejectPublishingCreatedPublicationDirectly() {
        Publication publication = createPublication(
                PublicationStatus.CREATED
        );

        assertThrows(
                IllegalStateException.class,
                publication::markPublished
        );

        assertEquals(
                PublicationStatus.CREATED,
                publication.status()
        );
    }

    /**
     * Uma Publication CREATED não pode ser marcada como FAILED
     * diretamente.
     */
    @Test
    void shouldRejectFailingCreatedPublicationDirectly() {
        Publication publication = createPublication(
                PublicationStatus.CREATED
        );

        assertThrows(
                IllegalStateException.class,
                publication::markFailed
        );

        assertEquals(
                PublicationStatus.CREATED,
                publication.status()
        );
    }

    /**
     * Uma Publication READY não pode executar retry, pois retry
     * é uma transição exclusiva de FAILED.
     */
    @Test
    void shouldRejectRetryFromReady() {
        Publication publication = createPublication(
                PublicationStatus.READY
        );

        assertThrows(
                IllegalStateException.class,
                publication::retry
        );

        assertEquals(
                PublicationStatus.READY,
                publication.status()
        );
    }

    /**
     * Uma Publication CREATED não pode executar retry.
     */
    @Test
    void shouldRejectRetryFromCreated() {
        Publication publication = createPublication(
                PublicationStatus.CREATED
        );

        assertThrows(
                IllegalStateException.class,
                publication::retry
        );

        assertEquals(
                PublicationStatus.CREATED,
                publication.status()
        );
    }

    /**
     * Uma Publication PUBLISHED não pode executar retry.
     */
    @Test
    void shouldRejectRetryFromPublished() {
        Publication publication = createPublication(
                PublicationStatus.PUBLISHED
        );

        assertThrows(
                IllegalStateException.class,
                publication::retry
        );

        assertEquals(
                PublicationStatus.PUBLISHED,
                publication.status()
        );
    }

    /**
     * Cria uma Publication com dados mínimos válidos.
     *
     * <p>Os testes de ciclo de vida não devem depender de infraestrutura.
     * Por isso, toda a cadeia de objetos é construída diretamente em memória.</p>
     */
    private Publication createPublication(PublicationStatus status) {
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
                null,
                Money.of("120.00"),
                Percentage.of("16.67"),
                Percentage.of("50"),
                4.5,
                100L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST"
        );

        DealEvaluation evaluation = new DealEvaluation(
                1L,
                snapshot,
                true,
                null,
                "v1",
                null,
                null,
                OffsetDateTime.now()
        );

        return new Publication(
                1L,
                evaluation,
                "template-v1",
                "Oferta de teste",
                "https://example.com/affiliate",
                status,
                OffsetDateTime.now()
        );
    }
}