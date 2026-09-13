package com.raspingamazon.domain.publication.contract;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
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
 * Testa as validações estruturais do contrato PublicationRequest.
 *
 * <p>Estes testes não verificam regras de publicação ou geração
 * de conteúdo. Essas responsabilidades pertencem a componentes
 * específicos do domínio.</p>
 */
class PublicationRequestTest {

    /**
     * Uma solicitação válida deve ser criada normalmente.
     */
    @Test
    void shouldCreateValidRequest() {
        DealEvaluation evaluation = createEvaluation();

        PublicationRequest request = new PublicationRequest(
                evaluation,
                "template-v1"
        );

        assertSame(evaluation, request.dealEvaluation());
        assertEquals("template-v1", request.templateVersion());
    }

    /**
     * A avaliação é obrigatória para o contrato.
     */
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

    /**
     * A versão do template é obrigatória.
     */
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

    /**
     * Uma versão vazia não representa uma versão válida.
     */
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

    /**
     * Espaços também não constituem uma versão válida.
     */
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

    /**
     * Cria uma DealEvaluation mínima e válida para os testes.
     *
     * <p>A construção é feita inteiramente em memória para manter
     * o teste independente de banco de dados e infraestrutura.</p>
     */
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